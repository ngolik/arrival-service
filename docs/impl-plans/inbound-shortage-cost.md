# Implementation Plan: Mark arrival as short (with optional remark)

## Summary

- **Goal:** Add operator-facing "mark as short" support to an existing
  `Arrival`, with an optional ≤500-char remark (hard reject over), and
  surface it on `GET /api/arrivals/{id}`, coexisting independently with the
  existing waiting and damaged facts.
- **In scope:** Entity fields, new request DTO, service update method,
  controller endpoint, response DTO fields, tests.
- **Out of scope:** "List all short" endpoint, mandatory shortage money
  amount (owned by `expenses-service`), identified-operator check (owned by
  `expenses-service`), any live call to another service, any change to
  `isWaiting`/`remark`/`isDamaged`/`damageRemark`/the `/waiting`/`/damaged`
  endpoints.
- **Architecture input:** `docs/architecture/inbound-shortage-cost.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/entity/Arrival.kt` | Add `isShort: Boolean = false`, `shortRemark: String? = null` (`@field:Size(max = 500)`) |
| `src/main/kotlin/com/ngolik/arrival/dto/MarkArrivalShortRequest.kt` (new) | Request body: `remark: String? = null`, validated ≤500 chars |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalResponse.kt` | Add `isShort: Boolean`, `shortRemark: String?` |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalMapper.kt` | Map the two new fields through in `toResponse()` |
| `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt` | Add `markAsShort(id: Long, remark: String?): Arrival?` |
| `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt` | Implement via `findById` + `.copy()` + `save()` |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Add `PUT /{id}/shortage` handler |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Cover: mark-short 200 (with/without remark), 404 on missing arrival, 400 on remark > 500 chars, GET by id surfacing the new fields, and waiting+damaged+short coexisting with independent remarks |

No changes to `repo/ArrivalRepository.kt` (existing `findById`/`save` cover
this) or to `application.yml`. No standalone service-level test file exists
in this repo today (service behavior for `markAsWaiting`/`markAsDamaged` is
covered only via `ArrivalControllerTest`) — `markAsShort` follows the same
convention, no new test layer introduced.

## Steps

### Step 1 — Entity: add `isShort` / `shortRemark` fields

- **Outcome:** `Arrival` carries the two new fields with backward-compatible
  defaults; existing create/list/get payloads that omit them are unaffected;
  `isWaiting`/`remark`/`isDamaged`/`damageRemark` are untouched.
- **Approach:** In `entity/Arrival.kt`, add after the existing `damageRemark`
  field:
  ```kotlin
  val isShort: Boolean = false,
  @field:Size(max = 500)
  val shortRemark: String? = null
  ```
  Keep field order additive at the end of the primary constructor so
  existing positional/named test fixtures (`sampleArrival()`) keep
  compiling.
- **Tests:** Compile check; existing `ArrivalControllerTest` cases (list,
  get-by-id, create, 400-on-missing-field, and all waiting/damaged-flow
  cases) must keep passing unmodified — this is the regression signal for
  backward compatibility.
- **Done when:** Project compiles; no existing test needed to change to keep
  passing.

### Step 2 — DTO: `MarkArrivalShortRequest`

- **Outcome:** A small, dedicated request body for the new endpoint,
  mirroring `MarkArrivalWaitingRequest`/`MarkArrivalDamagedRequest`.
- **Approach:** New file `dto/MarkArrivalShortRequest.kt`:
  ```kotlin
  data class MarkArrivalShortRequest(
          @field:Size(max = 500)
          val remark: String? = null
  )
  ```
- **Tests:** Covered by controller tests in Step 5.
- **Done when:** Compiles; used by the controller in Step 4.

### Step 3 — Service: add `markAsShort`

- **Outcome:** `ArrivalService` exposes a way to mark an existing arrival as
  short, returning `null` when the arrival does not exist (mirrors
  `markAsWaiting`/`markAsDamaged`'s not-found convention — no upsert-on-miss).
- **Approach:** Add `fun markAsShort(id: Long, remark: String?): Arrival?`
  to the interface. Implement in `ArrivalServiceImpl`:
  ```kotlin
  override fun markAsShort(id: Long, remark: String?): Arrival? =
          arrivalRepository.findById(id).orElse(null)
                  ?.copy(isShort = true, shortRemark = remark)
                  ?.let { arrivalRepository.save(it) }
  ```
- **Tests:** Covered indirectly via controller tests (no standalone service
  test layer exists today, same as `markAsWaiting`/`markAsDamaged`).
- **Done when:** Interface + impl compile; delegates to the repository with
  no new persistence machinery.

### Step 4 — Controller: `PUT /{id}/shortage`

- **Outcome:** `PUT /api/arrivals/{id}/shortage` returns 200 + `ArrivalResponse`
  (with `isShort = true` and the given remark) when the arrival exists, 404
  when it does not, 400 when the remark exceeds 500 chars (no record
  touched). No operator/amount parameter accepted (both are
  `expenses-service`'s concern for this story).
- **Approach:** Add to `ArrivalController`:
  ```kotlin
  @PutMapping("/{id}/shortage")
  fun markArrivalAsShort(
          @PathVariable id: Long,
          @Valid @RequestBody(required = false) request: MarkArrivalShortRequest?
  ): ResponseEntity<ArrivalResponse> =
          arrivalService.markAsShort(id, (request ?: MarkArrivalShortRequest()).remark)
                  ?.let { ResponseEntity.ok(it.toResponse()) }
                  ?: ResponseEntity.notFound().build()
  ```
- **Tests:** Controller tests (Step 5) verify this.
- **Done when:** Endpoint compiles; matches `ArrivalResponse` shape used
  elsewhere; `/waiting` and `/damaged` endpoints unchanged.

### Step 5 — DTO/mapper: surface fields on the read path

- **Outcome:** `GET /api/arrivals/{id}` (and list/create responses) include
  `isShort` and `shortRemark`, alongside the existing `isWaiting`/`remark`
  and `isDamaged`/`damageRemark`.
- **Approach:** Add `isShort: Boolean`, `shortRemark: String?` to
  `ArrivalResponse`; map both straight through in `Arrival.toResponse()`.
- **Tests:** Covered by Step 6's GET-by-id assertions.
- **Done when:** Compiles; `toResponse()` includes both new fields plus the
  unchanged existing ones.

### Step 6 — Tests

- **Outcome:** `ArrivalControllerTest` covers the new endpoint, the extended
  read shape, and waiting/damaged/short coexistence.
- **Approach:** Add, following the existing `MockBean`/`mockMvc` pattern:
  - `PUT /api/arrivals/{id}/shortage` with a remark → 200,
    `jsonPath("$.isShort").value(true)`,
    `jsonPath("$.shortRemark").value(...)`.
  - `PUT /api/arrivals/{id}/shortage` with no body → 200 (service called
    with `remark = null`).
  - `PUT /api/arrivals/{id}/shortage` for a missing id → 404.
  - `PUT /api/arrivals/{id}/shortage` with a remark > 500 chars → 400,
    service method never invoked (`verify(arrivalService,
    never()).markAsShort(...)`, same pattern as the waiting/damaged 400
    tests).
  - `GET /api/arrivals/{id}` fixture extended to assert `isShort`/
    `shortRemark` pass through when set.
  - Extend (or add a variant of) the existing "surfaces waiting and damaged
    as independent facts" GET test to also set `isShort`/`shortRemark`,
    asserting all three facts (waiting, damaged, short) surface
    independently with their own remarks on the same arrival. (`isSealed`
    is not present on this branch — see architecture doc note — so this is
    a three-fact coexistence check, not four.)
- **Done when:** `./gradlew test` (Windows: `gradlew.bat test`) passes,
  including all new cases and the pre-existing ones unchanged.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Adding fields to `Arrival`'s primary constructor could break existing test fixtures that construct it positionally | New fields use named defaults placed at the end; existing `sampleArrival()` uses named args already, so it's unaffected |
| Reusing `isWaiting`/`remark` or `isDamaged`/`damageRemark` for the short fact by mistake | Explicitly separate fields (`isShort`/`shortRemark`) per the brief; coexistence test in Step 6 guards against regressions |
| `save()` on a full round-trip `.copy()` could clobber other fields if the copy is built incorrectly | `.copy(isShort = true, shortRemark = remark)` only overrides the two target fields; Kotlin `.copy()` preserves the rest, including `isWaiting`/`remark`/`isDamaged`/`damageRemark` |
| Accidentally adding an operator/amount parameter to this endpoint, duplicating `expenses-service`'s AC2/AC3 checks | Contract memo (spec `## Repos`, D6) is explicit: no `operatorId`, no amount here; `MarkArrivalShortRequest` has only `remark` |
| No datasource configured in `application.yml` (pre-existing condition, shared with all other endpoints) | Out of scope here; `@SpringBootTest` already passes today under the same setup per prior features |

## Open questions

None specific to this slice — mirrors the resolved shape of
`waiting-delivery-cost`/`damaged-inbound-writeoff`. `/shortage` path-segment
naming was confirmed at G1 (architecture doc, `## 4`).
