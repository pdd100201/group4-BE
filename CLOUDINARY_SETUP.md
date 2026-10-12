# Cloudinary storage

The backend uses the official Cloudinary Java HTTP5 SDK 2.5.0. The secret never belongs in Vite variables or browser code.

## Configure

In the Cloudinary Console, open Settings → API Keys. Set these backend environment variables:

```properties
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
CLOUDINARY_FOLDER=online-learning
```

You may alternatively place them in `group4-BE/.env`, which is ignored by Git. Spring now imports that file as Java properties relative to the backend working directory: use `KEY=value`, without shell `export` or surrounding quotes. Environment variables take precedence. Start IntelliJ with these variables in its Run Configuration if it does not inherit them. Restart the backend after changes. No unsigned upload preset is required.

## Implemented

- `POST /api/v1/media`, authenticated multipart fields `file`, `purpose`, optional `courseId`.
- AVATAR: all signed-in roles; JPG/PNG/WebP, maximum 10 MiB.
- COURSE_IMAGE: owning Expert and editable course; same image limits.
- LESSON_VIDEO: owning Expert and editable course; MP4/WebM, maximum 100 MiB; chunked upload, authenticated Cloudinary delivery.
- CONTENT_IMAGE: Manager/Admin reusable image endpoint for future content modules.
- `GET /api/v1/media/{id}/playback`: signed HTTPS video URL valid for one hour, no-store. Owner/Manager may preview. Student requires Published course, linked active lesson and ACTIVE registration within its valid period.
- Common React upload component has progress and errors; public course thumbnail, profile avatar, Expert thumbnail/video and Manager video preview are connected.
- Database stores asset metadata and binding IDs; it never stores file bytes or API credentials. Existing development `ddl-auto:update` adds MediaAsset and thumbnail/video relations when the backend starts. Tests use H2, not development MySQL.

Upload validates magic bytes and size before calling Cloudinary; the provider enforces permitted formats. Invalid/oversize files return 400/413, absent configuration returns 503, provider failures return 502. The multipart server limit is 100 MiB (101 MiB including request overhead). Temporary files are removed after upload.

Upload and Save are separate actions: after uploading, click Save Profile, Save Course or Save Lesson to bind the asset. Save/cancel is disabled while upload is running. Create the course draft before uploading its thumbnail.

Replacing/removing a binding does not destroy the cloud asset automatically. This avoids deleting retained learning media. Unbound files can remain after cancelled forms or database/network failures; automatic cloud cleanup and usage quotas are not implemented. Do not delete cloud assets still referenced by records. Video links can be shared until their one-hour expiry; they are not DRM.

The public Home/Course Detail and Student learning pages still contain the earlier placeholders; this integration does not replace those unrelated modules. Existing implemented pages use this common storage layer; future pages can reuse it.

## Verification

Run frontend lint/typecheck/build and backend tests. MediaIntegrationTests mocks only the cloud provider and checks authorization, file rejection, bindings, upload failure and Student access. CloudinaryMediaStorageTests verifies configuration errors and signed URL generation without network access.

Optional live image/video upload/cleanup (requires real credentials): set `CLOUDINARY_SMOKE_TEST=true` for the command and run `mvnw.cmd -Dtest=CloudinaryLiveSmokeTests test`. This uploads a 1px PNG and a tiny one-second video from Cloudinary's demo environment to unique smoke-test public IDs, tests signed playback and rejects unsigned playback, then destroys those exact assets. Regular runs skip it.

Official references: https://cloudinary.com/documentation/java_integration and https://cloudinary.com/documentation/image_upload_api_reference.
