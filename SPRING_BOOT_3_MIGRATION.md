# Spring Boot 3 migration notes

Branch: `devin/1790695340-spring-boot-3` (from `master`).

## Status

- Spring Boot 2.5.12 -> 3.5.16, Java 11 -> 17, `javax.*` -> `jakarta.*` across all modules.
- `mvn clean install` (with tests) passes for the whole reactor:
  - sm-core: 27 run, 0 failures, 12 skipped
  - sm-shop: 32 run, 0 failures, 7 skipped
- Tested with Maven 3.9.9 + OpenJDK 17. The committed Maven wrapper still points at Maven 3.5.2.

## What changed

| Area | Change |
| --- | --- |
| Build | Boot parent 3.5.16, `java.version` 17, `mysql:mysql-connector-java` -> `com.mysql:mysql-connector-j`, jakarta validation/mail/inject/annotation APIs, Ehcache 3 (`jakarta` classifier) + `hibernate-jcache`, Caffeine pinned to 2.9.3 for Infinispan 9.4 |
| Persistence | Hibernate 6: `@Type(TextType)` -> `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`; removed `@Inheritance`/`@Table` from `@MappedSuperclass`es; removed `updatable=false` on `@JoinTable` join columns; HQL fixes for duplicate fetches/aliases; native SKU lookup result cast to `Number`; `jakarta.persistence.validation.mode=none` (Bean Validation was not on the classpath before, so persist-time validation never ran) |
| Cache | Hibernate L2 cache: Ehcache 2 region factory -> JCache/Ehcache 3; `ehcache.xml` rewritten in Ehcache 3 format; Spring `EhCacheCacheManager` -> `JCacheCacheManager` |
| Web | Springfox -> springdoc-openapi 2.8 (annotations converted to `io.swagger.v3`); `HandlerInterceptorAdapter` -> `HandlerInterceptor`; trailing-slash matching re-enabled (`PathMatchConfigurer.setUseTrailingSlashMatch`, deprecated); fixed invalid duplicate `{id}` path variable in `ProductImageApi` |
| Security | `WebSecurityConfigurerAdapter` -> `SecurityFilterChain` beans; `@EnableGlobalMethodSecurity` -> `@EnableMethodSecurity`; the 3 `AuthenticationManager` beans are now explicit beans (`customerAuthenticationManager` is `@Primary`, the others are injected with `@Qualifier`) |
| Boot | Legacy `spring.factories` starters (`CanadaPostAutoConfiguration`, `SearchAutoConfiguration`) imported explicitly; `spring.main.allow-circular-references=true` |
| Test DB | H2 2.x: `NON_KEYWORDS=VALUE` added to H2 JDBC URLs (`MERCHANT_CONFIGURATION.VALUE` column) |
| Tax mappers | Don't assign id `0` to new `TaxClass`/`TaxRate` (Hibernate 6 treats it as a detached entity and fails the merge) |

## Remaining work / risks

1. **JJWT 0.8.0** still depends on `javax.xml.bind` (kept `jaxb-api` on the classpath). Upgrade to JJWT 0.12.x and rewrite `JWTTokenUtil`.
2. **Infinispan 9.4.18** (CMS file storage) is EOL and needs Caffeine 2.x; the pin can conflict with anything else wanting Caffeine 3. Upgrade Infinispan to 14/15 (API changes in tree cache usage — `infinispan-tree` was removed upstream) or drop the Infinispan CMS backend.
3. **Deprecated compatibility switches** to remove eventually: trailing-slash matching, `allow-circular-references`, `jakarta.persistence.validation.mode=none`, `NON_KEYWORDS=VALUE` (or rename/quote the column).
4. **Spring Security**: request matchers use `AntPathRequestMatcher` to keep old semantics; verify every chain/role against a running admin + storefront (only integration tests were run, no manual UI testing).
5. **springdoc**: annotation conversion was mechanical; review generated OpenAPI docs at `/swagger-ui.html`.
6. **Hibernate 6 queries**: many hand-written HQL/JPQL fetch graphs (`ProductRepositoryImpl`, variant/availability repositories) were only fixed where tests hit them; untested paths may still fail validation at runtime. Also review `hibernate.id.new_generator_mappings` (removed in Hibernate 6) and the `SM_SEQUENCER` table generator behaviour against an existing MySQL database.
7. **MySQL/Postgres profiles** not exercised (tests use H2); run against a real DB with `hbm2ddl=validate`.
8. `sm-shop/Dockerfile` still uses an OpenJDK 11 base image and the Maven wrapper still downloads Maven 3.5.2; bump both (Java 17 image, Maven >= 3.6.3).
9. Other libraries left at old versions that should be reviewed: MapStruct 1.3.0.Final, Elasticsearch/search starter, Drools, AWS/GCP SDKs.
