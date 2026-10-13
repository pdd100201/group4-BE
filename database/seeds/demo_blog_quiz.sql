-- Local demo fixtures for blog and quiz management. Re-running does not duplicate rows.
SET NAMES utf8mb4;
START TRANSACTION;
SET @manager=(SELECT id FROM users WHERE email='manager@onlinelearning.local');
SET @expert=(SELECT id FROM users WHERE email='expert@onlinelearning.local');
SET @student=(SELECT id FROM users WHERE email='student@onlinelearning.local');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Lộ trình học Java cho người mới','demo-blog-1','Kiến thức thực hành và kinh nghiệm học tập: Lộ trình học Java cho người mới.','## Lộ trình học Java cho người mới

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',1,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 16 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-1');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Hiểu rõ lập trình hướng đối tượng','demo-blog-2','Kiến thức thực hành và kinh nghiệm học tập: Hiểu rõ lập trình hướng đối tượng.','## Hiểu rõ lập trình hướng đối tượng

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Lập trình',1,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 15 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-2');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] SQL: truy vấn dữ liệu hiệu quả','demo-blog-3','Kiến thức thực hành và kinh nghiệm học tập: SQL: truy vấn dữ liệu hiệu quả.','## SQL: truy vấn dữ liệu hiệu quả

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Cơ sở dữ liệu',1,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 14 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-3');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Spring Boot và REST API','demo-blog-4','Kiến thức thực hành và kinh nghiệm học tập: Spring Boot và REST API.','## Spring Boot và REST API

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 13 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-4');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] React: quản lý trạng thái','demo-blog-5','Kiến thức thực hành và kinh nghiệm học tập: React: quản lý trạng thái.','## React: quản lý trạng thái

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Lập trình',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 12 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-5');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Git: làm việc nhóm hiệu quả','demo-blog-6','Kiến thức thực hành và kinh nghiệm học tập: Git: làm việc nhóm hiệu quả.','## Git: làm việc nhóm hiệu quả

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Cơ sở dữ liệu',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 11 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-6');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Thiết kế cơ sở dữ liệu','demo-blog-7','Kiến thức thực hành và kinh nghiệm học tập: Thiết kế cơ sở dữ liệu.','## Thiết kế cơ sở dữ liệu

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 10 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-7');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Kiểm thử phần mềm cơ bản','demo-blog-8','Kiến thức thực hành và kinh nghiệm học tập: Kiểm thử phần mềm cơ bản.','## Kiểm thử phần mềm cơ bản

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Lập trình',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 9 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-8');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Bảo mật tài khoản trực tuyến','demo-blog-9','Kiến thức thực hành và kinh nghiệm học tập: Bảo mật tài khoản trực tuyến.','## Bảo mật tài khoản trực tuyến

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Cơ sở dữ liệu',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 8 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-9');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Lập kế hoạch ôn tập','demo-blog-10','Kiến thức thực hành và kinh nghiệm học tập: Lập kế hoạch ôn tập.','## Lập kế hoạch ôn tập

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-10');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Kỹ năng giải quyết vấn đề','demo-blog-11','Kiến thức thực hành và kinh nghiệm học tập: Kỹ năng giải quyết vấn đề.','## Kỹ năng giải quyết vấn đề

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Lập trình',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-11');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Chuẩn bị phỏng vấn lập trình','demo-blog-12','Kiến thức thực hành và kinh nghiệm học tập: Chuẩn bị phỏng vấn lập trình.','## Chuẩn bị phỏng vấn lập trình

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Cơ sở dữ liệu',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 5 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-12');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Học cùng cộng đồng','demo-blog-13','Kiến thức thực hành và kinh nghiệm học tập: Học cùng cộng đồng.','## Học cùng cộng đồng

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 4 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-13');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Quản lý thời gian học tập','demo-blog-14','Kiến thức thực hành và kinh nghiệm học tập: Quản lý thời gian học tập.','## Quản lý thời gian học tập

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Lập trình',0,1,0,'PUBLISHED',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY),UTC_TIMESTAMP(),UTC_TIMESTAMP()
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-14');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Bản nháp: Java nâng cao','demo-blog-15','Kiến thức thực hành và kinh nghiệm học tập: Bản nháp: Java nâng cao.','## Bản nháp: Java nâng cao

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Cơ sở dữ liệu',0,0,0,'DRAFT',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),UTC_TIMESTAMP(),NULL
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-15');
INSERT INTO posts(author_id,title,slug,summary,content,category,featured,published,deleted,status,created_at,updated_at,published_at)
SELECT @manager,'[DEMO-BLOG] Bản nháp: phương pháp ôn thi','demo-blog-16','Kiến thức thực hành và kinh nghiệm học tập: Bản nháp: phương pháp ôn thi.','## Bản nháp: phương pháp ôn thi

Bài viết mẫu để kiểm tra danh sách, tìm kiếm và đọc nội dung blog.

### Thực hành

1. Đọc kiến thức cơ bản.
2. Làm bài tập nhỏ mỗi ngày.
3. Ghi lại kết quả và trao đổi với giảng viên.

### Tổng kết

Kết hợp lý thuyết và thực hành giúp bạn ghi nhớ tốt hơn.','Kỹ năng học tập',0,0,0,'DRAFT',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY),UTC_TIMESTAMP(),NULL
WHERE @manager IS NOT NULL AND NOT EXISTS(SELECT 1 FROM posts WHERE slug='demo-blog-16');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 1] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-1' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 1] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 1] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-1' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 1] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 1] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.','HIDDEN',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-1' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 1] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 2] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-2' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 2] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 2] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-2' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 2] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 2] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.','HIDDEN',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-2' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 2] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 3] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-3' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 3] Bài viết dễ hiểu, mình sẽ thử làm bài tập theo hướng dẫn.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 3] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.','VISIBLE',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-3' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 3] Mình muốn tìm hiểu thêm phần thực hành, cảm ơn tác giả.');
INSERT INTO post_comment(post_id,author_id,content,status,created_at,updated_at) SELECT p.id,@student,'[DEMO-COMMENT 3] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.','HIDDEN',UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM posts p WHERE p.slug='demo-blog-3' AND @student IS NOT NULL AND NOT EXISTS(SELECT 1 FROM post_comment WHERE post_id=p.id AND content='[DEMO-COMMENT 3] Bình luận mẫu đã ẩn để kiểm tra quyền hiển thị.');
INSERT INTO course(expert_id,title,description,category,price,status,rejection_reason,created_at,updated_at) SELECT @expert,'[DEMO-QUIZ] Java căn bản - DRAFT','Khóa học mẫu cho quản lý quiz; trạng thái DRAFT','Lập trình',0,'DRAFT',NULL,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @expert IS NOT NULL AND NOT EXISTS(SELECT 1 FROM course WHERE title='[DEMO-QUIZ] Java căn bản - DRAFT' AND expert_id=@expert);
SET @course=(SELECT id FROM course WHERE title='[DEMO-QUIZ] Java căn bản - DRAFT' AND expert_id=@expert ORDER BY id LIMIT 1);
INSERT INTO lesson(course_id,title,content,position,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Biến và kiểu dữ liệu','Nội dung bài học mẫu về Java.',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu');
SET @lesson=(SELECT id FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu' ORDER BY id LIMIT 1);
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?','int dùng để lưu số nguyên.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?');
SET @question1=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'int',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='int');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'String',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='String');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'boolean',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='boolean');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?','class là từ khóa khai báo lớp.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?');
SET @question2=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'class',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='class');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'table',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='table');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'function',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='function');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?','Phép nhân được thực hiện trước phép cộng.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?');
SET @question3=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'14',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='14');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'20',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='20');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'24',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='24');
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,@lesson,'[DEMO-QUIZ] Kiểm tra kiến thức Java',15,70,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,NULL,'[DEMO-QUIZ] Ôn tập chưa kích hoạt',30,70,3,0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO course(expert_id,title,description,category,price,status,rejection_reason,created_at,updated_at) SELECT @expert,'[DEMO-QUIZ] Java căn bản - REJECTED','Khóa học mẫu cho quản lý quiz; trạng thái REJECTED','Lập trình',0,'REJECTED','Cần bổ sung câu hỏi thực hành.',UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @expert IS NOT NULL AND NOT EXISTS(SELECT 1 FROM course WHERE title='[DEMO-QUIZ] Java căn bản - REJECTED' AND expert_id=@expert);
SET @course=(SELECT id FROM course WHERE title='[DEMO-QUIZ] Java căn bản - REJECTED' AND expert_id=@expert ORDER BY id LIMIT 1);
INSERT INTO lesson(course_id,title,content,position,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Biến và kiểu dữ liệu','Nội dung bài học mẫu về Java.',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu');
SET @lesson=(SELECT id FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu' ORDER BY id LIMIT 1);
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?','int dùng để lưu số nguyên.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?');
SET @question1=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'int',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='int');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'String',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='String');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'boolean',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='boolean');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?','class là từ khóa khai báo lớp.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?');
SET @question2=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'class',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='class');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'table',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='table');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'function',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='function');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?','Phép nhân được thực hiện trước phép cộng.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?');
SET @question3=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'14',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='14');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'20',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='20');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'24',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='24');
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,@lesson,'[DEMO-QUIZ] Kiểm tra kiến thức Java',15,70,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,NULL,'[DEMO-QUIZ] Ôn tập chưa kích hoạt',30,70,3,0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO course(expert_id,title,description,category,price,status,rejection_reason,created_at,updated_at) SELECT @expert,'[DEMO-QUIZ] Java căn bản - PENDING_REVIEW','Khóa học mẫu cho quản lý quiz; trạng thái PENDING_REVIEW','Lập trình',0,'PENDING_REVIEW',NULL,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @expert IS NOT NULL AND NOT EXISTS(SELECT 1 FROM course WHERE title='[DEMO-QUIZ] Java căn bản - PENDING_REVIEW' AND expert_id=@expert);
SET @course=(SELECT id FROM course WHERE title='[DEMO-QUIZ] Java căn bản - PENDING_REVIEW' AND expert_id=@expert ORDER BY id LIMIT 1);
INSERT INTO lesson(course_id,title,content,position,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Biến và kiểu dữ liệu','Nội dung bài học mẫu về Java.',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu');
SET @lesson=(SELECT id FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu' ORDER BY id LIMIT 1);
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?','int dùng để lưu số nguyên.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?');
SET @question1=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'int',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='int');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'String',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='String');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'boolean',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='boolean');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?','class là từ khóa khai báo lớp.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?');
SET @question2=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'class',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='class');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'table',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='table');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'function',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='function');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?','Phép nhân được thực hiện trước phép cộng.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?');
SET @question3=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'14',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='14');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'20',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='20');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'24',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='24');
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,@lesson,'[DEMO-QUIZ] Kiểm tra kiến thức Java',15,70,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,NULL,'[DEMO-QUIZ] Ôn tập chưa kích hoạt',30,70,3,0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO course(expert_id,title,description,category,price,status,rejection_reason,created_at,updated_at) SELECT @expert,'[DEMO-QUIZ] Java căn bản - PUBLISHED','Khóa học mẫu cho quản lý quiz; trạng thái PUBLISHED','Lập trình',0,'PUBLISHED',NULL,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @expert IS NOT NULL AND NOT EXISTS(SELECT 1 FROM course WHERE title='[DEMO-QUIZ] Java căn bản - PUBLISHED' AND expert_id=@expert);
SET @course=(SELECT id FROM course WHERE title='[DEMO-QUIZ] Java căn bản - PUBLISHED' AND expert_id=@expert ORDER BY id LIMIT 1);
INSERT INTO lesson(course_id,title,content,position,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Biến và kiểu dữ liệu','Nội dung bài học mẫu về Java.',1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu');
SET @lesson=(SELECT id FROM lesson WHERE course_id=@course AND title='[DEMO-QUIZ] Biến và kiểu dữ liệu' ORDER BY id LIMIT 1);
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?','int dùng để lưu số nguyên.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?');
SET @question1=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kiểu dữ liệu nào lưu số nguyên trong Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'int',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='int');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'String',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='String');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question1,'boolean',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question1 AND content='boolean');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?','class là từ khóa khai báo lớp.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?');
SET @question2=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Từ khóa nào khai báo một lớp Java?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'class',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='class');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'table',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='table');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question2,'function',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question2 AND content='function');
INSERT INTO questions(course_id,content,explanation,question_type,type,status,active,created_at,updated_at) SELECT @course,'[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?','Phép nhân được thực hiện trước phép cộng.','SINGLE_CHOICE','SINGLE_CHOICE','ACTIVE',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?');
SET @question3=(SELECT id FROM questions WHERE course_id=@course AND content='[DEMO-QUIZ] Kết quả của 2 + 3 * 4 là bao nhiêu?' ORDER BY id LIMIT 1);
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'14',1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='14');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'20',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='20');
INSERT INTO question_option(question_id,content,correct,created_at,updated_at) SELECT @question3,'24',0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_option WHERE question_id=@question3 AND content='24');
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,@lesson,'[DEMO-QUIZ] Kiểm tra kiến thức Java',15,70,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Kiểm tra kiến thức Java' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
INSERT INTO quiz(course_id,lesson_id,title,duration_minutes,pass_rate,max_attempts,active,created_at,updated_at) SELECT @course,NULL,'[DEMO-QUIZ] Ôn tập chưa kích hoạt',30,70,3,0,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @course IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt');
SET @quiz=(SELECT id FROM quiz WHERE course_id=@course AND title='[DEMO-QUIZ] Ôn tập chưa kích hoạt' ORDER BY id LIMIT 1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question1,1,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question1 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question1);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question2,2,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question2 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question2);
INSERT INTO quiz_question(quiz_id,question_id,position,points,created_at,updated_at) SELECT @quiz,@question3,3,1,UTC_TIMESTAMP(),UTC_TIMESTAMP() WHERE @quiz IS NOT NULL AND @question3 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM quiz_question WHERE quiz_id=@quiz AND question_id=@question3);
COMMIT;
SELECT COUNT(*) AS demo_posts FROM posts WHERE slug LIKE 'demo-blog-%';
SELECT c.id,c.title,c.status,COUNT(q.id) AS quizzes FROM course c LEFT JOIN quiz q ON q.course_id=c.id WHERE c.title LIKE '[DEMO-QUIZ]%' GROUP BY c.id,c.title,c.status;
SELECT q.course_id, q.id AS question_id, q.content FROM questions q JOIN course c ON c.id=q.course_id WHERE c.title LIKE '[DEMO-QUIZ]%' ORDER BY q.course_id,q.id;
