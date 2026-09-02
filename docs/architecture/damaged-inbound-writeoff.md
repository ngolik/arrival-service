# Architecture: Mark arrival as damaged (with optional remark)

## 1. Scope and constraints

- **Problem**: `Arrival` has no operational "damaged" state today. An
  operator needs to mark an *existing* arrival as damaged, optionally with a
  plain-text remark, and see that state on the read path.
- **Goals**: Add `isDamaged: Boolean` + `damageRemark: String?` to the
  `Arrival` record; add an endpoint to set them on an existing arrival;
  surface them on `GET /api/arrivals/{id}` (and the list/create response
  shape, unchanged for callers that don't set them). Coexist independently
  with the existing `isWaiting`/`remark` fact — a delivery can be both
  waiting and damaged with different remarks.
- **Non-goals**: A "list all damaged deliveries" endpoint. Write-off money
  amount handling — owned by `expenses-service` in the same cross-repo story,
  correlated only by `Arrival.id`, no live call between the two services.
  Operator identity/auth — not required by this repo's slice (identity is
  `expenses-service`'s concern for its own record).
- **Constraints**: `layout: spring-layers` (controller / entity / repo /
  service + impl / dto). No version/stack changes (Kotlin 1.8.22, Spring Boot
  3.1.5, JPA — unchanged). `Arrival.id` stays client-supplied, unchanged — it
  is the shared correlation key with `expenses-service`. Do not reuse
  `isWaiting`/`remark` — damaged is a separate fact with its own remark.
- **Sources used**: `entity/Arrival.kt`; `controller/ArrivalController.kt`;
  `service/ArrivalService.kt` + `service/impl/ArrivalServiceImpl.kt`;
  `dto/ArrivalResponse.kt` + `dto/ArrivalMapper.kt`;
  `dto/MarkArrivalWaitingRequest.kt`; the shipped `waiting-delivery-cost`
  precedent (`docs/architecture/waiting-delivery-cost.md`,
  `docs/impl-plans/waiting-delivery-cost.md`) for this repo's conventions.

## 2. Existing shape to respect

- `ArrivalController` (`/api/arrivals`) delegates to `ArrivalService`, maps
  `Arrival` entities to `ArrivalResponse` via `toResponse()`.
- `Arrival` is a JPA `@Entity` implemented as a Kotlin `data class` with a
  client-supplied `@Id val id: Long` (no `@GeneratedValue`). "Update" means
  "load, produce a new instance via `.copy()`, save" — same pattern already
  used by `markAsWaiting`.
- `ArrivalService` / `ArrivalServiceImpl` already expose `getAllArrivals()`,
  `createArrival()`, `getArrivalById()`, `markAsWaiting()`. This change adds
  a sibling method, not a modification to any of those.
- Validation today is `@Valid` on entity/DTO fields + Spring Boot's default
  `MethodArgumentNotValidException` → 400 handling. No
  `@ControllerAdvice`/`@ExceptionHandler` exists in this repo — unchanged.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `entity/Arrival.kt` | Add `isDamaged: Boolean = false` and `damageRemark: String? = null`, both with sane defaults so existing JSON payloads (create) keep working unchanged | `@field:Size(max = 500)` on `damageRemark`, mirroring `remark`; separate field from `remark` so waiting and damaged remarks don't collide |
| `dto/MarkArrivalDamagedRequest.kt` (new) | Request body for the new endpoint: `remark: String? = null`, `@field:Size(max = 500)` | Mirrors `MarkArrivalWaitingRequest` exactly, dedicated to this action |
| `ArrivalService` (+ impl) | Add `markAsDamaged(id: Long, remark: String?): Arrival?` | Loads via `findById`, returns `null` if absent (same not-found convention as `markAsWaiting`/`getArrivalById`); on hit, `.copy(isDamaged = true, damageRemark = remark)` + `repository.save()` — does not touch `isWaiting`/`remark` |
| `ArrivalController` | Add `PUT /{id}/damaged` handler, `@Valid @RequestBody(required = false)` request | 200 + `ArrivalResponse` body on hit, 404 on miss (same pattern as `/waiting`); a body-validation failure (remark > 500 chars) short-circuits to Spring's default 400 before the handler runs — no record is created/updated |
| `dto/ArrivalResponse.kt` + `ArrivalMapper.kt` | Add `isDamaged: Boolean`, `damageRemark: String?` to the response, mapped straight through | Same shape for list/get-by-id/create responses — no second response DTO; existing `isWaiting`/`remark` fields unchanged |
| `ArrivalRepository` | No change | `findById`/`save` already available via `JpaRepository` |

- Happy path: controller → service `markAsDamaged` → repository `findById`
  → `.copy()` → `save()` → map to `ArrivalResponse` → 200.
- Not found: service returns `null` → controller returns 404, no
  create-on-miss (this is a mutation on an *existing* arrival).
- Remark too long: `@Valid` on the request DTO rejects before the controller
  method body runs → 400, no record touched (hard reject, not truncated).
- Coexistence: `isDamaged`/`damageRemark` are separate fields from
  `isWaiting`/`remark` on the same `Arrival.copy()` chain — marking one
  never clobbers the other.
- Backward compatibility: `isDamaged` defaults `false`, `damageRemark`
  defaults `null` on the entity — `POST /api/arrivals` payloads that omit
  both fields deserialize unchanged.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Field reuse | Reuse `isWaiting`/`remark` vs new separate fields | New `isDamaged`/`damageRemark` | A delivery can be both waiting and damaged with independent remarks; reusing the waiting fields would make the two facts mutually exclusive, which is wrong |
| Endpoint shape | `PATCH /{id}` vs `PUT /{id}/damaged` | `PUT /{id}/damaged` | Same rationale as `/waiting`: idempotent, scoped to one concern, sub-resource path mirrors the existing convention |
| Request body | Reuse `Arrival` entity vs new small DTO | New `MarkArrivalDamagedRequest(remark: String?)` | Mirrors `MarkArrivalWaitingRequest`; avoids letting callers smuggle in unrelated field changes through this endpoint |
| Mutation mechanism | Make `Arrival` mutable (`var`) vs `.copy()` + `save()` | `.copy()` + `save()` | Consistent with `markAsWaiting`; no new persistence pattern |
| `damageRemark` validation scope | Validate only on the damaged endpoint vs also on the entity | Also on the entity (`@field:Size(max = 500)`) | One rule enforced everywhere the field can be set, same as `remark` |
| Not-found handling | Throw + handler vs inline `ResponseEntity` branch | Inline branch (matches `markAsWaiting`/`getArrivalById`) | Consistent with existing convention |

## 5. Implementation handoff

- Touch areas: `entity/Arrival.kt`, `dto/ArrivalResponse.kt`,
  `dto/ArrivalMapper.kt`, new `dto/MarkArrivalDamagedRequest.kt`,
  `service/ArrivalService.kt`, `service/impl/ArrivalServiceImpl.kt`,
  `controller/ArrivalController.kt`, and
  `test/.../controller/ArrivalControllerTest.kt`.
- No repository changes, no new packages beyond one new file in the existing
  `dto` package.
- No changes to `application.yml`, Eureka registration, or the stack.
- `isWaiting`/`remark`/the `/waiting` endpoint are untouched — this change is
  strictly additive.
- Cross-repo note: correlation with `expenses-service` (write-off amount) is
  by `Arrival.id` value only — no outbound call is added from this repo.
