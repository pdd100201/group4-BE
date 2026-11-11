# Cấp quyền Expert

Luồng học viên gửi hồ sơ xin trở thành Expert và admin xét duyệt hồ sơ đã được bỏ. Backend không còn ánh xạ bảng `expert_applications` hoặc loại tài liệu `EXPERT_DOCUMENT`; frontend không còn form, API client và màn xét duyệt tương ứng.

Admin tiếp tục tạo tài khoản hoặc cấp quyền Expert trong Quản lý tài khoản. Quyền của tài khoản hiện có vẫn nằm trong `users`, `roles`, `user_roles`; không bị xóa khi bỏ chức năng hồ sơ. Khóa học hiện có vẫn giữ nguyên.

DB hiện tại đã được người dùng xử lý trước khi cập nhật code: chỉ xóa bảng `expert_applications` khi trống và bỏ khóa ngoại tương ứng từ `stored_assets`. Bảng `stored_assets` vẫn giữ ảnh và tệp chat. Các cột cũ `application_id`, `size_bytes`, `content` có thể còn trong DB nhưng không còn được code sử dụng; không cần xóa thêm để chạy backend.

Máy thành viên dùng schema cũ cần thực hiện cùng thay đổi DB sau khi dừng backend. Không chạy lại migration tạo chức năng hồ sơ expert. Biên dịch sạch backend để loại các `.class` cũ trước khi khởi động lại.
