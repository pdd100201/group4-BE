# UC25 — đồng bộ code với sheet Create Course

Nguồn: ảnh sheet Create Course do người dùng gửi ngày 19/10/2026.
Code: `src/test/java/com/onlinelearning/service/CreateCourseTest.java`.
Fixture chung: `src/test/java/com/onlinelearning/support/CreateCourseFixtures.java`.

## Input cố định

- Expert: ID `3`, `expert@onlinelearning.local`, role `EXPERT`, enabled `true`, blocked `false`.
- Title: `[DEMO-COURSE] Java cơ bản`.
- Description: `no video or no quiz for course.` (chuỗi mô tả trong ảnh,
  không phải kiểm tra số lượng lesson/video/quiz).
- Category: `Programming`.
- Price: `299000` VNĐ.
- Thumbnail: ảnh mock có `thumbnailMediaId=1001`, resourceType `image`, purpose `COURSE_IMAGE`.
  ID này chỉ dùng trong mock; không khẳng định nó tồn tại trong DB.
- Với dữ liệu biên, thêm `X` vào cuối giá trị gốc đến đúng chiều dài cần test.

| Sheet | Method tương ứng | Thay đổi input | Expected |
|---|---|---|---|
| UTCID01 | `UTCID01_createCourseWithSheetInput_returnsDraftAndRecordsAudit` | Input cố định | DTO mới, Draft, Expert ID3, đúng ảnh/giá/nội dung; audit COURSE_CREATED |
| UTCID02 | `UTCID02_title30Characters_hasNoValidationError` | Title 30 ký tự | Không có lỗi DTO; không audit |
| UTCID03 | `UTCID03_title31Characters_hasExactlyOneTitleError` | Title 31 ký tự | Đúng 1 lỗi title, thông báo tối đa 30 |
| UTCID04 | `UTCID04_description255Characters_hasNoValidationError` | Description 255 ký tự (biên của ≤255) | Không có lỗi DTO; không audit |
| UTCID05 | `UTCID05_descriptionOver256Characters_hasExactlyOneDescriptionError` | Description 257 ký tự (đúng điều kiện >256 trong ảnh) | Đúng 1 lỗi description, thông báo tối đa 255 |
| UTCID06 | `UTCID06_category30Characters_hasNoValidationError` | Category 30 ký tự | Không có lỗi DTO; không audit |
| UTCID07 | `UTCID07_category31Characters_hasExactlyOneCategoryError` | Category 31 ký tự | Đúng 1 lỗi category, thông báo tối đa 30 |
| UTCID08 | `UTCID08_emptyTitle_hasExactlyOneRequiredTitleError` | Title rỗng | Đúng 1 lỗi bắt buộc tại title |
| UTCID09 | `UTCID09_emptyDescription_hasExactlyOneRequiredDescriptionError` | Description rỗng | Đúng 1 lỗi bắt buộc tại description |
| UTCID10 | `UTCID10_emptyCategory_hasExactlyOneRequiredCategoryError` | Category rỗng | Đúng 1 lỗi bắt buộc tại category |
| UTCID11 | `UTCID11_accountNotFound_throws404WithoutSaving` | Lookup email Expert trả Optional.empty | ApiException NOT_FOUND, Không tìm thấy tài khoản.; không save/audit |

## Phân biệt để điền sheet chính xác

- UTCID11 đang được đánh dấu Expert tồn tại trong ảnh nhưng lại mong lỗi tài khoản
  không tồn tại. Để khớp expected 404, code mock lookup trả rỗng cho cùng email.
  Cần sửa precondition của riêng cột UTCID11 thành “Expert không tồn tại”.
- Dòng “Can connect with server” là điều kiện môi trường của mẫu; unit test này
  chạy bằng mock và Bean Validation, không mở server hoặc kết nối database thật.
  Các kiểm tra quyền tại security filter cần integration test riêng.
- Chuỗi `image` trong ảnh được biểu diễn bằng media ID vì DTO nhận `Long`,
  không nhận chuỗi, file hoặc URL trực tiếp. ID khóa học mới `100` là giá trị
  giả lập từ repository.save, không phải ID được tạo trong database thật.
- Các ca UTCID02–10 kiểm tra validation DTO, không gọi create/save/audit;
  UTCID01/11 kiểm tra service. ConstraintViolation là kết quả trả về của Validator,
  không phải exception do service ném.
- Giới hạn mô tả là 255: 256 cũng không hợp lệ. Ca phụ 256 vẫn được giữ trong
  `CourseRequestValidationTest` ngoài 11 UTCID, tránh bỏ sót biên ngay sát giới hạn.
- Hai test UC25 cũ trong `ExpertCourseServiceTest` đã chuyển sang lớp này để không
  giữ song song dữ liệu cũ 100000 / expert@example.com cho Create Course.

Chạy riêng 11 ca:

```powershell
.\mvnw.cmd '-Dtest=CreateCourseTest' test
```

Chạy cả kiểm tra phụ về validation và hồi quy các thao tác Expert:

```powershell
.\mvnw.cmd '-Dtest=CreateCourseTest,CourseRequestValidationTest,ExpertCourseServiceTest' test
```
