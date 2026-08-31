# Implementation Plan: Add GET /api/arrivals/{id} endpoint

## Summary

- **Goal:** Add `GET /api/arrivals/{id}` returning 200 + `ArrivalResponse` when found, 404 when not found.
- **In scope:** Controller handler, service method, controller + service tests.
- **Out of scope:** Update/delete, pagination/filtering, auth, CI, datasource config, new DTOs, repository changes.
- **Architecture input:** `docs/architecture/scrum-2-get-arrival-by-id.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt` | Add `getArrivalById(id: Long): Arrival?` to the interface |
| `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt` | Implement it via `arrivalRepository.findById(id)` |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Add `GET /{id}` handler mapping service result to 200/404 |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Add 200 and 404 cases for the new endpoint |

No changes to `repo/ArrivalRepository.kt` or `dto/ArrivalResponse.kt` — both already support this.

## Steps

### Step 1 — Service: add `getArrivalById`

- **Outcome:** `ArrivalService` exposes a way to fetch one arrival by id, returning `null` when absent.
- **Approach:** Add `fun getArrivalById(id: Long): Arrival?` to `ArrivalService` interface. Implement in `ArrivalServiceImpl` as `arrivalRepository.findById(id).orElse(null)`, matching the Kotlin-idiomatic nullable style used elsewhere.
- **Tests:** Covered indirectly via controller test (no standalone service test exists today for `getAllArrivals`/`createArrival` either — stay consistent, don't introduce a new test layer unprompted).
- **Done when:** Interface + impl compile; `ArrivalServiceImpl.getArrivalById` delegates to the repository with no new persistence logic.

### Step 2 — Controller: add `GET /{id}` handler

- **Outcome:** `GET /api/arrivals/{id}` returns 200 with `ArrivalResponse` body when the arrival exists, 404 (empty body) when it does not.
- **Approach:** Add `@GetMapping("/{id}") fun getArrivalById(@PathVariable id: Long): ResponseEntity<ArrivalResponse>` to `ArrivalController`. On `null` from the service, return `ResponseEntity.notFound().build()`; otherwise `ResponseEntity.ok(arrival.toResponse())`.
- **Tests:** Controller test (Step 3) is the verification for this step.
- **Done when:** Endpoint compiles and matches the response shape used by `GET /api/arrivals`.

### Step 3 — Tests: 200 and 404 cases

- **Outcome:** `ArrivalControllerTest` covers both branches of the new endpoint.
- **Approach:** Add two `@Test` methods following the existing pattern (`MockBean` `ArrivalService`, `mockMvc.perform(get(...))`):
  - `GET /api/arrivals/{id}` with `arrivalService.getArrivalById(1L)` stubbed to return a sample arrival → expect 200 + `jsonPath("$.id").value(1)`.
  - `GET /api/arrivals/{id}` with `arrivalService.getArrivalById` stubbed to return `null` → expect 404.
- **Tests:** These are the tests.
- **Done when:** `./gradlew test` passes, including the two new cases and the three existing ones (list, create, validation-400) unchanged.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| No datasource configured in `application.yml`/`application.properties` — `@SpringBootTest` may fail to load context if a real DB is expected at runtime | Pre-existing condition shared with `getAllArrivals`/`createArrival`; existing `ArrivalControllerTest` already passes today under the same setup, so this is not a new risk introduced by this change. Out of scope per brief. |
| Non-numeric `{id}` path variable (e.g. `/api/arrivals/abc`) | Not in acceptance criteria; Spring's default type-mismatch handling (400) applies — no custom handling added, no test added for it, consistent with "keep the change small" constraint. |

## Open questions

- None — architecture doc and brief fully bound this change.
