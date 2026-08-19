# Architecture — arrival-service

_Generated from repository analysis. See Evidence appendix for source references._

## Overview

`arrival-service` is a single-package JVM backend microservice, written in
Kotlin on Spring Boot, that models goods arrivals — a shipment (`Arrival`)
containing line items (`Item`), sourced from a `Supplier`. It registers itself
with a Netflix Eureka discovery server under the application name
`ARRIVAL-API` (`src/main/resources/application.yml:6`,
`ArrivalServiceApplication.kt:9`). It is not a monorepo: one Gradle build
(`build.gradle.kts`, `settings.gradle.kts`) defines a single project named
`arrival-service` (`settings.gradle.kts:1`), with no workspace markers
(`pnpm-workspace.yaml`, `nx.json`, multi-project `include(...)` in
`settings.gradle.kts`) present.

## Repository Structure

```
arrival-service/
├── build.gradle.kts              Gradle build: Kotlin 1.8.22, Spring Boot 3.1.5, Java 17 target
├── settings.gradle.kts           Single-project Gradle settings (rootProject.name = "arrival-service")
├── gradlew / gradlew.bat         Gradle wrapper
├── src/
│   ├── main/
│   │   ├── kotlin/com/ngolik/arrival/
│   │   │   ├── ArrivalServiceApplication.kt   Spring Boot entrypoint
│   │   │   ├── controller/                    REST controllers
│   │   │   ├── service/                       Business logic interfaces
│   │   │   ├── service/impl/                  Business logic implementations
│   │   │   ├── repo/                          Spring Data JPA repositories
│   │   │   └── entity/                        JPA entities
│   │   └── resources/
│   │       ├── application.yml                Port, app name, Eureka client config
│   │       └── application.properties         Empty
│   └── test/kotlin/com/ngolik/arrival/
│       ├── ArrivalServiceApplicationTests.kt  Spring context-load smoke test
│       └── controller/
│           └── ArrivalControllerTest.kt       Controller test (@SpringBootTest + @AutoConfigureMockMvc)
```

Docs already exist under `docs/ai-context/` (system overview, constraints,
structure, glossary, delivery log) — that folder covers the same ground as
this document for AI-assisted delivery purposes; this file is the
general-purpose architecture reference.

## Components & Features

| Component | Responsibility | Evidence |
| --- | --- | --- |
| `ArrivalController` | REST entry point: list (`GET`) and create (`POST`, validated) arrivals; depends on the `ArrivalService` interface | `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` |
| `ArrivalService` / `ArrivalServiceImpl` | Business logic: list all arrivals, create an arrival | `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt`, `service/impl/ArrivalServiceImpl.kt` |
| `ArrivalRepository`, `ItemRepository`, `ItemCategoryRepository`, `SupplierRepository` | Spring Data JPA persistence, thin `JpaRepository<T, Long>` interfaces with no custom queries | `src/main/kotlin/com/ngolik/arrival/repo/*.kt` |
| Entities: `Arrival`, `Item`, `ItemCategory`, `Supplier`, `ContactInfo` | JPA domain model; `Arrival` has `@NotNull` on `id`/`arrivalDate`/`items` | `src/main/kotlin/com/ngolik/arrival/entity/*.kt` |

`ItemCategory` is a standalone JPA entity (`entity/ItemCategory.kt`) but
`Item` has no field referencing it (`entity/Item.kt`) — the two are not
actually linked in code today `[INFERRED — verify: category/item relationship
appears planned but not implemented]`.

The root `README.md` contains only the repo name with no functional claims to
verify.

## Exposed Interfaces

| Protocol | Path | Purpose | Evidence |
| --- | --- | --- | --- |
| REST (HTTP GET) | `/api/arrivals` | Returns all `Arrival` records (entities serialized directly, no DTO) | `controller/ArrivalController.kt` |
| REST (HTTP POST) | `/api/arrivals` | Creates an `Arrival` from a `@Valid` request body, returns 201 with the saved entity (no DTO) | `controller/ArrivalController.kt` |

No OpenAPI/Swagger spec, GraphQL schema, gRPC/proto definitions, published
package manifest, CLI entry point, or scheduled job configuration was found.

## External Dependencies

| Dependency | Direction | Notes | Evidence |
| --- | --- | --- | --- |
| Eureka discovery server | Outbound (registers itself) | Default zone `http://localhost:8761/eureka/`; app registers as `ARRIVAL-API` | `application.yml:5-11`, `ArrivalServiceApplication.kt:9` (`@EnableDiscoveryClient`) |
| Relational database | Outbound (persistence) | H2 and PostgreSQL JDBC drivers are both on the runtime classpath, but no `spring.datasource.*` URL/credentials are set in `application.yml` or `application.properties` | `build.gradle.kts:36-37`, `application.yml`, `application.properties` — `[NOT FOUND]`: actual DB target is unconfigured in this repo |

No outbound REST/gRPC clients to other application services, no message
broker client, and no third-party API integration were found in code or
config.

## Data & Persistence

- ORM: Spring Data JPA / Hibernate (via `spring-boot-starter-data-jpa`,
  `build.gradle.kts:29`), repositories scanned from
  `com.ngolik.arrival.repo` (`ArrivalServiceApplication.kt:10`).
- Entities: `Arrival` (id, arrivalDate, `@OneToMany` to `Item`),
  `Item` (id, name, quantity, unitPrice, totalCost), `ItemCategory` (id,
  name), `Supplier` (id, name, `@Embedded ContactInfo`), `ContactInfo`
  (address, phone, email — embeddable value object, no own identity)
  (`src/main/kotlin/com/ngolik/arrival/entity/*.kt`).
- No migration tool (Flyway/Liquibase) or `.sql` migration/seed files were
  found anywhere in the repo — schema is presumably Hibernate
  auto-generated `[INFERRED — verify]`.
- Storage technology actually used at runtime is unconfigured — see External
  Dependencies gap above.

## Build, CI/CD & Deployment

- Build tool: Gradle (Kotlin DSL), wrapper committed (`gradlew`,
  `gradlew.bat`).
- Test runner: JUnit 5 via `useJUnitPlatform()` (`build.gradle.kts:49-51`):
  a Spring context-load smoke test (`ArrivalServiceApplicationTests.kt`) and
  a controller test (`controller/ArrivalControllerTest.kt`). The controller
  test uses `@SpringBootTest` + `@AutoConfigureMockMvc` rather than
  `@WebMvcTest` — the latter's context fails because `@EnableJpaRepositories`
  is declared directly on the `@SpringBootApplication` class and isn't
  filtered out by the web-slice's auto-configuration exclusions
  (`ArrivalServiceApplication.kt:10`).
- Container image: `bootBuildImage` task is configured with the Paketo
  Buildpacks Jammy base builder (`build.gradle.kts:53-55`) — this is the only
  packaging mechanism found; there is no standalone `Dockerfile`.
- **CI/CD pipeline: `[NOT FOUND]`.** No `.github/workflows/`,
  `.gitlab-ci.yml`, `Jenkinsfile`, `.circleci/config.yml`, or other
  recognized CI config exists in the repo. No `docker-compose.yml`, `k8s/`,
  or `helm/` deploy manifests were found either.
- Path from commit to running artifact today is manual:
  `./gradlew build` / `./gradlew bootBuildImage` locally, no automated
  pipeline.

## Evidence Appendix

- Ecosystem/build: `build.gradle.kts`, `settings.gradle.kts`, `gradlew`
- Entrypoint & component scan: `src/main/kotlin/com/ngolik/arrival/ArrivalServiceApplication.kt`
- REST surface: `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt`
- Service layer: `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt`, `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt`
- Persistence: `src/main/kotlin/com/ngolik/arrival/repo/ArrivalRepository.kt`, `ItemRepository.kt`, `ItemCategoryRepository.kt`, `SupplierRepository.kt`
- Domain model: `src/main/kotlin/com/ngolik/arrival/entity/Arrival.kt`, `Item.kt`, `ItemCategory.kt`, `Supplier.kt`, `ContactInfo.kt`
- Runtime config: `src/main/resources/application.yml`, `src/main/resources/application.properties` (empty)
- Tests: `src/test/kotlin/com/ngolik/arrival/ArrivalServiceApplicationTests.kt`, `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt`
- CI/CD/deploy absence: repo tree search — no `.github/`, `.gitlab-ci.yml`, `Jenkinsfile`, `Dockerfile`, `docker-compose.yml`, `k8s/`, `helm/`, `*.sql` found
