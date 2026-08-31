# Architecture: Mark arrival as waiting (with optional remark)

## 1. Scope and constraints

- **Problem**: `Arrival` has no operational "waiting" state today. An operator
  needs to mark an *existing* arrival as waiting, optionally with a plain-text
  remark, and see that state on the read path.
- **Goals**: Add `isWaiting: Boolean` + `remark: String?` to the `Arrival`
  record; add an endpoint to set them on an existing arrival; surface them on
  `GET /api/arrivals/{id}` (and the list/create response shape, unchanged for
  callers that don't set them).
- **Non-goals**: A "list all waiting deliveries" endpoint (explicitly out of
  scope per spec). Money/amount handling — owned by `expenses-service` in the
  same cross-repo story, correlated only by `Arrival.id`, no live call between
  the two services. Operator identity/auth — not required by this repo's
  slice (identity is `expenses-service`'s concern for its own record).
- **Constraints**: `layout: spring-layers` (controller / entity / repo /
  service + impl / dto). No version/stack changes (Kotlin 1.8.22, Spring Boot
  3.1.5, JPA — unchanged). `Arrival.id` stays client-supplied, unchanged — it
  is the shared correlation key with `expenses-service`.
- **Sources used**: `docs/specifications/waiting-delivery-cost.md` (external,
  read-only reference); `entity/Arrival.kt`; `controller/ArrivalController.kt`;
  `service/ArrivalService.kt` + `service/impl/ArrivalServiceImpl.kt`;
  `dto/ArrivalResponse.kt` + `dto/ArrivalMapper.kt`; prior architecture docs
  (`scrum-2-get-arrival-by-id.md`) for this repo's conventions.

## 2. Existing shape to respect

- `ArrivalController` (`/api/arrivals`) delegates to `ArrivalService`, maps
  `Arrival` entities to `ArrivalResponse` via `toResponse()`.
- `Arrival` is a JPA `@Entity` implemented as a Kotlin `data class` with a
  client-supplied `@Id val id: Long` (no `@GeneratedValue`). It is not
  immutable in the persistence sense — `ArrivalRepository.save()` (inherited
  `JpaRepository<Arrival, Long>`) already round-trips a full replacement
  object on `createArrival`. There is no reactive/detached-entity mutation
  pattern elsewhere in this repo to preserve — "update" here means "load,
  produce a new instance via `.copy()`, save," which fits Kotlin `data class`
  idiom and requires no change to how JPA is wired.
- `ArrivalService` / `ArrivalServiceImpl` today expose only
  `getAllArrivals()`, `createArrival()`, `getArrivalById()`. No update method.
- Validation today is `@Valid` on entity/DTO fields + Spring Boot's default
  `MethodArgumentNotValidException` → 400 handling (verified by the existing
  `POST arrivals with a missing required field returns 400` test). No
  `@ControllerAdvice`/`@ExceptionHandler` exists in this repo — this change
  does not introduce one, staying consistent.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `entity/Arrival.kt` | Add `isWaiting: Boolean = false` and `remark: String? = null`, both with sane defaults so existing JSON payloads (create) keep working unchanged | `@field:Size(max = 500)` on `remark` — applies to any path that sets it, including create, for consistency |
| `dto/MarkArrivalWaitingRequest.kt` (new) | Request body for the new endpoint: `remark: String? = null`, `@field:Size(max = 500)` | Small, dedicated request DTO — mirrors this repo's existing DTO-per-shape style (`ArrivalResponse`, `ItemResponse`) rather than reusing the entity as a request body for this action |
| `ArrivalService` (+ impl) | Add `markAsWaiting(id: Long, remark: String?): Arrival?` | Loads via `findById`, returns `null` if absent (mirrors `getArrivalById`'s not-found convention); on hit, `.copy(isWaiting = true, remark = remark)` + `repository.save()` |
| `ArrivalController` | Add `PUT /{id}/waiting` handler, `@Valid @RequestBody(required = false)` request | 200 + `ArrivalResponse` body on hit, 404 on miss (same pattern as `GET /{id}`); a body-validation failure (remark > 500 chars) short-circuits to Spring's default 400 before the handler runs — no record is created/updated |
| `dto/ArrivalResponse.kt` + `ArrivalMapper.kt` | Add `isWaiting: Boolean`, `remark: String?` to the response, mapped straight through | Same shape for list/get-by-id/create responses — no second response DTO |
| `ArrivalRepository` | No change | `findById`/`save` already available via `JpaRepository` |

- Happy path: controller → service `markAsWaiting` → repository `findById` →
  `.copy()` → `save()` → map to `ArrivalResponse` → 200.
- Not found: service returns `null` → controller returns 404, no
  create-on-miss (this is a mutation on an *existing* arrival, per spec).
- Remark too long: `@Valid` on the request DTO rejects before the controller
  method body runs → 400, no record touched (satisfies "hard reject, not
  truncated").
- Backward compatibility: `isWaiting` defaults `false`, `remark` defaults
  `null` on the entity — `POST /api/arrivals` payloads that omit both fields
  deserialize unchanged (Kotlin data class defaults + `jackson-module-kotlin`,
  already on the classpath).

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Endpoint shape | `PATCH /{id}` (generic partial update) vs `PUT /{id}/waiting` (state-specific sub-resource) vs `POST /{id}/waiting` | `PUT /{id}/waiting` | Setting waiting state + remark is idempotent (same call twice → same end state) and scoped to one concern, not a generic partial-update surface; sub-resource path mirrors REST conventions and avoids designing a general PATCH/merge semantics this codebase doesn't have yet. [INFERRED — please validate: spec does not mandate an HTTP verb/path] |
| Request body | Reuse `Arrival` entity vs new small DTO | New `MarkArrivalWaitingRequest(remark: String?)` | The action only ever sets `remark`; reusing the full entity would let callers smuggle in unrelated field changes (id, items, arrivalDate) through this endpoint, which isn't the intent |
| Mutation mechanism | Make `Arrival` mutable (`var`) vs `.copy()` + `save()` | `.copy()` + `save()` | Preserves `data class` value semantics used throughout the entity layer (`Item`, `Supplier`, etc.); `save()` on a client-supplied-id entity already round-trips full-object writes today (`createArrival`), so this is not a new persistence pattern |
| `remark` validation scope | Validate only on the waiting endpoint vs also on the entity (thus on create too) | Also on the entity (`@field:Size(max = 500)`) | One validation rule, enforced everywhere the field can be set, avoids a bypass via `POST /api/arrivals` with an inline `remark`/`isWaiting` in the body. [INFERRED — please validate: spec only describes the mark-as-waiting flow, not direct-create-with-remark] |
| Not-found handling | Throw + handler vs inline `ResponseEntity` branch | Inline branch (matches `getArrivalById`) | Consistent with existing convention, no new exception-handling layer |
| Empty request body | Require a JSON body always vs allow omission | `@RequestBody(required = false)`, treat null as "no remark" | Remark is optional per spec; forcing callers to always send `{}` is unnecessary friction |

- Open question: whether `isWaiting`/`remark` should be settable directly via
  `POST /api/arrivals` (create) is not addressed by the spec (which only
  describes marking an *existing* arrival). This change does not block it
  (the fields exist on the entity, so a create payload including them will be
  persisted), but the dedicated creation-time UX is out of scope here.
  [MISSING — input needed]

## 5. Implementation handoff

- Touch areas: `entity/Arrival.kt`, `dto/ArrivalResponse.kt`,
  `dto/ArrivalMapper.kt`, new `dto/MarkArrivalWaitingRequest.kt`,
  `service/ArrivalService.kt`, `service/impl/ArrivalServiceImpl.kt`,
  `controller/ArrivalController.kt`, and
  `test/.../controller/ArrivalControllerTest.kt`.
- No repository changes, no new packages beyond one new file in the existing
  `dto` package.
- No changes to `application.yml`, Eureka registration, or the stack.
- Cross-repo note: correlation with `expenses-service` is by `Arrival.id`
  value only — no outbound call is added from this repo.
