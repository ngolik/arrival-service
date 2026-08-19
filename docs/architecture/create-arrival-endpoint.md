# Architecture: create-arrival-endpoint

## 1. Scope and constraints

- **Problem**: `ArrivalService.createArrival` exists but nothing calls it —
  clients have no way to submit a new arrival over HTTP.
- **Goals**: Add `POST /api/arrivals` that validates and persists a new
  `Arrival`, returning HTTP 201 with the saved record. Fix `ArrivalController`
  to depend on the `ArrivalService` interface instead of the
  `ArrivalServiceImpl` concrete class.
- **Non-goals**: DTO layer, auth/authz, CI setup, datasource configuration
  (all explicitly out of scope in the brief).
- **Constraints**: `layout: spring-layers`; stay on Kotlin/Spring
  Boot/Gradle; `GET /api/arrivals` and all existing entity/repo/service
  signatures stay unchanged.
- **Sources used**: `docs/briefs/create-arrival-endpoint.md`,
  `docs/ai-context/constraints.md`, `docs/ai-context/system-overview.md`,
  `controller/ArrivalController.kt`, `service/ArrivalService.kt`,
  `service/impl/ArrivalServiceImpl.kt`, `entity/Arrival.kt`.

## 2. Existing shape to respect

- `ArrivalController` currently has one `GET` mapping and injects
  `ArrivalServiceImpl` directly — a known inconsistency the brief asks to fix
  (`docs/ai-context/structure.md` flags this same gap).
- `ArrivalService` (interface) already declares `createArrival(arrival: Arrival): Arrival`,
  implemented in `ArrivalServiceImpl` via `arrivalRepository.save(arrival)` — no
  service-layer change needed, only wiring it to HTTP.
- The controller returns JPA entities directly today (no DTO); the brief keeps
  that pattern for this change.
- `Arrival` has no bean-validation annotations yet
  (`id: Long`, `arrivalDate: LocalDateTime`, `items: List<Item>`) — validation
  constraints must be added to the entity itself since no DTO is in scope.
- `[MISSING — input needed]`: the brief doesn't say which fields are
  required. Recommendation below assumes `arrivalDate` and `items` must be
  present (an arrival with no date or no items is meaningless); `id` is
  treated as client-supplied per the existing `@Id` (no auto-generation
  strategy is configured on any entity in this repo) — validate `id` is
  non-null too, consistent with existing entities.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `ArrivalController` | Add `POST /api/arrivals`; accept `@Valid @RequestBody Arrival`, return `ResponseEntity<Arrival>` with 201 | Also switch constructor param type `ArrivalServiceImpl` → `ArrivalService` |
| `ArrivalService` / `ArrivalServiceImpl` | Unchanged | `createArrival` already implemented |
| `Arrival` entity | Add `jakarta.validation` constraints (`@NotNull`) on `id`, `arrivalDate`, `items` | No new class — annotate existing fields per non-DTO scope |
| Build config | Add `spring-boot-starter-validation` dependency | Required for `@Valid`/`jakarta.validation` annotations to be enforced in Spring Boot 3 — not currently on the classpath (`build.gradle.kts` has `spring-boot-starter-web` only) |

- **Happy path**: Client `POST`s an `Arrival` JSON body → controller
  validates via `@Valid` → delegates to `arrivalService.createArrival()` →
  repository persists → controller returns 201 with the saved entity.
- **Failure path**: A missing/null required field fails bean validation
  before reaching the service layer → Spring's default
  `MethodArgumentNotValidException` handling returns HTTP 400 (no custom
  exception handler needed for this scope).
- **Data classes**: Request and response both use the existing `Arrival`
  entity (public API surface = persistence model, per non-DTO non-goal).
  No new sensitive fields introduced.
- **Authn/authz**: None — explicitly out of scope; endpoint is open like the
  existing `GET`.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Validation mechanism | (a) DTO with its own constraints, (b) annotate the entity directly | (b) annotate entity | Brief explicitly excludes a DTO layer for this change |
| Dependency addition | (a) hand-roll null checks in the controller, (b) add `spring-boot-starter-validation` and use `@Valid` | (b) add starter-validation | Idiomatic Spring approach, minimal code, matches "Basic request validation (`@Valid`/`@NotNull`)" wording in the brief |
| Response status | 200 vs 201 | 201 | Brief acceptance criteria requires 201 for a successful create |

- **Decision Drift note**: adding `spring-boot-starter-validation` is a new
  runtime dependency not mentioned in `docs/ai-context/constraints.md` or the
  brief's constraints section. It's a minor, standard addition (no version
  conflict — managed by the existing Spring Boot BOM) but is called out here
  so it isn't silently introduced; flag in G3 diff review.
- **Open question**: `[MISSING — input needed]` whether `id` should really be
  client-supplied on create (no `@GeneratedValue` exists on any entity in this
  repo today). Proceeding with the existing pattern (client supplies `id`,
  same as every other entity) rather than introducing ID generation, since
  that's outside this brief's scope.

## 5. Implementation handoff

- Touch areas: `controller/ArrivalController.kt` (add endpoint, fix
  injection type), `entity/Arrival.kt` (add validation annotations),
  `build.gradle.kts` (add `spring-boot-starter-validation`), plus a new/updated
  test under `src/test/kotlin/com/ngolik/arrival/controller/`.
- Branch: `feature/create-arrival-endpoint` (already created).
