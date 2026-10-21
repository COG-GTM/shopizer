# Test baseline

## Running the baseline

CI (`.github/workflows/build.yml`) runs `./mvnw -B verify` on JDK 17 and JDK 21. The numbers below were recorded locally with the same goal:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw -B verify
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -B verify
```

Surefire XML and text reports are under each module's `target/surefire-reports/`. Module JaCoCo CSVs are under `sm-core/target/site/jacoco/jacoco.csv` and `sm-shop/target/site/jacoco/jacoco.csv`; the reactor aggregate is `sm-shop/target/site/jacoco-aggregate/jacoco.csv`. JaCoCo is reporting-only; there is no coverage gate.

Regenerate the Swagger 2 snapshot from the live `/v2/api-docs` endpoint with:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw -B -pl sm-shop -am test \
  -Dtest=ContractSnapshotTest \
  -Dcontract.update=true \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`contract.spec.path` can override the live specification path. Update mode writes `api-docs-v2.json` and `operations.json` under `sm-shop/src/test/resources/contract/`.

## Surefire totals

Counts are from Surefire XML after each full `verify`; `tests run` includes skipped tests. The same totals were produced on JDK 17 and JDK 21.

| Module | JDK 17 run | Failures | Errors | Skipped | JDK 21 run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `sm-core-model` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `sm-core-modules` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `sm-core` | 28 | 0 | 0 | 13 | 28 | 0 | 0 | 13 |
| `sm-shop-model` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `sm-shop` | 49 | 0 | 0 | 9 | 49 | 0 | 0 | 9 |

The three modules with zero totals produced no Surefire test-suite XML. The `sm-shop` skipped total includes the disabled known-failure test below.

## JaCoCo baseline

Percentages are covered lines or branches divided by covered plus missed, summed from CSV rows. Module-local CSVs were generated for `sm-core` and `sm-shop`; other modules have no module-local CSV.

| Module-local report | Line covered / total | Line % | Branch covered / total | Branch % |
| --- | ---: | ---: | ---: | ---: |
| `sm-core` | 1,587 / 9,907 | 16.02% | 304 / 3,010 | 10.10% |
| `sm-shop` | 3,287 / 13,836 | 23.76% | 737 / 4,402 | 16.74% |
| `sm-core-model`, `sm-core-modules`, `sm-shop-model` | n/a | n/a | n/a | n/a |

The aggregate CSV includes class coverage grouped by module, including `sm-shop` itself:

| Aggregate module group | Line covered / total | Line % | Branch covered / total | Branch % |
| --- | ---: | ---: | ---: | ---: |
| `sm-core` | 2,304 / 9,907 | 23.26% | 487 / 3,010 | 16.18% |
| `sm-core-model` | 1,782 / 4,037 | 44.14% | 41 / 156 | 26.28% |
| `sm-shop` | 3,287 / 13,836 | 23.76% | 737 / 4,402 | 16.74% |
| `sm-shop-model` | 1,134 / 2,899 | 39.12% | 2 / 64 | 3.12% |
| **Aggregate** | **8,507 / 30,679** | **27.73%** | **1,267 / 7,632** | **16.60%** |

`sm-core-modules` has no aggregate module group. Coverage figures are from the JDK 17 run.

## Contract inventory

The committed Swagger 2 inventory contains 309 operation records: 210 `admin-jwt`, 25 `customer-jwt`, and 74 `public`. Path-variable normalization produces 308 unique keys because two category routes normalize to the same `GET /api/v1/category/{}` key; that duplicate is present in both the committed inventory and packaged runtime specification.

## Smoke flows

| Flow | Test class |
| --- | --- |
| Admin login, private endpoint, and bad password | `AdminAuthSmokeTest` |
| Customer registration, login, and profile | `CustomerAuthSmokeTest` |
| Product create, read, update, and delete (read via `/api/v2/product/{sku}`) | `ProductCrudSmokeTest` |
| Cart create, add/update items, read, delete item, and reread | `CartSmokeTest` |
| Anonymous moneyorder checkout and admin order read | `CheckoutSmokeTest` |
| `/services/**` Basic-auth positive/negative checks and public store route | `ServicesBasicAuthSmokeTest` |
| Actuator health | `ActuatorSmokeTest` |
| Missing admin/customer tokens and customer token on private API | `AuthorizationSmokeTest` |
| Live operation snapshot comparison | `ContractSnapshotTest` |

## Known failures

| ID | Endpoint and expected/actual | Evidence | Test | Status |
| --- | --- | --- | --- | --- |
| KF-1 | `GET /api/v1/private/customers` with malformed Bearer token; expected 401, actual 500 | `MalformedJwtException` escapes `AuthenticationTokenFilter` (stack trace in the sm-shop test log when the test is enabled) | `AuthorizationSmokeTest.malformedBearerOnPrivateApiIsRejectedWith401` (`@Disabled`, `@Tag("known-failure")`) | To be filed separately (not filed in Jira by request) |

## Observed behaviours (not failures)

| Endpoint | Observed behaviour |
| --- | --- |
| `GET /api/v2/product/{sku}` after product deletion | Returns 400. |
| `GET /services/public/DEFAULT` without credentials | Returns 202 (legacy `Accepted` status). |

## Existing ignored tests

The legacy tests listed below were left untouched and are out of scope.

The following classes have a class-level `@Ignore` annotation:

- `sm-core/src/test/java/com/salesmanager/test/common/AbstractSalesManagerCoreTestCase.java`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentImagesTest.java`
- `sm-core/src/test/java/com/salesmanager/test/content/StaticContentTest.java`
- `sm-core/src/test/java/com/salesmanager/test/customer/CustomerTest.java`
- `sm-core/src/test/java/com/salesmanager/test/order/InvoiceTest.java`
- `sm-core/src/test/java/com/salesmanager/test/order/OrderTest.java`
- `sm-core/src/test/java/com/salesmanager/test/references/ReferencesTest.java`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingDistanceProcessorTest.java`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingMethodDecisionTest.java`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingQuoteByWeightTest.java`
- `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java`
- `sm-core/src/test/java/com/salesmanager/test/utils/SendEmailTest.java`
- `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/order/OrderApiIntegrationTest.java`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/search/SearchApiIntegrationTest.java`

The following individual methods have `@Ignore` or `@Disabled` annotations:

- `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java#listImages`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java#addFolder`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java#listFolders`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java#removeFolder`
- `sm-core/src/test/java/com/salesmanager/test/content/ContentImagesTest.java#createStoreLogo`
- `sm-core/src/test/java/com/salesmanager/test/order/InvoiceTest.java#createInvoice`
- `sm-core/src/test/java/com/salesmanager/test/references/ReferencesTest.java#testReferences`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingDistanceProcessorTest.java#testDistance`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingMethodDecisionTest.java#validateShippingMethod`
- `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingQuoteByWeightTest.java#testGetCustomShippingQuotesByWeight`
- `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java#importSpecificIntegrationModule`
- `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java#importNonExistingIntegrationModule`
- `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java#testCache`
- `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java#testCurrency`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#createProductReview`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#createOptionValue`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#createOption`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#getProducts`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#putProduct`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java#postProduct`
- `sm-shop/src/test/java/com/salesmanager/test/shop/integration/search/SearchApiIntegrationTest.java#searchItem`

The 13 skipped `sm-core` cases correspond to one class-level ignored test case for each of the 13 class-level ignored classes discovered by Surefire in that module. The `ImportIntegrationModule` class does not match Surefire's default test-class naming patterns, so it contributes no report or skipped count. The 8 pre-existing skipped `sm-shop` cases comprise one class-level ignored case each for `OrderApiIntegrationTest` and `SearchApiIntegrationTest`, plus the six ignored methods in `ProductManagementAPIIntegrationTest`. Method-level annotations in classes already ignored at class level do not add further skipped cases. KF-1 is the only new disabled smoke case.

`ImportIntegrationModule.java` is the test-like class not run: its filename does not match Surefire's default `Test*`, `*Test`, `*Tests`, or `*TestCase` patterns. No repository-specific Surefire include/exclude configuration was found. `CartTestBean.java` and `ServicesTestSupport.java` are test helper/support source files, not test classes.

Re-enabling ignored tests is out of scope for this change.
