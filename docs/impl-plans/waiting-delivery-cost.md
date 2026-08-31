# Implementation Plan: Mark arrival as waiting (with optional remark)

## Summary

- **Goal:** Add operator-facing "mark as waiting" support to an existing
  `Arrival`, with an optional ≤500-char remark (hard reject over), and surface
  it on `GET /api/arrivals/{id}`.
- **In scope:** Entity fields, new request DTO, service update method,
  controller endpoint, response DTO fields, tests.
- **Out of scope:** "List all waiting" endpoint, money/amount (owned by
  `expenses-service`), operator identity/auth, any live call to another
  service.
- **Architecture input:** `docs/architecture/waiting-delivery-cost.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/entity/Arrival.kt` | Add `isWaiting: Boolean = false`, `remark: String? = null` (`@field:Size(max = 500)`) |
| `src/main/kotlin/com/ngolik/arrival/dto/MarkArrivalWaitingRequest.kt` (new) | Request body: `remark: String? = null`, validated ≤500 chars |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalResponse.kt` | Add `isWaiting: Boolean`, `remark: String?` |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalMapper.kt` | Map the two new fields through in `toResponse()` |
| `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt` | Add `markAsWaiting(id: Long, remark: String?): Arrival?` |
| `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt` | Implement via `findById` + `.copy()` + `save()` |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Add `PUT /{id}/waiting` handler |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Cover: mark-waiting 200, 404 on missing arrival, 400 on remark > 500 chars, and GET by id surfacing the new fields |

No changes to `repo/ArrivalRepository.kt` (existing `findById`/`save` cover
this) or to `application.yml`.

## Steps

### Step 1 — Entity: add `isWaiting` / `remark` fields

- **Outcome:** `Arrival` carries the two new fields with backward-compatible
  defaults; existing create/list/get payloads that omit them are unaffected.
- **Approach:** In `entity/Arrival.kt`, add:
  ```kotlin
  val isWaiting: Boolean = false,
  @field:Size(max = 500)
  val remark: String? = null
  ```
  (import `jakarta.validation.constraints.Size`). Keep field order additive
  at the end of the primary constructor so existing positional test fixtures
  (`sampleArrival()`, `Item(...)` calls) that use named args keep compiling.
- **Tests:** Compile check; existing `ArrivalControllerTest` cases (list,
  get-by-id, create, 400-on-missing-field) must keep passing unmodified —
  this is the regression signal for backward compatibility (AC 4).
- **Done when:** Project compiles; no existing test needed to change to keep
  passing.

### Step 2 — DTO: `MarkArrivalWaitingRequest`

- **Outcome:** A small, dedicated request body for the new endpoint.
- **Approach:** New file `dto/MarkArrivalWaitingRequest.kt`:
  ```kotlin
  data class MarkArrivalWaitingRequest(
          @field:Size(max = 500)
          val remark: String? = null
  )
  ```
- **Tests:** Covered by controller tests in Step 5.
- **Done when:** Compiles; used by the controller in Step 4.

### Step 3 — Service: add `markAsWaiting`

- **Outcome:** `ArrivalService` exposes a way to mark an existing arrival as
  waiting, returning `null` when the arrival does not exist (mirrors
  `getArrivalById`'s not-found convention — no upsert-on-miss).
- **Approach:** Add `fun markAsWaiting(id: Long, remark: String?): Arrival?`
  to the interface. Implement in `ArrivalServiceImpl`:
  ```kotlin
  override fun markAsWaiting(id: Long, remark: String?): Arrival? =
          arrivalRepository.findById(id).orElse(null)
                  ?.copy(isWaiting = true, remark = remark)
                  ?.let { arrivalRepository.save(it) }
  ```
- **Tests:** Covered indirectly via controller tests (no standalone service
  test layer exists today for the other methods either — stay consistent).
- **Done when:** Interface + impl compile; delegates to the repository with
  no new persistence machinery.

### Step 4 — Controller: `PUT /{id}/waiting`

- **Outcome:** `PUT /api/arrivals/{id}/waiting` returns 200 + `ArrivalResponse`
  (with `isWaiting = true` and the given remark) when the arrival exists, 404
  when it does not, 400 when the remark exceeds 500 chars (no record
  touched).
- **Approach:** Add to `ArrivalController`:
  ```kotlin
  @PutMapping("/{id}/waiting")
  fun markArrivalAsWaiting(
          @PathVariable id: Long,
          @Valid @RequestBody(required = false) request: MarkArrivalWaitingRequest?
  ): ResponseEntity<ArrivalResponse> =
          arrivalService.markAsWaiting(id, (request ?: MarkArrivalWaitingRequest()).remark)
                  ?.let { ResponseEntity.ok(it.toResponse()) }
                  ?: ResponseEntity.notFound().build()
  ```
- **Tests:** Controller tests (Step 5) verify this.
- **Done when:** Endpoint compiles; matches `ArrivalResponse` shape used
  elsewhere.

### Step 5 — DTO/mapper: surface fields on the read path

- **Outcome:** `GET /api/arrivals/{id}` (and list/create responses) include
  `isWaiting` and `remark`.
- **Approach:** Add `isWaiting: Boolean`, `remark: String?` to
  `ArrivalResponse`; map both straight through in `Arrival.toResponse()`.
- **Tests:** Covered by Step 6's GET-by-id assertion.
- **Done when:** Compiles; `toResponse()` includes both fields.

### Step 6 — Tests

- **Outcome:** `ArrivalControllerTest` covers the new endpoint and the
  extended read shape.
- **Approach:** Add, following the existing `MockBean`/`mockMvc` pattern:
  - `PUT /api/arrivals/{id}/waiting` with a remark, service stubbed to return
    the updated arrival → 200, `jsonPath("$.isWaiting").value(true)`,
    `jsonPath("$.remark").value(...)`.
  - `PUT /api/arrivals/{id}/waiting` with no body → 200 (service called with
    `remark = null`).
  - `PUT /api/arrivals/{id}/waiting` for a missing id (service stubbed to
    return `null`) → 404.
  - `PUT /api/arrivals/{id}/waiting` with a remark > 500 chars → 400, service
    method never invoked (verify via `verifyNoInteractions`/`verify(never())`
    or equivalent, if the existing test style supports it; otherwise assert
    status only, consistent with the existing 400 test which doesn't verify
    service interaction either).
  - `GET /api/arrivals/{id}` sample fixture updated/extended to assert
    `isWaiting`/`remark` pass through when set.
- **Done when:** `./gradlew test` (Windows: `gradlew.bat test`) passes,
  including all new cases and the pre-existing ones unchanged.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Adding fields to `Arrival`'s primary constructor could break existing test fixtures that construct it positionally | New fields use named defaults (`isWaiting: Boolean = false`, `remark: String? = null`) placed at the end; existing `sampleArrival()` uses named args already, so it's unaffected |
| `save()` on a full round-trip `.copy()` could clobber `items`/`arrivalDate` if the copy is built incorrectly | `.copy(isWaiting = true, remark = remark)` only overrides the two target fields; Kotlin `.copy()` preserves the rest from the loaded entity |
| No datasource configured in `application.yml` (pre-existing condition, shared with all other endpoints) | Out of scope here; `@SpringBootTest` already passes today under the same setup per prior features |
| Validation bypass via `POST /api/arrivals` with a long inline `remark` | Entity-level `@field:Size(max = 500)` on `Arrival.remark` closes this, applied in Step 1 |

## Open questions

- Whether `isWaiting`/`remark` should be directly settable via
  `POST /api/arrivals` at create time (beyond just not breaking when omitted)
  is not specified. [MISSING — input needed] — not blocking this change since
  the fields' defaults keep create backward-compatible either way.
