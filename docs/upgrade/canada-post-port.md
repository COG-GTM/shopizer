# AB-543: Canada Post Jakarta-ready port

## Decision: option (a)

Own the small rating integration in `sm-core-modules` instead of depending on
`com.shopizer:shipping-canadapost-spring-boot-starter:2.17.0`. Keep the existing
`com.shopizer.modules.shipping.canadapost` package and `canadapost` and
`moduleConfig` bean names so the shipping map and consumers need no changes.

The original autoconfigure JAR was recovered with CFR 0.152 (no source JAR was
available). Its SHA-256 is
`147c13019fece7df44349bc28f6987ae8e3421474688793d99d2bcf0f8e8780c`.
No decompiler, binaries, or Jersey sources are included in the repository.

### What moved

- `com/shopizer/modules/shipping/canadapost/config/CanadaPostQuoteModuleConfig`:
  production and development endpoints.
- `.../module/CanadaPostQuoteModule`: validation, package conversion, request
  construction, service selection, quote aggregation and error translation.
- `.../autoconfigure/CanadaPostAutoConfiguration`: Spring configuration,
  registered through both Boot 2's `META-INF/spring.factories` and Boot 3's
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Only the consumed portions of `MailingScenario`, `PriceQuotes`, and
  `Messages`, including their nested types, under
  `ca.canadapost.cpcdp.rating.generated`. These are hand-ported Jakarta JAXB
  models; unused discovery/payment/tax/link types are not copied. Original
  namespaces, element names, and normalized-string adapters are retained.
  Unconsumed response fields, including service-standard dates, are ignored.

Endpoint keys remain `module.shipping.canadapost.prodUrl` and
`module.shipping.canadapost.devUrl`, with defaults
`https://soa-gw.canadapost.ca/rs/ship/price` and
`https://ct.soa-gw.canadapost.ca/rs/ship/price`. The supplied 2.17.0 bytecode
actually has literal field defaults without injection annotations, despite
shipping a properties file. The port explicitly binds those documented keys
as requested; a context test covers defaults and overrides.

HTTP now uses Spring `RestTemplate`, sending bytes explicitly so neither
Spring 5's javax JAXB converter nor Spring 6's Jakarta converter is involved.
Basic authorization, POST URL, vendor Accept/Content-Type and `en-CA`
Accept-Language match the original. Neither implementation configures a
timeout; the default blocking URL-connection behavior is retained.
HTTP error statuses are not rejected before parsing: Canada Post's
`messages` body is translated through the same IntegrationException path.
Transport failures remain unchecked; transport-library exception classes
necessarily change from Jersey to Spring.

### Deliberately retained behavior

The reference module rejects all destinations except CA/US before contacting
the rating server. Its international-building branch is unreachable: the GB
fixture therefore has a golden rejection and **zero requests**, not a new
international shipping feature. Blank destination postcodes return null;
empty package lists return an empty list.

The original captures weight before converting the package in place, rounds
converted dimensions upward, uses base for the first package unless shipping
tax is enabled, and adds due for subsequent packages regardless of that flag.
It does not populate delivery dates or estimated days, even when the response
contains them. Validation reports only the last missing credential field.
These quirks are preserved, not corrected in this port. API message errors
are wrapped in another IntegrationException with code 99; the message code
survives on the cause, not the outer exception.

## Dependencies and the JAXB/Hibernate finding

The starter and unused `shopizer-canadapost.version` property are removed.
Root properties now manage Jakarta XML Bind API 4.0.2 and GlassFish JAXB 4.0.5.
Jakarta Activation API is managed separately at 2.1.3: Boot 2.5 otherwise
selects the javax-package 1.2.2 API, causing
`NoClassDefFoundError: jakarta/activation/DataSource` in the port.
The separate API management intentionally does not change Boot's unrelated
`com.sun.activation:jakarta.activation` version.

**A JAXB-4-only override is not safe on this Hibernate version.** A full
JDK 17 reactor install with only Jakarta JAXB failed six sm-core application
context tests (28 tests, 6 errors, 13 skipped), with:

```text
Caused by: java.lang.NoClassDefFoundError: javax/xml/bind/JAXBException
    at org.hibernate.boot.spi.XmlMappingBinderAccess.<init>(XmlMappingBinderAccess.java:43)
    at org.hibernate.boot.MetadataSources.getXmlMappingBinderAccess(MetadataSources.java:115)
    at org.hibernate.boot.model.process.spi.MetadataBuildingProcess.prepare(MetadataBuildingProcess.java:110)
```

This is not hypothetical XML-mapping usage. Hibernate 5.4.33 constructs the
binder during normal annotation-only persistence bootstrap. Inspection of
the installed Hibernate bytecode shows that its MappingBinder still links
javax JAXB types at construction, even though JAXB context creation for
hbm.xml is lazy. Searching the application finds no `orm.xml` or
`*.hbm.xml`; `DataConfiguration.entityManagerFactory()` uses
`setPackagesToScan("com.salesmanager.core.model")`.

The least-invasive working compatibility measure is an explicit
`javax.xml.bind:jaxb-api:2.3.1` dependency **in sm-core, for Hibernate**, while
the Canada Post code uses only Jakarta JAXB 4. No javax JAXB implementation
or Jersey/JAX-RS dependency is retained. Both APIs can coexist because their
packages differ; the single GlassFish runtime is 4.0.5.
Full persistence tests and real seeded-database startup pass with this API
bridge. This is a scoped solution for this repository's annotation-only
mappings, **not support for Hibernate 5 XML mappings with JAXB 4**. Adding XML
mappings before Hibernate is upgraded requires revisiting this decision.

The final shipping application dependency tree, filtered as requested:

```text
[INFO] |  |  \- org.glassfish.jaxb:jaxb-runtime:jar:4.0.5:compile
[INFO] |  |     \- org.glassfish.jaxb:jaxb-core:jar:4.0.5:compile
[INFO] |  |        +- org.glassfish.jaxb:txw2:jar:4.0.5:compile
[INFO] |  +- javax.xml.bind:jaxb-api:jar:2.3.1:compile
[INFO] |  +- jakarta.xml.bind:jakarta.xml.bind-api:jar:4.0.2:compile
```

Thus the requested complete absence of javax XML Bind is not achieved:
Hibernate still requires its API. No `javax.ws.rs`, `com.sun.jersey`, or
external Canada Post starter remains.

### Spring MVC/content negotiation

Spring 5.3.18's WebMvcConfigurationSupport detects
`javax.xml.bind.Binder` before adding Jaxb2RootElementHttpMessageConverter in
its default converter list. The compatibility API keeps that detection as
before, but cannot supply a javax JAXB implementation. Repository source
search finds no javax `@XmlRootElement`/`@XmlType` REST DTOs.
ShopApplicationConfiguration.configureMessageConverters explicitly adds
MappingJackson2HttpMessageConverter; Spring's defaults are only added if
the configured list is empty. Existing API tests and an actual
`GET /api/v1/products?store=DEFAULT` confirm JSON operation; an explicit
`Accept: application/xml` gets HTTP 406, not a changed XML representation.
The Canada Post JAXB types are private integration payloads, not REST DTOs.

## Contract evidence and verification

`CanadaPostContractTest` uses a JDK local HTTP server, without adding test
dependencies. Handcrafted Rating API v4 XML examples are replayed for
domestic, US, international and error responses, plus empty and malformed
responses. This is **not a live Canada Post credential/integration test**.

Before removing the starter, the original module was run against that server
and its ShippingOption JSON and request contracts captured. Further cases
were captured with the original autoconfigure JAR explicitly first on the
classpath. The committed goldens are reference outputs, not expected values
derived from the new implementation. See the fixture README for reproduction.
Twelve scenarios cover domestic/US, international rejection, error status,
empty/malformed responses, imperial conversion, tax, multiple packages,
PROD routing, blank postcode, and empty packages.
All ShippingOption properties (including null delivery estimates) are compared.
Requests compare method/path, authorization, media types, language and a
namespace-aware XML tree, ignoring only XML declaration and namespace-prefix
spelling. Separate tests cover module validation and bean configuration.

Verified on JDK 17 with `mvn -B install` and JDK 21 with `mvn -B verify`:
all six reactor modules succeed; tests total 64, with 21 existing skips.
The Canada Post test class has 3 tests (12 replay scenarios within its
contract test), all passing. Existing full-suite tests rewrite
`sm-shop/files/store/DownlaodRepository.dat`; their generated binary diff
was reversed after verification, leaving tracked seed data unchanged.

Runtime smoke testing uses a copy of the seed H2 database and files under
`~/shopizer-runtime`, never the tracked database:

- Startup logs identify the ported `canadapost` bean and report application started.
- Products GET: HTTP 200, 4 products.
- Admin login POST: HTTP 200, a token returned.
- Authenticated shipping modules GET: HTTP 200, includes `canadapost`.
- Products GET with XML Accept: HTTP 406.

## AB-544 / Boot 3 follow-up

No Canada Post module source changes should be needed for the Jakarta
cut-over. Keep the AutoConfiguration.imports registration. On upgrading
Hibernate/Spring Boot, remove sm-core's temporary javax JAXB API bridge and
recheck the runtime tree and content negotiation. RestTemplate-to-RestClient
is optional in AB-544 once the Spring version provides RestClient; retain
these wire and output contracts if making that change.
