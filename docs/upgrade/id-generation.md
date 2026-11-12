# ID generation baseline for AB-544

AB-541 records the current Hibernate 5 behavior; it does not change generators,
sequencer data, configuration, or allocation sizes. **Applying the Hibernate 6
settings and validating the migration against an existing database is AB-544.**

## Effective configuration: the application is not using enhanced generators

There are two different settings in this application:

* `sm-shop/src/main/resources/application.properties` declares
  `spring.jpa.hibernate.use-new-id-generator-mappings=true`.
* `DataConfiguration.entityManagerFactory()` creates its own factory, calls
  `setJpaProperties(additionalProperties())`, and explicitly sets
  **`hibernate.id.new_generator_mappings=false`**. The Boot property above is not
  forwarded to this manually configured factory.

`IdGeneratorParityIntegrationTest` verifies both the environment's `true` and
the factory's effective `false`, then inspects the actual identifier generators:
they are `org.hibernate.id.MultipleHiLoPerTableGenerator`, not the enhanced
`org.hibernate.id.enhanced.TableGenerator`. This finding is important: choosing
`pooled` or `pooled-lo` based only on the Boot property would change existing IDs.
Neither setting has been changed here.

For allocation size 50, the actual legacy generator has `maxLo=49` and a
`LegacyHiLoAlgorithmOptimizer`. A table source value H supplies IDs
`H * 50 + lo`, for lo 0..49 (zero itself is skipped). The source row advances by
**one per allocated block**, not by 50. It stores the next high value, not the
last entity ID. For allocation size 1, `maxLo=0` bypasses the optimizer, skips a
zero source value, and advances the row once per ID. The row is then ID + 1.
The legacy implementation does not consume the JPA `initialValue`; Catalog's
annotated 2000 therefore does not mean that its first ID is 2000 today.

### What would happen with enhanced/new mappings?

The regression test separately configures the enhanced TableGenerator on the
installed Hibernate **5.4.33** without changing the application's factory. With
increment size 50 and no explicit optimizer it instantiates `PooledOptimizer`;
`hibernate.id.optimizer.pooled.preferred=pooled-lo` changes it to
`PooledLoOptimizer`. With increment size 1 it uses `NoopOptimizer` even when a
pooled preference is present. Its default `storeLastUsedValue` is true. These
are controlled generator-configuration checks, not evidence that the app uses
enhanced generators. No enhanced-generator entity insert is claimed here.

## Complete annotation inventory

All 80 `@TableGenerator` declarations use table **SM_SEQUENCER**, primary-key
column **SEQ_NAME**, and value column **SEQ_COUNT**. All `@GeneratedValue`
declarations use `GenerationType.TABLE`: 62 are on the non-description classes
below, with generator `TABLE_GEN`; the remaining declaration is on mapped
superclass `common/description/Description`, with generator `description_gen`,
inherited by the 18 description classes below. There are no AUTO, IDENTITY or
SEQUENCE declarations. Classes without generated IDs (including assigned or
composite IDs) are not table generators.

Names below identify classes under
`sm-core-model/src/main/java/com/salesmanager/core/model/`. Values 50 and 0 are
the JPA defaults where omitted. Explicit constants resolve to
`SchemaConstant.DESCRIPTION_ID_ALLOCATION_SIZE=1` and
`SchemaConstant.DESCRIPTION_ID_START_VALUE=2000`. These are annotation values,
not a claim that the effective legacy generator honors `initialValue`.

| Entity | Generator name | SM_SEQUENCER pkColumnValue | allocationSize | initialValue |
|---|---|---|---:|---:|
| Catalog | TABLE_GEN | CATALOG_SEQ_NEXT_VAL | 1 | 2000 |
| CatalogCategoryEntry | TABLE_GEN | CATALOG_ENT_SEQ_NEXT_VAL | 1 | 2000 |
| Category | TABLE_GEN | CATEGORY_SEQ_NEXT_VAL | 50 | 0 |
| CategoryDescription | description_gen | category_description_seq | 1 | 2000 |
| Product | TABLE_GEN | PRODUCT_SEQ_NEXT_VAL | 50 | 0 |
| ProductAttribute | TABLE_GEN | PRODUCT_ATTR_SEQ_NEXT_VAL | 50 | 0 |
| ProductOption | TABLE_GEN | PRODUCT_OPTION_SEQ_NEXT_VAL | 50 | 0 |
| ProductOptionDescription | description_gen | product_option_description_seq | 1 | 2000 |
| ProductOptionSet | TABLE_GEN | PRODUCT_OPT_SET_SEQ_NEXT_VAL | 50 | 0 |
| ProductOptionValue | TABLE_GEN | PRODUCT_OPT_VAL_SEQ_NEXT_VAL | 50 | 0 |
| ProductOptionValueDescription | description_gen | product_option_value_description_seq | 1 | 2000 |
| ProductAvailability | TABLE_GEN | PRODUCT_AVAIL_SEQ_NEXT_VAL | 50 | 0 |
| ProductDescription | description_gen | product_description_seq | 1 | 2000 |
| DigitalProduct | TABLE_GEN | PRODUCT_DGT_SEQ_NEXT_VAL | 50 | 0 |
| ProductImage | TABLE_GEN | PRODUCT_IMG_SEQ_NEXT_VAL | 50 | 0 |
| ProductImageDescription | description_gen | product_image_description_seq | 1 | 2000 |
| Manufacturer | TABLE_GEN | MANUFACT_SEQ_NEXT_VAL | 50 | 0 |
| ManufacturerDescription | description_gen | manufacturer_description_seq | 1 | 2000 |
| ProductPrice | TABLE_GEN | PRODUCT_PRICE_SEQ_NEXT_VAL | 50 | 0 |
| ProductPriceDescription | description_gen | product_price_description_seq | 1 | 2000 |
| ProductRelationship | TABLE_GEN | PRODUCT_RELATION_SEQ_NEXT_VAL | 50 | 0 |
| ProductReview | TABLE_GEN | PRODUCT_REVIEW_SEQ_NEXT_VAL | 50 | 0 |
| ProductReviewDescription | description_gen | product_review_description_seq | 1 | 2000 |
| ProductType | TABLE_GEN | PRD_TYPE_SEQ_NEXT_VAL | 50 | 0 |
| ProductTypeDescription | description_gen | product_type_description_seq | 1 | 2000 |
| ProductVariant | TABLE_GEN | PRODUCT_VAR_SEQ_NEXT_VAL | 50 | 0 |
| ProductVariantGroup | TABLE_GEN | PRODUCT_VAR_GROUP_SEQ_NEXT_VAL | 50 | 0 |
| ProductVariantImage | TABLE_GEN | PRD_VAR_IMG_SEQ_NEXT_VAL | 50 | 0 |
| ProductVariantImageDescription | description_gen | product_var_image_desc_seq | 1 | 2000 |
| ProductVariation | TABLE_GEN | PRODUCT_VARIN_SEQ_NEXT_VAL | 50 | 0 |
| Content | TABLE_GEN | CONTENT_SEQ_NEXT_VAL | 50 | 0 |
| ContentDescription | description_gen | content_description_seq | 1 | 2000 |
| Customer | TABLE_GEN | CUSTOMER_SEQ_NEXT_VAL | 50 | 0 |
| CustomerAttribute | TABLE_GEN | CUST_ATTR_SEQ_NEXT_VAL | 50 | 0 |
| CustomerOption | TABLE_GEN | CUSTOMER_OPTION_SEQ_NEXT_VAL | 50 | 0 |
| CustomerOptionDescription | description_gen | customer_option_description_seq | 1 | 2000 |
| CustomerOptionSet | TABLE_GEN | CUST_OPTSET_SEQ_NEXT_VAL | 50 | 0 |
| CustomerOptionValue | TABLE_GEN | CUSTOMER_OPT_VAL_SEQ_NEXT_VAL | 50 | 0 |
| CustomerOptionValueDescription | description_gen | customer_option_value_description_seq | 1 | 2000 |
| CustomerReview | TABLE_GEN | CUSTOMER_REVIEW_SEQ_NEXT_VAL | 50 | 0 |
| CustomerReviewDescription | description_gen | custome_review_description_seq | 1 | 2000 |
| MerchantStore | TABLE_GEN | STORE_SEQ_NEXT_VAL | 50 | 0 |
| Order | TABLE_GEN | ORDER_ID_SEQ_NEXT_VAL | 50 | 0 |
| OrderTotal | TABLE_GEN | ORDER_TOTAL_ID_NEXT_VALUE | 50 | 0 |
| OrderAttribute | TABLE_GEN | ORDER_ATTR_ID_NEXT_VALUE | 50 | 0 |
| FileHistory | TABLE_GEN | FILE_HISTORY_ID_NEXT_VALUE | 50 | 0 |
| OrderAccount | TABLE_GEN | ORDER_ACCOUNT_ID_NEXT_VALUE | 50 | 0 |
| OrderAccountProduct | TABLE_GEN | ORDERACCOUNTPRODUCT_SEQ_NEXT_VAL | 50 | 0 |
| OrderProduct | TABLE_GEN | ORDER_PRODUCT_ID_NEXT_VALUE | 50 | 0 |
| OrderProductAttribute | TABLE_GEN | ORDER_PRODUCT_ATTR_ID_NEXT_VAL | 50 | 0 |
| OrderProductDownload | TABLE_GEN | ORDER_PRODUCT_DL_ID_NEXT_VALUE | 50 | 0 |
| OrderProductPrice | TABLE_GEN | ORDER_PRD_PRICE_ID_NEXT_VAL | 50 | 0 |
| OrderStatusHistory | TABLE_GEN | STATUS_HIST_ID_NEXT_VALUE | 50 | 0 |
| Transaction | TABLE_GEN | TRANSACT_SEQ_NEXT_VAL | 50 | 0 |
| Country | TABLE_GEN | COUNTRY_SEQ_NEXT_VAL | 50 | 0 |
| CountryDescription | description_gen | country_description_seq | 1 | 2000 |
| Currency | TABLE_GEN | CURRENCY_SEQ_NEXT_VAL | 50 | 0 |
| GeoZone | TABLE_GEN | GEOZONE_SEQ_NEXT_VAL | 50 | 0 |
| GeoZoneDescription | description_gen | geozone_description_seq | 1 | 2000 |
| Language | TABLE_GEN | LANG_SEQ_NEXT_VAL | 50 | 0 |
| Zone | TABLE_GEN | ZONE_SEQ_NEXT_VAL | 50 | 0 |
| ZoneDescription | description_gen | zone_description_seq | 1 | 2000 |
| Quote | TABLE_GEN | SHIP_QUOTE_ID_NEXT_VALUE | 50 | 0 |
| ShippingOrigin | TABLE_GEN | SHP_ORIG_SEQ_NEXT_VAL | 50 | 0 |
| ShoppingCart | TABLE_GEN | SHP_CRT_SEQ_NEXT_VAL | 50 | 0 |
| ShoppingCartAttributeItem | TABLE_GEN | SHP_CRT_ATTR_ITM_SEQ_NEXT_VAL | 50 | 0 |
| ShoppingCartItem | TABLE_GEN | SHP_CRT_ITM_SEQ_NEXT_VAL | 50 | 0 |
| IntegrationModule | TABLE_GEN | MOD_CONF_SEQ_NEXT_VAL | 50 | 0 |
| MerchantConfiguration | TABLE_GEN | MERCH_CONF_SEQ_NEXT_VAL | 50 | 0 |
| MerchantLog | TABLE_GEN | MR_LOG_SEQ_NEXT_VAL | 50 | 0 |
| SystemConfiguration | TABLE_GEN | SYST_CONF_SEQ_NEXT_VAL | 50 | 0 |
| SystemNotification | TABLE_GEN | SYST_NOTIF_SEQ_NEXT_VAL | 50 | 0 |
| CustomerOptin | TABLE_GEN | CUST_OPT_SEQ_NEXT_VAL | 50 | 0 |
| Optin | TABLE_GEN | OPTIN_SEQ_NEXT_VAL | 50 | 0 |
| TaxClass | TABLE_GEN | TX_CLASS_SEQ_NEXT_VAL | 50 | 0 |
| TaxRate | TABLE_GEN | TAX_RATE_ID_NEXT_VALUE | 50 | 0 |
| TaxRateDescription | description_gen | taxrate_description_seq | 1 | 2000 |
| Group | TABLE_GEN | GROUP_SEQ_NEXT_VAL | 50 | 0 |
| Permission | TABLE_GEN | PERMISSION_SEQ_NEXT_VAL | 50 | 0 |
| User | TABLE_GEN | USER_SEQ_NEXT_VAL | 50 | 0 |

## Empirical Hibernate 5 / H2 capture

Dedicated test run, with the normal `ServicesTestSupport` Spring context and
fresh initialized H2 memory database. The test opens two different sessions per
entity, performs three actual `persist`/`flush` operations per session and reads
the sequencer after each flush. Entity transactions roll back to keep fixtures
isolated; generator allocations commit independently and are not rolled back.
Both distinct annotation allocation sizes (50 and 1) are represented.

| Entity | Session | Row before | Generated IDs | Row after each insert |
|---|---:|---:|---|---|
| Product | 1 | absent | 1, 2, 3 | 1, 1, 1 |
| Product | 2 | 1 | 4, 5, 6 | 1, 1, 1 |
| Catalog | 1 | absent | 1, 2, 3 | 2, 3, 4 |
| Catalog | 2 | 4 | 4, 5, 6 | 5, 6, 7 |
| Manufacturer | 1 | 1 | 2, 3, 4 | 1, 1, 1 |
| Manufacturer | 2 | 1 | 5, 6, 7 | 1, 1, 1 |
| ManufacturerDescription | 1 | 2 | 2, 3, 4 | 3, 4, 5 |
| ManufacturerDescription | 2 | 5 | 5, 6, 7 | 6, 7, 8 |

Each manufacturer description has a separately persisted manufacturer, so the
description probe additionally consumes six manufacturer IDs. Initialization
has already generated Manufacturer ID 1 and ManufacturerDescription ID 1.
These are capture values, not fixed IDs assumed by the test: its assertions use
the actual source row, optimizer state, and consecutive-ID arithmetic so that
the test also works when the full reactor has consumed other allocations.

The complete before/after snapshot includes every row present in either
snapshot. All remaining inventory segments were absent in both snapshots.

| SEQ_NAME | Before | After |
|---|---:|---:|
| CATALOG_SEQ_NEXT_VAL | absent | 7 |
| COUNTRY_SEQ_NEXT_VAL | 5 | 5 |
| CURRENCY_SEQ_NEXT_VAL | 4 | 4 |
| GROUP_SEQ_NEXT_VAL | 1 | 1 |
| LANG_SEQ_NEXT_VAL | 1 | 1 |
| MANUFACT_SEQ_NEXT_VAL | 1 | 1 |
| MERCH_CONF_SEQ_NEXT_VAL | 1 | 1 |
| MOD_CONF_SEQ_NEXT_VAL | 1 | 1 |
| OPTIN_SEQ_NEXT_VAL | 1 | 1 |
| PERMISSION_SEQ_NEXT_VAL | 1 | 1 |
| PRD_TYPE_SEQ_NEXT_VAL | 1 | 1 |
| PRODUCT_SEQ_NEXT_VAL | absent | 1 |
| STORE_SEQ_NEXT_VAL | 1 | 1 |
| TX_CLASS_SEQ_NEXT_VAL | 1 | 1 |
| USER_SEQ_NEXT_VAL | 1 | 1 |
| ZONE_SEQ_NEXT_VAL | 5 | 5 |
| country_description_seq | 712 | 712 |
| manufacturer_description_seq | 2 | 8 |
| zone_description_seq | 652 | 652 |

The new sessions share one SessionFactory, so they keep its optimizer pools.
A process/SessionFactory restart was not exercised in this test. By the legacy
algorithm, restarting with source row 1 would allocate Product ID 50 next,
discarding unused IDs 7..49; that example is a source-derived prediction, not a
measured restart. A production migration must preserve safe gaps, not reuse
unconsumed in-memory IDs.

Reproduce the focused capture with system Maven and JDK 17:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B -pl sm-shop -am \
  -Dtest=IdGeneratorParityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The output prefixes `ID-PARITY` and `ID-ROW` identify measurements. The test also
guards the effective configuration so an accidental generator switch is visible.

## Hibernate 6 parity configuration to implement in AB-544

The following is a **source-reviewed migration recipe, not Hibernate 6 runtime
certification**. Hibernate 6's enhanced TableGenerator still supports explicit
optimizer selection and `hibernate.id.generator.stored_last_used`. Simply
removing the legacy-mappings setting and leaving all JPA annotations unchanged
does **not** preserve this application's current sequences.

Preserve each segment name, table/column names, schema and existing row value.
To reproduce the actual legacy allocation algorithm with enhanced generators:

| Current annotation allocation | Explicit enhanced-generator parameters |
|---|---|
| 50 | `optimizer=legacy-hilo`, **`increment_size=49`**, `initial_value=0` |
| 1 | `optimizer=none`, `increment_size=1`, **`initial_value=1`** |

Set **`hibernate.id.generator.stored_last_used=false`**. This makes the table
callback return the old source value and store its successor, like the legacy
generator. With `legacy-hilo`, `increment_size` is the maximum low value (49),
not the block size (50); leaving it at 50 would produce blocks of 51. The source
advances by one because this optimizer does not apply its increment size to the
database source. With `none`, starting a missing row at 1 reproduces the legacy
zero-skip behavior without ever emitting entity ID 0. The annotated 2000 initial
values must not silently become effective during migration.

These are **per-generator overrides**, for example via explicit enhanced
TableGenerator configuration in AB-544; they are not a suggestion to change JPA
`allocationSize` to 49 and assume that alone is sufficient. Explicit optimizer
parameters take precedence over the implicit pooled preference, so
`hibernate.id.optimizer.pooled.preferred` may remain `pooled` (the default), but
it is not the parity mechanism. Neither `pooled` nor `pooled-lo` alone reproduces
legacy hi/lo rows. Do not reinterpret or reseed existing rows as last entity IDs.

For a *different deployment that is actually verified to use enhanced H5
generators*, retain its measured per-generator increment/initial values and
optimizer: default `pooled` for increments greater than 1 (or explicit
`pooled-lo` if configured), `none` for increment 1, and
`hibernate.id.generator.stored_last_used=true` if that is its observed setting.
That alternative is not this repository's current effective runtime.

AB-544 must test both existing rows and missing rows, block exhaustion, a real
factory/process restart, parallel writers and migration cutover with old
writers stopped. Matching the sequence means matching the same source row and
pool state; arbitrary restarts legitimately introduce gaps.

Source references used to check the algorithms and defaults:

* [H5 MultipleHiLoPerTableGenerator](https://github.com/hibernate/hibernate-orm/blob/5.4.33/hibernate-core/src/main/java/org/hibernate/id/MultipleHiLoPerTableGenerator.java)
* [H5 enhanced TableGenerator](https://github.com/hibernate/hibernate-orm/blob/5.4.33/hibernate-core/src/main/java/org/hibernate/id/enhanced/TableGenerator.java)
* [H6 OptimizerFactory](https://github.com/hibernate/hibernate-orm/blob/6.6/hibernate-core/src/main/java/org/hibernate/id/enhanced/OptimizerFactory.java)
* [H6 TableGenerator](https://github.com/hibernate/hibernate-orm/blob/6.6/hibernate-core/src/main/java/org/hibernate/id/enhanced/TableGenerator.java)
* [H6 LegacyHiLoAlgorithmOptimizer](https://github.com/hibernate/hibernate-orm/blob/6.6/hibernate-core/src/main/java/org/hibernate/id/enhanced/LegacyHiLoAlgorithmOptimizer.java)

## Local packaged-runtime smoke test

Ran the JDK 17 jar on port 8081 using PropertiesLauncher, an external
`database.properties`, and a copy of `sm-shop/SALESMANAGER.h2.db` and `files` in
`~/shopizer-runtime`. No tracked seed database was used in place. Admin login
used the seed's local test credentials; tokens are omitted below.

| Request | Status | Response excerpt |
|---|---:|---|
| GET `/api/v1/products?store=DEFAULT` | 200 | `number:4`, product `id:1, sku:table1` |
| GET `/api/v1/product/friendly/olive-table?store=DEFAULT` | 200 | `id:1, sku:table1` |
| GET `/api/v2/product/table1?store=DEFAULT` | 200 | `id:1, sku:table1` |
| GET `/api/v1/category?store=DEFAULT` | 200 | `number:3`, includes `code:DEFAULT` |
| POST `/api/v1/private/login` | 200 | admin ID and JWT returned |
| GET `/api/v2/private/product/1?store=DEFAULT` (Bearer) | 200 | `id:1, sku:table1` |
| POST `/api/v1/private/product?store=DEFAULT` (Bearer, `id:0`) | 201 | `id:100` |
| GET `/api/v2/private/product/100?store=DEFAULT` (Bearer) | 200 | `id:100, sku:ab541-smoke-zero` |
| POST `/api/v1/customer/register?store=DEFAULT` (`id:0`) | 200 | `id:200`, JWT returned |
| GET `/api/v1/private/customer/200?store=DEFAULT` (Bearer) | 200 | `id:200, emailAddress:ab541-smoke@example.com` |
| POST `/api/v1/cart?store=DEFAULT` | 201 | `id:50, quantity:1, total:199.0`, cart code returned |
| GET `/api/v1/cart/{returned-code}?store=DEFAULT` | 200 | same `id:50, quantity:1, total:199.0` |

Successful creation payloads (password and token values omitted):

```json
{
  "id": 0, "sku": "ab541-smoke-zero", "available": true, "visible": true,
  "inventory": {"sku": "ab541-smoke-zero", "quantity": 10, "price": {"price": 12.34}},
  "productSpecifications": {"manufacturer": "DEFAULT"},
  "descriptions": [{"language": "en", "name": "AB541 zero ID smoke", "friendlyUrl": "ab541-zero-smoke", "description": "Local smoke fixture"}],
  "categories": [{"id": 50}]
}
```

Customer: `id:0`, email `ab541-smoke@example.com`, a password, gender `M`,
language `en`, storeCode `DEFAULT`, billing firstName `AB541`, lastName `Smoke`,
country `BE`. Cart: `{"product":"table1","quantity":1}`.

Smoke-test discoveries, left unchanged: v1's numeric product-ID handler is
commented out ("Removed in 3.2.5"), so `/api/v1/product/1` is interpreted as a
friendly URL and returns 500 via the already documented empty-result exception;
`/api/v1/products/1` returns 404. The authenticated v2 numeric-ID endpoint works.
The v1 controller is unchanged from the base branch. An initial product request
without `inventory` returned 400 ("product must have at least one availability");
a cart request with numeric product 1 returned 400 ("Cannot get product with sku
[1]"). Using the actual DTO's inventory and SKU fields produced the successful
requests above. These were request corrections, not application changes.

## Implemented in AB-544 (Hibernate 6.6)

* `DataConfiguration` no longer sets `hibernate.id.new_generator_mappings` (removed in
  Hibernate 6) and `spring.jpa.hibernate.use-new-id-generator-mappings` was removed from
  `application.properties`.
* `com.salesmanager.core.business.configuration.LegacyHiLoTableGenerator` extends the
  Hibernate 6 enhanced `TableGenerator` and translates every JPA `@TableGenerator` into the
  parameters of the table above: allocation `n > 1` becomes `optimizer=legacy-hilo`,
  `increment_size=n-1`, `initial_value=0`; allocation `1` becomes `optimizer=none`,
  `increment_size=1`, `initial_value=1`. The JPA annotations (and their never-effective
  `initialValue=2000`) are unchanged.
* `LegacyIdentifierGeneratorStrategyProvider` routes `GenerationType.TABLE`
  (`enhanced-table`) to that class through
  `hibernate.identifier_generator_strategy_provider`; `DataConfiguration` also sets
  `hibernate.id.generator.stored_last_used=false` and, as a safety net for any generator
  that bypasses the provider, `hibernate.id.optimizer.pooled.preferred=legacy-hilo`.
* `IdGeneratorParityIntegrationTest` (sm-shop) asserts every table generator in the
  metamodel uses these parameters and replays the Hibernate 5 capture above (same
  `SM_SEQUENCER` rows, same `source * 50 + lo` / `row value` arithmetic across new sessions).
