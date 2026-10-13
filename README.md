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
2. Create the `online_learning_system` MySQL database, or grant the user permission for `createDatabaseIfNotExist=true`.
3. Set SMTP variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and credentials if required) to enable password reset emails.
4. Run `./mvnw.cmd spring-boot:run` on Windows. Open `http://localhost:8080/swagger-ui/index.html` for the API explorer.

## Question Bank Legacy FK

If saving a question fails for a newly created course, inspect the FKs on
`questions.course_id`. The ORM uses `course`, not the old `courses` table.
Back up the database and run
`database/migrations/V012__align_question_course_reference.sql` to remove only
the obsolete FK after checking every existing question against the current
course table. The migration is repeatable and stops on orphan IDs; do not delete
questions or disable FK checks to bypass the problem. Quiz/student history and
all payment records remain unchanged.

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

The current development configuration uses Hibernate `ddl-auto: update`. Existing databases with incompatible ID/foreign-key types need an explicit, reviewed migration; Hibernate cannot safely reconcile those changes automatically. Use versioned migrations and `validate` for production.

### Existing course / lesson progress schema

Before starting the updated backend on an existing database, back it up and run
`database/migrations/V011__course_timestamps_progress_registration.sql` in MySQL
Workbench against the configured database. This adds and backfills missing course
timestamps before enforcing `NOT NULL`, and aligns the progress registration FK
with the current `course_registration` table. Unknown historical timestamps use
the migration time; valid timestamps and payment data remain unchanged.

The migration can be rerun after successful completion. It deliberately stops
before changing data if existing progress references a legacy registration table
or contains orphan IDs: those rows need reviewed ID mapping, not deletion. Do not
run with `--force`, disable FK checks, or use `ddl-auto=create` to bypass errors.
