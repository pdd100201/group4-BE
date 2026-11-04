# Giáo trình theo bài học

## Luồng sử dụng

Expert vào Quản lý khóa học → mở khóa → khu vực nội dung có ba mục:

- **Tổng quan giáo trình**: bài học theo thứ tự; quiz nằm dưới bài liên quan; quiz toàn khóa nằm cuối. Thống kê số bài, quiz, câu hỏi ngân hàng và số bài chưa có quiz hoạt động.
- **Quản lý bài học**: tạo, sửa hoặc xóa nội dung theo quyền hiện có. Bài học mới mặc định hoạt động; Expert không chọn ẩn/hiện. Khóa học chỉ được xuất bản sau khi Manager duyệt. Tại tổng quan, nút lên/xuống lưu thứ tự ngay; bài học được đánh số liên tục. Tắt sắp xếp khi đang tìm kiếm để tránh đổi thứ tự ngoài phần nhìn thấy.
- **Quản lý quiz**: cấu hình thời gian, tỷ lệ đạt, số lượt, bài liên quan và câu hỏi.

Tại mỗi bài trong tổng quan có liên kết quản lý câu hỏi và tạo quiz cho đúng bài. Trang câu hỏi và trang soạn quiz có đường quay về giáo trình.

## Phân bổ câu hỏi và quiz

- Câu hỏi có phạm vi một bài học hoặc dùng chung toàn khóa. Câu hỏi cũ và câu hỏi import Excel vẫn thuộc phạm vi dùng chung cho đến khi Expert phân loại lại.
- Ngân hàng câu hỏi có bộ lọc phạm vi, tìm kiếm, loại, trạng thái và phân trang.
- Quiz vẫn có thể lấy câu hỏi từ nhiều bài trong cùng khóa để tạo đánh giá tổng hợp. Bộ lọc phạm vi trong trình soạn quiz chỉ hỗ trợ chọn câu, không âm thầm bỏ câu đã chọn khi đổi bộ lọc.
- Câu hỏi không bị tự chia ngẫu nhiên vào các bài. Expert quyết định theo chủ đề; số lượng trong tổng quan giúp nhận biết phần thiếu hoặc tập trung quá nhiều nội dung.
- Trong quiz có tổng điểm, số câu, thao tác đánh lại thứ tự câu từ 1 và chia đều trọng số 1 điểm/câu. Chia đều điểm chỉ thay đổi bản soạn; cần lưu quiz để áp dụng.
- Không yêu cầu mọi bài đều có quiz. Cảnh báo thiếu quiz phục vụ quản lý nội dung, không phải điều kiện chặn học hoặc cấp chứng nhận.

## Phía học viên

- Giáo trình sử dụng thứ tự bài do Expert lưu.
- Quiz được hiển thị ngay sau bài gắn với nó. Quiz toàn khóa hiển thị cuối giáo trình và có nhãn đánh giá toàn khóa.
- Tìm kiếm trong giáo trình tìm được tên bài và tên quiz liên quan; khi tìm thấy quiz của bài, bài đó vẫn hiển thị để giữ ngữ cảnh.
- Tab bài kiểm tra của bài chỉ chứa quiz của bài đó; quiz toàn khóa truy cập ở cuối giáo trình.
- Chưa khóa bài theo thứ tự hoặc yêu cầu đạt quiz mới được học bài tiếp. Quy tắc cấp chứng nhận vẫn do Manager/Admin cấu hình riêng.

## Quyền sửa và dữ liệu cũ

- Chỉ Expert sở hữu được sắp xếp giáo trình hoặc phân loại câu hỏi; thao tác ghi chỉ được thực hiện khi khóa Nháp hoặc Bị từ chối.
- Cơ chế giữ lại quiz/câu hỏi đã có kết quả làm bài vẫn áp dụng.
- Bài học đang có quiz hoặc câu hỏi liên kết không thể xóa; cần chuyển liên kết sang bài khác hoặc toàn khóa trước.
- Sắp xếp lưu trong một giao dịch, khóa course, kiểm tra danh sách thứ tự trước đó để từ chối yêu cầu từ giao diện đã cũ, có audit log.
- Không thêm cấp chương mới trong đợt này; cấu trúc hiện tại là khóa → bài → quiz, kèm đánh giá toàn khóa.

## Schema và API

- Thêm `questions.lesson_id` nullable, FK sang `lesson(id)`.
- Môi trường phát triển đang cấu hình Hibernate ddl-auto=update: restart backend để tạo cột. Môi trường quản lý schema riêng dùng `database/migrations/V019__question_lesson_scope.sql`. Không chạy script này lần nữa nếu Hibernate đã tạo cột.
- GET `/api/v1/expert/courses/{courseId}/curriculum` lấy tổng quan.
- PUT `.../curriculum/order` nhận `lessonIds`, `expectedLessonIds`.
- API câu hỏi nhận `lessonId` nullable khi tạo/sửa; API danh sách câu hỏi và question-bank nhận bộ lọc `lessonId`: bỏ trống = tất cả, 0 = dùng chung, ID dương = bài cụ thể.

## Kiểm chứng

Frontend được kiểm tra bằng TypeScript và Vite build. Kiểm thử backend bao phủ sắp xếp/thứ tự cũ/ID trùng hoặc khác khóa/quyền sở hữu/trạng thái xuất bản, thống kê quiz-câu hỏi, phân loại và lọc câu hỏi, tương thích request cũ, chặn xóa bài liên kết. Chưa xác nhận giao diện trực tiếp bằng trình duyệt và chưa chạy migration trên MySQL thực.
Kết quả: 66 kiểm thử thành công, không lỗi: CourseCurriculumIntegrationTests (8), ExpertQuestionIntegrationTests (5), ExpertQuizIntegrationTests (9), ExpertLessonServiceTest (23), StudentLessonIntegrationTests (4), CertificateIntegrationTests (17). Các kiểm thử tích hợp sử dụng H2; dịch vụ lưu media được mock để không phụ thuộc mạng ngoài.
