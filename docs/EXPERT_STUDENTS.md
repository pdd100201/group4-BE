# Học viên của khóa học

Expert chọn **Quản lý học viên** trong sidebar để mở `/expert/students`, tìm và chọn khóa học của mình. Trang `/expert/students?courseId=...` hiển thị danh sách và thống kê theo khóa học. Liên kết cũ `?step=students` trong trang soạn khóa tự chuyển sang trang mới.

- `GET /api/v1/expert/courses/{courseId}/students`: tìm theo tên (`search`, tối đa 100 ký tự), lọc quyền học (`access`: CURRENT/INACTIVE), tiến độ (`learning`: NOT_STARTED/LEARNING/COMPLETED), `page` từ 0 và `size` từ 1 đến 50.
- `GET /api/v1/expert/courses/{courseId}/students/{studentId}`: tiến độ các bài học đang hoạt động và lịch sử quiz trong đúng khóa học.
- `GET /api/v1/chat/contact?courseId=...&peerId=...`: xác minh quan hệ chat còn hiệu lực trước khi mở cuộc trò chuyện từ danh sách.

Danh sách đếm mỗi học viên một lần, hiển thị đăng ký có ID mới nhất. Một đăng ký mới đang chờ thanh toán không làm mất quyền học từ đăng ký khác còn hiệu lực. Quyền học sử dụng cùng quy tắc ACTIVE, validFrom, validUntil/lifetime của chức năng học và chat hiện tại. Không hiển thị thông tin thanh toán, email hoặc điện thoại.

Thống kê toàn khóa phân biệt tổng lượt đăng ký và số học viên duy nhất. Hoàn thành nghĩa là đã hoàn tất tất cả bài học đang hoạt động (khóa không có bài học không được tính hoàn thành); không đồng nghĩa đạt quiz hoặc được cấp chứng nhận. Đang học nghĩa là có tiến độ bài học hoặc lượt làm quiz và chưa hoàn thành tất cả bài học. Tiến độ được giữ theo học viên/khóa học giống màn hình học hiện tại, kể cả khi gia hạn đăng ký.

Mọi truy vấn danh sách/chi tiết đều kiểm tra quyền sở hữu khóa học; chỉ ROLE_EXPERT truy cập các endpoint này. Học viên bị khóa hoặc không còn quyền học không có nút chat. Không thay đổi dữ liệu đăng ký, quyền truy cập hay kết quả học tập.

Kiểm thử: `./mvnw -Dtest=ChatIntegrationTests test` kiểm tra quyền sở hữu, phân quyền role, tìm kiếm ký tự đặc biệt, lọc tiến độ, đăng ký trùng, hết hạn, chi tiết bài học/quiz và liên kết chat.
