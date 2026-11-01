# Private chat media on Cloudinary

## Code

- `api/ChatMediaStorage.java`: private storage gateway for chat, independent of public course/avatar images.
- `api/CloudinaryChatStorage.java`: upload both images/videos with `type=authenticated`; generate a 60-second signed download URL and stream bytes server-side.
- `service/ChatAttachmentService.java`: inspect/limit uploads, use an OS temporary file, upload to Cloudinary, save metadata and remove the temporary file in `finally`. Ownership/participant checks run before downloading.
- `service/ChatAttachmentMigrationService.java`: resumable migration of legacy disk uploads. Network uploads run outside database transactions; a short locked transaction updates each record only after success.
- `config/ChatAttachmentMigrationRunner.java`: explicit opt-in startup task; not a public migration endpoint.
- `database/migrations/V015__chat_cloudinary_storage.sql`: additive metadata columns. Existing attachment IDs and messages are retained.

Paths above are relative to `src/main/java/com/onlinelearning` unless a different root is shown.

## Configuration

Use the existing server configuration/environment variables; never put credentials into React or commit actual values:

```text
CLOUDINARY_CLOUD_NAME=<your-cloud-name>
CLOUDINARY_API_KEY=<your-api-key>
CLOUDINARY_API_SECRET=<your-api-secret>
```

The upload folder comes from `app.cloudinary.folder`, with `/chat/<owner-id>` appended. Images remain limited to 5 MiB and videos to 25 MiB. Chat GIF support is retained. The browser still uses the same protected `/api/v1/chat/attachments/{id}` endpoint and transient blob URLs; no frontend API or socket payload change is required.

`authenticated` is applied to both image and video uploads. Unsigned public delivery is not used. The signed download URL remains on the server, expires after 60 seconds and is not returned in a message DTO. Account status and private-conversation membership are checked before the provider is contacted. API errors do not expose provider credentials, signatures or response details.

If Cloudinary upload fails, no attachment row is saved. Temporary files are cleaned on success and failure. If persistence fails after uploading a new asset, cleanup of that newly created cloud asset is attempted; operator reconciliation may be needed if the provider is unavailable during cleanup.

## Existing files

1. Apply V015 once to the existing database; check for existing columns before rerunning its ALTER statement.
2. Keep the legacy upload directory accessible for migration and rollback.
3. Run with Java 21:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=0 --spring.jpa.hibernate.ddl-auto=validate --ngrok.enabled=false --app.chat.migrate-local=true --app.chat.migration-exit=true'
```

The runner logs `CHAT_CLOUDINARY_MIGRATION migrated=<count> failed=<count>`. Rows already containing a Cloudinary public ID are skipped, so it can be rerun. A failed record keeps its original metadata and disk file. Originals are not deleted automatically; retain them until cloud delivery is verified. Legacy fallback serves only those old rows that could not migrate. Every new upload goes to Cloudinary and is not saved under the legacy upload directory.

## Checks

```powershell
.\mvnw.cmd '-Dtest=ChatIntegrationTests,CloudinaryChatStorageTests,ChatAttachmentMigrationTests,CloudinaryMediaStorageTests' test
```

Automated tests use provider mocks/H2 and do not send private files to an external account. They cover authenticated image/video options, signed download URLs, explicit missing configuration, all chat roles, staged/private access, upload failure cleanup, deduplication and non-destructive migration. Verify live media playback separately using the configured development Cloudinary account.

Provider references: https://cloudinary.com/documentation/control_access_to_media and https://cloudinary.com/documentation/image_upload_api_reference.
