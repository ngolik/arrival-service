# Architecture: Mark arrival as sealed (inspection passed)

## 1. Scope and constraints

- **Problem**: `Arrival` has no operational "sealed" state today. An
  identified operator needs to mark an *existing* arrival as sealed
  (inspection passed), optionally with a plain-text note, and see that state
  on the read path. Unlike `waiting-delivery-cost`/`damaged-inbound-writeoff`,
  this story has no `expenses-service` leg — no money amount is involved
  (confirmed, `docs/specifications/inbound-seal-check.md`).
- **Goals**: Add `isSealed: Boolean` + `sealNote: String?` to the `Arrival`
  record; add an endpoint to set them on an existing arrival, gated on an
  identified operator; surface them on `GET /api/arrivals/{id}`. Coexist
  independently with the existing `isWaiting`/`remark` and
  `isDamaged`/`damageRemark` facts.
- **Non-goals**: A "list all sealed deliveries" endpoint. Any money/finance
  data. Persisting the validated operator id on `Arrival` — no AC requires
  retrieving who sealed it (Product decision, this conversation, 2026-09-02).
- **Constraints**: `layout: spring-layers` (controller / entity / repo /
  service + impl / dto), consistent with `waiting-delivery-cost` and
  `damaged-inbound-writeoff`. No version/stack changes (Kotlin 1.8.22, Spring
  Boot 3.1.5, JPA — unchanged). `Arrival.id` stays client-supplied,
  unchanged. Do not reuse `isWaiting`/`remark`/`isDamaged`/`damageRemark` —
  sealed is a separate fact with its own note.
- **New for this repo**: this is `arrival-service`'s first outbound HTTP
  call. The existing `markAsWaiting`/`markAsDamaged` endpoints take no
  operator parameter at all — the identified-operator requirement for those
  two sibling stories is enforced entirely on `expenses-service`'s side (see
  `expenses-service/authclient/client.go`), because that's where the money
  record is created. This story has no money leg, so `arrival-service` itself
  must validate the operator, via a live call to `auth-service`'s existing
  `GET /auth/api/users/{id}` (same endpoint `expenses-service` already
  calls). [confirmed absence of any HTTP client / RestTemplate bean in this
  repo — `build.gradle.kts`, full `src/main` tree]
- **Sources used**: `entity/Arrival.kt`; `controller/ArrivalController.kt`;
  `service/ArrivalService.kt` + `service/impl/ArrivalServiceImpl.kt`;
  `dto/ArrivalResponse.kt` + `dto/ArrivalMapper.kt`;
  `dto/MarkArrivalWaitingRequest.kt` / `MarkArrivalDamagedRequest.kt`; the
  shipped `damaged-inbound-writeoff` precedent (this repo's own
  `docs/architecture/damaged-inbound-writeoff.md`) for local conventions;
  `expenses-service/authclient/client.go` and
  `expenses-service/service/waiting_delivery_cost.go` for the
  validate/reject/upstream-error split this repo is porting (Go → Kotlin,
  same shape).

## 2. Existing shape to respect

- `ArrivalController` (`/api/arrivals`) delegates to `ArrivalService`, maps
  `Arrival` entities to `ArrivalResponse` via `toResponse()`.
- `Arrival` is a JPA `@Entity` implemented as a Kotlin `data class` with a
  client-supplied `@Id val id: Long`. "Update" means "load, produce a new
  instance via `.copy()`, save" — same pattern as `markAsWaiting`/
  `markAsDamaged`.
- No `@ControllerAdvice`/`@ExceptionHandler` exists in this repo. This
  change adds two small, local exception types and catches them directly in
  the new controller method — it does not introduce global exception
  handling infrastructure, to keep the diff scoped to this endpoint.

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `entity/Arrival.kt` | Add `isSealed: Boolean = false` and `sealNote: String? = null` | `@field:Size(max = 500)` on `sealNote`, mirroring `remark`/`damageRemark`; independent field so waiting/damaged/sealed notes never collide |
| `dto/MarkArrivalSealedRequest.kt` (new) | Request body: `operatorId: Long?` with `@field:NotNull`, `note: String? = null` (`@field:Size(max = 500)`) | `operatorId` must be nullable so Bean Validation's `@NotNull` catches a missing field explicitly — a non-nullable `Long` silently defaults to `0` when the JSON key is absent (Jackson-kotlin only throws `MissingKotlinParameterException` for reference types like `arrivalDate: LocalDateTime`, not primitive-backed types like `Long`/`Int`/`Boolean`); this was caught by a failing test during implementation. Unlike `MarkArrivalWaitingRequest`/`MarkArrivalDamagedRequest`, this DTO has one mandatory field — the endpoint's `@RequestBody` is therefore **not** optional |
| `authclient/UserValidator.kt` (new) | `interface UserValidator { fun userExists(operatorId: Long): Boolean }` | Structural seam so `ArrivalServiceImpl` doesn't depend on HTTP machinery directly — mirrors `expenses-service/service.UserValidator` |
| `authclient/HttpUserValidator.kt` (new) | Calls `auth-service`'s `GET /auth/api/users/{id}` via `RestTemplate`; returns `true`/`false` on 200/404, throws `AuthServiceUnavailableException` on network error or unexpected status | Base URL from `auth-service.base-url` property (`AUTH_SERVICE_BASE_URL` env override, default `http://localhost:8081`) — same default `expenses-service`'s `authclient` uses |
| `exception/UnknownOperatorException.kt`, `exception/AuthServiceUnavailableException.kt` (new) | Two small `RuntimeException` subtypes | Mirrors `expenses-service`'s `ValidationError` (→400) / `UpstreamError` (→502) split |
| `config/RestTemplateConfig.kt` (new) | `@Bean fun restTemplate(): RestTemplate = RestTemplate()` | Only new infra bean needed |
| `ArrivalService` (+ impl) | Add `markAsSealed(id: Long, operatorId: Long, note: String?): Arrival?` | Validates operator first (throws on unknown/unreachable), then loads via `findById`, returns `null` if absent (same not-found convention as siblings); on hit, `.copy(isSealed = true, sealNote = note)` + `repository.save()` |
| `ArrivalController` | Add `PUT /{id}/sealed` handler, **required** `@Valid @RequestBody` | 200 + `ArrivalResponse` on hit, 404 on miss, 400 on unknown operator (or a body-validation failure), 502 on auth-service unreachable |
| `dto/ArrivalResponse.kt` + `ArrivalMapper.kt` | Add `isSealed: Boolean`, `sealNote: String?` | Same shape for list/get-by-id/create responses |
| `ArrivalRepository` | No change | `findById`/`save` already available via `JpaRepository` |

- Happy path: controller → service `markAsSealed` → `userValidator.userExists`
  (200 from auth-service) → repository `findById` → `.copy()` → `save()` →
  map to `ArrivalResponse` → 200.
- Unknown operator: `userValidator.userExists` returns `false` → service
  throws `UnknownOperatorException` → controller catches → 400. Arrival is
  never touched.
- Auth-service unreachable: `HttpUserValidator` throws
  `AuthServiceUnavailableException` → controller catches → 502. Arrival is
  never touched.
- Not found: service returns `null` (after operator validation passes) →
  controller returns 404.
- Note too long: `@Valid` on the request DTO rejects before the controller
  method body runs → 400, no upstream call made, no record touched.
- Coexistence: `isSealed`/`sealNote` are separate fields from
  `isWaiting`/`remark`/`isDamaged`/`damageRemark` on the same `.copy()`
  chain — marking one never clobbers another.
- Backward compatibility: `isSealed` defaults `false`, `sealNote` defaults
  `null` — existing create/list/get payloads that omit them are unaffected.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Where does identity validation live | Add a call to `expenses-service` vs. `arrival-service` calling `auth-service` directly | `arrival-service` → `auth-service` directly | No money/`expenses-service` leg exists for this story; routing through `expenses-service` just to borrow its validator would be an artificial cross-repo dependency for a same-repo concern |
| Persist `operatorId` on `Arrival` | Persist for audit vs. validate-only | Validate-only, not persisted | No AC requires retrieving who sealed it (Product decision, this conversation, 2026-09-02); avoids a field with no read consumer |
| Error mapping | Global `@ControllerAdvice` vs. local try/catch in the new handler | Local try/catch | Repo has no existing global exception handling; two exception types used by exactly one endpoint don't justify new shared infrastructure |
| Field reuse | Reuse `isWaiting`/`isDamaged`/`remark`/`damageRemark` vs. new fields | New `isSealed`/`sealNote` | A delivery can be waiting, damaged, and sealed independently with separate notes — same rationale as `damaged-inbound-writeoff`'s field-separation decision |
| Request body optionality | Optional body (like `/waiting`, `/damaged`) vs. required | Required | `operatorId` is mandatory (AC2/AC3); an optional body with a mandatory field inside it would just move the 400 check into the handler instead of Spring's request binding |

## 5. Implementation handoff

- Touch areas: `entity/Arrival.kt`, `dto/ArrivalResponse.kt`,
  `dto/ArrivalMapper.kt`, new `dto/MarkArrivalSealedRequest.kt`, new
  `authclient/UserValidator.kt` + `authclient/HttpUserValidator.kt`, new
  `exception/UnknownOperatorException.kt` +
  `exception/AuthServiceUnavailableException.kt`, new
  `config/RestTemplateConfig.kt`, `service/ArrivalService.kt`,
  `service/impl/ArrivalServiceImpl.kt`, `controller/ArrivalController.kt`,
  `application.yml`, and `test/.../controller/ArrivalControllerTest.kt`.
- No repository changes.
- `isWaiting`/`remark`/`isDamaged`/`damageRemark`/`/waiting`/`/damaged` are
  untouched — this change is strictly additive.
- Cross-repo note: no correlation needed with `expenses-service` for this
  story (no money leg); the only cross-repo dependency is the new runtime
  call to `auth-service`'s already-shipped `GET /auth/api/users/{id}`.
