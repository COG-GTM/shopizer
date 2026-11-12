# Drools 10 / Jakarta (AB-545)

Drools and KIE now use the Apache KIE `org.drools:drools-bom:10.2.0`
(the newest stable 10.x release on Maven Central at upgrade time). The engine,
MVEL dialect, XML support and decision tables are aligned by that BOM. The old
MVEL override is removed; the BOM supplies the JDK 21-compatible version.
The explicit Jsoup dependency remains because application code uses it.

`DroolsBeanFactory` builds `DrlProject` explicitly, retaining classic DRL/MVEL
semantics instead of switching existing rules to the executable model. Containers
use the built module's release ID and compilation errors fail immediately. Shipping
decision, distance pricing and promo callers retain their existing session API.
No DRL dialect edits or rule logic changes are necessary.

Verify with JDK 21 and system Maven: `mvn -B install`. The unchanged
`ShippingMethodDecisionTest` and `DroolsRuleParityTest` cover shipping selection,
distance boundaries and promo validity. Inspect transitive dependencies with
`mvn -B dependency:tree -Dincludes=org.kie,org.drools,org.mvel`.

Three storefront imports of the removed Drools `StringUtils` utility now use
the existing Commons Lang 3 dependency; their null/empty checks are unchanged.

## Verification

JDK 21 `mvn -B install` passed across all seven reactor modules, including the
unchanged shipping decision (3 cases) and DRL parity (23 cases) tests. Resolved
Drools/KIE artifacts are all 10.2.0, with MVEL 2.5.2.Final supplied by the BOM.
No Drools/KIE dependency branch introduces javax artifacts. Unrelated existing
javax dependencies (JCache, Infinispan, Google storage and payment SDKs) remain
outside this upgrade's scope.

The packaged Boot 3 application started on port 8081 under JDK 21 using
`org.springframework.boot.loader.launch.PropertiesLauncher`, external database
configuration, and a copy of `SALESMANAGER.mv.db` plus `files` in
`~/shopizer-runtime` (the epic branch uses the newer H2 file format). Product
listing returned HTTP 200 with four products; adding `table1` to a cart returned
201. Applying `Test1234` returned 201 and logged successful Drools KieBase
creation; this coupon is expired, so no discount was expected. A shipping quote
attempt returned 503 because the quote was null in `OrderShippingApi`; shipping
endpoint success is not verified. The unchanged shipping rule tests passed.
The known packaged default-image warning was also present at startup.
