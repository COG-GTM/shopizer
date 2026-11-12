# Hibernate 6 readiness on Hibernate 5 (AB-541)

This preparation retains Spring Boot 2, Hibernate 5 and `javax.persistence`.
It does not change REST/JWT payloads, custom Hibernate types or ID generator settings.

- `Description` is a mapped superclass, not an entity inheritance root. Its concrete
  description entities do not have entity subclasses requiring table-per-class inheritance.
- `USERCONNECTION` and its existing uniqueness constraint are declared on the concrete
  `UserConnection` entity, rather than its mapped superclass.
- The customer/group, user/group and product/category associations each own their
  join table. Their columns are writable consistently. The similarly named entity
  primary keys belong to different tables, not duplicate mappings of those columns;
  making the associations read-only would lose relationship inserts.
- Native product SKU lookups accept any JDBC `Number`, not only `BigInteger`.
- `SalesManagerEntityServiceImpl` normalizes a boxed `Long` zero ID to `null` before
  single and bulk saves. Subclass paths that save directly through a repository call
  the same guard. Positive, null, Integer and composite IDs retain their semantics.
  Entity ID accessors use boxed types; no primitive Long ID requires special handling.
- The `OrderFacadeImpl.refreshOrder` customer-ID reset is intentionally retained:
  it clears a potentially previously assigned customer ID when switching to guest
  checkout. Normalizing zero at persistence does not replace that reset operation.
  The API model's default zero ID is retained for compatibility.

Regression coverage: `EntityIdReadinessTest` exercises normalization and native result
types, including empty/error paths. `ZeroIdEntityCreationIntegrationTest` creates
products, customers, orders and categories through services against the existing H2
test context, asserting new rows and generated IDs. These tests run on Hibernate 5;
they are not a claim that a Hibernate 6 upgrade has already been validated.

Run from the repository root:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B install
```

## Known pre-existing query quirks (preserved)

- Variant graph queries fetch `pvpov.descriptions` again while traversing the
  variation-value branch, rather than fetching `pvvpov.descriptions`. The duplicate
  fetch is removed without redirecting it to the other branch. In
  `ProductVariantRepository.findBySku`, the second language predicate continues to
  constrain the original branch via the surviving `pvpovd` alias; it does not add a
  new language requirement on the variation-value option's value.
- `MerchantRepository.findByCode` concatenates `mp` and `left` into the alias
  `mpleft`. Its country join is therefore an inner join in the original query.
  The rewritten query gives the parent an explicit alias and retains that inner
  country join instead of silently changing it to a left join.
- SKU predicates in availability, price and native product queries use
  `productSku = sku OR variantSku = sku AND store = store`. Their existing operator
  precedence is retained, not changed to `(productSku = sku OR variantSku = sku)
  AND store = store`.
- `ProductRepositoryImpl.getByFriendlyUrl` indexes the first result even when
  the result list is empty. The existing `IndexOutOfBoundsException` is preserved
  and tested rather than changing the repository's empty-result contract here.
- `PageableProductVariantRepositoty.findByProductId` has an entity-selecting
  explicit count query. That unchanged count query is syntactically valid HQL;
  repairing its pagination semantics is outside this no-behavior-change step.
  The graph parity test uses a page larger than the fixture result set.

## Pre-existing broken branches repaired

Each original branch was first executed on Hibernate 5/H2. These branches failed
there too; the regression tests assert the original failure and explicit IDs
from the repaired method, rather than claiming executable-result parity:

| Repository / method | Original failure | Minimal repair |
| --- | --- | --- |
| `CustomerRepositoryImpl.listByStore`, first-name count and result queries | `c..billing.firstName`: `QuerySyntaxException` | Remove the extra dot in the shared predicate. |
| `ProductRepositoryImpl.getProductsListForLocale`, result query | Missing `:` in `p.merchantStore.id=mId`; binding `mId` fails | Use `:mId`; keep the original joins, filters and ordering. |
| `CategoryRepositoryImpl.listByStoreAndParent`, null parent with/without store | `IsNull`: `QuerySyntaxException`; unconditional parent dereference also cannot handle null | Use `is null`; bind `cid` only when a parent exists. |
| `CategoryRepositoryImpl.listByStoreAndParent`, store and parent supplied | Query declares `:cId` but binds `cid` | Use the existing binding's spelling, `:cid`. |

The no-store/non-null-parent category branch executes on Hibernate 5 and has
ordinary result parity coverage. The locale-product count's original
`p.available=1` also executes on Hibernate 5: its replacement with the boolean
literal `true` is a parity case, not a pre-existing-broken exception.

## HQL audit and changed-query inventory

The repository-wide inventory is 228 active `@Query` annotations (225 HQL, three
native) and 42 `createQuery` dispatch sites in nine custom implementation classes.
The audit includes explicit count queries, nested queries and conditional HQL
builders, not commented-out query alternatives. Native SQL remains unchanged.
The following is the complete list of changed methods/statements; other audited
queries did not need a syntax/alias change.

| Repository | Changed queries | Rewrite |
| --- | --- | --- |
| `CategoryRepositoryImpl` | `countProductsByCategories`, `listByProduct` | Replace entity/attribute-name aliases `product`/`category` with `productRef`/`categoryRef`. |
| `CategoryRepositoryImpl` | `listByStoreAndParent` (three broken branches) | Syntax/binding/null-guard repairs listed above; retain `sortOrder asc`. |
| `ProductRepositoryImpl` | `getByCode(String, Language)`, `getByCode(String, MerchantStore)`, `getByFriendlyUrl`, `getProductForLocale`, both `getProductsListByCategories` overloads, `listByStore(MerchantStore)`, `listByTaxClass` | Replace attribute-name aliases `images`/`type` with `productImages`/`productTypeRef` (the store-specific SKU query has only `type`). |
| `ProductRepositoryImpl` | `getProductsListForLocale` count and result | Boolean literal in count; missing named-parameter colon in result; rename result's `images`/`type` aliases. |
| `ProductRepositoryImpl` | `listByStore(MerchantStore, Language, ProductCriteria)` | Rename `images`/`type`; deduplicate variation-value description fetch in shop-origin branch without redirecting its path. |
| `ProductRepositoryImpl` | `productQueryV1`, `productQueryV2`, `getById(Long, MerchantStore, Language)` | Rename colliding aliases; V2 and full-ID graph additionally remove duplicate `p.type` and `pvpov.descriptions` fetches. V2 also serves the ordinary ID lookups through `get`. |
| `ProductAvailabilityRepository` | Both `getById` overloads | Remove repeated `p.merchantStore` fetch using the same `pm` alias. |
| `PageableProductAvailabilityRepository` | `listByStore(Long, Integer, String, Pageable)` explicit count only | Remove repeated merchant-store join/alias; keep existing count multiplicity and inner joins. |
| `ManufacturerRepository` | `findByCategoriesAndLanguage`, `findByProductInCategoryId` | Rename entity/attribute-name alias `manufacturer` to `manufacturerRef`. |
| `ProductRelationshipRepositoryImpl` | `getByType(MerchantStore, String, Product)`, `getByGroup` | Rename `type` on the related-product fetch. |
| `ProductReviewRepository` | Both `findByProduct` overloads, `findByProductAndCustomer` | Remove repeated product, merchant-store and description fetches/aliases. |
| `ProductVariantRepository` | `findOne`, `findByIds`, `findById(Long, Long, Integer)`, `findBySku`, `findByProductId` | Remove repeated `pvpov.descriptions` fetch; keep `findBySku`'s original language-filter path via the surviving alias. |
| `PageableProductVariantRepositoty` | `findByProductId` value query only | Remove repeated `pvpov.descriptions` fetch. |
| `CustomerRepository` | Both store-specific `findByNick` overloads, `findByResetPasswordToken` | Ordinary joins on embedded delivery/billing; retain nested country/zone fetches and aliases. |
| `CustomerRepositoryImpl` | `listByStore` first-name count/result branch | Double-dot repair listed above. |
| `MerchantRepository` | `findByCode`, `getById`, `getByParent` | Give currency its own `mcur` alias instead of reusing country's `mc`; preserve `findByCode`'s effective inner country join when separating the concatenated alias. |
| `MerchantRepositoryImpl` | `listByCriteria` result query | Give currency a unique `mcur` alias. |
| `PageableMerchantRepository` | `listByStore`, `listAll`, `listAllRetailers` value queries | Give currency a unique `mcur` alias. |
| `OrderRepository` | `findOne(Long, Integer)` | Ordinary joins on embedded delivery/billing, retaining nested association fetches. |
| `PermissionRepository` | `findByGroups` | Rename attribute-name alias `groups` to `permissionGroups`. |

`HqlRepositoryParityIntegrationTest` uses the existing seeded H2 application
context plus transactional fixtures for parent/child stores/categories, products,
availability/prices, manufacturer, attributes, variations, customer/review, order
lines/totals and permission/group membership. Original executable HQL is retained
in `hibernate5-original-hql.properties`, captured from step-1 commit `6981b8b0`.
It invokes each changed repository query, compares IDs with the original on the
same fixtures, and exercises non-empty/empty cases. Ordered queries compare lists;
unordered queries compare ID sets. The differing languages on the two variation
values guard against silently correcting the preserved wrong-path semantics.
Private graph builders are additionally executed directly for parity. The test
also compiles every active annotated HQL and explicit count query on Hibernate 5
and checks alias uniqueness/collisions and repeated fetch paths.

These are Hibernate 5 readiness checks, not a Hibernate 6 runtime certification.
The mandatory whole-reactor checks are:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B install
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B install
```
