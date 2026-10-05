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

1. Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` as environment variables or in the IntelliJ run configuration. `.env.example` is a reference; Spring Boot does not load `.env` automatically.
2. Create the `online_learning_system` MySQL database, or grant the user permission for `createDatabaseIfNotExist=true`.
3. Set SMTP variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, and credentials if required) to enable password reset emails.
4. Run `./mvnw.cmd spring-boot:run` on Windows. Open `http://localhost:8080/swagger-ui/index.html` for the API explorer.

The current development configuration uses Hibernate `ddl-auto: update`. Existing databases with incompatible ID/foreign-key types need an explicit, reviewed migration; Hibernate cannot safely reconcile those changes automatically. Use versioned migrations and `validate` for production.
