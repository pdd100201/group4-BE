# Online Learning API

Spring Boot 3 REST API for the Online Learning System. The browser talks to React; React calls these JSON endpoints. The first architecture diagram's presentation layer maps to REST controllers and its model/data layer maps to JPA entities and repositories. No JSP or direct JDBC is used in this project.

## Package architecture

```text
com.onlinelearning
├── config/       Security filter chain, JWT filter/provider, role initialization
├── controller/   HTTP endpoints and response mapping
├── dto/          Validated requests and API responses
├── service/      Business contracts
│   └── impl/     Business rules and transactions
├── repository/   Spring Data JPA queries
├── entity/       Database mappings and status enums
├── externalapi/  Outgoing integrations (EmailGateway, SMTP adapter)
├── common/       Small shared response types
└── exception/    Consistent API errors
```

Request path: `SecurityConfig/JwtAuthenticationFilter → Controller → Service → Repository → MySQL`. A service calls `externalapi` when it needs an external system. Controllers return DTOs so JPA entities are not serialized directly.

## Run locally

1. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` as environment variables, in the IntelliJ run configuration, or in a backend `.env` using Java properties syntax. `.env.example` is a reference, not the loaded file.
2. Create an empty MySQL schema named `online_learning_system_new` and import the consolidated schema and data dump from `../outputs/database-exports/online_learning_system_full_20261110_090622.sql`. For a different schema name, set `DB_URL` to match. Import before starting the backend.
3. Set SMTP variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and credentials if required) to enable password reset emails.
4. Run `./mvnw.cmd spring-boot:run` on Windows. Open `http://localhost:8080/swagger-ui/index.html` for the API explorer.

The consolidated dump is the baseline for local setup. Hibernate uses `ddl-auto: update` in development. Older implementation notes may mention SQL migrations that were consolidated into the dump.

## Automatic Local Ngrok

On the shared BE host, use `ngrok-local.properties.example` as a reference for
the ignored `ngrok-local.properties` file. Install and authenticate ngrok once.
With `ngrok.enabled=true`, running BE in IntelliJ or Maven starts ngrok after
the HTTP server is ready. FE still starts separately. No ngrok token is stored
in the repository; ngrok uses its existing local authentication configuration.

The launcher logs the public URL, reuses an existing agent on port 4040 without
stopping it, and stops only its own ngrok child on normal BE shutdown (including
DevTools restarts). A failed tunnel does not prevent local BE startup. Forced
termination of Java may leave a child agent running. Test profiles do not launch
ngrok. Set `NGROK_ENABLED=false` to disable it for a particular run.

Only enable this on the shared BE owner's machine: the tunnel exposes BE to the
Internet. MySQL must already be running. If the public URL changes, update SePay's
webhook and the team FE URL. This does not create/configure a SePay webhook.

## Gmail SMTP

Registration verification and password reset emails are sent through Gmail SMTP.

1. Turn on 2-Step Verification for the sender Google account.
2. Create a Google App Password for this application. Do not use the normal Google account password.
3. Add these environment variables to the IntelliJ run configuration (or the shell that starts Spring Boot):

```text
GMAIL_USERNAME=your-account@gmail.com
GMAIL_APP_PASSWORD=the-16-character-app-password
MAIL_FROM=your-account@gmail.com
```

The backend uses `smtp.gmail.com:587` with authenticated STARTTLS. Spring Boot does not automatically load the `.env.example` file; it is only a configuration reference. Never commit the App Password.
