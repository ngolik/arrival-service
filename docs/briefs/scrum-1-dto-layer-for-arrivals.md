# Brief: Introduce DTO layer for arrival API responses

> Source: https://nikitagolik56.atlassian.net/browse/SCRUM-1 (Task)

## Goal
Introduce a thin DTO layer so REST responses for the arrival API use dedicated
types instead of exposing JPA entities (`Arrival`, `Item`, …) directly over HTTP.

## In scope
- [INFERRED — verify] Define response DTO type(s) for the arrival API, mapped
  from existing JPA entities (`Arrival`, `Item`, …)
- [INFERRED — verify] Map entities to DTOs at the service/controller boundary
- `GET /api/arrivals` returns response DTOs instead of raw entities
- `POST /api/arrivals` returns a response DTO instead of the raw saved entity
- Request body for `POST /api/arrivals` stays as the JPA entity for now
  (out of scope — see below)

## Out of scope / non-goals
- Introducing a request DTO for `POST /api/arrivals` (response mapping only,
  per this ticket's description)

## Constraints
- Stay on the existing stack (Kotlin, Spring Boot, Gradle)
- `layout:` `spring-layers`
- `complexity:` omit (thin arch)
- [FLAG] This conflicts with a prior decision: `docs/briefs/create-arrival-endpoint.md`
  explicitly lists "DTO layer" as out of scope, and `docs/ai-context/constraints.md`
  says not to add a DTO layer without confirming with the user first. Treating
  this Jira ticket as that confirmation — verify before running the playbook.

## Unchanged contracts
- HTTP methods, paths, and status codes for `GET /api/arrivals` and
  `POST /api/arrivals` stay the same (only the response body shape changes)
- `ArrivalService` interface signature, repository, and JPA entity classes
  keep their existing shape
- No other endpoints are touched

## Acceptance criteria
- [ ] Controllers don't return JPA entity types in response bodies
- [ ] `GET /api/arrivals` returns arrival response DTOs
- [ ] `POST /api/arrivals` returns arrival response DTO with 201
- [ ] `./gradlew test` passes
- [ ] No internal-only persistence fields leaked in API JSON (or explicitly documented)

## Suggested slug
`scrum-1-dto-layer-for-arrivals`
