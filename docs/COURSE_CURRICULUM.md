# Giáo trình và quiz cuối khóa

## Luồng Expert

Quản lý khóa học → Tạo khóa học → Lưu bản nháp → Xây dựng giáo trình.

- Tổng quan giáo trình: sắp xếp bài học bằng nút lên/xuống, xem quiz cuối khóa.
- Bài học: soạn bài và tải video. Bài mới hoạt động mặc định; khóa cần Manager duyệt để xuất bản.
- Quiz cuối khóa: nhập tên, thời lượng, điểm đạt (%) và số lượt làm tối đa; thêm câu hỏi trực tiếp, không qua ngân hàng câu hỏi.
- Mỗi câu có bốn đáp án khác nhau, một hoặc nhiều đáp án đúng, điểm nguyên dương và giải thích tùy chọn. Đổi loại câu hỏi sẽ xóa lựa chọn đáp án đúng để Expert xác nhận lại.
- Dùng nút lên/xuống để đổi thứ tự câu, hoặc bỏ câu. Giới hạn 200 câu mỗi quiz.
- Lưu quiz và câu hỏi trong một lần. Chuyển tab/bước không làm mất bản soạn; chưa lưu thì không gửi duyệt được.
- Sau khi hoàn tất nội dung, cấu hình gói học rồi kiểm tra và gửi duyệt.

## Quy tắc

- Mỗi course có đúng một quiz toàn khóa trước khi gửi duyệt. Bản nháp có thể chưa có quiz để Expert hoàn thiện dần.
- Quiz không gắn riêng với bài học; câu hỏi mới thuộc quiz toàn khóa.
- Bỏ menu ngân hàng câu hỏi và menu quản lý quiz riêng. Các URL cũ chuyển về phần quiz trong khóa học.
- Backend khóa course khi ghi, chặn tạo quiz thứ hai kể cả khi gửi đồng thời hoặc quiz hiện có chưa hoạt động.
- Chỉ Expert sở hữu được sửa khi course là DRAFT/REJECTED. Quiz đã có lượt làm bài chỉ được xem; lưu giữ câu hỏi, đáp án và kết quả.
- Quiz và toàn bộ câu hỏi được lưu trong một transaction. Câu hỏi hoặc ảnh đính kèm không hợp lệ sẽ làm cả lần lưu thất bại.
- Gửi duyệt yêu cầu đúng một quiz hoạt động, đủ cấu hình và ít nhất một câu hỏi hợp lệ. Các điều kiện ảnh, video, bài học và gói giá vẫn áp dụng.

## Dữ liệu cũ

Không tự xóa quiz hoặc lịch sử học tập. Khóa có nhiều quiz hiển thị danh sách cũ; Expert có thể xác nhận xóa quiz chưa có lượt làm để giữ lại một quiz. Quiz còn lại được sửa thành quiz toàn khóa khi lưu. Khóa có nhiều quiz đã có lượt làm phải bảo lưu lịch sử; không gửi duyệt lại bằng quy tắc mới cho đến khi có phương án chuyển đổi dữ liệu.

Không drop bảng câu hỏi: các bảng này vẫn lưu câu hỏi và đáp án của quiz, đồng thời bảo toàn dữ liệu cũ. Không cần migration schema cho thay đổi này.

## Student và chứng nhận

Học viên thấy quiz mới ở cuối giáo trình. Thời lượng, điểm đạt, số lượt làm, quyền truy cập và chấm điểm tiếp tục do backend kiểm soát. Các quiz cũ vẫn được giữ để đọc lịch sử. Quy tắc chứng nhận vẫn do Manager/Admin xác nhận; thay đổi quiz bắt buộc cần cấu hình lại phiên bản điều kiện như trước.

## API

- GET /api/v1/expert/courses/{courseId}/assessment: quiz cùng câu hỏi, bốn đáp án và cờ retained; chỉ Expert sở hữu có quyền đọc.
- PUT cùng đường dẫn: lưu quiz và câu hỏi cùng một transaction. id là null khi tạo, hoặc ID hiện có khi sửa. questionId là null cho câu mới, hoặc ID đang thuộc quiz cho câu cũ. Thứ tự được xác định bằng thứ tự mảng.
- Các API quiz cũ vẫn đọc được dữ liệu cũ; API tạo mới cũng chặn quiz thứ hai và từ chối gắn quiz theo bài học.

## Kiểm tra

Kiểm thử tích hợp dùng H2 tách biệt, dịch vụ media được mock. Bao phủ tạo/sửa, kiểm tra đáp án, tính nguyên tử khi lỗi giữa lần lưu, quyền sở hữu, quyền đọc đáp án, chặn tạo trùng đồng thời, lưu giữ lượt làm và điều kiện gửi duyệt. Frontend kiểm tra TypeScript, build, và Chrome với dữ liệu giả lập cho thao tác soạn câu, thứ tự, lưu, giữ bản nháp khi chuyển tab hoặc lỗi, khóa quiz có lượt làm, dữ liệu cũ và hiển thị ở sáu chiều rộng màn hình. Chưa chạy kiểm thử với MySQL thật.
