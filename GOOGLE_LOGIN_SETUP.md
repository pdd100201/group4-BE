# Google Login (UC01 / C16)

Frontend reads the public Google Client ID from GET /api/v1/auth/config.
It uses the same app.google.client-id as the backend token verifier.
No Gmail password, OAuth client secret or App Password is returned by this endpoint.
VITE_GOOGLE_CLIENT_ID is no longer needed.

## Setup

1. Open Google Cloud Console, select your project, and create an OAuth client
   of type Web application under Google Auth Platform / Clients.
2. Add both http://localhost and http://localhost:5173 as Authorized JavaScript origins.
   Add other exact origins only when actually used, including different Vite ports.
3. Configure branding, audience and test users as required by your Google project.
4. Set GOOGLE_CLIENT_ID to the generated public Client ID in the backend run environment.
   It ends with .apps.googleusercontent.com. Restart backend and reload the frontend.
5. The JavaScript credential callback posts the ID token to /api/v1/auth/google;
   the backend validates its signature, audience, expiration and verified email.

The Client ID present during the local audit was rejected by the real Google SDK
with "The given client ID is not found". A valid client created by the project owner
is still needed; code fixes cannot create an OAuth client in Google Cloud.
This is independent of Gmail SMTP/App Password configuration.

## Verification

AuthConfigurationControllerTests checks the anonymous configuration endpoint exposes
only the public Client ID. Existing auth integration tests verify Google login rules.
Browser SDK fixtures check callback routing, responsive button sizing and script retry.
Actual Google account consent/login must be verified with a valid project Client ID.

Official setup: https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid
