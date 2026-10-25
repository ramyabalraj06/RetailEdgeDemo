# Prompt and Generation Notes

## User Prompt

Act as a senior Spring Boot + security engineer. Review and remediate the inherited AI-generated Inventory module using `.github/copilot-instructions.md` (or `instructions.md` if missing). Do not start cart-abandonment work.

Goal:
1) Create `REVIEW.md` with evidence-based findings from the current baseline.
2) Fix the Inventory module to production standards.
3) Add tests and report actual results.

Scope:
- Keep work to inventory + shared infra needed for inventory only.
- Backend only (no UI work).
- Preserve/record baseline before edits.

Review checklist (verify, don’t assume):
- Layering: entity → repository → service → REST controller.
- Missing controllers/APIs for create, update-stock, get-by-product, delete.
- No direct body-to-entity binding; use DTO whitelisting.
- Input validation on all external inputs.
- PostgreSQL + Spring Data JPA conventions; no unsafe raw SQL.
- No hardcoded secrets; env-based config.
- Structured logging; no PII/JWT/secrets in logs.
- Domain errors + machine-readable error responses.
- Nonnegative stock enforcement.
- Concurrency safety via optimistic locking (`@Version`) and 409 on stale updates.
- Store scoping and resource ownership checks (BOLA prevention).
- Inventory mutations restricted to admin/service roles.
- 401 vs 403 correctness.
- JWT security: algorithm pinned (RS256 or HS256 per instructions), reject `alg:none`, validate signature + `exp` + `iat` + `nbf` (+ issuer/audience if required).
- Pagination: if list endpoint exists, use cursor/limit and return items/nextCursor/hasMore (no offset/page/skip).
- At least 2 indexes in entity metadata, with query purpose documented.

`REVIEW.md` format for each finding:
- ID, title, severity (Critical/High/Medium/Low)
- file/symbol/line reference
- evidence
- retail impact
- detection method
- remediation
- status: Open/Fixed/Partially Fixed/Blocked
- verification evidence

Required `REVIEW.md` sections:
- Security review (JWT pinning/claims, DTO mass-assignment prevention, BOLA/store isolation)
- “Issues Copilot Introduced That Required Human Judgment” (include at least one verified retail-specific risk; if none, say so honestly)

Implementation expectations:
- Complete inventory layers + APIs.
- DTOs, validation, authz, logging, error handling.
- Keep changes focused; no unrelated refactors.
- Add JavaDoc for public methods.

Tests (JUnit5/Mockito/MockMvc; Testcontainers PG if available):
- CRUD/service success paths
- negative stock rejection
- stale version/conflict path
- invalid input + unknown-field rejection
- unauthenticated 401
- unauthorized role 403
- cross-store access denied
- JWT alg-none/claim validation failures
- machine-readable errors
- DB constraints/locking behavior

Run build/tests/coverage and report exact commands + actual outcomes. If not runnable, state why clearly.
Append this prompt to `PROMPTS.md` with feature + technique notes and list post-generation corrections.

## Feature Notes

- Added store-scoped inventory CRUD REST API protected by ADMIN or INVENTORY_SERVICE role plus `inventory:manage` scope.
- Added RS256 resource-server JWT validation with required issuer, audience, JWKS URI, `exp`, `iat`, `nbf`, `store_id`, `role`, and `scope` values supplied/configured without hardcoded credentials.
- Added optimistic versioning, expected-version writes, nonnegative constraints, documented ORM indexes, validated DTOs, machine-readable errors, request correlation, and structured safe operation logs.
- No inventory list route or cart-abandonment feature was introduced.

## Technique Notes

- Preserved the source baseline with pre-edit SHA-256 hashes and a passing Maven test run because the project folder has no `.git` directory and Git is unavailable in the environment.
- Used Spring Data JPA scoped derived queries, Bean Validation DTO records, stateless Spring Security resource-server JWT support, and centralized exception handling.
- Added MockMvc and JWT validator tests, optional PostgreSQL Testcontainers tests, and a JaCoCo line-coverage gate for `InventoryService`.
- Retained the existing Java 17 / Spring Boot 4.1.2 versions per the user’s platform decision; recorded the mismatch with the instructions’ Java 21 / Boot 3.x guidance in `REVIEW.md`.

## Post-Generation Corrections

- Replaced the inherited globally unique product ID with store-scoped uniqueness after identifying that multiple retailers/stores commonly carry the same SKU; every lookup and mutation is scoped by the authenticated store.
- Added `@Version` and expected-version checks to prevent lost stock updates; stale writes map to HTTP 409.
- Added the missing controller/API and DTO whitelist rather than exposing JPA entities, with unknown JSON fields rejected.
- Added RS256 pinning and required claim validation; authorization distinguishes unauthenticated 401 responses from authenticated role/store 403 responses.
- Replaced generic service exceptions with domain errors and a consistent machine-readable response envelope.
- The baseline source hashes and Maven test result are recorded in `REVIEW.md`; no Git diff could be captured because Git metadata and executable are absent.

## Follow-up UI Notes

- Added a Thymeleaf inventory operations page at `/inventory-ui`, with JavaScript calls for all four inventory API operations.
- Configured `classpath:/templates/` and `classpath:/static/` explicitly in `application.properties`.
- Kept API authorization intact: the page/assets are public, while API calls still require the existing RS256 bearer token and inventory role/scope. The token is not persisted to local or session storage.
- Added a page/static-resource MockMvc smoke test; execution was skipped, so runtime rendering remains unverified.
