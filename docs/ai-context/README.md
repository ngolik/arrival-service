# AI context — arrival-service

Durable, evidence-based context for AI-assisted delivery in this repo. Read
these before starting a feature; keep them current when reality drifts
(Decision Drift — update the doc in the same change as the code).

## Index

| Doc | Status | Owner |
| --- | --- | --- |
| [system-overview.md](system-overview.md) | drafted from code | |
| [constraints.md](constraints.md) | drafted from code | |
| [structure.md](structure.md) | drafted from code | |
| [glossary.md](glossary.md) | stub — few domain terms yet | |
| [delivery-log.md](delivery-log.md) | empty table, ready for first feature | |

## Known gaps

- [ ] No CI pipeline (`.github/workflows` absent) — verification is local-only today.
- [ ] No datasource configured in `application.yml`/`application.properties` — both
      H2 and PostgreSQL drivers are on the classpath but neither is wired up
      explicitly. [MISSING — input needed: which DB backs which environment?]
- [ ] No DTO layer — controller returns JPA entities directly. Decide whether to
      formalize `spring-layers` with request/response DTOs or stay minimal.
- [x] `ArrivalService.createArrival` exists but is not exposed by any controller
      endpoint — fixed by `create-arrival-endpoint` (`POST /api/arrivals`).
- [ ] `ArrivalServiceApplication` declares `@EnableJpaRepositories` directly on
      the `@SpringBootApplication` class. This breaks `@WebMvcTest` slices
      (context fails with `No bean named 'entityManagerFactory' available`)
      because the import isn't filtered by the slice's auto-configuration
      exclusions. Controller tests must use `@SpringBootTest` +
      `@AutoConfigureMockMvc` instead until `@EnableJpaRepositories` is moved
      off the primary application class.
- [ ] No owners assigned to the docs above.

## Next feature

Start delivery with:

```text
/demo-engineering:feature-delivery-playbook docs/briefs/<slug>.md
```
