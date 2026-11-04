# Module chứng nhận hoàn thành

## Ba màn hình

1. Học viên: `/certificates` — danh sách chứng nhận, tìm kiếm, trạng thái, phân trang, xem trước, tải PDF và kiểm tra điều kiện theo khóa học.
2. Công khai: `/verify-certificate` — nhập mã hoặc mở liên kết QR để xác minh; hiển thị tên học viên, khóa học, đơn vị cấp, ngày cấp và trạng thái. Không trả về email, đăng ký, bằng chứng học tập hay lý do thu hồi.
3. Quản lý: `/manager/certificates` hoặc `/admin/certificates` — dùng chung màn hình, tìm/lọc/phân trang chứng nhận, cấu hình điều kiện theo khóa, xem lịch sử và thu hồi có lý do.

## Bắt đầu sử dụng

- Khởi động lại backend và frontend sau khi cập nhật code. Cấu hình phát triển hiện dùng `spring.jpa.hibernate.ddl-auto=update`, Hibernate tạo bảng mới khi khởi động.
- Với môi trường quản lý schema bằng migration, áp dụng `database/migrations/V018__course_certificates.sql` theo quy trình hiện có. Chưa chạy migration trên database thực trong quá trình triển khai này.
- Đăng nhập Manager/Admin → Chứng nhận → Điều kiện cấp theo khóa → chọn khóa đã xuất bản.
- Nhập đơn vị cấp, chọn ít nhất một quiz hợp lệ, bật cấp chứng nhận và lưu phiên bản điều kiện.
- Học viên hoàn thành toàn bộ bài học đang hoạt động trong phiên bản điều kiện và đạt tất cả quiz đã chọn. Hệ thống kiểm tra tự động sau thao tác hoàn thành bài học hoặc chấm quiz.
- Với kết quả học đã có trước khi bật module: học viên vào Chứng nhận → Kiểm tra điều kiện → Kiểm tra và nhận chứng nhận.
- Chứng nhận cấp thành công hoặc bị thu hồi đều tạo thông báo trong hệ thống.

## Quy tắc nghiệp vụ

- Lần cấp đầu cần đăng ký học ACTIVE còn hiệu lực và khóa học PUBLISHED.
- Bài học phải có tiến độ hoàn thành và thời điểm hoàn thành; quiz phải có bài làm đã nộp và đạt. Module chưa dùng bài tập làm điều kiện.
- Một học viên chỉ có một chứng nhận cho mỗi khóa học. Khóa giao dịch và ràng buộc unique ngăn cấp trùng khi có yêu cầu đồng thời.
- Mỗi lần lưu điều kiện tạo phiên bản mới. Toàn bộ bài học đang hoạt động và các quiz được chọn được lưu thành snapshot.
- Khi nội dung bài học hoặc cấu hình/nội dung quiz bắt buộc thay đổi, hệ thống chặn cấp mới cho đến khi quản lý lưu phiên bản điều kiện mới. Khi lưu lại, kết quả hoàn thành/đạt đã có vẫn được tính; không tự buộc học viên thi lại.
- Tên học viên, tên khóa, giảng viên, đơn vị cấp và bằng chứng được chụp tại lúc cấp. Đổi tên hoặc sửa nội dung sau đó không thay đổi chứng nhận đã cấp.
- Hết quyền học vẫn được xem và tải chứng nhận đã cấp. Thu hồi làm mất hiệu lực, chặn tải PDF mới và không cấp lại tự động. File đã tải trước đó không thể thu hồi khỏi thiết bị; trang xác minh luôn phản ánh trạng thái hiện tại.
- Chỉ học viên sở hữu hoặc Manager/Admin được tải PDF. Trang xác minh công khai chỉ trả thông tin tối thiểu, không cache.
- Lịch sử lưu người thực hiện, thời điểm cấp/thu hồi và lý do thu hồi.
- Khóa có dữ liệu chứng nhận hoặc điều kiện cấp được giữ lại, không cho xóa cứng.

## PDF và QR

- PDF A4 ngang, tiếng Việt, mã chứng nhận và QR dẫn tới trang xác minh.
- PDF dùng ảnh lossless 216 dpi được tạo tại server, không cần thư viện PDF/QR hoặc tải font từ mạng. Chữ trong PDF hiện không chọn/copy được; trang HTML có nội dung văn bản tương ứng.
- `FRONTEND_URL` / `app.frontend-url` phải trỏ đúng URL frontend HTTP(S), không có query/fragment. Khi triển khai cần dùng URL truy cập được từ thiết bị quét QR; localhost chỉ phù hợp phát triển trên cùng máy.
- Tổng URL xác minh tối đa 271 byte UTF-8. Server cần font SansSerif hiển thị được tiếng Việt.

## Kiểm chứng

- 140 kiểm thử thành công. Đợt đầu 114 kiểm thử: CertificateIntegrationTests (17), CertificateDocumentTests (4), CourseModerationServiceTest (22), DeleteCourseTest (33), ExpertCourseServiceTest (17), StudentLessonServiceTest (21).
- Đợt bổ sung: NotificationIntegrationTests (16), StudentLessonIntegrationTests (4), StudentQuizIntegrationTests (6), tất cả thành công. Các integration test dùng mock dịch vụ lưu media để không phụ thuộc mạng ngoài.
- Kiểm thử chứng nhận bao phủ điều kiện thiếu/đạt, đăng ký hết hạn, cấu hình không hợp lệ, phiên bản cũ, thay đổi nội dung, snapshot, quyền sở hữu, thu hồi, dữ liệu xác minh công khai, thông báo, tích hợp hoàn thành bài/quiz và hai yêu cầu cấp đồng thời.
- Ma trận QR được so sánh từng ô với fixture độc lập từ ReportLab (version 10, mức L, mask 0).
- PDF mẫu thường và nội dung dài đã được render lại bằng Poppler và kiểm tra trực quan, không tràn/đè nội dung.
- Frontend: `npx tsc --noEmit` và `npm run build` thành công. ESLint hiện tại chỉ cấu hình JS/JSX nên không áp dụng cho TS/TSX của module.
- Chưa xác nhận giao diện qua trình duyệt hoặc áp dụng schema trên MySQL thực; integration tests sử dụng H2 chế độ MySQL.

PDF mẫu kiểm tra nằm trong `target/certificate-qa/` sau khi chạy CertificateDocumentTests.