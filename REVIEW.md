# Inventory Review and Remediation Record

## Baseline

Captured before implementation on 2026-10-25 from `C:\Users\Administrator\Downloads\retailedge\retailedge`.

- Baseline test command: `Push-Location .\retailedge; .\mvnw.cmd test; Pop-Location`
- Baseline result: **BUILD SUCCESS**, 4 tests run, 0 failures, 0 errors, 0 skipped (3 inventory tests, 1 application context test).
- Git baseline: **unavailable**. `git` is not installed/on PATH, the standard Windows Git path was absent, and `retailedge\.git` does not exist. `git status --short`, `git diff`, and `git ls-files --others --exclude-standard` could not run. To preserve a recoverable source baseline, SHA-256 hashes were captured before source edits:

| Baseline file | SHA-256 |
|---|---|
| `pom.xml` | `6DE4301521EE26F1F06B5F8B1E6799DA821A88DC421B2E2F61B81AE60FEC2668` |
| `src/main/java/com/demo/retailedge/inventory/Inventory.java` | `774ED4BDCD6C47D4849A4DD5D29A3DABFCC6E755DEA096AA04B9FE1E351F6CC9` |
| `src/main/java/com/demo/retailedge/inventory/InventoryRepository.java` | `80E7B2B0CC349E5FC83E7BB07687646297B2B47A3F5FAB38982E02732E81382F` |
| `src/main/java/com/demo/retailedge/inventory/InventoryService.java` | `FB1EBDBBF60595958BFD0019F69F09C9A4638D412E23917D0E5F910674719A28` |
| `src/main/resources/application.properties` | `4B8744F3571F61A2851B95EEDFF9946207A8DE3A0D2EFB7DF002B48ADE34D31C` |
| `src/test/java/com/demo/retailedge/inventory/InventoryServiceTests.java` | `86985D371A5266104474BFA2841FBFE315565C2B40CEBFF61BF7FC9E2782E561` |
| `.mvn/wrapper/.github/copilot-instructions.md` | `9C165B81DCEFE67A04B5A0A71C1D2A6D382386EA88FB298B1ED7CC63E126BE17` |

## Security Review

The only discovered instructions file is `.mvn/wrapper/.github/copilot-instructions.md`; it requires layered architecture, store isolation, explicit JWT algorithm and `exp`/`iat`/`nbf` validation, DTO whitelisting, machine-readable errors, two entity indexes, and at least 80% coverage of new business logic. It specifies Java 21 and Spring Boot 3.x; the project currently uses Java 17 and Spring Boot 4.1.2. Per user decision, the platform versions will remain unchanged and the mismatch will remain documented.

### INV-SEC-01 — High — No authenticated inventory API or JWT validation

- **File/symbol/line:** `pom.xml`, `<dependencies>` lines 33-49; `RetailedgeApplication`; no inventory controller or security configuration exists.
- **Evidence:** The POM contains JPA, PostgreSQL, test, and H2 dependencies only; source discovery found no web/security resource server or inventory controller. No verifier pins RS256, rejects `alg:none`, or validates signature, `exp`, `iat`, `nbf`, issuer, or audience.
- **Retail impact:** There is no HTTP API to serve inventory, and adding one without authentication could expose stock operations to anonymous callers.
- **Detection method:** Dependency/source inspection and baseline test suite.
- **Remediation:** Add an authenticated resource-server API using environment-configured RS256 trust, issuer/JWKS/public-key source, audience, and required claims; fail closed.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Added `InventoryController` CRUD routes and `InventorySecurityConfiguration` with RS256-pinned JWKS decoding, issuer validation, and required claim validation in `InventoryJwtClaimsValidator`. JWT/API tests were added but the post-change Maven run was skipped; runtime behavior remains unverified.

### INV-SEC-02 — High — No DTO whitelist, request validation, or role gate

- **File/symbol/line:** `InventoryService.create` line 18; `InventoryService.updateStock` line 27; no request DTO/controller exists.
- **Evidence:** Current write methods accept primitive arguments and the module has no API DTO, Bean Validation, unknown-property rejection, or Spring Security authorization rules.
- **Retail impact:** Any future HTTP binding directly to `Inventory` could allow mass assignment; no ADMIN/service restriction exists for inventory mutations.
- **Detection method:** Source search for controller, DTO, `@Valid`, Jackson unknown-field policy, and method/request authorization.
- **Remediation:** Add explicit whitelisted request DTOs with Bean Validation and unknown-field rejection; require `ADMIN` or `INVENTORY_SERVICE` on inventory APIs.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Added request DTOs with Bean Validation, unknown-field rejection, and role/scope authorization. MockMvc tests cover these contracts but were not executed after implementation.

### INV-SEC-03 — High — Inventory has no store ownership boundary (verified retail-specific risk)

- **File/symbol/line:** `Inventory.productId` line 18; `InventoryRepository.findByProductId` line 9, `existsByProductId` line 11, `deleteByProductId` line 13.
- **Evidence:** Product ID alone is globally unique and is the only repository key; the entity and queries contain no `storeId`.
- **Retail impact:** Two stores may legitimately carry the same SKU. Global uniqueness prevents the second store from recording it; if the uniqueness constraint is removed without scoped queries, one store could read, modify, or delete another store’s stock (BOLA).
- **Detection method:** Entity/repository inspection; baseline has no cross-store tests.
- **Remediation:** Scope inventory uniqueness and every repository operation by authenticated `store_id`; deny cross-store requests with 403.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Entity uniqueness and repository methods are store-scoped; H2 service tests passed for same SKU in two stores and cross-store lookup isolation (6 inventory service tests, 0 failures). The controller path-store 403 test was added but not executed.

### INV-SEC-04 — High — Stock writes have no optimistic concurrency control

- **File/symbol/line:** `Inventory.stockQuantity` line 22; `InventoryService.updateStock` line 27.
- **Evidence:** No `@Version` field exists and updateStock loads, changes, and saves without an expected version.
- **Retail impact:** Concurrent stock changes can silently overwrite one another and produce inaccurate on-hand quantities.
- **Detection method:** Entity/service inspection; no concurrent update test exists in the baseline.
- **Remediation:** Add JPA optimistic versioning, require expected version for updates, translate stale writes to HTTP 409, and test stale updates against the database.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Added `@Version`, expected-version checks, and 409 mapping. The H2 service stale-version test passed; the Testcontainers stale-entity PostgreSQL test was added but not run, so database-level concurrent locking remains unverified.

### INV-SEC-05 — Medium — Generic exceptions cannot produce stable API error contracts

- **File/symbol/line:** `InventoryService.create` line 22; `getByProduct` line 39; `deleteByProduct` line 45; validation lines 52 and 58.
- **Evidence:** The service throws `IllegalStateException`, `NoSuchElementException`, and `IllegalArgumentException`; there is no domain exception hierarchy or REST advice.
- **Retail impact:** Clients cannot reliably distinguish validation, missing inventory, authorization, and conflict cases; internal exception handling can leak implementation details.
- **Detection method:** Service/source inspection and absence of `@RestControllerAdvice`.
- **Remediation:** Introduce domain validation/not-found/forbidden/conflict errors and a centralized `{ "error": { "code": "...", "message": "..." } }` response without stack traces or database details.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Added domain validation/not-found/conflict errors and `InventoryApiExceptionHandler` with the required nested error envelope. MockMvc error assertions were added but not executed after implementation.

### INV-SEC-06 — Medium — Required index metadata is absent

- **File/symbol/line:** `Inventory` table annotation line 11 and product ID constraint line 18.
- **Evidence:** No `@Index` metadata exists. The unique product ID constraint does not provide the required documented pair of high-frequency ORM indexes for store-scoped access.
- **Retail impact:** Store-scoped inventory reads and stock-availability queries may become expensive as catalog size grows.
- **Detection method:** Entity annotation inspection.
- **Remediation:** Define/document a composite store/product lookup index and a second store-scoped stock/availability index based on inventory query patterns.
- **Status:** Open (baseline).
- **Status:** Fixed.
- **Verification evidence:** `Inventory` declares the documented `store_id, product_id` and `store_id, stock_quantity` indexes plus store/product uniqueness. The entity was initialized against H2 during the passing focused service tests; no production query-plan test was required or run.

### INV-SEC-07 — Low — No operation logging or request correlation

- **File/symbol/line:** `InventoryService` lines 10-60.
- **Evidence:** No logger or request correlation context is present.
- **Retail impact:** Operators have limited ability to diagnose inventory failures or trace mutation attempts.
- **Detection method:** Service/source inspection.
- **Remediation:** Add structured operation/request/store context logging, excluding bearer tokens, credentials, customer PII, and other secrets.
- **Status:** Open (baseline).
- **Status:** Partially Fixed.
- **Verification evidence:** Added SLF4J key-value mutation logs, request-ID MDC filtering, and a console pattern that emits `%kvp`. Post-change rendered log output was not verified because the final test command was skipped.

### INV-SEC-08 — Low — Build platform differs from repository guidance

- **File/symbol/line:** `pom.xml` parent version line 8 and `java.version` line 30.
- **Evidence:** Current build is Spring Boot 4.1.2 / Java 17; the only instructions file specifies Spring Boot 3.x / Java 21.
- **Retail impact:** Runtime and library compatibility expectations in the project guidance are not met.
- **Detection method:** Compare POM to `.mvn/wrapper/.github/copilot-instructions.md`.
- **Remediation:** Platform migration was explicitly deferred by the user; retain current versions and report the mismatch.
- **Status:** Open (deferred by user decision).
- **Verification evidence:** Baseline test suite passes on Java 17 / Boot 4.1.2.

### INV-SEC-09 — High — Existing inventory rows need an explicit store backfill before deployment

- **File/symbol/line:** `src/main/resources/application.properties`, `spring.jpa.hibernate.ddl-auto` line 4; baseline `Inventory` had no `storeId` field (`Inventory` entity line 12).
- **Evidence:** The baseline schema was configured for Hibernate `update`, inventory rows had no store ownership, and no migration framework or existing-data store mapping is present in the workspace.
- **Retail impact:** Automatically assigning legacy stock to a guessed/default store could expose or misstate another store’s inventory; existing rows cannot safely be assigned from product ID alone.
- **Detection method:** Baseline schema/config inspection; no PostgreSQL instance or live inventory data was available for inspection.
- **Remediation:** Before deploying to a populated database, provide an authoritative product-to-store ownership mapping and add a versioned migration that backfills `store_id`, then enforces non-null and `(store_id, product_id)` uniqueness. Do not assign a shared default store.
- **Status:** Blocked pending deployment data mapping.
- **Verification evidence:** No live database was inspected and no migration/backfill was run. The Testcontainers database test was added but not executed.

## Issues Copilot Introduced That Required Human Judgment

- **Verified retail-specific risk:** The inherited model makes `productId` globally unique. Stores commonly carry the same SKU, so a single global inventory row is an invalid retail ownership model; the data must be unique and queried within a store boundary. Removing only the unique constraint would instead risk cross-store access unless all repository operations are scoped.
- The prior implementation added service CRUD and a database mapping but no API/security layer. This review treats that as an incomplete exposed feature rather than claiming authenticated API coverage.
- Git metadata/executable is absent in this environment. A Git diff baseline could not be recorded; pre-edit hashes and the exact passing test result above are the available source baseline. No files were overwritten as part of baseline capture.

## Baseline Test Evidence

- Command: `Push-Location .\retailedge; .\mvnw.cmd test; Pop-Location`
- Result: **BUILD SUCCESS**; 4 tests, 0 failures, 0 errors, 0 skipped.
- The baseline tests use H2; PostgreSQL behavior, security, API contracts, concurrency, and coverage thresholds were not tested/configured.

## Remediation Verification

### Commands and outcomes

- Baseline: `Push-Location .\\retailedge; .\\mvnw.cmd test; Pop-Location` — **BUILD SUCCESS**, 4 tests, 0 failures, 0 errors, 0 skipped.
- Domain slice: `Push-Location .\\retailedge; .\\mvnw.cmd test -Dtest=InventoryServiceTests; Pop-Location` — **BUILD SUCCESS**, 6 tests, 0 failures, 0 errors, 0 skipped. This ran after the store-scoped/versioned entity and service changes, before API/security/test additions.
- API/JWT focused suite: `Push-Location .\\retailedge; .\\mvnw.cmd test -Dtest=InventoryServiceTests,InventoryApiTests,InventoryJwtClaimsValidatorTests; Pop-Location` — **skipped by the user/tool**, not run; no result is claimed.
- Compile-only: `Push-Location .\\retailedge; .\\mvnw.cmd -DskipTests compile; Pop-Location` — terminal context reports **exit code 0** for the inventory/API/security source before the final `HandlerMethodValidationException` mapping was added.
- Full verification: `Push-Location .\\retailedge; .\\mvnw.cmd clean verify; Pop-Location` — **skipped by the user/tool**, not run; no result is claimed.
- Latest focused validation regression: `Push-Location .\\retailedge; .\\mvnw.cmd test -Dtest=InventoryApiTests; Pop-Location` — **skipped by the user/tool**, not run; the new all-route `@Size` assertions have no runtime result yet.
- An initial compile detected incorrect Boot 4/Spring Security/Jackson package imports. Imports were corrected, but the requested recompile was skipped; final Maven compilation is therefore unverified.
- Workspace Java diagnostics: **No errors found** for inventory main and test sources.
- Coverage: JaCoCo report/check is configured with an 80% line threshold for `InventoryService`; coverage has not been measured because `verify` was skipped.
- PostgreSQL/Testcontainers: tests are present but were not run. Docker availability was not established; PostgreSQL constraints and real database optimistic locking remain unverified.
- Git status/diff: unavailable because Git is not installed/on PATH and the project directory has no `.git` directory. Baseline hashes above were captured before source edits.
- Existing populated-database migration: blocked until the owner can provide authoritative store ownership for baseline inventory rows; deploying the new non-null store field to that schema is not verified safe.

The findings retain baseline evidence and distinguish implemented controls from runtime verification that could not be completed. The selected Java 17 / Spring Boot 4.1.2 platform mismatch remains deferred by user decision.

## UI Follow-up

- Added a public Thymeleaf operations page at `GET /inventory-ui`; the inventory `/api/**` routes remain protected by the existing RS256/role/scope policy.
- Template: `src/main/resources/templates/inventory.html`.
- JavaScript: `src/main/resources/static/js/inventory.js`.
- Styles: `src/main/resources/static/css/inventory.css`.
- Template/static roots are configured in `src/main/resources/application.properties` with `spring.thymeleaf.prefix=classpath:/templates/`, `spring.thymeleaf.suffix=.html`, and `spring.web.resources.static-locations=classpath:/static/`.
- The page calls create, get-by-product, update-stock, and delete. Bearer tokens remain in the password field/page memory, are sent only in the Authorization header to same-origin APIs, and are not stored in browser storage or printed in the response panel.
- UI route/asset smoke test: `Push-Location .\\retailedge; .\\mvnw.cmd test -Dtest=InventoryPageTests; Pop-Location` — skipped by the user/tool; rendering and static serving are not runtime-verified. Java diagnostics report no errors.