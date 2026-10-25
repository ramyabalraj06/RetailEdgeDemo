# RetailEdge Copilot Instructions

## Role & Engineering Responsibilities (Role-Based Prompting)

You are a senior software engineer building RetailEdge backend services with Spring Boot.
Generate production-grade code that is secure, maintainable, and testable.

When generating or reviewing code:
- Follow strict layered architecture: **entity → repository → service → controller**.
- Treat AI output as draft code; validate logic, security, and edge cases before accepting.
- Enforce store/tenant isolation and ownership checks in all scoped queries.
- Prefer explicit, machine-readable errors for predictable API behavior.
- Prioritize correctness and security over speed.

---

## 1) Technology Stack & Framework Conventions

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web + Spring Security + Spring Data JPA
- PostgreSQL
- Jakarta Bean Validation
- JUnit 5 + Mockito + MockMvc
- SLF4J/Logback structured logging

Conventions:
- Constructor injection only.
- Controllers are thin (HTTP mapping, request validation, response mapping).
- Business rules belong in services.
- Data access belongs in repositories only.
- Avoid static mutable state.

---

## 2) Retail Domain Rules (Mandatory)

- Use `BigDecimal` for all money values (`unitPrice`, `cartTotal`, `discount`, `tax`).
- Never use `float`/`double` for currency calculations.
- Apply explicit rounding and scale rules (`HALF_UP`, scale 2 unless configured).
- Never log raw customer PII (name, email, phone).
- Inventory stock must never become negative.
- All store-scoped data must enforce `storeId` boundaries.

---

## 3) Required Layered Architecture

Use strict layering:
1. **Entity/Model**: JPA entities, constraints, indexes, optimistic locking
2. **Repository**: query methods and persistence logic
3. **Service**: business rules, orchestration, authorization checks
4. **Controller**: API contracts, validation, HTTP status handling

Rules:
- Do not bypass layers.
- No business logic in controllers.
- No direct request-body-to-entity writes.

---

## 4) Coding Standards

### Naming
- Classes: `PascalCase`
- Methods/fields: `camelCase`
- Constants/env keys: `UPPER_SNAKE_CASE`
- DTO classes end with `Dto`

### Error Handling
- Use domain exceptions (`NotFound`, `Forbidden`, `Conflict`, `Validation`).
- Handle errors centrally via `@RestControllerAdvice`.
- Return machine-readable errors:
  - `{ "error": { "code": "SOME_CODE", "message": "Human readable message" } }`
- Never expose stack traces or internal DB details in responses.

### Logging
- Use structured logs with correlation ID / request ID.
- Include contextual fields like `storeId`, `customerId` where safe.
- Never log secrets, raw JWT, raw voucher codes, or raw PII.

---

## 5) Security Constraints (Non-Negotiable)

- No hardcoded credentials or secrets.
- Validate all external input (body, path, query, headers when applicable).
- Avoid unsafe raw SQL construction; prefer JPA repository patterns.
- Apply least privilege for all endpoints and service operations.

### JWT Requirements
- Explicitly pin JWT algorithm to **RS256 or HS256**.
- Explicitly reject `alg: none`.
- Validate signature plus claims: **`exp`, `iat`, `nbf`**.
- If validation fails, return `401` with machine-readable error code:
  - `AUTH_TOKEN_INVALID`
  - `AUTH_TOKEN_EXPIRED`
  - `AUTH_TOKEN_NOT_YET_VALID`

### DTO Whitelisting
- All write endpoints must accept explicit DTOs.
- Only whitelisted fields may be mapped.
- Direct entity binding from request body is prohibited (prevent mass assignment).

### Authorization & BOLA Prevention
- Authentication alone is not sufficient.
- Every resource-by-id endpoint must verify ownership/permission.
- Extract and validate JWT claims:
  - `store_id`, `customer_id`, `role`, `scope`
- All queries must be scoped by authenticated store context.
- Customer data must never be accessible across customers/stores.
- Admin-only endpoints must return `403` (not `401/500`) for non-admin users with machine-readable error code.

---

## 6) Performance Rules (Mandatory)

### Cursor-Based Pagination
All list endpoints must use:
- Request: `cursor`, `limit`
- Response: `items`, `nextCursor`, `hasMore`
- Offset-based pagination (`page`, `skip`, `offset`) is prohibited.

### Batch Processing
For abandonment scan jobs:
- Support configurable `batchSize`.
- Commit each batch independently.
- Track last successful cursor/checkpoint.
- Must be restartable without reprocessing committed batches.

### Indexing
- Define at least 2 indexes at ORM/entity level for high-frequency queries.
- Prefer composite indexes for multi-column filters used by abandonment scans and analytics retrieval.

---

## 7) API Authorization Expectations

- `POST /scan/abandoned-carts`
  - Admin-only
  - Non-admin => `403` with machine-readable code (e.g., `FORBIDDEN_ADMIN_ONLY`)

- `GET /carts/{id}/recovery`
  - Must enforce ownership check
  - Cross-customer access => `403` with machine-readable code (e.g., `FORBIDDEN_RESOURCE`)

- `GET /analytics/recovery`
  - Admin-only
  - Must follow cursor-based pagination
  - Non-admin => `403` with machine-readable code

---

## 8) Testing Expectations

- Target minimum 80% coverage for new business logic.
- Unit tests for services using mocked repositories/integrations.
- Controller tests for auth, validation, and error contracts.
- Mandatory security tests:
  - Reject `alg: none`
  - Reject tokens with invalid/missing `exp`, `iat`, `nbf`
  - Enforce DTO whitelist on writes
  - Verify BOLA protections return `403`
  - Verify admin-only endpoints return `403` for non-admin tokens

---

## 9) Copilot Usage Guidance

- Generate code in small, reviewable chunks.
- After each generation:
  1. Validate against security/authorization/performance rules in this file.
  2. Add or update tests.
  3. Refactor to preserve layer boundaries and naming standards.
- Do not accept generated code without review, especially auth, money, and data-access logic.

---

## 10) Definition of Done (for any implemented unit)

A change is complete only if:
- Layered architecture is preserved.
- DTO validation and authorization checks are present.
- JWT pinning and claim validation are implemented correctly.
- Cursor pagination is used where required.
- Batch jobs are restartable and independently committed.
- Required tests pass, including security-focused tests.