# Implementation Plan: Mark arrival as damaged (with optional remark)

## Summary

- **Goal:** Add operator-facing "mark as damaged" support to an existing
  `Arrival`, with an optional ≤500-char remark (hard reject over), and
  surface it on `GET /api/arrivals/{id}`, coexisting independently with the
  existing waiting fact.
- **In scope:** Entity fields, new request DTO, service update method,
  controller endpoint, response DTO fields, tests.
- **Out of scope:** "List all damaged" endpoint, write-off money amount
  (owned by `expenses-service`), operator identity/auth, any live call to
  another service, any change to `isWaiting`/`remark`/the `/waiting`
  endpoint.
- **Architecture input:** `docs/architecture/damaged-inbound-writeoff.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/entity/Arrival.kt` | Add `isDamaged: Boolean = false`, `damageRemark: String? = null` (`@field:Size(max = 500)`) |
| `src/main/kotlin/com/ngolik/arrival/dto/MarkArrivalDamagedRequest.kt` (new) | Request body: `remark: String? = null`, validated ≤500 chars |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalResponse.kt` | Add `isDamaged: Boolean`, `damageRemark: String?` |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalMapper.kt` | Map the two new fields through in `toResponse()` |
| `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt` | Add `markAsDamaged(id: Long, remark: String?): Arrival?` |
| `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt` | Implement via `findById` + `.copy()` + `save()` |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Add `PUT /{id}/damaged` handler |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Cover: mark-damaged 200 (with/without remark), 404 on missing arrival, 400 on remark > 500 chars, GET by id surfacing the new fields, and waiting+damaged coexisting with independent remarks |

No changes to `repo/ArrivalRepository.kt` (existing `findById`/`save` cover
this) or to `application.yml`.

## Steps

### Step 1 — Entity: add `isDamaged` / `damageRemark` fields

- **Outcome:** `Arrival` carries the two new fields with backward-compatible
  defaults; existing create/list/get payloads that omit them are unaffected;
  `isWaiting`/`remark` are untouched.
- **Approach:** In `entity/Arrival.kt`, add after the existing `remark`
  field:
  ```kotlin
  val isDamaged: Boolean = false,
  @field:Size(max = 500)
  val damageRemark: String? = null
  ```
  Keep field order additive at the end of the primary constructor so
  existing positional/named test fixtures (`sampleArrival()`) keep
  compiling.
- **Tests:** Compile check; existing `ArrivalControllerTest` cases (list,
  get-by-id, create, 400-on-missing-field, and all waiting-flow cases) must
  keep passing unmodified — this is the regression signal for backward
  compatibility.
- **Done when:** Project compiles; no existing test needed to change to keep
  passing.

### Step 2 — DTO: `MarkArrivalDamagedRequest`

- **Outcome:** A small, dedicated request body for the new endpoint,
  mirroring `MarkArrivalWaitingRequest`.
- **Approach:** New file `dto/MarkArrivalDamagedRequest.kt`:
  ```kotlin
  data class MarkArrivalDamagedRequest(
          @field:Size(max = 500)
          val remark: String? = null
  )
  ```
- **Tests:** Covered by controller tests in Step 5.
- **Done when:** Compiles; used by the controller in Step 4.

### Step 3 — Service: add `markAsDamaged`

- **Outcome:** `ArrivalService` exposes a way to mark an existing arrival as
  damaged, returning `null` when the arrival does not exist (mirrors
  `markAsWaiting`'s not-found convention — no upsert-on-miss).
- **Approach:** Add `fun markAsDamaged(id: Long, remark: String?): Arrival?`
  to the interface. Implement in `ArrivalServiceImpl`:
  ```kotlin
  override fun markAsDamaged(id: Long, remark: String?): Arrival? =
          arrivalRepository.findById(id).orElse(null)
                  ?.copy(isDamaged = true, damageRemark = remark)
                  ?.let { arrivalRepository.save(it) }
  ```
- **Tests:** Covered indirectly via controller tests (no standalone service
  test layer exists today, same as `markAsWaiting`).
- **Done when:** Interface + impl compile; delegates to the repository with
  no new persistence machinery.

### Step 4 — Controller: `PUT /{id}/damaged`

- **Outcome:** `PUT /api/arrivals/{id}/damaged` returns 200 + `ArrivalResponse`
  (with `isDamaged = true` and the given remark) when the arrival exists, 404
  when it does not, 400 when the remark exceeds 500 chars (no record
  touched).
- **Approach:** Add to `ArrivalController`:
  ```kotlin
  @PutMapping("/{id}/damaged")
  fun markArrivalAsDamaged(
          @PathVariable id: Long,
          @Valid @RequestBody(required = false) request: MarkArrivalDamagedRequest?
  ): ResponseEntity<ArrivalResponse> =
          arrivalService.markAsDamaged(id, (request ?: MarkArrivalDamagedRequest()).remark)
                  ?.let { ResponseEntity.ok(it.toResponse()) }
                  ?: ResponseEntity.notFound().build()
  ```
- **Tests:** Controller tests (Step 5) verify this.
- **Done when:** Endpoint compiles; matches `ArrivalResponse` shape used
  elsewhere; `/waiting` endpoint unchanged.

### Step 5 — DTO/mapper: surface fields on the read path

- **Outcome:** `GET /api/arrivals/{id}` (and list/create responses) include
  `isDamaged` and `damageRemark`, alongside the existing `isWaiting`/`remark`.
- **Approach:** Add `isDamaged: Boolean`, `damageRemark: String?` to
  `ArrivalResponse`; map both straight through in `Arrival.toResponse()`.
- **Tests:** Covered by Step 6's GET-by-id assertions.
- **Done when:** Compiles; `toResponse()` includes both new fields plus the
  unchanged existing ones.

### Step 6 — Tests

- **Outcome:** `ArrivalControllerTest` covers the new endpoint, the extended
  read shape, and waiting/damaged coexistence.
- **Approach:** Add, following the existing `MockBean`/`mockMvc` pattern:
  - `PUT /api/arrivals/{id}/damaged` with a remark → 200,
    `jsonPath("$.isDamaged").value(true)`,
    `jsonPath("$.damageRemark").value(...)`.
  - `PUT /api/arrivals/{id}/damaged` with no body → 200 (service called with
    `remark = null`).
  - `PUT /api/arrivals/{id}/damaged` for a missing id → 404.
  - `PUT /api/arrivals/{id}/damaged` with a remark > 500 chars → 400, service
    method never invoked (`verify(arrivalService, never()).markAsDamaged(...)`,
    same pattern as the waiting 400 test).
  - `GET /api/arrivals/{id}` fixture extended to assert `isDamaged`/
    `damageRemark` pass through when set.
  - `GET /api/arrivals/{id}` with both `isWaiting`/`remark` and
    `isDamaged`/`damageRemark` set on the same arrival → both facts surface
    independently with their own remarks (coexistence check).
- **Done when:** `./gradlew test` (Windows: `gradlew.bat test`) passes,
  including all new cases and the pre-existing ones unchanged.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Adding fields to `Arrival`'s primary constructor could break existing test fixtures that construct it positionally | New fields use named defaults placed at the end; existing `sampleArrival()` uses named args already, so it's unaffected |
| Reusing `isWaiting`/`remark` for the damaged fact by mistake | Explicitly separate fields (`isDamaged`/`damageRemark`) per the brief; coexistence test in Step 6 guards against regressions |
| `save()` on a full round-trip `.copy()` could clobber other fields if the copy is built incorrectly | `.copy(isDamaged = true, damageRemark = remark)` only overrides the two target fields; Kotlin `.copy()` preserves the rest, including `isWaiting`/`remark` |
| No datasource configured in `application.yml` (pre-existing condition, shared with all other endpoints) | Out of scope here; `@SpringBootTest` already passes today under the same setup per prior features |

## Open questions

None specific to this slice — mirrors the resolved shape of
`waiting-delivery-cost`.
