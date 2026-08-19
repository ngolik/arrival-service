# Brief: create-arrival endpoint

## Goal
Expose the existing `ArrivalService.createArrival` method via a REST endpoint so
clients can submit new arrival records over HTTP.

## In scope
- `POST /api/arrivals` — accepts an `Arrival` request body, delegates to
  `ArrivalService.createArrival`, returns the saved entity with HTTP 201
- Basic request validation (`@Valid` / `@NotNull` on required fields)
- Unit test for the new controller method

## Out of scope / non-goals
- DTO layer (controller may continue to accept/return the JPA entity for now)
- Authentication / authorization
- CI pipeline setup
- DB datasource configuration

## Constraints
- Stay on the existing stack (Kotlin, Spring Boot, Gradle)
- `layout:` `spring-layers`
- Follow the existing package convention: controller in
  `com.ngolik.arrival.controller`, service interface in
  `com.ngolik.arrival.service`
- Do **not** inject `ArrivalServiceImpl` directly — inject `ArrivalService`
  interface (fix the existing inconsistency in `ArrivalController`)

## Unchanged contracts
- `GET /api/arrivals` — unchanged
- All existing entity classes, repository, service interface signature

## Acceptance criteria
- [ ] `POST /api/arrivals` with a valid body returns HTTP 201 and the saved object
- [ ] `POST /api/arrivals` with a missing required field returns HTTP 400
- [ ] `ArrivalController` injects `ArrivalService` (not `ArrivalServiceImpl`)
- [ ] `./gradlew test` passes

## Suggested slug
`create-arrival-endpoint`
