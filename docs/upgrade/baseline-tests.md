# Test baseline

## JDK 17 clean-room run

- JDK: OpenJDK 17.0.20.1
- Maven: Apache Maven 3.9.16, bootstrapped by Maven Wrapper 3.3.4
- Result: `BUILD SUCCESS`
- Command:

  ```sh
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
  MAVEN_USER_HOME=/tmp/ab532-wrapper \
  MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2 \
  ./mvnw -s /home/ubuntu/ab532-logs/maven-settings.xml -B -ntp verify \
    -Dmaven.repo.local=/tmp/ab532-m2-17
  ```

`repo.maven.apache.org` returned HTTP 429 during the Central availability check. The run therefore used a local-only Maven settings file at `/home/ubuntu/ab532-logs/maven-settings.xml`, mapping `central` to `https://maven-central.storage-download.googleapis.com/maven2`; `MVNW_REPOURL` routed the wrapper's Maven distribution download to the same mirror.

| Module | Tests run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `sm-core` | 28 | 0 | 0 | 13 |
| `sm-shop` | 33 | 0 | 0 | 8 |
| **Total** | **61** | **0** | **0** | **21** |

The module-level Maven `Tests run` summaries agree with the Surefire XML totals:

```text
[WARNING] Tests run: 28, Failures: 0, Errors: 0, Skipped: 13
[WARNING] Tests run: 33, Failures: 0, Errors: 0, Skipped: 8
```

## Ignored and disabled tests

The following classes have a class-level `@Ignore` or `@Disabled` annotation:

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

The 13 skipped `sm-core` cases correspond to one class-level ignored test case for each of the 13 class-level ignored classes discovered by Surefire in that module. The `ImportIntegrationModule` class does not match Surefire's default test-class naming patterns, so it contributes no report or skipped count. The 8 skipped `sm-shop` cases comprise one class-level ignored case each for `OrderApiIntegrationTest` and `SearchApiIntegrationTest`, plus the six ignored methods in `ProductManagementAPIIntegrationTest`. Method-level annotations in classes already ignored at class level do not add further skipped cases. There are no `@Disabled` annotations.

`ImportIntegrationModule.java` is the test-like class not run: its filename does not match Surefire's default `Test*`, `*Test`, `*Tests`, or `*TestCase` patterns. No repository-specific Surefire include/exclude configuration was found. `CartTestBean.java` and `ServicesTestSupport.java` are test helper/support source files, not test classes.

Re-enabling ignored tests is out of scope for this change.
