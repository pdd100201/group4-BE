package com.onlinelearning.service;

import com.onlinelearning.api.EmailGateway;
import com.onlinelearning.entity.CourseRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationEmailService {
    private final ApplicationEventPublisher events;
    private final EmailGateway email;
    @Value("${app.frontend-url}") private String frontendUrl;

    // Snapshot the message inside the transaction; no lazy entities are used after commit.
    public record RegistrationCreated(Long registrationId, String recipient, String body) {}

    public void registrationCreated(CourseRegistration registration) {
        String amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
            .format(registration.getAmount()) + " đ";
        String duration = Boolean.TRUE.equals(registration.getLifetimeSnapshot()) ? "Vĩnh viễn"
            : registration.getValidityMonthsSnapshot() != null
                ? registration.getValidityMonthsSnapshot() + " tháng" : registration.getPackageNameSnapshot();
        String url = frontendUrl.replaceAll("/+$", "") + "/student/registrations/" + registration.getId();
        String body = "Chào " + registration.getStudentNameSnapshot() + ",\n\n"
            + "EduLearn đã tiếp nhận yêu cầu đăng ký khóa học của bạn. Vui lòng hoàn tất thanh toán để kích hoạt quyền học.\n\n"
            + "Mã đăng ký: #" + registration.getId() + "\n"
            + "Khóa học: " + registration.getCourse().getTitle() + "\n"
            + "Gói học: " + registration.getPackageNameSnapshot() + "\n"
            + "Thời hạn học: " + duration + "\n"
            + "Số tiền cần thanh toán: " + amount + "\n"
            + "Trạng thái tại thời điểm tạo đăng ký: Chờ thanh toán.\n\n"
            + "Quyền học chỉ được kích hoạt sau khi hệ thống xác nhận thanh toán thành công. "
            + "Thời hạn học được tính từ thời điểm kích hoạt.\n"
            + "Nếu bạn đã thanh toán sau khi nhận email này, hãy mở trang đăng ký bên dưới để xem trạng thái mới nhất. Nội dung email lưu trạng thái tại thời điểm gửi.\n"
            + "Số tiền trên là số tiền tại thời điểm đăng ký. Vui lòng kiểm tra số tiền mới nhất trên trang thanh toán trước khi chuyển khoản.\n\n"
            + "Xem đăng ký và tiếp tục thanh toán: " + url + "\n\n"
            + "Cảm ơn bạn đã học cùng EduLearn!";
        events.publishEvent(new RegistrationCreated(registration.getId(), registration.getStudentEmailSnapshot(), body));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(RegistrationCreated event) {
        try {
            email.sendCourseRegistration(event.recipient(), event.body());
            log.info("Course registration confirmation email sent: registrationId={}", event.registrationId());
        } catch (RuntimeException failure) {
            // The registration is already saved. SMTP failure must not cause a false registration error.
            // Detailed, sanitized SMTP diagnostics are logged by the gateway.
            log.warn("Course registration confirmation email failed: registrationId={}, type={}",
                event.registrationId(), failure.getClass().getSimpleName());
        }
    }
}
