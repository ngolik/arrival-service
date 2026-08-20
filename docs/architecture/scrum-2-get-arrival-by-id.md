# Architecture: Add GET /api/arrivals/{id} endpoint

## 1. Scope and constraints

- **Problem**: Clients can list all arrivals (`GET /api/arrivals`) and create one (`POST /api/arrivals`), but cannot fetch a single arrival by id. No read-by-id path exists.
- **Goals**: Add `GET /api/arrivals/{id}` returning 200 + body when found, 404 when not found, using the same response shape as the list endpoint.
- **Non-goals**: Update/delete, pagination/filtering on the list endpoint, auth, CI, datasource configuration, new persistence model.
- **Constraints**: `layout: spring-layers` (controller / entity / repo / service+impl). Reuse existing `ArrivalResponse` DTO — do not add a second response shape. No version/stack changes.
- **Sources used**: `docs/briefs/scrum-2-get-arrival-by-id.md`; `controller/ArrivalController.kt`; `service/ArrivalService.kt` + `service/impl/ArrivalServiceImpl.kt`; `repo/ArrivalRepository.kt`; `dto/ArrivalResponse.kt`.

## 2. Existing shape to respect

- `ArrivalController` (`/api/arrivals`) delegates to `ArrivalService`, maps `Arrival` entities to `ArrivalResponse` via `toResponse()` before returning.
- `ArrivalService` / `ArrivalServiceImpl` currently expose only `getAllArrivals()` and `createArrival()`, backed by `ArrivalRepository : JpaRepository<Arrival, Long>`.
- `ArrivalRepository` already inherits `findById(id): Optional<Arrival>` from `JpaRepository` — no new repository method needed.
- No datasource is configured in `application.yml`/`application.properties` today; this is pre-existing (the list/create paths already depend on the JPA repository), not a new dependency introduced by this change.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `ArrivalController` | Add `GET /{id}` handler, path variable `id: Long` | Return `ResponseEntity<ArrivalResponse>`: 200 with mapped body, or 404 |
| `ArrivalService` (+ impl) | Add `getArrivalById(id): Arrival?` (or equivalent nullable/Optional return) | Delegates to `arrivalRepository.findById(id)`; no new persistence logic |
| `ArrivalRepository` | No change | `findById` already available via `JpaRepository` |
| `dto` package | No change | Reuse `ArrivalResponse` + existing `toResponse()` mapper |

- Happy path: controller → service `getArrivalById` → repository `findById` → map to `ArrivalResponse` → 200.
- Not found: service/controller returns empty → controller returns `ResponseEntity.notFound().build()` (404).
- Data classes: no new DTOs; `ArrivalResponse` is already the public shape for arrivals.
- Authn/authz: none today, unchanged.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Response shape | New single-arrival DTO vs reuse `ArrivalResponse` | Reuse `ArrivalResponse` | Brief requires same shape as list endpoint; avoids a redundant DTO |
| Not-found handling | Throw + `@ExceptionHandler` vs inline `ResponseEntity` branch | Inline `ResponseEntity` branch in controller | Smallest change; no existing exception-handling layer to extend into |
| Service method signature | Return `Arrival?` vs `Optional<Arrival>` | `Arrival?` (Kotlin-idiomatic) | Matches existing service style (`Arrival`, not `Optional<Arrival>`), converts from repo's `Optional` at the service boundary |

- Open question: `application.yml` has no datasource block, so how `findById` behaves at runtime (real DB vs failure to start) is a pre-existing condition shared with `getAllArrivals`/`createArrival` — out of scope to fix here per the brief's non-goals.

## 5. Implementation handoff

- Touch areas: `controller/ArrivalController.kt`, `service/ArrivalService.kt`, `service/impl/ArrivalServiceImpl.kt`, and their tests (`test/.../controller/ArrivalControllerTest.kt`, service impl test if one exists).
- No new packages, no new DTOs, no repository changes.
- Branch: `feature/SCRUM-2-get-arrival-by-id` (already created).
