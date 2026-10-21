# Identifier generation compatibility

## New-entity id semantics

`SalesManagerEntityServiceImpl` normalizes a numeric identifier of zero to `null`
before `save`, `saveAll`, and `saveAndFlush` delegate to Spring Data. The entity
model and API mapper defaults remain unchanged, so incoming DTOs may continue to
use `0` to represent a new entity. Hibernate 6.6 treats a non-null identifier as
an existing/detached entity; clearing the sentinel before the repository save
allows the configured generator to assign the identifier.

## Current generator configuration

`DataConfiguration` sets `hibernate.id.new_generator_mappings=false`. The shop
application properties also contain
`spring.jpa.hibernate.use-new-id-generator-mappings=true`. Tests inspecting the
Product persister in both the sm-core and sm-shop contexts reported
`org.hibernate.id.MultipleHiLoPerTableGenerator`, and both entity-manager
factories exposed `hibernate.id.new_generator_mappings=false`. The Boot property
therefore does not determine the effective Product generator in either context.

## `SM_SEQUENCER` measurements

The shipped database was copied to
`/home/ubuntu/ab541-notes/SALESMANAGER.h2.db` and inspected with the build's
H2 `1.4.200` driver. The listed allocation size and initial value are the
`@TableGenerator` defaults (`50` and `0`) for these entities. The measurements
of generated identifiers and the post-generation stored counts have not yet
been run, and the table is not a complete inventory of all mapped
`pkColumnValue`s.

| `SEQ_NAME` | Entity | Allocation size | Initial value | Seed `SEQ_COUNT` | Generated IDs | Final `SEQ_COUNT` |
| --- | --- | ---: | ---: | --- | --- | --- |
| `PRODUCT_SEQ_NEXT_VAL` | `Product` | 50 | 0 | 2 | Not measured | Not measured |
| `CUSTOMER_SEQ_NEXT_VAL` | `Customer` | 50 | 0 | 4 | Not measured | Not measured |
| `ORDER_ID_SEQ_NEXT_VAL` | `Order` | 50 | 0 | No row in seed | Not measured | Not measured |
| `CATEGORY_SEQ_NEXT_VAL` | `Category` | 50 | 0 | 2 | Not measured | Not measured |

The seed contains 29 `SM_SEQUENCER` rows. The next-id behavior, sequence
formula, and measurements for the remaining generator keys still need to be
captured; no formula is asserted here.

## Hibernate 6 generator settings

These settings must be tested with the Hibernate 6.6 harness using the same
starting sequence state and comparing both generated identifiers and the
stored sequence count. Applying any Hibernate 6-specific setting belongs to
AB-544.

| Setting | Status |
| --- | --- |
| `hibernate.id.optimizer.pooled.preferred=legacy-hilo` | UNVERIFIED |
| `hibernate.id.generator.stored_last_used` | UNVERIFIED |
