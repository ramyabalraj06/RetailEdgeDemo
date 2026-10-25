# Cart Abandonment Recovery Specification

## Scope and Architecture

Implement the cart-abandonment recovery backend only. Preserve the existing inventory module and shared JWT validation. Use Spring Boot with the existing Java 17 / Spring Boot 4.1.2 project baseline, Spring Data JPA, PostgreSQL, Jakarta Validation, and the existing entity -> repository -> service -> controller boundaries. Do not implement unrelated inventory or cart-abandonment analytics products.

The only repository guidance file found is `.mvn/wrapper/.github/copilot-instructions.md`; the project-root `.github/copilot-instructions.md` does not exist. This feature follows that available guidance for store isolation, secure JWT validation, DTO whitelisting, machine-readable errors, ORM indexes, structured logs, and independent batch transactions.

## Domain Model

### Cart

A cart is owned by one `storeId` and one `customerId`, and has a `sessionId`. It contains one or more `CartItem` values with `productId`, positive integer `quantity`, and nonnegative `BigDecimal unitPrice`. `cartTotal` is a `BigDecimal` computed by the service from items and rounded to scale 2 using `HALF_UP`; callers cannot set it independently. Currency is a validated uppercase three-letter code.

`lastActivityAt` is an instant. `abandonmentTimestamp` is nullable and is set once when the cart first qualifies as abandoned. Checkout state and recovery state are separate:

- `CheckoutStatus`: `PENDING`, `COMPLETED`, `CANCELLED`.
- `RecoveryStatus`: `PENDING`, `NOTIFIED`, `CONVERTED`, `EXPIRED`.

`voucherCode` and `recoveryTimestamp` are nullable. `recoveryTimestamp` is the instant the recovery notification is committed to the outbox. `@Version` is an integer. Store/session identity is unique so retries cannot create a second cart for the same active session.

### Required indexes

- Composite `(store_id, checkout_status, recovery_status, last_activity_at, id)` supports the store-scoped inactivity scan and keyset cursor.
- Composite `(store_id, customer_id, last_activity_at, id)` supports the newest-cart-per-customer check.
- Composite `(store_id, recovery_status, recovery_timestamp, id)` supports recovery expiry and analytics time-window retrieval.
- Unique `(store_id, session_id)` prevents duplicate active-session carts.

Index names and query purposes must be declared in ORM metadata. Repository queries must use ORM/derived query APIs, not hand-built SQL.

### Durable supporting records

- A per-store scan checkpoint stores the last successfully committed cart ID, cycle state, update time, and `@Version`.
- A transactional outbox stores a unique idempotency key, event type, store/cart association, safe serialized cart summary, optional voucher, creation time, delivery state, attempts, and `@Version`.
- An order-event inbox stores trusted event IDs uniquely with store/cart association and consumption time.
- A transition log stores each checkout/recovery state change with store, cart, customer reference, previous/next state, event time, and reason. Do not include PII or voucher codes in logs.

## Abandonment Detection

A cart qualifies when checkout status is `PENDING`, recovery status is `PENDING`, it has no abandonment timestamp, and `lastActivityAt <= clock.instant() - 30 minutes`. The 30-minute boundary is inclusive.

A scan processes at most configurable `cart.recovery.batch-size` rows per transaction, ordered by ascending cart ID and selected after the store-specific durable cursor. Every batch is `REQUIRES_NEW`; cart transitions, voucher, outbox event, and checkpoint cursor commit or roll back together. A crash resumes strictly after the last committed cursor. When a cycle exhausts eligible rows, the checkpoint resets to the beginning for the next scan cycle so carts that become inactive later are not skipped forever.

For each store/customer, only the most recently active eligible pending cart may trigger recovery; order by `lastActivityAt DESC`, then cart ID DESC as a deterministic tie-breaker. The check queries persisted carts, not only the current batch, so it works across batch boundaries. Older eligible carts are marked recovery `EXPIRED` with a `SUPERSEDED` transition and do not receive a notification. A previously notified older recovery is expired when a newer eligible cart is selected, keeping one active recovery per store/customer.

The configured voucher threshold is a `BigDecimal`; a voucher is generated only when `cartTotal >= threshold`. Voucher codes are cryptographically random and unique. A unique database constraint and deterministic outbox idempotency key prevent retries from generating a second code or notification. Below threshold, queue the same recovery notification without a voucher. Above threshold, include the optional voucher and a minimal cart summary in the outbox event.

The outbox event is the durable email/push queue boundary. This repository has no email or push provider; a delivery adapter must consume pending outbox rows idempotently. Notification payloads contain recipient customer identity for routing and item/total/currency summary, but logs must never contain customer identity or voucher values.

## Recovery Tracking

Trusted order completion is delivered internally as an event with unique event ID, store ID, cart ID, customer ID, and completion instant. The consumer validates store/cart/customer association in the service, records the event ID transactionally, and treats duplicate IDs as successful no-ops.

Only a `NOTIFIED` recovery can convert. Completion is within the window when `recoveryTimestamp <= completedAt <= recoveryTimestamp + 24 hours`, including both boundaries. A matching timely order marks checkout `COMPLETED`, recovery `CONVERTED`, sets `recoveryTimestamp` if missing only for data repair (normal notified carts already have it), and records transitions. An order associated with a notified cart after the inclusive deadline expires the recovery instead. Events for a different store/customer/cart cannot convert the record.

Unresolved `NOTIFIED` recoveries expire only when `clock.instant() > recoveryTimestamp + 24 hours`; exact deadline is still eligible for conversion. Expiry runs in bounded batches and persists each state transition. All clock-sensitive code receives an injected `Clock`.

## APIs

All routes are stateless JWT protected. JWT verification reuses the existing RS256-pinned decoder and validates signature, issuer, audience, `exp`, `iat`, `nbf`, `store_id`, `customer_id`, `role`, and `scope`. Derive store/customer from claims, never write ownership from request DTO fields. Every external query includes authenticated store scope.

- `POST /scan/abandoned-carts`: admin only, scoped to the authenticated `store_id`; no entity binding. Uses configured batch size, processes independent batches until the cycle is drained or a bounded operational limit is reached, and returns scan counters plus last successful cursor. Non-admin returns HTTP 403 with `{ "error": { "code": "FORBIDDEN_ADMIN_ONLY", "message": "..." } }`.
- `GET /carts/{id}/recovery`: authenticated customer owner only; query by `(store_id, cart_id)`, compare `customer_id` in the service, return 403 for a cart owned by another customer in the same store. Do not reveal cross-store existence.
- `GET /analytics/recovery`: admin only and store-scoped. Requires valid `from` and `to` instants with `from < to`, optional opaque cursor, and `limit` default 50/max 100. Returns `{items, nextCursor, hasMore}`. Cursor is an opaque encoding of the last transition ID; use keyset criteria queries and fetch `limit + 1`, never page/offset/skip pagination.

All write APIs use explicit validated DTOs. Reject unknown JSON fields. Central error handling returns stable machine-readable errors and no database internals or stack traces. Use structured request-correlated logs; do not log JWTs, voucher codes, customer IDs, or other PII.

## Configuration

- `cart.recovery.inactivity-minutes=30`
- `cart.recovery.conversion-window-hours=24`
- `cart.recovery.batch-size` is positive and externally configurable.
- `cart.recovery.voucher-threshold` is a nonnegative decimal supplied/configured without floating-point conversion.
- Existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_ISSUER_URI`, `JWT_JWKS_URI`, and `JWT_AUDIENCE` remain environment-sourced.

## Validation and Tests

Validate store/customer/session/product identifiers, item count, positive item quantity, nonnegative prices/totals, three-letter currency, event IDs, date bounds, cursor format, and page-size bounds. Calculate totals only with `BigDecimal` and explicit scale/rounding. Database constraints enforce nonnegative quantity/price/total and uniqueness for vouchers, sessions, checkpoints, outbox idempotency keys, and processed event IDs.

Tests cover inclusive 30-minute inactivity; voucher at/above and below threshold; conversion at and within 24 hours; expiry after the deadline; latest-cart suppression across batches; cross-customer 403; non-admin scan 403/error code; admin analytics and cursor/limit behavior; restart from committed checkpoint; duplicate outbox/order events; store isolation; optimistic locking; DTO validation/unknown fields; and PostgreSQL constraints where Testcontainers is available. Target 80% line coverage for new business logic.

## Open Questions and Integration Boundaries

- The repository has no notification provider or transport. The transactional outbox provides reliable email/push queueing, but production delivery requires a separate adapter and provider configuration.
- No order domain currently exists. `OrderCompletedEvent` is an internal trusted contract; the owning order service must publish it after durable order completion.
- Existing inventory/cart data has no authoritative store/customer migration mapping. Do not infer ownership for legacy rows; deployment migration requires a source-of-truth mapping.
