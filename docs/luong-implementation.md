# Triển khai use case của Lương

Ngày 12/10/2026. FE và BE cùng ở nhánh `luong`, tạo từ phiên bản `origin/hai` đã fetch. Phạm vi lấy đúng các dòng có người phụ trách **Lương** trong sheet RMS: UC07, UC08, UC21, UC22, UC23, UC60, UC61, UC62, UC63, UC64, UC65. Không suy ra nhãn TV5 là Lương.

Nguồn yêu cầu: `D:/OLS_MASTER_CODING_PROMPT.md`, `D:/Software Requirement Specification.docx`, `D:/SWP392_OnlineLearning_ProjectTracking.xlsm`. Các quyết định C01–C18 thay thế nội dung SRS cũ tương ứng. Không sửa workbook tracking hoặc tài liệu nguồn.

## Quyết định bổ sung từ người dùng

- UI quản lý/tạo/sửa bài viết của UC21–23 chờ người dùng cung cấp mockup. Không tự tạo màn quản lý mới.
- Student được sửa/xóa comment của mình không giới hạn thời gian khi comment và bài viết vẫn hiển thị.
- Mục Khóa Học Tiêu Biểu trên Home tạm dùng 4 course Published mới nhất. Do Course chưa có createdAt, API sắp theo ID giảm dần; không thêm field Featured hoặc dữ liệu mẫu.

## Audit trước triển khai

Hai repo sạch trước thay đổi, không có AGENTS.md được tìm thấy. Đã đọc README, routes, hooks, API, entities, service, security và cấu hình test. Nhánh hai mới hơn thông tin hiện trạng trong master prompt: Home đã đọc course API; Course Detail, Auth, course/lesson management và management read đã có code. Blog và comment mới chỉ có entity; route public/manager blog là placeholder.

Entity Post/PostComment thiếu ngày tạo/cập nhật. Post không có cờ xóa mềm. V007 bổ sung metadata vào entity hiện có, giữ nguyên ngày lịch sử chưa biết là NULL. Không thêm entity nghiệp vụ mới.

## Traceability và trạng thái

Các dòng dưới ghi trạng thái code và test, không đánh dấu toàn bộ phạm vi DONE. MySQL integration và migration chưa được xác minh trên database thực tế.

| UC | Nguồn | Route / API | Service / dữ liệu | Test | Trạng thái và phần còn thiếu |
|---|---|---|---|---|---|
| UC07 | SRS 2.2.1, 3.1.1; BR-10–14 | `/`; GET `/public/posts`, `/public/courses?search=&category=` | PublicPostService, CourseCatalogServiceImpl; Post/Course | PublicPostIntegrationTests, PublicCourseIntegrationTests, browser smoke | Home đọc API thật; search/category hoạt động. CoursePackage/giá gói và CTA đăng ký phụ thuộc module course/registration chưa có; không nhận là hoàn tất các phần đó. |
| UC08 | SRS 2.3.7, 3.4.1; BR-11,65 | `/blog`; GET `/public/posts`, `/public/posts/categories` | PublicPostService / PostRepository / Post | PublicPostIntegrationTests, browser smoke | List/search/category/pagination/loading/empty/error đã triển khai. |
| UC21 | SRS 2.3.10; C03; BR-63,64 | POST `/manager/posts` | ManagerPostService / Post, MediaAsset, AuditLog | ManagerPostIntegrationTests uc21 + role denial | API đã triển khai; BLOCKED-MISSING-MOCKUP cho UI. |
| UC22 | SRS 2.3.11; C03; BR-63–65 | PUT `/manager/posts/{id}` | ManagerPostService / Post, MediaAsset, AuditLog | ManagerPostIntegrationTests uc22 + role denial | API sửa/publish/unpublish đã triển khai; BLOCKED-MISSING-MOCKUP cho UI. |
| UC23 | SRS 2.3.12; C03 | DELETE `/manager/posts/{id}` | ManagerPostService / Post, retained PostComment, AuditLog | ManagerPostIntegrationTests uc23 + role denial | API xóa mềm giữ comment/audit; BLOCKED-MISSING-MOCKUP cho UI. |
| UC60 | SRS 2.3.1; BR-68,69 | `/blog/:postId`; GET `/public/posts/{postId}/comments`; Manager/Admin read endpoints | CommentService / PostCommentRepository / PostComment | CommentIntegrationTests uc60, moderationReads; browser smoke | Public list và API moderation có phân trang. UI moderation chờ mockup. |
| UC61 | SRS 2.3.2; BR-68,69 | GET `/public/posts/{postId}/comments/{id}`; detail hiển thị inline trong blog | CommentService / PostComment | CommentIntegrationTests uc61, moderationReads; browser smoke | Public detail không lộ hidden hoặc comment thuộc post khác. API detail moderation có; UI moderation chờ mockup. |
| UC62 | SRS 2.3.3; C04; BR-66 | POST `/student/posts/{postId}/comments` | CommentService / PostComment, User, AuditLog | CommentIntegrationTests uc62; browser smoke | UI/API tạo comment; chỉ Student; author lấy từ session; content 1–2000 ký tự; React hiển thị text an toàn. |
| UC63 | SRS 2.3.4; C04; BR-67; policy người dùng | PUT `/student/comments/{id}` | CommentService / PostComment, AuditLog | CommentIntegrationTests uc63; browser smoke | UI/API sửa comment của mình; không giới hạn thời gian, chỉ khi post/comment hiển thị. |
| UC64 | SRS 2.3.5; C04; BR-67,70 | DELETE `/comments/{id}` | CommentService / PostComment, AuditLog | CommentIntegrationTests uc64; browser smoke | Student-own và Manager/Admin delete API. UI public cho action hợp lệ; xử lý comment hidden trong management UI chờ mockup. |
| UC65 | SRS 2.3.6; C04; BR-68,69 | PATCH `/comments/{id}/status` | CommentService / PostComment, AuditLog | CommentIntegrationTests uc65, moderationReads | API Manager/Admin đổi Visible/Hidden/Disabled; không phục hồi Deleted. BLOCKED-MISSING-MOCKUP cho UI moderation. |

## Hợp đồng API

Prefix chung `/api/v1`.

- Public posts list: `search`, `category`, `page` (0-based), `size` (1–100); sắp `updatedAt DESC, id DESC`. Response `{content,totalElements,totalPages,number}`. Categories chỉ lấy từ bài published chưa deleted.
- Post summary chỉ có id/title/category/thumbnailUrl/summary/authorName/createdAt/updatedAt. Detail `{post,content}`. Không serialize entity hoặc expose author email/password.
- Manager post request: `title` (1–255), `category` (tối đa 255), `summary` (tối đa 1000), `content` (1–12000), `thumbnailMediaId` (optional), `published` (required). Author lấy từ actor, không từ payload. Ảnh phải là MediaAsset image/CONTENT_IMAGE do Manager đó tải lên. Khi sửa, thumbnailMediaId null giữ ảnh hiện có. Publish đòi category/summary/thumbnail; thumbnail upload dùng Media API sẵn có. Featured hiện có trong entity được bảo toàn nhưng chưa thêm control/API riêng.
- Manager read: GET `/manager/posts`, GET `/manager/posts/{id}` để hỗ trợ UI sau khi có mockup.
- Public comment list/detail kiểm tra post còn public và status Visible. Comment DTO chỉ có thông tin hiển thị, trạng thái, timestamps và `canEdit/canDelete`.
- Manager/Admin comment read: GET `/{manager|admin}/posts/{postId}/comments?status=&page=&size=` và GET `/{manager|admin}/posts/{postId}/comments/{id}`. Có thể đọc Hidden/Disabled; không đưa Deleted vào danh sách moderation. Student/Expert/Guest bị chặn.
- Comment request chỉ có `content`; role và ownership kiểm tra lại tại service. Cả sửa và xóa của Student đòi comment Visible/post published chưa deleted.
- Status request `{status:"VISIBLE"|"HIDDEN"|"DISABLED"}`; dùng DELETE để xóa. Duplicate status hoặc thao tác lên comment đã Deleted trả conflict.
- Mutation khóa post trước comment và refresh comment sau khi có khóa; audit lưu actor/target/action/time. Không log content, credential hay token.

## UI và phụ thuộc

`/blog/:postId` được dùng làm màn chứa UC60–64 với read API tối thiểu cho bài viết. UC09 vẫn do TV5 phụ trách; không đánh dấu UC09 hoàn thành, không làm newsletter/like/reply/report hoặc backend social sharing. Rich content được chuyển thành React nodes theo allowlist; không inject HTML nguồn. Các thẻ script/style/iframe/SVG, event attributes và link javascript bị loại. Comment được hiển thị như text.

Home giữ bố cục sẵn có. Số danh mục lấy từ published courses thật; bài mới nhất lấy từ public post API. Không thêm dữ liệu demo vào database người dùng. Course API được bổ sung search/category tương thích response array hiện có; bộ lọc chạy tại DB. Contract pagination/package của module course chưa được thay thế.

## Database và giới hạn kiểm chứng

`database/preflight/V007__check_blog_schema.sql` chỉ đọc metadata. `database/migrations/V007__blog_and_comment_metadata.sql` thêm Post.deleted, createdAt/updatedAt cho Post/PostComment và indexes bằng conditional checks. Ngày lịch sử không biết giữ NULL. Kiểm tra schema và sao lưu trước khi áp dụng; chưa chạy migration.

Kết nối chỉ đọc local MySQL bằng cấu hình shell trả SQLState 28000 (không được cấp quyền). Không in credential. Đã hỏi vị trí cấu hình đúng; việc rà schema/migration và kiểm thử FE↔BE↔MySQL còn chờ. Không tuyên bố đạt 2 giây/5 giây/1000 concurrent users; chưa chạy load test.

## Verification

- `npm run lint`, `npx tsc --noEmit`, `npm run build`: PASS.
- `mvnw.cmd -B test`: toàn bộ 72 tests PASS, 0 failures, 0 errors, 0 skipped sau các thay đổi cuối cùng. Các test dùng profile test với H2 in-memory, không dùng DB thật.
- `tests/blog-smoke.mjs`: PASS với Playwright/Chrome headless và API fixtures; kiểm tra Home search, filter/paging giữ URL, comment detail/create/edit/delete, quyền Guest/Student/Expert/Manager/Admin, XSS, empty/error, mobile không tràn ngang. Đã kiểm tra screenshots desktop/mobile. Đây là browser fixture test, không phải FE↔BE↔MySQL end-to-end.
- `git diff --check`: PASS. Không commit/push.

Maven dùng JDK 21, `JAVA_HOME` trỏ tới thư mục JDK (không phải bin). Maven wrapper và browser test cần cache/runtime được phép sử dụng trên máy.

## Changed files

Paths dưới đây tính tương đối theo từng repo.

`src/main/resources/application.yaml` có thay đổi ngoài phần triển khai này; đã giữ nguyên, không đưa vào danh sách thay đổi của tác vụ.

| Repo | File | Mục đích |
|---|---|---|
| BE | `entity/Post.java` | Timestamp, soft delete, author lazy. |
| BE | `entity/PostComment.java` | Timestamp và quan hệ lazy. |
| BE | `repository/PostRepository.java` | Public query, categories và khóa mutation. |
| BE | `repository/PostCommentRepository.java` | Paging public, scoped detail và specification moderation. |
| BE | `dto/blog/BlogPage.java` | Pagination DTO chung. |
| BE | `dto/blog/PostResponse.java` | Public summary DTO. |
| BE | `dto/blog/PostDetailResponse.java` | Public detail DTO. |
| BE | `dto/blog/PostRequest.java` | Validate post mutation. |
| BE | `dto/blog/ManagedPostResponse.java` | Manager DTO. |
| BE | `dto/blog/CommentResponse.java` | Comment DTO và action availability. |
| BE | `dto/blog/CommentRequest.java` | Validate comment content. |
| BE | `dto/blog/CommentStatusRequest.java` | Validate moderation status. |
| BE | `service/PublicPostService.java` | Public visibility/filter/paging/detail. |
| BE | `service/BlogActorService.java` | Backend kiểm tra account/role. |
| BE | `service/ManagerPostService.java` | Create/update/delete post và audit. |
| BE | `service/CommentService.java` | Public/moderation read; comment mutations, ownership, policy và audit. |
| BE | `controller/PublicPostController.java` | Public endpoints. |
| BE | `controller/ManagerPostController.java` | Manager endpoints. |
| BE | `controller/CommentController.java` | Public, Student và moderation endpoints. |
| BE | `exception/GlobalExceptionHandler.java` | JSON request sai định dạng trả 400 với error contract hiện có. |
| BE | `controller/PublicCourseController.java` | Nhận filter từ Home. |
| BE | `service/CourseCatalogService.java` | Overload filter tương thích. |
| BE | `service/impl/CourseCatalogServiceImpl.java` | Filter course tại DB. |
| BE | `src/test/.../PublicPostIntegrationTests.java` | Public visibility và paging test. |
| BE | `src/test/.../ManagerPostIntegrationTests.java` | UC21–23, role, image và retained-data tests. |
| BE | `src/test/.../CommentIntegrationTests.java` | UC60–65 và moderation scope tests. |
| BE | `src/test/.../PublicCourseIntegrationTests.java` | Home search/category test. |
| BE | `database/preflight/V007__check_blog_schema.sql` | Read-only schema preflight. |
| BE | `database/migrations/V007__blog_and_comment_metadata.sql` | Metadata/index migration chưa áp dụng. |
| BE | `docs/luong-implementation.md` | Traceability, trạng thái và bàn giao. |
| FE | `features/blog/api/blogApi.ts` | Typed API contracts. |
| FE | `shared/hooks/useBlogPosts.ts` | Post list/categories/detail queries. |
| FE | `shared/hooks/useBlogComments.ts` | Comment queries/mutations/invalidation, cache theo viewer. |
| FE | `features/blog/screens/PostCard.tsx` | Public/home post card. |
| FE | `features/blog/screens/BlogListPage.tsx` | UC08 filters/paging/sidebar. |
| FE | `features/blog/screens/BlogDetailPage.tsx` | Host public comment UI trong route sẵn có. |
| FE | `features/blog/screens/SafePostContent.tsx` | Rich content allowlist. |
| FE | `features/blog/screens/CommentSection.tsx` | UC60–64 interaction và feedback. |
| FE | `features/blog/screens/blog.css` | Styles theo visual language SRS, reflow mobile. |
| FE | `features/home/screens/HomePage.tsx` | UC07 API posts/search/category/latest courses. |
| FE | `features/courses/api/courseApi.ts` | Truyền search/category. |
| FE | `shared/hooks/usePublishedCourses.ts` | Query key bao gồm filter. |
| FE | `features/courses/screens/CourseCatalogPage.tsx` | Đọc filter URL và gọi API. |
| FE | `routes/AppRoutes.tsx` | Thay public blog placeholders. |
| FE | `tests/blog-smoke.mjs` | Browser regression/visual QA test. |

Các path `entity`, `repository`, `dto`, `service`, `controller` ở BE nằm dưới `src/main/java/com/onlinelearning`. Các path `features`, `shared`, `routes` ở FE nằm dưới `src`.
