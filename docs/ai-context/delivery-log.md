# Delivery log (ROI + per-feature cost)

Per-feature detail: `docs/ai-context/features/<slug>/cost-summary.json`.

| Date | Slug | Wall time | AI cost (USD) | Cost basis | Uncached tok | Cache tok | Out tok | Messages | Rework (Y/N) | Escaped (n) | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-08-19 | create-arrival-endpoint | | $3.9436 (estimated) | estimated | 136 | 9564218 | 41337 | 68 | N | 0 | Full pipeline (brief already drafted); pre-push-review clean, no Critical/Major |
| 2026-08-20 | scrum-1-dto-layer-for-arrivals | | $1.6677 (estimated) | estimated | 74 | 3545430 | 22636 | 37 | N | 0 | Full pipeline from Jira SCRUM-1 via jira-to-brief; pre-push-review clean, no Critical/Major |

**Cost basis:** `measured` = session-reported; `estimated` = projected (not billing).
Never present estimated USD as invoiced cost.
