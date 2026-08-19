# Implementation Plan: create-arrival-endpoint

## Summary

- **Goal:** Expose `ArrivalService.createArrival` via `POST /api/arrivals`
  with basic validation, and fix `ArrivalController` to depend on the
  `ArrivalService` interface instead of `ArrivalServiceImpl`.
- **In scope:** New `POST` endpoint, bean-validation annotations on `Arrival`,
  `spring-boot-starter-validation` dependency, controller unit test.
- **Out of scope:** DTO layer, auth, CI, datasource config (per brief).
- **Architecture input:** `docs/architecture/create-arrival-endpoint.md`.

## Touch map

| Area | Why |
| --- | --- |
| `build.gradle.kts` | Add `spring-boot-starter-validation` so `@Valid`/`jakarta.validation` constraints are enforced |
| `entity/Arrival.kt` | Add `@NotNull` on `id`, `arrivalDate`, `items` (no DTO in scope) |
| `controller/ArrivalController.kt` | Add `POST /api/arrivals`; switch constructor param `ArrivalServiceImpl` → `ArrivalService` |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` (new) | Unit test for the new endpoint (success + validation failure) |

## Steps

### Step 1 — Add validation dependency

- **Outcome:** `jakarta.validation` annotations are enforced at runtime.
- **Approach:** Add `implementation("org.springframework.boot:spring-boot-starter-validation")`
  to `dependencies { }` in `build.gradle.kts`.
- **Tests:** None directly; enables Step 2/3 to work.
- **Done when:** `./gradlew build` still resolves dependencies cleanly.

### Step 2 — Annotate `Arrival` for validation

- **Outcome:** Missing/null `id`, `arrivalDate`, or `items` fails validation.
- **Approach:** Add `@field:NotNull` (Kotlin data class — annotation must
  target the backing field) from `jakarta.validation.constraints.NotNull` to
  `id`, `arrivalDate`, and `items` in `entity/Arrival.kt`. Leave `Item`/other
  entities untouched (out of scope).
- **Tests:** Covered indirectly by Step 4's 400 case.
- **Done when:** Entity compiles with the new annotations.

### Step 3 — Add `POST /api/arrivals` and fix injection

- **Outcome:** Clients can create an arrival over HTTP; controller no longer
  depends on the concrete service impl.
- **Approach:** In `ArrivalController.kt`:
  - Change constructor param type from `ArrivalServiceImpl` to
    `ArrivalService` (import from `com.ngolik.arrival.service`).
  - Add `@PostMapping fun createArrival(@Valid @RequestBody arrival: Arrival): ResponseEntity<Arrival>`
    that calls `arrivalService.createArrival(arrival)` and returns
    `ResponseEntity.status(HttpStatus.CREATED).body(...)`.
- **Tests:** Manual smoke check deferred to Step 4's automated test.
- **Done when:** Endpoint compiles and existing `GET /api/arrivals` still
  works unchanged.

### Step 4 — Controller test

- **Outcome:** Automated coverage for the acceptance criteria.
- **Approach:** New `ArrivalControllerTest`, `ArrivalService` mocked via
  `@MockBean`. Cases:
  1. Valid body → `POST /api/arrivals` → 201, body echoes saved entity.
  2. Body missing a required field (e.g. null `arrivalDate`) → 400.
- **Tests:** This step *is* the test.
- **Done when:** `./gradlew test` passes both cases.
- **Deviation (discovered during implementation):** `@WebMvcTest(ArrivalController::class)`
  fails to load its context — `ArrivalServiceApplication` declares
  `@EnableJpaRepositories` directly on the `@SpringBootApplication` class, and
  that import is not filtered out by the web-slice's auto-configuration
  exclusions, so the sliced context tries to build JPA repository beans
  without an `entityManagerFactory` bean (`No bean named 'entityManagerFactory'
  available`). Fixing that properly means moving `@EnableJpaRepositories` off
  the primary application class — out of scope for this brief. Used
  `@SpringBootTest` + `@AutoConfigureMockMvc` instead (same context-loading
  path the pre-existing `ArrivalServiceApplicationTests` already uses
  successfully, with Spring Boot auto-configuring an embedded H2 datasource).
  Test-only change; no architecture decision affected.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| `@field:NotNull` on a JPA `@Id` (`Long`, non-nullable Kotlin type) may be redundant since Kotlin already enforces non-null at compile time for the field type | Keep the annotation for consistency/explicitness per architecture doc; if Kotlin's non-null type makes the constraint unreachable in practice, note it in the PR rather than silently dropping validation intent |
| Adding `starter-validation` could theoretically pull in a transitive version mismatch | Low risk — managed by the existing Spring Boot 3.1.5 BOM already in `build.gradle.kts`; verify with `./gradlew build` in Step 1 |
| `@WebMvcTest` mock setup may differ slightly by Spring Boot 3.1 minor version (`@MockBean` deprecation path) | Use whatever mocking annotation is current for Boot 3.1.5 at implementation time; flag if it requires an extra test dependency not already present |

## Open questions

- `[MISSING — input needed]` (carried from architecture doc): brief doesn't
  specify required fields explicitly; proceeding with `id`, `arrivalDate`,
  `items` as non-null per the architecture doc's reasoning.
