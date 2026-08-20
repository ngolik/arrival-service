# Implementation Plan: Introduce DTO layer for arrival API responses

## Summary

- **Goal:** `GET /api/arrivals` and `POST /api/arrivals` return dedicated
  response DTOs instead of JPA entities.
- **In scope:** new `dto` package (`ArrivalResponse`, `ItemResponse` + mapper),
  controller-level mapping on both endpoints, tests for the new response shape.
- **Out of scope:** request DTO for `POST` (body stays `Arrival` entity); any
  service/repo/entity signature change.
- **Architecture input:** `docs/architecture/scrum-1-dto-layer-for-arrivals.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/dto/` (new) | Response DTOs + entity→DTO mapper |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Map service output to DTOs before returning |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Assert response body is the DTO shape; add missing `GET` coverage |

## Steps

### Step 1 — Add response DTOs and mapper

- **Outcome:** `com.ngolik.arrival.dto.ArrivalResponse` and
  `com.ngolik.arrival.dto.ItemResponse` exist as plain data classes (no JPA
  annotations), plus a mapper (`Arrival.toResponse()` / `Item.toResponse()`
  extension functions, or an equivalent small mapper object) that converts
  `Arrival`/`Item` → the new DTOs, mirroring all current fields 1:1.
- **Approach:** `ItemResponse(id, name, quantity, unitPrice, totalCost)`;
  `ArrivalResponse(id, arrivalDate, items: List<ItemResponse>)`. No change to
  `entity` or `service` packages.
- **Tests:** none required standalone (covered by Step 2's controller test),
  but keep the mapper a plain function so it's trivially unit-testable if
  needed later.
- **Done when:** the `dto` package compiles and mirrors the entity fields
  evidenced in `Arrival.kt` / `Item.kt`.

### Step 2 — Wire the controller to the DTO boundary

- **Outcome:** `ArrivalController.getAllArrivals()` returns
  `List<ArrivalResponse>`; `createArrival()` returns
  `ResponseEntity<ArrivalResponse>`. Request handling for `POST` is unchanged
  (`@RequestBody arrival: Arrival` stays as-is — no request DTO, per
  non-goals).
- **Approach:** call the Step 1 mapper on the service's return value before
  building the response. `ArrivalService` interface, `ArrivalServiceImpl`,
  repositories, and entities are untouched.
- **Tests:** update `ArrivalControllerTest`:
  - Existing `POST … returns 201` test: assert the response JSON matches the
    `ArrivalResponse` shape (not just status code).
  - Existing `POST … missing field returns 400` test: unchanged behavior,
    keep as regression check.
  - New test: `GET /api/arrivals` returns 200 with a JSON array shaped like
    `ArrivalResponse` (currently no `GET` test exists — add one).
- **Done when:** both endpoints return DTO-shaped JSON; no `Arrival`/`Item`
  entity type appears in any response body.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Jackson serializes the DTO differently than the entity was (e.g. date format) if field types/names drift | Mirror field names/types exactly from `Arrival`/`Item`; assert response JSON shape in tests (Step 2) rather than only status codes |
| Scope creep into a request DTO | Explicitly out of scope per brief and architecture doc — `@RequestBody` stays `Arrival` |

## Open questions

- `[MISSING — input needed]` (carried from architecture doc): is 1:1 field
  mirroring acceptable, or should any field be excluded from the response
  DTO? Proceeding with 1:1 mirroring unless told otherwise.
