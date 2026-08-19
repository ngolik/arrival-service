# Glossary

Domain terms as reflected by the current entity model. Expand as real product
vocabulary emerges from briefs and stakeholders.

| Term | Meaning in this product | Avoid confusing with |
| --- | --- | --- |
| Arrival | A received shipment/delivery event; has a date and a list of `Item`s (`entity/Arrival.kt`) | A `Supplier` (the source) |
| Item | A single line item within an `Arrival`: name, quantity, unit price, total cost (`entity/Item.kt`) | `ItemCategory` (a classification of items, not an item itself) |
| ItemCategory | A category label for items (`entity/ItemCategory.kt`) | Not currently linked from `Item` in code — relationship is [MISSING — input needed] |
| Supplier | The source of goods, with embedded `ContactInfo` (`entity/Supplier.kt`) | — |
| ContactInfo | Embedded value object: address, phone, email (`entity/ContactInfo.kt`) | A standalone entity — it has no `@Entity`/`@Id` of its own |
| ARRIVAL-API | The Eureka service registration name for this app (`application.yml`) | The Gradle project name `arrival-service` |
