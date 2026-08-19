# System overview

## Purpose

`arrival-service` (Eureka application name `ARRIVAL-API`) is a backend
microservice that tracks goods arrivals: an `Arrival` groups received `Item`s,
each `Item` optionally belongs to an `ItemCategory`, and a `Supplier` (with
embedded `ContactInfo`) is the source of goods. [INFERRED — please validate:
no product brief exists yet; this is inferred from entity shapes alone.]

## Components

| Component | Responsibility | Evidence (path) |
| --- | --- | --- |
| `ArrivalController` | REST entry point: list (`GET`) and create (`POST`) arrivals | `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` |
| `ArrivalService` / `ArrivalServiceImpl` | Business logic for arrivals (list, create) | `src/main/kotlin/com/ngolik/arrival/service/` |
| `ArrivalRepository`, `ItemRepository`, `ItemCategoryRepository`, `SupplierRepository` | Spring Data JPA persistence, no custom queries yet | `src/main/kotlin/com/ngolik/arrival/repo/` |
| Entities: `Arrival`, `Item`, `ItemCategory`, `Supplier`, `ContactInfo` | Domain model (JPA) | `src/main/kotlin/com/ngolik/arrival/entity/` |

## Runtime

- Language / framework: Kotlin 1.8.22, Java 17, Spring Boot 3.1.5 (Gradle Kotlin DSL).
- How to run locally: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`).
  Requires a running Eureka server at `http://localhost:8761/eureka/`
  (`@EnableDiscoveryClient`); the app registers under name `ARRIVAL-API`.
- Listens on port `8082` (`src/main/resources/application.yml`).
- Main entrypoint: `ArrivalServiceApplication.kt`.
- Persistence: Spring Data JPA with H2 and PostgreSQL drivers both on the
  classpath; no explicit datasource URL is configured in this repo. [MISSING —
  input needed: confirm actual DB per environment.]

## Integrations

| System | Direction | Notes |
| --- | --- | --- |
| Eureka discovery server | outbound (registers itself) | `spring-cloud-starter-netflix-eureka-client`, default zone `http://localhost:8761/eureka/` |
| Relational DB (H2 or PostgreSQL) | outbound | Driver present, connection not configured in-repo — see gap in `README.md` |

## Non-goals of this doc

Class-level inventories, full API catalogs, temporary spike notes — see
[structure.md](structure.md) for the package map and code for exact signatures.
