# Course unit tests — tracking đối chiếu

Nguồn: `SWP392_OnlineLearning_ProjectTracking (1).xlsm`, sheet `RMS`,
dòng 15–16, 31–35, 41–44; các mô tả màn hình Course ở sheet `Test`.
Quy tắc: `../OLS_MASTER_CODING_PROMPT.md` tại thư mục cha repository;
các giới hạn mới do người dùng chốt: tên/danh mục 30, mô tả 255 ký tự.

Đây là unit test JUnit 5 + Mockito/Bean Validation; không khởi động Spring,
không kết nối MySQL, không gửi email hoặc tải media lên Cloudinary.
Tracking được đọc làm dữ liệu yêu cầu, không chạy macro và không sửa workbook.

## Đối chiếu phạm vi

Cập nhật 19/10/2026: các ca UC25 đã chuyển sang `CreateCourseTest` với đúng
11 UTCID của ảnh sheet mới. Xem `CREATE_COURSE_SHEET_MAPPING.md` để đối chiếu
input, fixture dùng chung và precondition cần chỉnh ở UTCID11. Các kết quả
ngày 18/10 bên dưới là lịch sử chạy trước thay đổi này.

| Tracking RMS | Use case | File test | Trường hợp được kiểm tra |
|---|---|---|---|
| 15–16 | UC10/11 danh sách/chi tiết công khai | `CourseCatalogServiceTest` | Không có dữ liệu; giá thấp nhất sau giảm từ repository gói active; không dùng giá legacy; không có gói; ID sai; course không public trả 404; outline lesson và quiz count |
| 31 | UC24 đọc course của Expert | `ExpertCourseServiceTest` | Đọc của mình, trả lý do từ chối; chặn owner khác; không tồn tại |
| 32 | UC25 tạo course | `ExpertCourseServiceTest` | Owner từ email đăng nhập; Draft mặc định; trim dữ liệu; audit; tài khoản không tồn tại |
| 33 | UC26 sửa course | `ExpertCourseServiceTest` | Sửa Draft/Rejected; không đổi status; chặn owner khác; khóa Pending Review/Approved legacy/Archived |
| 32–33 | Validation thông tin | `CourseRequestValidationTest` | Đúng/dưới/vượt 30/255/30; null/rỗng/toàn khoảng trắng; thông báo lỗi và đúng trường |
| 32–33 | C13 gói giá | `CoursePackageServiceTest` | Đúng 3 gói; trùng duration; giá âm/lẻ/quá lớn; giảm ngoài 0–100 hoặc quá precision; biên hợp lệ; owner, role, trạng thái tài khoản/course; audit |
| 34–35 | UC27/28 moderation | `CourseModerationServiceTest` | Admin/Manager ẩn Published; không sửa metadata; sai role/state; lý do bắt buộc/tối đa 255; chặn hard-delete ở 6 loại tham chiếu |
| 35 | UC28 Expert xóa | `ExpertCourseServiceTest` | Chặn sai owner/state; registration còn tham chiếu; xóa Draft/Rejected rỗng; tháo liên kết media và ghi audit |
| 41,44 | UC66/69 điều kiện gửi duyệt | `CourseSubmissionReadinessServiceTest` | Thiếu thông tin/ảnh/giá/gói/lesson/quiz; lesson ẩn/thiếu video/sai loại/thứ tự; quiz ẩn/thời lượng/pass rate/attempts; trạng thái eligible |
| 41–44 | UC66–69 workflow | `CourseWorkflowServiceTest` | Submit chỉ Draft; resubmit chỉ Rejected; readiness failure không đổi state; owner sai; chỉ Manager duyệt/từ chối; approve thẳng Published; approve lặp; lý do null/rỗng/quá dài/đúng 255; reviewer và audit |

## Chạy test

Tại `group4-BE`, với `JAVA_HOME` trỏ JDK 21:

```powershell
.\mvnw.cmd '-Dtest=*Course*Test' test
.\mvnw.cmd '-Dtest=ExpertCourseServiceTest' test
.\mvnw.cmd test
```

Lệnh đầu chạy unit test Course, bao gồm test validation đã có từ lần trước.
Lệnh cuối chạy toàn bộ test, gồm integration test hiện hữu dùng H2 profile `test`.
Báo cáo: `target/surefire-reports`.

## Kết quả chạy ngày 18/10/2026

- Course unit tests: **144 tests, 0 failures, 0 errors** (131 ca mới và
  13 ca validation đã thêm trước đó).
- Toàn bộ backend: **272 tests, 2 failures, 0 errors**. Hai test hiện hữu lỗi:
  - `PaymentMergeIntegrationTests.cancelledRegistrationIsNotReactivatedByLateTransfer`:
    mong HTTP 200 nhưng API trả 409.
  - `StudentRegistrationIntegrationTests.repeatedSubmitReopensSamePendingDespiteNewPackagePrices`:
    so sánh BigDecimal `90001` với `90001.00` bằng equality có tính scale.
- Không sửa implementation hoặc các integration test này trong nhiệm vụ thêm
  unit test Course. Toàn bộ suite chưa đạt PASS.

## Giới hạn và khác biệt cần lưu ý

- Unit test dùng mock không chứng minh SQL predicate thực sự lọc Draft/Hidden,
  phân trang hoặc tách dữ liệu giữa hai Expert. Phần này cần integration tests
  (`PublicCourseIntegrationTests`, `ExpertCourseIntegrationTests`,
  `ManagerCourseIntegrationTests`) và kiểm thử giao diện hiện hữu.
- Quyền HTTP/owner giả trong payload được thực thi tại controller/security;
  gọi service bằng Mockito không kiểm tra được security filter. Không đánh dấu
  các ca đó đã phủ bởi unit test service.
- Rollback khi lưu gói trùng, khóa đồng thời, giá snapshot của registration và
  toàn vẹn FK sau xóa cần integration test; không suy ra từ mock repository.
- Tracking RMS dòng 44 yêu cầu **đã chỉnh sửa trước khi resubmit**. Code hiện tại
  chỉ kiểm tra status Rejected + readiness; chưa thấy revision check. Bộ unit test
  kiểm tra các điều kiện đang thực thi, chưa khẳng định yêu cầu revision đã đạt.
- Tracking RMS dòng 33 nói chỉ sửa Draft/Rejected, nhưng code hiện tại và tài liệu
  `PUBLISHED_COURSE_EDITING.md` cho phép sửa Published. Không thêm test khẳng định
  Published bị khóa và không đổi business code trong nhiệm vụ viết test này.
- Sheet `Test` có mô tả cũ Admin sửa nội dung/gói giá và subject dimension;
  không dùng chúng để mở rộng quyền hoặc thêm feature ngoài quy tắc đã chốt.
- Không bao gồm unit test mới cho Lesson, Quiz, Registration/Payment, My Courses,
  báo cáo hoặc giao diện. Chúng là module riêng trong tracking.
- Không cập nhật các ô DONE/PASS của workbook dựa chỉ trên kết quả unit test.
