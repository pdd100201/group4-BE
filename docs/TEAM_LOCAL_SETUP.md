# Chạy backend trên máy thành viên

1. Pull code backend, mở thư mục `group4-BE` chứa `pom.xml` bằng IntelliJ và import Maven. Dùng JDK 21.
2. Copy `.env.example` thành `.env` trong thư mục đó.
3. Điền tên DB, tài khoản và mật khẩu MySQL trên máy mình. Import bản DB đã được team thống nhất nếu máy chưa có DB. Backend dùng `ddl-auto: validate`, không tự tạo hay nâng cấp schema.
4. Thay `JWT_SECRET` bằng chuỗi bí mật dài, ngẫu nhiên. Máy chạy backend độc lập có thể dùng khóa riêng; các instance cùng xác thực token phải dùng cùng khóa.
5. Điền Gmail gửi, App Password tương ứng (bỏ khoảng trắng) và MAIL_FROM cùng Gmail. Muốn dùng email hệ thống chung thì nhận cấu hình thật qua kênh riêng của team. Không dùng mật khẩu đăng nhập Google hay OAuth Client ID thay App Password.
6. Điền Cloudinary nếu sử dụng upload. Nếu cần cấu hình Google login riêng, bật dòng GOOGLE_CLIENT_ID và điền đúng Web Client ID tương ứng.
7. Nếu dùng SePay/ngrok, copy các file `sepay-local.properties.example`, `ngrok-local.properties.example` sang tên không có `.example`, rồi điền cấu hình thật của môi trường đang chạy. Webhook phải trỏ đến backend nhận giao dịch.
8. Chọn mục chạy chia sẻ `OnlineLearningApplication`, bấm Run. Không cần tạo cấu hình có tên khác. Nếu đã có cấu hình cũ cùng tên chứa biến môi trường, xóa các biến đó hoặc bỏ cấu hình cũ để dùng mục được lưu tại `.run/OnlineLearningApplication.run.xml`.

Cấu hình dùng `$PROJECT_DIR$` nên không phụ thuộc tên người dùng hay ổ đĩa. Đây là cấu hình dành cho việc mở backend làm project IntelliJ. Nếu mở cả thư mục cha chứa FE và BE, working directory phải trỏ vào thư mục backend hoặc thư mục cha được hỗ trợ bởi `spring.config.import`.

Không commit `.env`, App Password, JWT secret hoặc khóa Cloudinary/SePay thật. Commit `.env.example`, `.run/OnlineLearningApplication.run.xml` và tài liệu này. Các file thật đã được `.gitignore` loại trừ.

Các biến `PAYMENT_CALLBACK_URL`, `MEDIA_MAX_FILE_SIZE`, `MEDIA_MAX_REQUEST_SIZE` trong mẫu cũ không được code hiện tại đọc. SePay dùng cấu hình riêng; giới hạn upload nằm trong `application.yaml`.
