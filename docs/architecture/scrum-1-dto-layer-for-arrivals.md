# Architecture: Introduce DTO layer for arrival API responses

## 1. Scope and constraints

- **Problem**: `ArrivalController` returns JPA entities (`Arrival`, `Item`)
  directly in HTTP responses, coupling the public API to the persistence
  model.
- **Goals**: `GET /api/arrivals` and `POST /api/arrivals` return dedicated
  response types instead of entities; no JPA type appears in a response body.
- **Non-goals**: request DTO for `POST /api/arrivals` (request body stays the
  `Arrival` entity); auth; persistence/datasource changes.
- **Constraints**: stay on Kotlin/Spring Boot/Gradle; `layout: spring-layers`;
  thin arch (`complexity` omitted); `ArrivalService` interface signature,
  repository, and entity classes must not change (Unchanged contracts).
- **Sources used**: `docs/briefs/scrum-1-dto-layer-for-arrivals.md`,
  `docs/ai-context/system-overview.md`, `ArrivalController.kt`, `Arrival.kt`,
  `Item.kt`.

## 2. Existing shape to respect

- Layering today: `controller` → `service` (`ArrivalService` /
  `ArrivalServiceImpl`) → `repo` → `entity`. No `dto` package exists yet.
- `ArrivalController` (`src/main/kotlin/com/ngolik/arrival/controller/`) has
  two endpoints: `GET` (list) and `POST` (create), both currently
  entity-in/entity-out.
- `Arrival` entity: `id`, `arrivalDate`, `items: List<Item>` (`@OneToMany`).
- `Item` entity: `id`, `name`, `quantity`, `unitPrice`, `totalCost`. No
  `ItemCategory`/`Supplier` fields evidenced on `Item` in code today, despite
  `system-overview.md` describing that relationship at the domain level —
  treated as out of scope here since it isn't in the current entity shape.
  [INFERRED — please validate]
- No field on `Arrival`/`Item` is evidenced as sensitive or internal-only
  today; the coupling problem is structural (JPA annotations, entity
  identity/lazy semantics leaking into the API contract), not a field-leak
  problem. [INFERRED — please validate against the ticket's actual intent]

## 3. Recommended change

| Component | Responsibility | Notes |
| --- | --- | --- |
| `dto` package (new) | Response-only DTOs (`ArrivalResponse`, `ItemResponse`) — plain data classes, no JPA annotations | New area under `com.ngolik.arrival.dto` |
| `ArrivalController` | Maps service output (entities) to response DTOs before returning | Only the boundary changes; request handling for `POST` is unchanged (still accepts `Arrival`) |
| `ArrivalService` / repo / entities | Unchanged | Mapping happens strictly at the controller boundary, not inside the service |

- **Happy path**: `GET` → service returns `List<Arrival>` → controller maps to
  `List<ArrivalResponse>` → 200. `POST` → service returns `Arrival` →
  controller maps to `ArrivalResponse` → 201.
- **Data classes**: `ArrivalResponse(id, arrivalDate, items: List<ItemResponse>)`,
  `ItemResponse(id, name, quantity, unitPrice, totalCost)` — same fields as
  today (no field trimming; nothing sensitive identified), but no JPA
  annotations, decoupling response shape from persistence mapping.
- **Authn/authz**: unaffected — no change to who can call these endpoints.

## 4. Key decisions and risks

| Decision | Options considered | Choice | Rationale |
| --- | --- | --- | --- |
| Where mapping lives | (a) inline in controller method, (b) dedicated `dto` package + mapper function, (c) mapping inside `ArrivalService` | (b) dedicated `dto` package + mapper function | Keeps controller thin and mapping testable/reusable; (c) rejected because brief's Unchanged contracts requires `ArrivalService` signature to stay the same |
| DTO field set | Trim fields vs. mirror entity fields | Mirror entity fields (no trimming) | No sensitive/internal-only field is evidenced in current entities; the ticket's concern is structural coupling, not a field leak — flag as open question below |

- **Open question** `[MISSING — input needed]`: should any `Item`/`Arrival`
  field be excluded from the response DTO (e.g. future internal-only fields),
  or is 1:1 field mirroring acceptable for this pass? Assuming 1:1 mirroring
  unless told otherwise.
- **Risk**: `Item` on `Arrival` is `@OneToMany` (default fetch = LAZY);
  today's entity-direct serialization already forces this within the request
  scope, so mapping to DTO after the service call does not change that
  behavior — no new risk introduced.

## 5. Implementation handoff

- New area: `com.ngolik.arrival.dto` — `ArrivalResponse`, `ItemResponse`, and
  a mapper (extension functions or a small mapper object).
- Touch: `ArrivalController` (map before returning on both endpoints).
- Tests: extend `ArrivalControllerTest` to assert response body shape is the
  DTO (not the entity), status codes unchanged (200 / 201).
- Branch: `feature/scrum-1-dto-layer-for-arrivals` (already checked out).
