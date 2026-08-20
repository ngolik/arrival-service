# Brief: Add GET /api/arrivals/{id} endpoint

> Source: https://nikitagolik56.atlassian.net/browse/SCRUM-2 (Task)

## Tracker
- system: jira
- key: SCRUM-2
- type: Task
- url: https://nikitagolik56.atlassian.net/browse/SCRUM-2
- branch: feature/SCRUM-2-get-arrival-by-id
- commit_prefix: SCRUM-2

## Goal
Add a `GET /api/arrivals/{id}` endpoint so clients can fetch a single arrival by id (currently only list and create exist).

## In scope
- `GET /api/arrivals/{id}` — path variable `id` (Long)
- Delegate to existing service/repository (`findById` or equivalent — not yet present on `ArrivalService`, will need to be added)
- HTTP 200 with body when found; HTTP 404 when not found
- Unit/controller test for 200 and 404

## Out of scope / non-goals
- Create/update/delete beyond what already exists
- Pagination / filtering on the list endpoint
- Auth / CI / datasource configuration

## Constraints
- Stay on the existing stack
- `layout:` `spring-layers`
- Response shape: use the existing `ArrivalResponse` DTO (`com.ngolik.arrival.dto`) via `toResponse()`, consistent with `GET /api/arrivals` — DTO layer already exists in this repo, so no ambiguity here despite the ticket's "if DTO layer already exists" phrasing
- No new persistence model
- No datasource is configured — verify with the user before assuming a real lookup backend if `findById` requires one beyond current in-memory/whatever `ArrivalService` currently uses

## Unchanged contracts
- Existing `GET /api/arrivals` and `POST /api/arrivals` behavior unchanged
- `./gradlew test` passes

## Acceptance criteria
- [ ] `GET /api/arrivals/{id}` returns 200 and the arrival when it exists
- [ ] `GET /api/arrivals/{id}` returns 404 when the id is unknown
- [ ] Existing `GET /api/arrivals` and `POST /api/arrivals` behavior unchanged
- [ ] `./gradlew test` passes

## Suggested slug
`scrum-2-get-arrival-by-id`
