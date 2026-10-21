# AB-543: Canada Post shipping port

## Decision

**Option (a): vendor the Canada Post module into `sm-core-modules`.** The request and response types are hand-ported and serialized with JDK StAX (`javax.xml.stream`). HTTP goes through Spring `RestTemplate`. `com.shopizer:shipping-canadapost-spring-boot-starter:2.17.0` is removed, which also removes its `shipping-canadapost-spring-boot-autoconfigure` and `com.sun.jersey:jersey-client:1.19.4` transitive dependencies.

Option (b), keeping the starter plus a javax JAXB/Jersey shim, was rejected. It would still block the Spring Boot 3 cut-over (AB-544).

### Why StAX instead of jakarta JAXB 4

The ticket suggested regenerating the rating types with `jakarta.xml.bind-api` 4 and `jaxb-runtime` 4. That does not work cleanly on the current Boot 2.5.12 classpath:

- Boot 2.5.12 manages `jakarta.xml.bind:jakarta.xml.bind-api` at **2.3.3**. That version still uses the `javax.xml.bind` package, and JAXB 4 publishes under the same coordinates. Declaring 4.x in `sm-core-modules` alone is overridden in `sm-shop` by dependency management, which would cause `ClassNotFoundException` for `jakarta.xml.bind.*` at runtime.
- Overriding Boot's `jakarta-xml-bind.version` and `glassfish-jaxb.version` in the shared root POM would take `javax.xml.bind` away from Hibernate 5.4 and `jjwt` 0.8.0 (JWT login uses `DatatypeConverter`). That would require an extra javax shim and edits to Boot-managed versions owned by other tickets.

StAX lives in the JDK's `java.xml` module. The jakarta rename does not affect it, it adds no dependency, and nothing needs to be redone at AB-544. Only the three types actually used are ported: `mailing-scenario`, `price-quotes` and `messages`. Unknown response elements are ignored, and DTDs and external entities are disabled.

## What is preserved

- Bean name `canadapost`, registered by `CanadaPostConfiguration`, which `ModulesConfiguration` imports. The map entry in `shopizer-core-modules.xml` is unchanged.
- Integration keys `username`, `password`, `account` and `apikey`.
- Service option keys `services-domestic`, `services-usa` and `services-intl`.
- Environment selection: `PROD` uses `https://soa-gw.canadapost.ca/rs/ship/price`; anything else uses `https://ct.soa-gw.canadapost.ca/rs/ship/price`. Both can be overridden with `module.shipping.canadapost.prodUrl` and `module.shipping.canadapost.devUrl`.
- Request headers: `Accept` and `Content-Type` set to `application/vnd.cpc.ship.rate-v4+xml`, `Accept-Language: en-CA`, and HTTP Basic authentication.
- Quote rules: CA/US destinations, per-package aggregation, the base vs. due price choice driven by `taxOnShipping`, and unit conversion and rounding.

**Intentional behaviour change:** the starter caught its own `IntegrationException`, so Canada Post `<messages>` errors lost their code and description. The port rethrows it with `TRANSACTION_EXCEPTION` and the message `"<code> - <description>"`.

## Parity

The new module was run against the original starter (2.17.0) with identical inputs and recorded responses:

| Scenario | taxOnShipping=false | taxOnShipping=true |
| --- | --- | --- |
| Domestic (DOM.RP/EP/XP) | same codes, names, prices | same |
| USA (USA.EP/TP/XP) | same | same |
| Two packages, aggregated | same | same |
| `<messages>` error | starter swallows details; port reports `12345 - Invalid account credentials` | — |

The request XML is identical except that the XML declaration has no `standalone="yes"`.

The contract tests live in `sm-core-modules/src/test/java/.../shipping/canadapost/`. They use `MockRestServiceServer` with recorded responses from `sm-core-modules/src/test/resources/canadapost/`.

## Classpath after this change

- No `com.sun.jersey` or `javax.ws.rs` artifacts, and no Canada Post starter or autoconfigure jar, are on the `sm-shop` runtime classpath or in `shopizer.jar`.
- `javax.xml.bind` is **still** on the runtime classpath, through `jakarta.xml.bind-api:2.3.3`, which Hibernate 5.4's `jaxb-runtime:2.3.6` and `jjwt` 0.8.0 need. None of it comes from Canada Post any more. It goes away with the Hibernate 6 / Boot 3 cut-over in AB-544.

## Follow-up (AB-544)

- Optionally replace `RestTemplate` with `RestClient`.
- No XML or JAXB work is needed: StAX is unchanged on Jakarta EE.

## ARB triage

Triaged by hand against the arb-review `arb-triggers.md`. The plugin script could not be run because the guardrails plugin blocks plugin scripts.

| Trigger | Hit? | Notes |
| --- | --- | --- |
| T1 new deployable | No | Code moves into the existing `sm-core-modules` jar |
| T2 data store / classification | No | No storage changes |
| T3 new external vendor/API | No | Canada Post is already integrated; same endpoints and credentials |
| T4 contract change | No | Same `ShippingQuoteModule` contract and the same Canada Post rate-v4 API |
| T5 infra | No | — |
| T6 auth/network boundary | No | Same outbound HTTPS calls and Basic auth |
| T7 framework upgrade | No | No framework or runtime change; removes Jersey 1 |
| T8 shared library / multi-team | No | Replaces a third-party starter with in-repo code in an existing module |
| T9 cost | No | — |

**Verdict: `NO_ARB`.** This is a refactor inside one service with unchanged contracts, so no ADR is needed.
