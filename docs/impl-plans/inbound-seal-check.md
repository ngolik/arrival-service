# Implementation Plan: Mark arrival as sealed (inspection passed)

## Summary

- **Goal:** Add operator-facing "mark as sealed" support to an existing
  `Arrival`, gated on a live-validated identified operator, with an optional
  ≤500-char note (hard reject over), surfaced on `GET /api/arrivals/{id}`,
  coexisting independently with the existing waiting/damaged facts.
- **In scope:** Entity fields, new request DTO, new `auth-service` client
  (this repo's first outbound HTTP call), two small exception types, a
  `RestTemplate` bean, service update method, controller endpoint, response
  DTO fields, tests.
- **Out of scope:** "List all sealed" endpoint, any money/finance data,
  persisting the validated operator id, any change to
  `isWaiting`/`remark`/`isDamaged`/`damageRemark`/`/waiting`/`/damaged`.
- **Architecture input:** `docs/architecture/inbound-seal-check.md`

## Touch map

| Area | Why |
| --- | --- |
| `src/main/kotlin/com/ngolik/arrival/entity/Arrival.kt` | Add `isSealed: Boolean = false`, `sealNote: String? = null` (`@field:Size(max = 500)`) |
| `src/main/kotlin/com/ngolik/arrival/dto/MarkArrivalSealedRequest.kt` (new) | Request body: `operatorId: Long` (mandatory), `note: String? = null` (≤500 chars) |
| `src/main/kotlin/com/ngolik/arrival/authclient/UserValidator.kt` (new) | `interface UserValidator { fun userExists(operatorId: Long): Boolean }` |
| `src/main/kotlin/com/ngolik/arrival/authclient/HttpUserValidator.kt` (new) | Calls `auth-service` `GET /auth/api/users/{id}` via `RestTemplate`; 200→true, 404→false, else throws `AuthServiceUnavailableException` |
| `src/main/kotlin/com/ngolik/arrival/exception/UnknownOperatorException.kt` (new) | Thrown when `userExists` returns false |
| `src/main/kotlin/com/ngolik/arrival/exception/AuthServiceUnavailableException.kt` (new) | Thrown on network error / unexpected status from `auth-service` |
| `src/main/kotlin/com/ngolik/arrival/config/RestTemplateConfig.kt` (new) | `@Bean fun restTemplate(): RestTemplate` |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalResponse.kt` | Add `isSealed: Boolean`, `sealNote: String?` |
| `src/main/kotlin/com/ngolik/arrival/dto/ArrivalMapper.kt` | Map the two new fields through in `toResponse()` |
| `src/main/kotlin/com/ngolik/arrival/service/ArrivalService.kt` | Add `markAsSealed(id: Long, operatorId: Long, note: String?): Arrival?` |
| `src/main/kotlin/com/ngolik/arrival/service/impl/ArrivalServiceImpl.kt` | Implement via `userValidator.userExists` + `findById` + `.copy()` + `save()`; inject `UserValidator` |
| `src/main/kotlin/com/ngolik/arrival/controller/ArrivalController.kt` | Add `PUT /{id}/sealed` handler with local try/catch for the two new exceptions |
| `src/main/resources/application.yml` | Add `auth-service.base-url: ${AUTH_SERVICE_BASE_URL:http://localhost:8081}` |
| `src/test/kotlin/com/ngolik/arrival/controller/ArrivalControllerTest.kt` | Cover: mark-sealed 200 (with/without note), 404 on missing arrival, 400 on note > 500 chars, 400 on unknown operator, 502 on auth-service unreachable, GET by id surfacing the new fields, and waiting+damaged+sealed coexisting independently |

No changes to `repo/ArrivalRepository.kt`.

## Steps

### Step 1 — Entity: add `isSealed` / `sealNote` fields

- **Outcome:** `Arrival` carries the two new fields with backward-compatible
  defaults; existing payloads that omit them are unaffected.
- **Approach:** In `entity/Arrival.kt`, add after the existing
  `damageRemark` field:
  ```kotlin
  val isSealed: Boolean = false,
  @field:Size(max = 500)
  val sealNote: String? = null
  ```
- **Tests:** Compile check; all existing `ArrivalControllerTest` cases must
  keep passing unmodified.
- **Done when:** Project compiles; no existing test needs to change.

### Step 2 — DTO: `MarkArrivalSealedRequest`

- **Outcome:** A required request body with one mandatory field.
- **Approach:** New file `dto/MarkArrivalSealedRequest.kt`:
  ```kotlin
  data class MarkArrivalSealedRequest(
          @field:NotNull
          val operatorId: Long?,
          @field:Size(max = 500)
          val note: String? = null
  )
  ```
  `operatorId` is nullable + `@field:NotNull` rather than a plain
  non-nullable `Long` — a primitive-backed Kotlin type silently defaults to
  `0` when the JSON key is absent (Jackson-kotlin only throws
  `MissingKotlinParameterException` for reference types, e.g.
  `Arrival.arrivalDate: LocalDateTime`, not `Long`/`Int`/`Boolean`). Making
  it nullable forces Bean Validation's `@NotNull` to catch a missing value
  explicitly. The controller unwraps with `request.operatorId!!`, safe
  because `@Valid` already rejected a null value before the handler body
  runs.
- **Tests:** Covered by controller tests in Step 7.
- **Done when:** Compiles; used by the controller in Step 6.

### Step 3 — `authclient`: `UserValidator` + `HttpUserValidator`

- **Outcome:** A structural seam (`UserValidator`) plus a real HTTP-backed
  implementation that mirrors `expenses-service/authclient/client.go`'s
  contract: confirmed-exists → `true`, confirmed-missing (404) → `false`,
  anything else → an exception distinct from "confirmed missing".
- **Approach:**
  ```kotlin
  // authclient/UserValidator.kt
  package com.ngolik.arrival.authclient

  interface UserValidator {
      fun userExists(operatorId: Long): Boolean
  }
  ```
  ```kotlin
  // authclient/HttpUserValidator.kt
  package com.ngolik.arrival.authclient

  import com.ngolik.arrival.exception.AuthServiceUnavailableException
  import org.springframework.beans.factory.annotation.Value
  import org.springframework.stereotype.Component
  import org.springframework.web.client.HttpClientErrorException
  import org.springframework.web.client.RestClientException
  import org.springframework.web.client.RestTemplate

  @Component
  class HttpUserValidator(
          private val restTemplate: RestTemplate,
          @Value("\${auth-service.base-url}") private val baseUrl: String
  ) : UserValidator {
      override fun userExists(operatorId: Long): Boolean {
          val url = "${baseUrl.trimEnd('/')}/auth/api/users/$operatorId"
          return try {
              restTemplate.getForEntity(url, Any::class.java)
              true
          } catch (e: HttpClientErrorException.NotFound) {
              false
          } catch (e: RestClientException) {
              throw AuthServiceUnavailableException(
                      "failed to validate operator $operatorId with auth-service at $url", e)
          }
      }
  }
  ```
- **Tests:** No standalone unit test for `HttpUserValidator` (no live
  `auth-service` in this test suite, same reasoning `expenses-service`
  applies to its own `authclient` — that package has its own dedicated test
  file there, but this repo's test layer stays at the controller level like
  its siblings); covered indirectly via controller tests mocking
  `ArrivalService`, since `@MockBean` on `ArrivalService` replaces the whole
  service bean and its `UserValidator` dependency is never exercised in
  those tests.
- **Done when:** Compiles; wired into `ArrivalServiceImpl` in Step 5.

### Step 4 — Exceptions + `RestTemplate` bean

- **Outcome:** Two small exception types and the one new infra bean this
  story needs.
- **Approach:**
  ```kotlin
  // exception/UnknownOperatorException.kt
  package com.ngolik.arrival.exception

  class UnknownOperatorException(message: String) : RuntimeException(message)
  ```
  ```kotlin
  // exception/AuthServiceUnavailableException.kt
  package com.ngolik.arrival.exception

  class AuthServiceUnavailableException(message: String, cause: Throwable? = null) :
          RuntimeException(message, cause)
  ```
  ```kotlin
  // config/RestTemplateConfig.kt
  package com.ngolik.arrival.config

  import org.springframework.context.annotation.Bean
  import org.springframework.context.annotation.Configuration
  import org.springframework.web.client.RestTemplate

  @Configuration
  class RestTemplateConfig {
      @Bean
      fun restTemplate(): RestTemplate = RestTemplate()
  }
  ```
  Also add to `application.yml`:
  ```yaml
  auth-service:
    base-url: ${AUTH_SERVICE_BASE_URL:http://localhost:8081}
  ```
- **Tests:** Context loads (covered by existing `@SpringBootTest` tests).
- **Done when:** Compiles; context starts without a live `auth-service`
  (the bean itself makes no network call at construction time).

### Step 5 — Service: add `markAsSealed`

- **Outcome:** `ArrivalService` exposes a way to mark an existing arrival as
  sealed, validating the operator first, returning `null` when the arrival
  does not exist.
- **Approach:** Add `fun markAsSealed(id: Long, operatorId: Long, note: String?): Arrival?`
  to the interface. Implement in `ArrivalServiceImpl` (constructor now also
  takes `userValidator: UserValidator`):
  ```kotlin
  override fun markAsSealed(id: Long, operatorId: Long, note: String?): Arrival? {
      if (!userValidator.userExists(operatorId)) {
          throw UnknownOperatorException(
                  "operatorId $operatorId does not correspond to an existing user")
      }
      return arrivalRepository.findById(id).orElse(null)
              ?.copy(isSealed = true, sealNote = note)
              ?.let { arrivalRepository.save(it) }
  }
  ```
- **Tests:** Covered indirectly via controller tests (no standalone service
  test layer exists today, same as `markAsWaiting`/`markAsDamaged`).
- **Done when:** Interface + impl compile; operator validation happens
  before any repository access.

### Step 6 — Controller: `PUT /{id}/sealed`

- **Outcome:** `PUT /api/arrivals/{id}/sealed` returns 200 + `ArrivalResponse`
  when the arrival exists and the operator is valid, 404 when the arrival
  doesn't exist, 400 when the operator is unknown or the note exceeds 500
  chars, 502 when `auth-service` is unreachable.
- **Approach:** Add to `ArrivalController`:
  ```kotlin
  @PutMapping("/{id}/sealed")
  fun markArrivalAsSealed(
          @PathVariable id: Long,
          @Valid @RequestBody request: MarkArrivalSealedRequest
  ): ResponseEntity<ArrivalResponse> =
          try {
              arrivalService.markAsSealed(id, request.operatorId, request.note)
                      ?.let { ResponseEntity.ok(it.toResponse()) }
                      ?: ResponseEntity.notFound().build()
          } catch (e: UnknownOperatorException) {
              ResponseEntity.badRequest().build()
          } catch (e: AuthServiceUnavailableException) {
              ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
          }
  ```
- **Tests:** Controller tests (Step 7) verify this.
- **Done when:** Endpoint compiles; `/waiting`/`/damaged` unchanged.

### Step 7 — DTO/mapper + tests

- **Outcome:** `GET /api/arrivals/{id}` includes `isSealed`/`sealNote`;
  `ArrivalControllerTest` covers the new endpoint end to end.
- **Approach:** Add `isSealed: Boolean`, `sealNote: String?` to
  `ArrivalResponse`; map both in `Arrival.toResponse()`. In
  `ArrivalControllerTest`, add a `@MockBean private lateinit var userValidator: UserValidator`
  (unused directly by these tests since `ArrivalService` itself is mocked,
  but present so the Spring context wires cleanly) and:
  - `PUT /api/arrivals/{id}/sealed` with a note → 200,
    `jsonPath("$.isSealed").value(true)`,
    `jsonPath("$.sealNote").value(...)`.
  - `PUT /api/arrivals/{id}/sealed` with no note → 200
    (`sealNote` null).
  - `PUT /api/arrivals/{id}/sealed` for a missing id → 404 (service mock
    throws nothing, returns null after "operator valid").
  - `PUT /api/arrivals/{id}/sealed` with a note > 500 chars → 400, service
    method never invoked.
  - `PUT /api/arrivals/{id}/sealed` where the service throws
    `UnknownOperatorException` → 400.
  - `PUT /api/arrivals/{id}/sealed` where the service throws
    `AuthServiceUnavailableException` → 502.
  - `PUT /api/arrivals/{id}/sealed` with `operatorId` missing from the JSON
    body → 400 (Jackson/binding failure, same convention as the existing
    "missing required field" test on `POST /api/arrivals`).
  - `GET /api/arrivals/{id}` fixture extended to assert `isSealed`/
    `sealNote` pass through when set.
  - `GET /api/arrivals/{id}` with waiting, damaged, and sealed all set on
    the same arrival → all three facts surface independently with their own
    notes/remarks (coexistence check, extending the existing two-fact
    coexistence test).
- **Done when:** `gradlew.bat test` passes, including all new cases and the
  pre-existing ones unchanged.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Adding fields to `Arrival`'s primary constructor could break existing test fixtures that construct it positionally | New fields use named defaults placed at the end; existing `sampleArrival()` uses named args already |
| Confusing "unknown operator" (400) with "auth-service unreachable" (502) | Two distinct exception types, tested separately in Step 7 |
| `HttpUserValidator` making a real network call during `@SpringBootTest` context startup | Bean construction (`RestTemplate()`) makes no network call; the HTTP call only happens inside `userExists()`, which controller tests never reach because `ArrivalService` itself is `@MockBean`-replaced |
| Reusing `isWaiting`/`isDamaged`/`remark`/`damageRemark` for the sealed fact by mistake | Explicitly separate fields (`isSealed`/`sealNote`); three-way coexistence test in Step 7 guards against regressions |

## Open questions

None specific to this slice — both open questions from the specification
(field reuse, identity-check pattern) were resolved during
`multi-repo-coordinator` discovery; see `docs/specifications/inbound-seal-check.md`
`## Repos`.
