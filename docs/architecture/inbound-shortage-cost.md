# Architecture: Mark arrival as short (with optional remark)

## 1. Scope and constraints

- **Problem**: `Arrival` has no operational "short" state today. An operator
  needs to mark an *existing* arrival as short, optionally with a plain-text
  remark, and see that state on the read path. This repo's slice does **not**
  carry the mandatory shortage amount or the identified-operator check — both
  live entirely on `expenses-service`'s own creation path for this story.
- **Goals**: Add `isShort: Boolean` + `shortRemark: String?` to the `Arrival`
  record; add an endpoint to set them on an existing arrival; surface them on
  `GET /api/arrivals/{id}` (and the list/create response shape, unchanged for
  callers that don't set them). Coexist independently with the existing
  `isWaiting`/`remark` and `isDamaged`/`damageRemark` facts — a delivery can
  be short and waiting and/or damaged at once, each with its own remark.
- **Non-goals**: A "list all short deliveries" endpoint (explicitly out of
  scope per spec — AC8). The mandatory shortage money amount and the
  identified-operator check — both owned by `expenses-service` in the same
  cross-repo story (spec AC2/AC3/AC4), correlated only by `Arrival.id`, no
  live call between the two services. This repo's endpoint takes no operator
  parameter and no amount — optional body, same as `/waiting`/`/damaged`.
- **Constraints**: `layout: spring-layers` (controller / entity / repo /
  service + impl / dto). No version/stack changes (Kotlin 1.8.22, Spring Boot
  3.1.5, JPA — unchanged). `Arrival.id` stays client-supplied, unchanged — it
  is the shared correlation key with `expenses-service`. Do not reuse
  `isWaiting`/`remark` or `isDamaged`/`damageRemark` — short is a separate
  fact with its own remark (spec: "the ticket explicitly frames shortage as a
  separate fact").
- **Note on repo state**: `isSealed`/`sealNote` (from the sibling
  `inbound-seal-check` story) lives on branch `feature/inbound-seal-check`
  only and is **not** present on `main` or this branch as of this change —
  this slice does not depend on it and adds no coupling to it.
- **Sources used**: `docs/specifications/inbound-shortage-cost.md` (external,
  read-only reference, `arrival-service` paragraph); `entity/Arrival.kt`;
  `controller/ArrivalController.kt`; `service/ArrivalService.kt` +
  `service/impl/ArrivalServiceImpl.kt`; `dto/ArrivalResponse.kt` +
  `dto/ArrivalMapper.kt`; `dto/MarkArrivalDamagedRequest.kt`; the shipped
  `waiting-delivery-cost`/`damaged-inbound-writeoff` precedents
  (`docs/architecture/waiting-delivery-cost.md`,
  `docs/architecture/damaged-inbound-writeoff.md`) for this repo's
  conventions.

## 2. Existing shape to respect

- `ArrivalController` (`/api/arrivals`) delegates to `ArrivalService`, maps
  `Arrival` entities to `ArrivalResponse` via `toResponse()`.
- `Arrival` is a JPA `@Entity` implemented as a Kotlin `data class` with a
  client-supplied `@Id val id: Long` (no `@GeneratedValue`). "Update" means
  "load, produce a new instance via `.copy()`, save" — same pattern already
  used by `markAsWaiting`/`markAsDamaged`.
- `ArrivalService` / `ArrivalServiceImpl` already expose `getAllArrivals()`,
  `createArrival()`, `getArrivalById()`, `markAsWaiting()`, `markAsDamaged()`.
  This change adds a sibling method, not a modification to any of those.
- Validation today is `@Valid` on entity/DTO fields + Spring Boot's default
  `MethodArgumentNotValidException` → 400 handling. No
  `@ControllerAdvice`/`@ExceptionHandler` exists in this repo — unchanged.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `entity/Arrival.kt` | Add `isShort: Boolean = false` and `shortRemark: String? = null`, both with sane defaults so existing JSON payloads (create) keep working unchanged | `@field:Size(max = 500)` on `shortRemark`, mirroring `remark`/`damageRemark`; separate field so waiting/damaged/short remarks don't collide |
| `dto/MarkArrivalShortRequest.kt` (new) | Request body for the new endpoint: `remark: String? = null`, `@field:Size(max = 500)` | Mirrors `MarkArrivalWaitingRequest`/`MarkArrivalDamagedRequest` exactly, dedicated to this action; no `operatorId` field — identity is `expenses-service`'s concern for this story |
| `ArrivalService` (+ impl) | Add `markAsShort(id: Long, remark: String?): Arrival?` | Loads via `findById`, returns `null` if absent (same not-found convention as `markAsWaiting`/`markAsDamaged`); on hit, `.copy(isShort = true, shortRemark = remark)` + `repository.save()` — does not touch `isWaiting`/`isDamaged` or their remarks |
| `ArrivalController` | Add `PUT /{id}/shortage` handler, `@Valid @RequestBody(required = false)` request | 200 + `ArrivalResponse` body on hit, 404 on miss (same pattern as `/waiting`/`/damaged`); a body-validation failure (remark > 500 chars) short-circuits to Spring's default 400 before the handler runs — no record is created/updated |
| `dto/ArrivalResponse.kt` + `ArrivalMapper.kt` | Add `isShort: Boolean`, `shortRemark: String?` to the response, mapped straight through | Same shape for list/get-by-id/create responses — no second response DTO; existing fields unchanged |
| `ArrivalRepository` | No change | `findById`/`save` already available via `JpaRepository` |

- Happy path: controller → service `markAsShort` → repository `findById` →
  `.copy()` → `save()` → map to `ArrivalResponse` → 200.
- Not found: service returns `null` → controller returns 404, no
  create-on-miss (this is a mutation on an *existing* arrival).
- Remark too long: `@Valid` on the request DTO rejects before the controller
  method body runs → 400, no record touched (hard reject, not truncated —
  spec AC6).
- Coexistence: `isShort`/`shortRemark` are separate fields from
  `isWaiting`/`remark` and `isDamaged`/`damageRemark` on the same
  `Arrival.copy()` chain — marking one never clobbers the others (spec AC1).
- Backward compatibility: `isShort` defaults `false`, `shortRemark` defaults
  `null` on the entity — `POST /api/arrivals` payloads that omit both fields
  deserialize unchanged.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Field reuse | Reuse `isWaiting`/`remark` or `isDamaged`/`damageRemark` vs new separate fields | New `isShort`/`shortRemark` | Spec frames shortage as a separate fact from waiting/sealed/damaged; reusing existing fields would make facts mutually exclusive, which is wrong |
| Endpoint shape | `PATCH /{id}` vs `PUT /{id}/shortage` | `PUT /{id}/shortage` | Same rationale as `/waiting`/`/damaged`: idempotent, scoped to one concern, sub-resource path mirrors the existing convention. Path segment is `shortage` (noun, matches spec title "inbound-shortage-cost" and the `expenses-service` `/shortage` routes) rather than `short`, for cross-repo naming consistency. [INFERRED — please validate: spec does not mandate this repo's path segment] |
| Amount / operator identity | Carry a shortage amount and/or `operatorId` on this endpoint vs leave both to `expenses-service` | Leave both to `expenses-service` | Contract memo (spec `## Repos`, `arrival-service` paragraph, D6): identity and amount are `expenses-service`'s concern for this story, same split `waiting-delivery-cost` already uses; this repo's fact-only endpoint takes no operator/amount parameter |
| Request body | Reuse `Arrival` entity vs new small DTO | New `MarkArrivalShortRequest(remark: String?)` | Mirrors `MarkArrivalWaitingRequest`/`MarkArrivalDamagedRequest`; avoids letting callers smuggle in unrelated field changes through this endpoint |
| Mutation mechanism | Make `Arrival` mutable (`var`) vs `.copy()` + `save()` | `.copy()` + `save()` | Consistent with `markAsWaiting`/`markAsDamaged`; no new persistence pattern |
| `shortRemark` validation scope | Validate only on the shortage endpoint vs also on the entity | Also on the entity (`@field:Size(max = 500)`) | One rule enforced everywhere the field can be set, same as `remark`/`damageRemark` |
| Not-found handling | Throw + handler vs inline `ResponseEntity` branch | Inline branch (matches `markAsWaiting`/`markAsDamaged`/`getArrivalById`) | Consistent with existing convention |

## 5. Implementation handoff

- Touch areas: `entity/Arrival.kt`, `dto/ArrivalResponse.kt`,
  `dto/ArrivalMapper.kt`, new `dto/MarkArrivalShortRequest.kt`,
  `service/ArrivalService.kt`, `service/impl/ArrivalServiceImpl.kt`,
  `controller/ArrivalController.kt`, and
  `test/.../controller/ArrivalControllerTest.kt`.
- No repository changes, no new packages beyond one new file in the existing
  `dto` package.
- No changes to `application.yml`, Eureka registration, or the stack.
- `isWaiting`/`remark`/`isDamaged`/`damageRemark`/the `/waiting`/`/damaged`
  endpoints are untouched — this change is strictly additive.
- Cross-repo note: correlation with `expenses-service` (mandatory shortage
  amount, identified-operator check) is by `Arrival.id` value only — no
  outbound call is added from this repo. This repo's endpoint enforces
  neither the amount (spec AC2) nor the identity check (spec AC3) — both are
  `expenses-service`'s mandatory-field checks on its own creation path.
