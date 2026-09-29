# Shopizer developer onboarding

This guide gets a new backend developer from a clean machine to a running,
debuggable `sm-shop` (the Shopizer headless commerce REST API).

Every command in this guide was executed and checked on Ubuntu 22.04 with
OpenJDK 17.0.19 and Docker 29 against the `3.2.7` branch. Commands are run from the **repository
root** unless the step says `cd sm-shop`.

- [1. Repository map](#1-repository-map)
- [2. Prerequisites](#2-prerequisites)
- [3. Clone and build](#3-clone-and-build)
- [4. Run sm-shop](#4-run-sm-shop)
- [5. Smoke-test the running API](#5-smoke-test-the-running-api)
- [6. Debug sm-shop](#6-debug-sm-shop)
- [7. Run the tests](#7-run-the-tests)
- [8. Optional: run against MySQL](#8-optional-run-against-mysql)
- [9. Configuration reference](#9-configuration-reference)
- [10. Troubleshooting](#10-troubleshooting)

## TL;DR

```bash
git clone https://github.com/COG-GTM/shopizer.git
cd shopizer
cp sm-shop/src/main/resources/profiles/docker/database.properties \
   sm-shop/src/main/resources/database.properties
./mvnw clean install -DskipTests
./mvnw -pl sm-shop spring-boot:run
# in another terminal
curl http://localhost:8080/actuator/health
```

Then open <http://localhost:8080/swagger-ui.html>.

---

## 1. Repository map

Shopizer is a single Maven reactor (`pom.xml`, packaging `pom`, parent
`spring-boot-starter-parent` 2.5.12, compiled for Java 11). The modules are
built in this order:

```
sm-core-model  ─┐
                ├─> sm-core ─┐
sm-core-modules ┘            ├─> sm-shop  (Spring Boot app -> shopizer.jar)
                sm-shop-model┘
```

| Module | Packaging | What it is for | Where to look |
|---|---|---|---|
| `sm-core-model` | jar | JPA **domain entities** shared by everything: catalog (products, categories, options), merchant stores, customers, orders, payments, shipping, tax, users, reference data (languages, countries, currencies). No business logic. | `com.salesmanager.core.model.*` |
| `sm-core-modules` | jar | Small **SPI / extension contracts** for pluggable integrations: payment and shipping module interfaces, order-total modules, `Module`, encryption and geolocation helpers. External integration starters implement these. | `com.salesmanager.core.modules.*` |
| `sm-core` | jar | The **business layer**: Spring Data JPA repositories, services (`*ServiceImpl`), core Spring configuration, data source / Hibernate setup, and built-in modules for CMS/file storage (Infinispan, local httpd, AWS S3, GCP), email (SMTP, SES), payments, shipping, order totals, search (OpenSearch), Drools rules and FreeMarker email templates. | `com.salesmanager.core.business.{configuration,repositories,services,modules}`; `src/main/resources/{spring,profiles,rules,templates,search}` |
| `sm-shop-model` | jar | **API DTOs**: the readable/persistable request and response objects exposed by the REST API (`Readable*`, `Persistable*`), plus shared validation and web utilities. | `com.salesmanager.shop.model.*` |
| `sm-shop` | jar (Spring Boot fat jar `target/shopizer.jar`) | The **runnable application**: `ShopApplication` main class, REST controllers, facades (API -> service orchestration), mappers/populators (entity <-> DTO), JWT security, initial data loading, Swagger config, and the API integration tests. | `com.salesmanager.shop.application` (boot + config), `store.api.v1` / `store.api.v2` (REST controllers), `store.facade`, `mapper`, `populator`, `store.security`, `init.data` |

Key entry points in `sm-shop`:

- `sm-shop/src/main/java/com/salesmanager/shop/application/ShopApplication.java` – `main()`.
- `.../application/config/ShopApplicationConfiguration.java` – scans
  `com.salesmanager.shop` and imports `CoreApplicationConfiguration` from `sm-core`.
- `.../application/config/MultipleEntryPointsSecurityConfig.java` – which URLs
  are public, customer-authenticated, or admin-authenticated (`/api/v1/private/**`).
- `.../store/api/v1/**` – the main REST API, mounted under `/api/v1`.
  `.../store/api/v2/product` holds the newer product API (`/api/v2`).
- `.../init/data/InitializationLoader.java` – seeds reference data, the
  `DEFAULT` store and the default admin user on first start.

A typical request flows:
`store.api.v1.*Api` (controller) -> `store.facade.*Facade` -> `sm-core`
`*Service` -> `*Repository` -> database, with `mapper`/`populator` classes
converting between `sm-core-model` entities and `sm-shop-model` DTOs.

**Not in this repository:** the Angular admin (`shopizer-admin`) and the React
storefront (`shopizer-shop-reactjs`) live in separate repositories and talk to
this API. Several integrations (OpenSearch search, Canada Post shipping, Square
payments, `shopizer-commons`) are pulled in as Maven dependencies from Maven
Central (see `sm-core/pom.xml`).

Other top-level files: `mvnw`/`.mvn/` (Maven wrapper, Maven 3.5.2),
`.circleci/` (upstream CI; relies on a private build image, not usable
locally), `sm-shop/Dockerfile` (packages `target/shopizer.jar`),
`sm-shop/SALESMANAGER.h2.db` (pre-built H2 database used by default).

## 2. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 11 or 17 (verified with OpenJDK 17.0.19) | building and running |
| git, curl | any | cloning, smoke tests |
| Maven | **not needed** – use the bundled `./mvnw` | building |
| Docker | optional | only for the MySQL setup in section 8 |

On Ubuntu/Debian:

```bash
sudo apt-get install -y openjdk-17-jdk git curl
java -version
```

`java -version` should report 11 or 17. Newer JDKs are untested with this
Spring Boot 2.5 / Maven 3.5.2 toolchain.

The first build downloads roughly 500 MB of dependencies into `~/.m2`.

## 3. Clone and build

```bash
git clone https://github.com/COG-GTM/shopizer.git
cd shopizer
./mvnw -v
```

`./mvnw -v` downloads the Maven wrapper jar and Maven 3.5.2 on first use and
prints the Maven and Java versions.

### 3.1 Create the runtime database config (required)

`sm-shop` loads `classpath:database.properties` when no Spring profile is
active, but that file is **not committed** (`sm-shop/src/main/resources/*` is
git-ignored so every developer can keep their own). Without it startup fails
with `class path resource [database.properties] cannot be opened because it
does not exist`.

Copy the H2 configuration that ships with the repo:

```bash
cp sm-shop/src/main/resources/profiles/docker/database.properties \
   sm-shop/src/main/resources/database.properties
```

This points the app at the file-based H2 database `./SALESMANAGER.h2.db`
(relative to the working directory, i.e. `sm-shop/`), user `test` / password
`password`. No database server is needed.

Do this **before** building: the file is copied into `target/classes` and into
`shopizer.jar` at build time.

### 3.2 Build all modules

```bash
./mvnw clean install -DskipTests
```

This builds the five modules in reactor order and installs them into
`~/.m2/repository/com/shopizer/*/3.2.5` (the POM version is `3.2.5` even on the
`3.2.7` branch). Expect `BUILD SUCCESS` for `shopizer`, `sm-core-model`,
`sm-core-modules`, `sm-core`, `sm-shop-model` and `sm-shop`. The first run
took ~12 minutes (dependency downloads); later runs take well under a minute.

Once dependencies are cached you can build offline:

```bash
./mvnw -o install -DskipTests
```

`install` (not just `package`) matters: when you run only `sm-shop`
(`-pl sm-shop` or `cd sm-shop`), Maven resolves `sm-core` & friends from
`~/.m2`, so **re-run `install` after changing code in any other module**.

## 4. Run sm-shop

### 4.1 With the Spring Boot Maven plugin (recommended for development)

From the repository root:

```bash
./mvnw -pl sm-shop spring-boot:run
```

or, equivalently, from the module directory:

```bash
cd sm-shop
./mvnw spring-boot:run
```

Startup takes ~10–20 seconds and ends with
`Started ShopApplication in ... seconds`. The app listens on port **8080**.
Stop it with `Ctrl+C`.

On the very first start against an empty database `InitializationLoader`
creates reference data, the `DEFAULT` merchant store and the default admin
user. The bundled `SALESMANAGER.h2.db` already contains this data.

### 4.2 As a jar

```bash
./mvnw -pl sm-shop package -DskipTests
cd sm-shop
java -jar target/shopizer.jar
```

Run the jar from `sm-shop/` so the relative H2 path resolves to
`sm-shop/SALESMANAGER.h2.db` (running from elsewhere silently creates a new,
empty database in that directory).

### 4.3 Resetting local data

The H2 file `sm-shop/SALESMANAGER.h2.db` is **tracked in git** and is modified
every time the app runs. Do not commit it; to get back to the pristine database:

```bash
git restore sm-shop/SALESMANAGER.h2.db
```

## 5. Smoke-test the running API

With the app running, from another terminal:

```bash
curl http://localhost:8080/actuator/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/swagger-ui.html
curl http://localhost:8080/api/v1/languages
curl "http://localhost:8080/api/v1/products?count=5"
curl http://localhost:8080/api/v1/store/DEFAULT
```

Expected: health returns `{"status":"UP",...}`, Swagger returns `200`, and the
API calls return JSON.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI (Swagger 2) JSON: <http://localhost:8080/v2/api-docs>
- Actuator: <http://localhost:8080/actuator>

### Authenticated (admin) endpoints

`/api/v1/private/**` requires a JWT. Without one you get `401`:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/private/user/profile
```

Log in as the seeded admin (`admin@shopizer.com` / `password` — a local
development default created by `InitializationLoader`) and reuse the token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/private/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin@shopizer.com","password":"password"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/private/user/profile
```

In Swagger UI, click **Authorize** and enter `Bearer <token>`.

## 6. Debug sm-shop

### 6.1 Start with a JDWP debug port

With `spring-boot:run` (the plugin forks a JVM, so the agent must be passed
through `jvmArguments`):

```bash
./mvnw -pl sm-shop spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

With the jar:

```bash
cd sm-shop
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar target/shopizer.jar
```

The log starts with `Listening for transport dt_socket at address: 5005`.
Use `suspend=y` instead to make the JVM wait for a debugger before starting
(useful for debugging startup/bean wiring).

### 6.2 Attach a debugger

**IntelliJ IDEA:** *Run > Edit Configurations > + > Remote JVM Debug*, host
`localhost`, port `5005`, then *Debug*. **Eclipse:** *Debug Configurations >
Remote Java Application*, port `5005`. **VS Code:** a `java` launch config
with `"request": "attach"`, `"hostName": "localhost"`, `"port": 5005`.

Command-line check with `jdb` (ships with the JDK):

```bash
jdb -attach localhost:5005
```

Then, at the `jdb` prompt:

```
stop in com.salesmanager.shop.store.api.v1.references.ReferencesApi.getLanguages
```

and in another terminal:

```bash
curl http://localhost:8080/api/v1/languages
```

`jdb` reports `Breakpoint hit: ... ReferencesApi.getLanguages()`; `where` shows
the stack, `cont` resumes, `exit` detaches.

Good first breakpoints: a controller in `store/api/v1/**`, the matching
`store/facade/**` class, or `sm-core` `*ServiceImpl` classes.

### 6.3 Run/debug directly from the IDE

Import the root `pom.xml` as a Maven project, then create an *Application* run
configuration with main class `com.salesmanager.shop.application.ShopApplication`,
module `sm-shop`, and **working directory `sm-shop`** (for the H2 path). Step 3.1
must be done first. (IDE steps are not command-verified; the JDWP flow above is.)

### 6.4 More logging

Raise log levels without editing files:

```bash
./mvnw -pl sm-shop spring-boot:run \
  -Dspring-boot.run.arguments="--logging.level.com.salesmanager=DEBUG"
```

`DEBUG` on `com.salesmanager` also logs every environment variable at startup
(`ShopServletContextListener`), so don't paste those logs anywhere public.

SQL is printed to stdout when `db.show.sql=true` in `database.properties`
(already the case for the H2 config copied in 3.1).

## 7. Run the tests

```bash
./mvnw test
```

Tests use an in-memory H2 database from `src/test/resources/database.properties`
and need no external services. Result on `3.2.7`: `sm-core` 28 tests (13
skipped), `sm-shop` 33 tests (8 skipped), `BUILD SUCCESS` in under a minute
once dependencies are cached.

Single module / single test class (after `./mvnw install -DskipTests`):

```bash
./mvnw -pl sm-shop test -Dtest=ShoppingCartAPIIntegrationTest
```

The `sm-shop` tests in `src/test/java/com/salesmanager/test/shop/integration/**`
boot the full application (`@SpringBootTest(classes = ShopApplication.class,
webEnvironment = RANDOM_PORT)`) and call the REST API, so they are a good way to
debug an endpoint end-to-end: run them from the IDE in debug mode, or have
surefire's forked JVM wait for a debugger on port 5005:

```bash
./mvnw -pl sm-shop test -Dtest=ActuatorTest -Dmaven.surefire.debug
```

Maven prints `Listening for transport dt_socket at address: 5005` and blocks
until you attach (IDE remote debug or `jdb -attach localhost:5005`, then `cont`).

## 8. Optional: run against MySQL

The `local` Spring profile uses MySQL at `127.0.0.1:3306`, database
`SALESMANAGER`, user `root` / password `password`
(`sm-shop/src/main/resources/profiles/local/database.properties`).

Start MySQL 8 in Docker (image is the official `mysql` image via Google's
Docker Hub mirror; `mysql:8.0` works too if Docker Hub is reachable):

```bash
docker run -d --name shopizer-mysql \
  -e MYSQL_ROOT_PASSWORD=password -e MYSQL_DATABASE=SALESMANAGER \
  -p 3306:3306 mirror.gcr.io/library/mysql:8.0
docker exec shopizer-mysql mysqladmin -uroot -ppassword ping
```

Wait until `ping` prints `mysqld is alive`, then run with the `local` profile:

```bash
./mvnw -pl sm-shop spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dsearch.noindex=true"
```

Hibernate creates the schema (`hibernate.hbm2ddl.auto=update`) and
`InitializationLoader` seeds it on first start, including the admin user.

`-Dsearch.noindex=true` is required unless you also run OpenSearch on
`localhost:9200`: the `local` profile's `shopizer-core.properties` enables
search indexing, and without it startup fails with
`FileNotFoundException: class path resource [search/MAPPINGS.json] cannot be resolved to absolute file path`.

Stop and remove the database container when done:

```bash
docker rm -f shopizer-mysql
```

## 9. Configuration reference

Configuration is split between Spring Boot's `application.properties` and
Spring XML property placeholders selected by **Spring profile**
(`sm-core/src/main/resources/spring/shopizer-core-config.xml`):

| Profile | Properties loaded | Notes |
|---|---|---|
| *(none / default)* | `database.properties`, `shopizer-core.properties`, `email.properties`, `authentication.properties` from the classpath root | You create `database.properties` (section 3.1). Search indexing is off (`search.noindex=true`). |
| `local` | `profiles/local/*.properties` | MySQL on localhost; search on `localhost:9200` (section 8). |
| `mysql`, `cloud`, `gcp`, `firebase` | `profiles/<name>/*.properties` | Deployment profiles; need externally supplied hosts/credentials. Not covered here. |

Activate a profile with `-Dspring-boot.run.profiles=<name>` (Maven plugin) or
`--spring.profiles.active=<name>` (jar), e.g. from `sm-shop/`:

```bash
java -Dsearch.noindex=true -jar target/shopizer.jar --spring.profiles.active=local
```

| File | Module | Contents |
|---|---|---|
| `sm-shop/src/main/resources/application.properties` | sm-shop | Port 8080, actuator exposure, log levels, multipart limits. |
| `sm-shop/src/main/resources/shopizer-properties.properties` | sm-shop | Shop/API settings. |
| `sm-shop/src/main/resources/profiles/*/database.properties` | sm-shop | JDBC URL, credentials, Hibernate dialect, pool sizes per profile. |
| `sm-core/src/main/resources/shopizer-core.properties` | sm-core | Search (OpenSearch), CMS storage method, shipping, `db.init.data`. |
| `sm-core/src/main/resources/authentication.properties` | sm-core | JWT settings. |
| `sm-core/src/main/resources/email.properties` | sm-core | SMTP settings (placeholders; configure before relying on outgoing email). |

Values committed in these files (JWT secret, DB passwords, admin password)
are local-development defaults only.

## 10. Troubleshooting

**`class path resource [database.properties] cannot be opened because it does not exist`**
You skipped section 3.1, or built the jar before creating the file. Create it,
then re-run `./mvnw -pl sm-shop package -DskipTests` (for the jar) or simply
restart `spring-boot:run`.

**`search/MAPPINGS.json cannot be resolved to absolute file path` with `-Dspring-boot.run.profiles=local`**
Add `-Dspring-boot.run.jvmArguments="-Dsearch.noindex=true"` or start OpenSearch
on port 9200 (section 8).

**Changes in `sm-core` (or another library module) are not picked up**
Run `./mvnw install -DskipTests` from the root before `-pl sm-shop spring-boot:run`.

**Port 8080 or 5005 already in use**
Find the process with `ss -ltnp | grep -E ':8080|:5005'` and stop it, or start
on another port with `-Dspring-boot.run.arguments="--server.port=8081"`.

**HTTP 429 / downloads failing from `repo.maven.apache.org`**
Maven Central rate-limits some networks (CI runners, shared NAT). Point Maven
at Google's mirror of Central by creating `~/.m2/settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>google-maven-central</id>
      <name>Google mirror of Maven Central</name>
      <url>https://maven-central.storage-download.googleapis.com/maven2/</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

If `./mvnw` itself cannot download its wrapper jar, fetch it from the same
mirror (the Maven distribution it then downloads must still be reachable, or
be pre-populated under `~/.m2/wrapper/dists`):

```bash
curl -fsSL -o .mvn/wrapper/maven-wrapper.jar \
  https://maven-central.storage-download.googleapis.com/maven2/io/takari/maven-wrapper/0.4.2/maven-wrapper-0.4.2.jar
```
