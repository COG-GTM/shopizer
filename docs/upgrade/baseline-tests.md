# AB-532 baseline tests

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
