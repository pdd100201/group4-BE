package com.onlinelearning.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.api.SepayGateway;
import com.onlinelearning.common.RegistrationAccessTerms;
import com.onlinelearning.config.SepayConfig;
import com.onlinelearning.dto.payment.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.exception.PaymentExpiredException;
import com.onlinelearning.repository.*;
import com.onlinelearning.service.PaymentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional
public class PaymentServiceImpl implements PaymentService {
    private static final Pattern CODE = Pattern.compile("(?<![A-Z0-9])OLS([TL])([1-9][0-9]{0,17})(?![A-Z0-9])");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private final CourseRegistrationRepository registrations;
    private final CourseRepository courses;
    private final PaymentRepository payments;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final SepayGateway gateway;
    private final SepayConfig config;
    private final ObjectMapper json;
    private final EntityManager em;

    // Receiver snapshots keep a pending QR stable across configuration changes.
    public record Receiver(String bank, String account, String qrAccount, String holder, String prefix, boolean test) {}

    public PaymentResponse initiate(String email, Long id) {
        var initial = owned(student(email), id);
        courses.findForUpdate(initial.getCourse().getId()).orElseThrow();
        var r = registrations.findForUpdate(id).orElseThrow(); em.refresh(r);
        if (r.getStatus() != RegistrationStatus.PENDING_PAYMENT || r.getCourse().getStatus() != CourseStatus.PUBLISHED)
            throw error(HttpStatus.CONFLICT, "Dang ky khong con du dieu kien thanh toan.");
        validateSnapshot(r); gateway.requireConfiguration();
        var payment = payments.findByRegistrationId(id).orElseGet(() -> {
            var p = new Payment(); p.setRegistration(r); p.setAmount(r.getAmount()); p.setProvider(config.provider());
            return payments.saveAndFlush(p);
        });
        retireExpiredUnpaidProvider(payment);
        // Do not overwrite a retired provider's financial records.
        if (!config.provider().equals(payment.getProvider()) || payment.getAmount().compareTo(r.getAmount()) != 0 || payment.getStatus() != PaymentStatus.PENDING)
            throw error(HttpStatus.CONFLICT, "Dang ky co thanh toan cu hoac khac moi truong. Can xu ly dang ky cu truoc khi tao dang ky moi.");
        var existing = transactions.findFirstByPaymentIdAndTypeOrderByIdDesc(payment.getId(), "SEPAY_PENDING");
        if (existing.isPresent()) {
            expireIfNeeded(r,payment,existing.get());
            return response(r, payment, existing.get());
        }
        var tx = new Transaction(); tx.setPayment(payment); tx.setAmount(r.getAmount()); tx.setType("SEPAY_PENDING");
        tx.setPayload(write(new Receiver(config.getBank(), config.getAccountNumber(), config.qrAccountNumber(), config.getAccountHolder(), config.getDescriptionPrefix(), config.testMode())));
        if (tx.getPayload().length() > 255) throw error(HttpStatus.BAD_REQUEST, "Cau hinh tai khoan SePay qua dai.");
        transactions.saveAndFlush(tx);
        return response(r, payment, tx);
    }

    public PaymentResponse status(String email, Long id) {
        var initial=owned(student(email),id);
        courses.findForUpdate(initial.getCourse().getId()).orElseThrow();
        var r=registrations.findForUpdate(id).orElseThrow(); em.refresh(r);
        var p = payments.findByRegistrationId(id).orElse(null);
        if(p!=null) em.refresh(p);
        var tx = p == null ? null : transactions.findFirstByPaymentIdOrderByIdDesc(p.getId()).orElse(null);
        if(tx!=null) em.refresh(tx);
        expireIfNeeded(r,p,tx);
        return response(r, p, tx);
    }

    public void expire(Long id) {
        var initial=registrations.findById(id).orElse(null);
        if(initial==null) return;
        courses.findForUpdate(initial.getCourse().getId()).orElseThrow();
        var r=registrations.findForUpdate(id).orElseThrow(); em.refresh(r);
        var p=payments.findByRegistrationId(id).orElse(null);
        if(p==null) return;
        em.refresh(p);
        var tx=transactions.findFirstByPaymentIdOrderByIdDesc(p.getId()).orElse(null);
        if(tx!=null) em.refresh(tx);
        expireIfNeeded(r,p,tx);
    }

    private void expireIfNeeded(CourseRegistration r,Payment p,Transaction tx) {
        if(p==null || tx==null || p.getStatus()!=PaymentStatus.PENDING || !Set.of("SEPAY","SEPAY_TEST").contains(p.getProvider())
            || !"SEPAY_PENDING".equals(tx.getType()) || Instant.now().isBefore(expiresAt(tx))) return;
        p.setStatus(PaymentStatus.CANCELLED);
        tx.setType("SEPAY_EXPIRED");
        if(r.getStatus()==RegistrationStatus.PENDING_PAYMENT) r.setStatus(RegistrationStatus.CANCELLED);
    }

    private Instant expiresAt(Transaction tx) { return tx.getCreatedAt().plusSeconds(300); }

    @Transactional(noRollbackFor=PaymentExpiredException.class)
    public void confirm(SepayWebhook event) {
        gateway.requireConfiguration();
        if (!"in".equals(event.transferType())) return;
        String reference = config.provider() + ":" + event.id();
        if (payments.findByProviderReference(reference).isPresent()) return;
        String code = paymentCode(event);
        if (code == null) return;
        var match = CODE.matcher(code); match.matches();
        if (("T".equals(match.group(1))) != config.testMode()) throw error(HttpStatus.CONFLICT, "Sai moi truong SePay.");
        Long transactionId = Long.parseLong(match.group(2));
        var found = transactions.findById(transactionId);
        if (found.isEmpty() || !Set.of("SEPAY_PENDING","SEPAY_EXPIRED").contains(found.get().getType())) return;
        var initial = found.get(); var initialPayment = initial.getPayment();
        courses.findForUpdate(initialPayment.getRegistration().getCourse().getId()).orElseThrow();
        var r = registrations.findForUpdate(initialPayment.getRegistration().getId()).orElseThrow();
        var p = payments.findByRegistrationId(r.getId()).orElseThrow(); var tx = transactions.findById(transactionId).orElseThrow();
        em.refresh(r); em.refresh(p); em.refresh(tx);
        if (payments.findByProviderReference(reference).isPresent()) return;
        if (!config.provider().equals(p.getProvider())) throw error(HttpStatus.CONFLICT, "Giao dich khong thuoc SePay dang cau hinh.");
        if (p.getStatus() == PaymentStatus.SUCCEEDED) return;
        if (!Set.of("SEPAY_PENDING","SEPAY_EXPIRED").contains(tx.getType())) return;
        var receiver = receiver(tx);
        if (receiver.test() != config.testMode() || !receiver.bank().equalsIgnoreCase(event.gateway()) || !receiver.account().equals(event.accountNumber()) ||
                (!receiver.qrAccount().equals(receiver.account()) && !receiver.qrAccount().equals(event.subAccount())))
            throw error(HttpStatus.BAD_REQUEST, "Giao dich khong dung tai khoan nhan.");
        if (event.transferAmount().compareTo(tx.getAmount()) != 0 || p.getAmount().compareTo(tx.getAmount()) != 0)
            throw error(HttpStatus.CONFLICT, "So tien chuyen khoan khong khop. Can doi soat thu cong.");
        Instant paidAt;
        try { paidAt = LocalDateTime.parse(event.transactionDate(), DATE).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
        catch (DateTimeException ex) { throw error(HttpStatus.BAD_REQUEST, "Thoi gian giao dich khong hop le."); }
        if (paidAt.isBefore(tx.getCreatedAt().minusSeconds(60)) || paidAt.isAfter(Instant.now().plusSeconds(300)))
            throw error(HttpStatus.BAD_REQUEST, "Thoi gian giao dich khong khop dang ky.");
        expireIfNeeded(r,p,tx);
        if("SEPAY_EXPIRED".equals(tx.getType())) throw new PaymentExpiredException();
        if(!paidAt.isBefore(expiresAt(tx))) throw error(HttpStatus.CONFLICT,"Giao dich nam ngoai thoi han thanh toan, can doi soat thu cong.");
        if(p.getStatus()!=PaymentStatus.PENDING || r.getStatus()!=RegistrationStatus.PENDING_PAYMENT)
            throw error(HttpStatus.CONFLICT,"Dang ky da huy hoac khong con cho thanh toan. Can doi soat thu cong.");
        validateSnapshot(r);
        p.setStatus(PaymentStatus.SUCCEEDED); p.setProviderReference(reference);
        tx.setType("SEPAY_SUCCEEDED"); tx.setCreatedAt(paidAt); tx.setPayload(write(Map.of("eventId", event.id(), "code", code, "test", receiver.test())));
        if (r.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
            r.setStatus(RegistrationStatus.ACTIVE); r.setValidFrom(paidAt);
            r.setValidUntil(RegistrationAccessTerms.validUntil(paidAt, r.getValidityMonthsSnapshot(), Boolean.TRUE.equals(r.getLifetimeSnapshot())));
        }
        payments.saveAndFlush(p);
    }

    private String paymentCode(SepayWebhook event) {
        String fromCode = null;
        if (event.code() != null && !event.code().isBlank()) {
            fromCode = event.code().trim().toUpperCase(Locale.ROOT);
            if (!CODE.matcher(fromCode).matches()) return null;
        }
        var matcher = CODE.matcher(event.content().toUpperCase(Locale.ROOT));
        Set<String> codes = new HashSet<>(); while (matcher.find()) codes.add(matcher.group());
        if (codes.size() != 1) return null;
        String contentCode = codes.iterator().next();
        if (fromCode != null && !fromCode.equals(contentCode)) throw error(HttpStatus.BAD_REQUEST, "Ma thanh toan khong khop noi dung.");
        return contentCode;
    }
    private PaymentResponse response(CourseRegistration r, Payment p, Transaction tx) {
        var state = p == null ? PaymentStatus.PENDING : p.getStatus();
        Instant deadline=tx!=null && Set.of("SEPAY_PENDING","SEPAY_EXPIRED").contains(tx.getType())?expiresAt(tx):null;
        boolean expired=tx!=null && "SEPAY_EXPIRED".equals(tx.getType());
        if (tx == null || state != PaymentStatus.PENDING || !"SEPAY_PENDING".equals(tx.getType())) return new PaymentResponse(r.getId(), state, gateway.configured(), config.testMode(), null, null, null, null, null, r.getAmount(),deadline,Instant.now(),expired);
        var receiver = receiver(tx);
        String code = "OLS" + (receiver.test() ? "T" : "L") + tx.getId();
        String description = (receiver.prefix().isBlank() ? "" : receiver.prefix() + " ") + code;
        return new PaymentResponse(r.getId(), state, gateway.configured(), receiver.test(), gateway.qrUrl(receiver.bank(), receiver.qrAccount(), tx.getAmount(), description),
                receiver.bank(), receiver.qrAccount(), receiver.holder(), description, tx.getAmount(),deadline,Instant.now(),false);
    }
    private void retireExpiredUnpaidProvider(Payment payment) {
        if (!"VNPAY".equals(payment.getProvider()) || payment.getStatus() != PaymentStatus.PENDING || payment.getProviderReference() != null) return;
        for (var tx : transactions.findByPaymentId(payment.getId())) {
            if (!Set.of("VNPAY_PENDING", "VNPAY_EXPIRED").contains(tx.getType())) throw error(HttpStatus.CONFLICT, "Thanh toan cu can doi soat, khong duoc ghi de.");
            try {
                var expiry = json.readTree(tx.getPayload()).get("expiresAt");
                if (expiry == null || Instant.parse(expiry.asText()).isAfter(Instant.now())) throw error(HttpStatus.CONFLICT, "Hay doi yeu cau thanh toan cu het han truoc khi tao QR SePay.");
            } catch (JsonProcessingException | DateTimeException ex) { throw error(HttpStatus.CONFLICT, "Khong xac dinh duoc tinh trang thanh toan cu."); }
        }
        payment.setProvider(config.provider());
    }
    private Receiver receiver(Transaction tx) {
        try { return json.readValue(tx.getPayload(), Receiver.class); }
        catch (JsonProcessingException ex) { throw error(HttpStatus.CONFLICT, "Du lieu QR khong hop le."); }
    }
    private void validateSnapshot(CourseRegistration r) {
        if (r.getAmount() == null || r.getAmount().signum() <= 0 || r.getAmount().stripTrailingZeros().scale() > 0 || r.getAmount().compareTo(new BigDecimal("999999999999")) > 0 || r.getPackageDurationSnapshot() == null || r.getLifetimeSnapshot() == null)
            throw error(HttpStatus.CONFLICT, "Dang ky thieu gia hoac goi hoc hop le.");
        if (config.testMode() && (r.getAmount().compareTo(new BigDecimal("1000")) < 0 || r.getAmount().compareTo(new BigDecimal("499000000")) > 0))
            throw error(HttpStatus.CONFLICT, "SePay Test Mode chi ho tro so tien tu 1000 den 499000000 VND.");
        try { RegistrationAccessTerms.validUntil(Instant.now(), r.getValidityMonthsSnapshot(), r.getLifetimeSnapshot()); }
        catch (IllegalArgumentException ex) { throw error(HttpStatus.CONFLICT, "Dang ky thieu thoi han da luu."); }
    }
    private User student(String email) {
        var u = users.findByEmailIgnoreCase(email).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Tai khoan khong ton tai."));
        if (!u.isEnabled() || u.isBlocked() || u.getRoles().stream().noneMatch(role -> "STUDENT".equals(role.getCode()))) throw error(HttpStatus.FORBIDDEN, "Chi Student dang hoat dong duoc thanh toan.");
        return u;
    }
    private CourseRegistration owned(User student, Long id) { return registrations.findByIdAndStudentId(id, student.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Khong tim thay dang ky cua ban.")); }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize payment metadata", ex); }
    }
    private ApiException error(HttpStatus status, String message) { return new ApiException(status, message); }
}
