# Upgrade test baselines

The AB-532 measurements below are historical. The current behaviour and coverage
measurement is recorded in the AB-533 section at the end of this document.

## Build and resolution

Maven Wrapper 3.3.2 (only-script), Apache Maven 3.9.9; OpenJDK 17 and 21.
Both complete clean reactors succeeded. Java source/target remains 11.

Canonical Maven Central returned HTTP 429 in this sandbox, including wrapper
bootstrap. Local verification therefore used the public Google Maven Central
replica exclusively, with a fresh local repository for JDK 17, reused on JDK 21.
No mirror configuration is committed; the wrapper and GitHub Actions use canonical
Maven Central. Canonical endpoint verification remains for GitHub Actions.

The external user settings file contained only:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors>
    <mirror>
      <id>google-central</id>
      <mirrorOf>*</mirrorOf>
      <url>https://maven-central.storage-download.googleapis.com/maven2/</url>
    </mirror>
  </mirrors>
</settings>
```

The external global settings file contained only
`<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>`, excluding ambient
global/user mirrors and credentials. Commands, run from the repository root:

```sh
# /home/ubuntu/m2-empty-ab532-google did not exist before this first build.
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2 \
./mvnw -B \
  -s /home/ubuntu/ab532-evidence/central-mirror-settings.xml \
  -gs /home/ubuntu/ab532-evidence/empty-settings.xml \
  clean verify -Dmaven.repo.local=/home/ubuntu/m2-empty-ab532-google

JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2 \
./mvnw -B \
  -s /home/ubuntu/ab532-evidence/central-mirror-settings.xml \
  -gs /home/ubuntu/ab532-evidence/empty-settings.xml \
  clean verify -Dmaven.repo.local=/home/ubuntu/m2-empty-ab532-google
```

JBoss is not needed: the complete fresh-repository build resolved dependencies
and plugins with every repository mirrored to the Central replica. Spring and
Sonatype snapshot repositories are likewise unnecessary. The Square autoconfigure
dependency is managed at release 1.0.0 rather than the starter's unavailable
1.0.0-SNAPSHOT.

## Surefire results

Counts below sum the `tests`, `skipped`, `failures`, and `errors` attributes
of actual `TEST-*.xml` files under each module's `target/surefire-reports`.
JDK 17 reports were copied outside the repository before the JDK 21 clean build;
both runs produced identical counts. "Tests run" includes skipped cases, as in
Surefire output; 40 cases executed. No Failsafe reports were produced.

| Module | JDK | Tests run | Skipped | Failures | Errors |
| --- | --- | ---: | ---: | ---: | ---: |
| shopizer (aggregator) | 17 / 21 | 0 | 0 | 0 | 0 |
| sm-core-model | 17 / 21 | 0 | 0 | 0 | 0 |
| sm-core-modules | 17 / 21 | 0 | 0 | 0 | 0 |
| sm-core | 17 / 21 | 28 | 13 | 0 | 0 |
| sm-shop-model | 17 / 21 | 0 | 0 | 0 | 0 |
| sm-shop | 17 / 21 | 33 | 8 | 0 | 0 |
| **Total per JDK** | **17 / 21** | **61** | **21** | **0** | **0** |

Modules with zero counts have no Surefire XML reports. All six reactor entries
reported SUCCESS on both JDKs. Ignored classes may be reported as one skipped
case rather than one per method; the inventory below is not a skipped-test count.

To reproduce the XML aggregation after either build:

```sh
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

for module in ("sm-core-model", "sm-core-modules", "sm-core", "sm-shop-model", "sm-shop"):
    totals = dict.fromkeys(("tests", "skipped", "failures", "errors"), 0)
    for path in (Path(module) / "target/surefire-reports").glob("TEST-*.xml"):
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
    print(module, totals)
PY
```

## Explicit ignore/disable inventory

Source search used:

```sh
rg -n -A6 '@(Ignore|Disabled)\b' --glob '*.java' --glob '!**/target/**' .
```

There are **37 explicit @Ignore annotations: 16 classes and 21 methods**.
No `@Disabled` annotations were found anywhere in repository Java sources.
Each row names the declaration directly annotated, including abstract support
classes and methods without `@Test`; ignored classes' unannotated methods are
not additional explicit annotations. No ignored tests were re-enabled.

| File and annotation line | Annotation | Annotated declaration |
| --- | --- | --- |
| `sm-core/src/test/java/com/salesmanager/test/common/AbstractSalesManagerCoreTestCase.java:56` | `@Ignore` | `class AbstractSalesManagerCoreTestCase` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java:19` | `@Ignore` | `class ContentFolderTest` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java:26` | `@Ignore` | `public void listImages()` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java:30` | `@Ignore` | `public void addFolder()` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java:54` | `@Ignore` | `public void listFolders()` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentFolderTest.java:59` | `@Ignore` | `public void removeFolder()` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentImagesTest.java:30` | `@Ignore` | `class ContentImagesTest` |
| `sm-core/src/test/java/com/salesmanager/test/content/ContentImagesTest.java:37` | `@Ignore` | `public void createStoreLogo()` |
| `sm-core/src/test/java/com/salesmanager/test/content/StaticContentTest.java:35` | `@Ignore` | `class StaticContentTest` |
| `sm-core/src/test/java/com/salesmanager/test/customer/CustomerTest.java:29` | `@Ignore` | `class CustomerTest` |
| `sm-core/src/test/java/com/salesmanager/test/order/InvoiceTest.java:11` | `@Ignore` | `class InvoiceTest` |
| `sm-core/src/test/java/com/salesmanager/test/order/InvoiceTest.java:18` | `@Ignore` | `public void createInvoice()` |
| `sm-core/src/test/java/com/salesmanager/test/order/OrderTest.java:53` | `@Ignore` | `class OrderTest` |
| `sm-core/src/test/java/com/salesmanager/test/references/ReferencesTest.java:22` | `@Ignore` | `class ReferencesTest` |
| `sm-core/src/test/java/com/salesmanager/test/references/ReferencesTest.java:34` | `@Ignore` | `public void testReferences()` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingDistanceProcessorTest.java:14` | `@Ignore` | `class ShippingDistanceProcessorTest` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingDistanceProcessorTest.java:20` | `@Ignore` | `public void testDistance()` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingMethodDecisionTest.java:22` | `@Ignore` | `class ShippingMethodDecisionTest` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingMethodDecisionTest.java:29` | `@Ignore` | `public void validateShippingMethod()` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingQuoteByWeightTest.java:44` | `@Ignore` | `class ShippingQuoteByWeightTest` |
| `sm-core/src/test/java/com/salesmanager/test/shipping/ShippingQuoteByWeightTest.java:58` | `@Ignore` | `public void testGetCustomShippingQuotesByWeight()` |
| `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java:29` | `@Ignore` | `class ImportIntegrationModule` |
| `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java:44` | `@Ignore` | `public void importSpecificIntegrationModule()` |
| `sm-core/src/test/java/com/salesmanager/test/utils/ImportIntegrationModule.java:88` | `@Ignore` | `public void importNonExistingIntegrationModule()` |
| `sm-core/src/test/java/com/salesmanager/test/utils/SendEmailTest.java:23` | `@Ignore` | `class SendEmailTest` |
| `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java:30` | `@Ignore` | `class UtilsTestCase` |
| `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java:52` | `@Ignore` | `public void testCache()` |
| `sm-core/src/test/java/com/salesmanager/test/utils/UtilsTestCase.java:71` | `@Ignore` | `public void testCurrency()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/order/OrderApiIntegrationTest.java:11` | `@Ignore` | `class OrderApiIntegrationTest` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:115` | `@Ignore` | `public void createProductReview()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:147` | `@Ignore` | `public void createOptionValue()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:193` | `@Ignore` | `public void createOption()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:235` | `@Ignore` | `public void getProducts()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:253` | `@Ignore` | `public void putProduct()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/product/ProductManagementAPIIntegrationTest.java:262` | `@Ignore` | `public void postProduct()` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/search/SearchApiIntegrationTest.java:27` | `@Ignore` | `class SearchApiIntegrationTest` |
| `sm-shop/src/test/java/com/salesmanager/test/shop/integration/search/SearchApiIntegrationTest.java:42` | `@Ignore` | `public void searchItem()` |

## AB-533 behaviour and coverage baseline

This is the pre-cutover Spring Boot **2.5.12** baseline for
[AB-533](https://cog-gtm.atlassian.net/browse/AB-533), under
[AB-531](https://cog-gtm.atlassian.net/browse/AB-531). Production Java, application
configuration, framework version, and Java language target remain unchanged.

### Reproduce

Run from the repository root with system Maven:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B clean install
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify
```

Both commands passed on 2026-11-11; `install` includes `verify`. Existing GitHub
Actions runs `./mvnw -B verify` on JDK 17 and 21. Clean builds avoid stale bytecode
and appended JaCoCo data. Tests seed an in-memory H2 database from the existing
test resources; no external database, SMTP server, or payment service is needed.

Key output on both JDKs:

```text
sm-core: Tests run: 21, Failures: 0, Errors: 0, Skipped: 6
sm-shop: Tests run: 44, Failures: 0, Errors: 0, Skipped: 7
BUILD SUCCESS
```

Surefire XML totals: **65 reported, 52 executed, 13 skipped, 0 failures, 0 errors**.
New JUnit 5 tests comprise eight enabled HTTP smoke tests, one live contract test,
three inventory tests, and one disabled defect characterization. Existing ignored
tests remain disabled after the prerequisite AB-537 JUnit 5 migration; removal
of Vintage changes reported/skipped counts compared with AB-532, not the number
of enabled tests. See [known-failures.md](known-failures.md) for the observed
deleted-product defect and its disabled test; this ticket does not fix it.

### Coverage measurement

JaCoCo **0.8.12** attaches to each module's test JVM. The reporting-only `coverage`
POM runs last and aggregates all five source modules and both execution files.
It includes coverage from HTTP calls across module boundaries, even for modules
without their own tests. The application gains no dependencies from this POM.

Open `coverage/target/site/jacoco-aggregate/index.html`; XML/CSV counters are in the
same directory. Local `target/site/jacoco/` reports in test-bearing modules omit
execution from other modules. This table uses the aggregate counters and
covered / (covered + missed). JDK 17 and 21 produced identical line/branch counters.

| Module | Covered / total lines | Line coverage | Covered / total branches | Branch coverage |
| --- | ---: | ---: | ---: | ---: |
| sm-core-model | 1,768 / 4,037 | 43.79% | 39 / 156 | 25.00% |
| sm-core-modules | 0 / 107 | 0.00% | 0 / 20 | 0.00% |
| sm-core | 2,316 / 9,904 | 23.38% | 491 / 3,010 | 16.31% |
| sm-shop-model | 1,182 / 2,899 | 40.77% | 2 / 64 | 3.12% |
| sm-shop | 3,336 / 13,832 | 24.12% | 754 / 4,402 | 17.13% |
| **Total** | **8,602 / 30,779** | **27.95%** | **1,286 / 7,652** | **16.81%** |

Parent/reporting POMs have no executable source. Zero coverage for
`sm-core-modules` is measured, not omitted. This is an observational baseline,
not a new minimum-coverage gate or a claim that every behaviour is covered.

### API contract snapshot

`sm-shop/src/test/resources/contract/swagger-v2.json` is the complete live
`GET /v2/api-docs` response, including definitions and security declarations.
`operations.json` beside it contains **309 operations**, sorted by exact method
and path, with path/query/header/body parameters and required flags,
request/response media types, and security requirements.

`ApiContractTest` writes live review artifacts under `sm-shop/target/contract/`
and fails on a removed/renamed baseline method + path. Additions are allowed;
metadata changes are logged for review, not automatically judged for compatibility.
Exact path variables distinguish `/category/{id}` from `/category/{friendlyUrl}`.
Inventory tests remove every baseline operation in turn, change a path/method,
and check that an added operation is allowed.

The normalizer accepts Swagger 2 and OpenAPI 3 (including local references);
an equivalent-document test guards the AB-536 transition. The live test falls
back to `/v3/api-docs` after a 404 from `/v2/api-docs`. Raw capture-time host/port
is excluded from the normalized inventory. Empty published security requirements
do not imply anonymous access: real HTTP tests verify enforcement below.

To deliberately refresh after an approved API change:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B -pl sm-shop -am \
  -Dtest=ApiContractTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcontract.capture=true test
cp sm-shop/target/contract/api-docs.json sm-shop/src/test/resources/contract/swagger-v2.json
cp sm-shop/target/contract/operations.json sm-shop/src/test/resources/contract/operations.json
```

Review both diffs, particularly removals/security changes, and run the full
verification without `contract.capture`. Never enable capture in CI.

### HTTP smoke suite

`BehaviourSmokeTest` uses JUnit 5, `@SpringBootTest(RANDOM_PORT)`, `TestRestTemplate`,
real security filters, and H2 persistence. It verifies:

1. Admin login yields a JWT accepted by a private endpoint.
2. Customer registration/login yields a customer JWT; the authenticated profile
   returns the registered ID/email.
3. Missing tokens and customer JWTs cannot access admin private endpoints (401/403).
4. Private product create/read/update/delete persists changes and removes the SKU.
5. Cart create/add/update/remove persists the expected quantities when fetched again.
6. Built-in offline `moneyorder` checkout creates an order readable by the admin.
7. `/services/private/system/optin` accepts valid Basic Auth and returns 401 for
   missing/wrong credentials, with a JSON `Accept` header.
8. `/actuator/health` reports `UP`.

Only outbound SMTP transport is replaced during checkout. The test waits for both
real email-composition calls and restores the transport in `finally`. Its Spring
context matches the existing integration suite because Ehcache 2's named manager
is JVM-global: a second context fails initialization. No production cache/email
configuration changes are needed. As with existing tests, Infinispan may update
`sm-shop/files/store/DownlaodRepository.dat`; do not commit generated store changes.
