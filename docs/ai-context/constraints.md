# Constraints

Hard rules for humans and AI. Prefer short bullets.

## Layout (briefs use one token, not a layer list)

Put `layout:` in the feature brief. Details live **here**, not repeated in
every brief.

| Token | Meaning |
| --- | --- |
| `spring-layers` | Current repo layout: `controller` / `entity` / `repo` / `service` (+ `service.impl`). No dedicated `dto` package yet — the controller returns JPA entities directly. |
| `flat-package` | Not used in this repo today. |
| `n/a` | Docs/CI/chore — no product packaging. |

Default for new arrival-service features: `layout: spring-layers`.

## Never

- Invent missing requirements; use gap markers (`[MISSING — input needed]`,
  `[INFERRED — please validate]`) instead.
- Silently change Kotlin, Spring Boot, or Java version pins in
  `build.gradle.kts` (Decision Drift — call it out explicitly if it's needed).
- Return JPA entities across service boundaries without checking whether the
  feature actually needs a DTO — but don't add a DTO layer unprompted either;
  confirm with the user first since the existing code doesn't use one.
- Assume a datasource — none is configured in `application.yml`/`application.properties`.
  Verify with the user before adding persistence-dependent code paths.

## Always

- Follow Decision Drift: if stack/version/API decisions change, update
  `docs/ai-context/system-overview.md` and/or the architecture/impl-plan doc
  in the same change.
- Non-trivial features: brief → architecture → impl plan → review before push.
- Register new JPA repositories under `com.ngolik.arrival.repo` — that's the
  package `@EnableJpaRepositories` scans (`ArrivalServiceApplication.kt`).

## Security / data

- No secrets in `docs/ai-context/` or cost summaries.
- No datasource credentials are currently committed — keep it that way; use
  environment variables or a secrets manager if a real DB config is added.

## Delivery

- Briefs: `docs/briefs/<slug>.md`
- Architecture: `docs/architecture/<slug>.md` (thin unless `complexity: high`)
- Impl plans: `docs/impl-plans/<slug>.md`
- Cost log: `docs/ai-context/delivery-log.md`

## Build / test / run

- Build: `./gradlew build`
- Test: `./gradlew test` (JUnit 5 via `useJUnitPlatform()`; only a context-load
  smoke test exists today — `ArrivalServiceApplicationTests.kt`)
- Run: `./gradlew bootRun` (needs Eureka at `localhost:8761`, port `8082`)
- No CI pipeline exists in this repo yet — these commands are the verification
  baseline until one is added.
