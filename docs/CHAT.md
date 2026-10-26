# Chat — Bằng branch

Product Owner explicitly approved this new feature on 25 October 2026, extending the previous 72-use-case scope. Existing courses, registrations, questions and tests are unchanged by chat.

## Behavior

- All active signed-in Student, Expert, Manager and Admin accounts can join one community room and send direct messages to any other active account, without course-enrollment restrictions.
- `/chat` uses the existing role-specific layout. Directory results expose display name, avatar and role only, not email, phone or credentials. Search is by name and paginated (30 users).
- Text messages contain up to 2000 UTF-16 characters after trimming. A message requires text or one image/video attachment. No HTML rendering, editing or deletion.
- MySQL stores both community and private history. `recipient_id IS NULL` denotes community messages. Private history always includes the authenticated user as one participant; the client cannot choose a different sender.
- Latest 50 messages load first; the `before` ID cursor loads earlier messages without timestamp ambiguity.
- Reusing the same sender/client UUID/content/recipient returns the existing message, preventing duplicates after an acknowledgement is lost. A concurrent duplicate write may return a retryable error; retrying the same UUID reads the committed original.
- Counts of new messages in other rooms are local to the currently open chat page. They are not persistent read receipts or an offline notification system.

## Setup

### Messenger-style interface and chat media (26 October 2026)

The chat UI now includes a vertical avatar/contact rail, searchable contact list, blue outgoing/gray incoming bubbles, a date separator, Enter-to-send / Shift+Enter for a new line, a file picker with preview/removal, inline images and a video player. Initials appear when an account has no avatar; profile images are used when available. On small screens, the contact list and thread alternate using the back control.

Green indicators mean the account has an authenticated chat WebSocket open. `PRESENCE` frames send the distinct connected user IDs after join/leave and the periodic session sweep. They do not invent last-seen timestamps or claim that a disconnected user is active elsewhere in the app.

Apply `database/migrations/V014__chat_attachments.sql` once after V013. It adds `chat_attachments` and the nullable unique `chat_messages.attachment_id`, preserving existing text history. The ALTER statements must be skipped if the column/constraints already exist. This migration has been applied to the local development database.

`POST /api/v1/chat/attachments` accepts multipart `file` and optional `recipientId` (omit for community). JPG, PNG, WebP and GIF are limited to 5 MiB; MP4 and WebM to 25 MiB. The backend inspects magic bytes rather than trusting the extension or declared MIME. SVG, HTML, documents and executables are rejected. Preview is local; upload happens when Send is pressed. The same uploaded ID/UUID is retained on retry. Playback support depends on the browser codec; H.264 MP4 and VP8/VP9 WebM are recommended.

New chat images and videos are stored on Cloudinary with authenticated delivery. Apply V015 and see `docs/CHAT_CLOUDINARY.md` for credentials, the protected proxy endpoint and resumable migration. `app.chat.storage-dir` is now only a legacy read/migration source; no new upload is persisted there. Retain old disk copies as backups until cloud delivery is verified. Only the owner may read a staged upload. Once attached, private files can be read only by sender/recipient; community files can be read by authenticated active accounts. An uploaded file cannot be sent by another owner, moved into a different conversation or attached to a second message. Failed/abandoned uploads remain private; no automatic retention/deletion policy is imposed.

`GET /api/v1/chat/attachments/{id}` validates the current account and membership before serving bytes, with `Cache-Control: no-store`, an inspected Content-Type, safe inline Content-Disposition and `X-Content-Type-Options: nosniff`. Frontend fetches with the existing bearer-token API client and renders a temporary blob URL, revoked on unmount. It never puts access tokens in a media URL. The SEND frame optionally includes `attachmentId`; the MESSAGE/history DTO contains `attachment: {id,name,contentType,bytes}` or null.

Apply `database/migrations/V013__community_private_chat.sql` to the existing MySQL database. It only adds `chat_messages` and its indexes/foreign keys; existing records are not changed. The referenced `users.id` must be signed BIGINT. Use Hibernate `validate` after migration. The migration was applied to this machine's `online_learning_system` database during implementation.

The only new dependency is Spring Boot's `spring-boot-starter-websocket`, managed by the existing Boot version. Frontend uses the browser's native WebSocket, with no Socket.IO/STOMP dependency.

Set `CORS_ALLOWED_ORIGINS` to an explicit comma-separated list of trusted frontend origins, e.g. `http://localhost:5173`. Use exactly the same host in the browser; `127.0.0.1` is a different origin. WebSocket origin restrictions reuse this list, with no wildcard.

Frontend derives `ws://host/ws/chat` or `wss://host/ws/chat` from `VITE_API_BASE_URL`. Set `VITE_CHAT_SOCKET_URL` only when a reverse proxy uses a different WebSocket URL. The proxy must forward HTTP Upgrade and Connection headers. HTTPS frontends require WSS.

## Protocol and security

`GET /api/v1/chat/users?query=&page=0` and `GET /api/v1/chat/messages?peerId=&before=` require the normal access-token HTTP authentication. Omit `peerId` for community history. Responses are DTOs, never JPA entities.

`/ws/chat` permits the HTTP upgrade but does **not** authorize anonymous chat. The first text frame must be `{"type":"AUTH","token":"<access token>"}`. No token is put into the URL or query log. The server checks token purpose, expiry, active persisted session, token subject and account status and replies `{"type":"READY","userId":123}`. An unauthenticated connection receives no messages and is closed by the periodic sweep (at most 40 seconds).

Send `{"type":"SEND","recipientId":null,"content":"Hello","clientMessageId":"<UUID>"}` for community chat; use a user ID for direct messages. Sender identity is taken from the authenticated socket, never a client-supplied sender field. The committed message is delivered as `{"type":"MESSAGE","message":{...}}` to the sender and the recipient, or to authenticated community connections. The same saved ID is the acknowledgement. Browser renders text through React escaping.

`{"type":"PING"}` replies `PONG`. The UI sends PING every 20 seconds and reconnects with exponential backoff, refreshing the access token through the existing authenticated HTTP interceptor. Failed sends keep the draft; retry preserves its UUID until acknowledged.

Session/account validity is rechecked before inbound commands, every delivery and the 30-second sweep. Logout/deactivation prevents further sends and deliveries; idle invalid sockets are eventually closed. Frame size is capped at 16 KiB, outbound buffers at 64 KiB, and each connection accepts at most 30 frames per 10 seconds. Concurrent socket writes use Spring's `ConcurrentWebSocketSessionDecorator`.

Socket errors contain a safe message and optional `clientMessageId`; no SQL detail, stack trace or secret is returned. This implementation uses an in-process connection registry and is intended for one backend instance. Multi-instance delivery needs a shared broker before scaling out.

## Verification

`ChatIntegrationTests` uses a dedicated in-memory H2 database and a real random-port WebSocket server. It covers all four roles, community delivery and persistence, private delivery/history isolation, duplicate retry and conflicting UUID, blank/oversize/malformed inputs, self/missing/blocked recipients, guests, refresh tokens, untrusted origins, revoked sessions and stable pagination. Run with Java 21:

```powershell
.\mvnw.cmd '-Dtest=ChatIntegrationTests' test
```

Frontend checks: `npm exec tsc -- --noEmit`, `npm run build`, `npm run lint`. The repository's existing ESLint configuration targets JavaScript/JSX only; TypeScript chat files are checked by the TypeScript compiler.

Manual check: sign in, choose **Trò chuyện**, send a message in **Phòng chat chung**, select a user for private chat, reload and check retained history. A second signed-in user receives community messages and messages addressed to them. A third user cannot see the private exchange. Stop/restart the backend to verify reconnection and draft retention.

## Conversation search
- `GET /api/v1/chat/messages/search?query=...&peerId=...&before=...` searches stored message text in the selected conversation. Omit peerId for the community room.
- The authenticated account determines private-conversation scope; results never include other users' private threads. Newest first, 50 results per cursor page.
- Keywords are literal, case-insensitive substrings (1–100 characters); `%`, `_`, and `!` are escaped.
- Contact suggestions match full names only. The frontend debounces typing by 250 ms and shows only individuals while filtering.
- The EduLearn E logo toggles the contact list; the conversation header search button opens message search with highlighted matches.
- Validation: ChatIntegrationTests 19/19 passed, frontend TypeScript and production build passed; localhost UI search verified.
