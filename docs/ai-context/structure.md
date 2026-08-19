# Structure

Package map for `com.ngolik.arrival` (Kotlin, `src/main/kotlin/com/ngolik/arrival/`).
See [constraints.md](constraints.md) for the `layout: spring-layers` token this
maps to.

| Package | Contents | Notes |
| --- | --- | --- |
| (root) | `ArrivalServiceApplication.kt` | `@SpringBootApplication`, `@EnableDiscoveryClient`, `@EnableJpaRepositories(basePackages = ["com.ngolik.arrival.repo"])` |
| `controller` | `ArrivalController.kt` | Two endpoints: `GET /api/arrivals`, `POST /api/arrivals` (`@Valid` body, returns 201). Injects `ArrivalService` interface. |
| `service` | `ArrivalService.kt` | Interface: `getAllArrivals()`, `createArrival()`. |
| `service.impl` | `ArrivalServiceImpl.kt` | Implements `ArrivalService` via `ArrivalRepository`. |
| `repo` | `ArrivalRepository.kt`, `ItemRepository.kt`, `ItemCategoryRepository.kt`, `SupplierRepository.kt` | Thin `JpaRepository<T, Long>` interfaces, no custom queries. |
| `entity` | `Arrival.kt`, `Item.kt`, `ItemCategory.kt`, `Supplier.kt`, `ContactInfo.kt` | JPA `@Entity` data classes. `Arrival` has a `@OneToMany` to `Item`. `Supplier` has an `@Embedded ContactInfo`. `ItemCategory` exists but nothing currently references it from `Item`. |

Tests mirror the same root package under `src/test/kotlin/com/ngolik/arrival/`:
`ArrivalServiceApplicationTests.kt` (Spring context load) and
`controller/ArrivalControllerTest.kt` (`@SpringBootTest` + `@AutoConfigureMockMvc`
— see the `@EnableJpaRepositories` gap note in `README.md` for why controller
tests can't use `@WebMvcTest`).

Resources: `src/main/resources/application.yml` (port, app name, Eureka zone)
and `application.properties` (currently empty).

## Where new code goes

- New REST endpoints → `controller/`, calling through a `service` interface
  (as `ArrivalController` now does).
- New persistence types → matching `entity/` + `repo/` pair, repository
  interface under `com.ngolik.arrival.repo` so `@EnableJpaRepositories` picks
  it up.
- Business logic → `service/` interface + `service.impl/` implementation,
  following the existing `ArrivalService` / `ArrivalServiceImpl` split.
