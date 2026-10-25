# RetailEdge

RetailEdge is a Spring Boot service for store-scoped inventory management and abandoned-cart recovery. It uses PostgreSQL, Spring Data JPA, stateless JWT authentication, and a Thymeleaf inventory operations page.

## Requirements

- Java 17
- PostgreSQL
- Windows: use the included `mvnw.cmd` Maven wrapper. On macOS/Linux, use `./mvnw`.
- Docker is needed only for tests that use PostgreSQL Testcontainers.

## Run Locally

Create a PostgreSQL database named `retailedge`, then configure the database credentials and JWT settings. `DB_URL` defaults to `jdbc:postgresql://localhost:5432/retailedge`; the username, password, and JWT settings must be supplied.

PowerShell example (replace the placeholders with your development values):

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-local-password"
$env:JWT_ISSUER_URI = "https://your-issuer.example"
$env:JWT_JWKS_URI = "https://your-issuer.example/.well-known/jwks.json"
$env:JWT_AUDIENCE = "retailedge"
.\mvnw.cmd spring-boot:run
```

Open the inventory operations page at `http://localhost:8080/inventory-ui`. The page is public, but inventory API requests still require a valid bearer token. Do not use production credentials in local shell history or commit them to the repository.

## Configuration

| Setting | Environment variable | Default | Purpose |
| --- | --- | --- | --- |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/retailedge` | PostgreSQL connection URL |
| `spring.datasource.username` | `DB_USERNAME` | Required | Database user |
| `spring.datasource.password` | `DB_PASSWORD` | Required | Database password |
| `security.jwt.issuer-uri` | `JWT_ISSUER_URI` | Required | Expected JWT issuer |
| `security.jwt.jwks-uri` | `JWT_JWKS_URI` | Required | JWKS endpoint used to verify RS256 tokens |
| `security.jwt.audience` | `JWT_AUDIENCE` | Required | Expected JWT audience |
| `cart.recovery.inactivity-minutes` | `CART_RECOVERY_INACTIVITY_MINUTES` | `30` | Cart inactivity threshold |
| `cart.recovery.conversion-window-hours` | `CART_RECOVERY_CONVERSION_WINDOW_HOURS` | `24` | Recovery conversion window |
| `cart.recovery.batch-size` | `CART_RECOVERY_BATCH_SIZE` | `100` | Rows processed per scan batch |
| `cart.recovery.max-batches-per-scan` | `CART_RECOVERY_MAX_BATCHES_PER_SCAN` | `100` | Maximum scan batches per request |
| `cart.recovery.expiry-batch-size` | `CART_RECOVERY_EXPIRY_BATCH_SIZE` | `100` | Rows processed per expiry batch |
| `cart.recovery.voucher-threshold` | `CART_RECOVERY_VOUCHER_THRESHOLD` | `50.00` | Minimum cart total for a recovery voucher |

JWTs are verified with RS256 and validated against the configured issuer and audience, plus required time and identity claims. Authorization also requires the roles and scopes listed below.

## API Overview

All endpoints except the inventory page and its static assets require JWT authentication. Responses use JSON; errors use a machine-readable error code.

### Inventory

All inventory endpoints are scoped to the store in the JWT. Access requires `ADMIN` or `INVENTORY_SERVICE` and the `inventory:manage` scope.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/stores/{storeId}/inventory` | Create product inventory |
| `GET` | `/api/stores/{storeId}/inventory/{productId}` | Read product inventory |
| `PUT` | `/api/stores/{storeId}/inventory/{productId}/stock` | Set stock using an expected version |
| `DELETE` | `/api/stores/{storeId}/inventory/{productId}?version={version}` | Delete inventory using its current version |

### Cart Recovery

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/scan/abandoned-carts` | `ADMIN`, `cart-recovery:manage` | Run a bounded, restartable scan for the authenticated store |
| `GET` | `/carts/{id}/recovery` | Owning `CUSTOMER`, `cart:recovery:read` | Read recovery state for an owned cart |
| `GET` | `/analytics/recovery?from={instant}&to={instant}&limit={n}&cursor={cursor}` | `ADMIN`, `cart-recovery:manage` | Query store-scoped analytics with cursor pagination |

Analytics `from` and `to` values are ISO-8601 instants with `from < to`; `limit` defaults to 50 and is capped at 100. The cursor is opaque and may be omitted on the first request.

## Tests

Run the test suite with the Maven wrapper:

```powershell
.\mvnw.cmd test
```

Run the Maven verification lifecycle, including the configured JaCoCo check:

```powershell
.\mvnw.cmd verify
```

Most tests use an in-memory H2 database. PostgreSQL integration tests use Testcontainers and require Docker to be available.

## Architecture

The application is organized by domain under `src/main/java/com/demo/retailedge`:

- `inventory`: store-scoped inventory API, validation, optimistic locking, JWT security, and the inventory UI.
- `cart`: cart recovery services and APIs, durable scan checkpoints, notification outbox, order-event deduplication, expiry processing, and analytics.

The local configuration currently uses Hibernate `ddl-auto=update`. For production deployments, use a reviewed schema migration strategy. Existing inventory rows need an authoritative store-ownership mapping before store-scoped inventory changes can be deployed to a populated database.

## Integration Boundaries

- Recovery notifications are written to a transactional outbox; this project does not include an email or push delivery provider. A separate adapter must deliver pending outbox events idempotently.
- Order completion is represented by the trusted internal `OrderCompletedEvent` contract. An order service must publish these events after order completion is durable.

See [SPEC.md](SPEC.md) for the cart-recovery behavior and [REVIEW.md](REVIEW.md) for implementation review notes and verification status.