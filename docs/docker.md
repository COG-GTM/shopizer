# Running Shopizer locally with Docker

This guide covers building the Shopizer backend (`sm-shop`) as a container image and running
it locally together with a MySQL database using Docker Compose.

| File | Purpose |
| --- | --- |
| `Dockerfile` (repo root) | Multi-stage build: compiles the Maven reactor, then packages a slim JRE runtime image |
| `.dockerignore` | Keeps the build context small (no `.git`, `target/`, local DB files, `.env`) |
| `docker-compose.yml` | Local stack: backend + MySQL 8, plus optional admin console / storefront |
| `.env.example` | Every knob `docker-compose.yml` understands, with its default |

> `sm-shop/Dockerfile` is the legacy image used by the CircleCI release pipeline. It expects a jar
> that was already built on the host. The root `Dockerfile` described here is self-contained.

## Prerequisites

- Docker Engine 23+ (or Docker Desktop) with BuildKit (the default) and the Compose v2 plugin
  (`docker compose version`).
- ~4 GB of RAM available to Docker.
- A JDK and Maven are **not** needed on the host: the build runs inside the container.

## Quick start

```bash
cp .env.example .env          # optional: change ports/credentials
docker compose up --build -d  # first build downloads Maven dependencies (a few minutes)
docker compose logs -f shopizer
```

When the logs show `Started ShopApplication`, the stack is ready:

| What | URL / address |
| --- | --- |
| REST API | http://localhost:8080/api/v1/ |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| MySQL | `localhost:3307`, database `SALESMANAGER`, user/password `shopizer` / `shopizer` |

On first start Shopizer creates the schema (`hibernate.hbm2ddl.auto=update`) and loads its
reference data, including the `DEFAULT` store and the admin user `admin@shopizer.com` /
`password`. Get an API token with:

```bash
curl -s -X POST http://localhost:8080/api/v1/private/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin@shopizer.com","password":"password"}'
```

`docker compose ps` shows both services as `healthy` once they are ready. The backend
container only starts after MySQL passes its healthcheck.

## Everyday workflow

| Task | Command |
| --- | --- |
| Start (or apply compose changes) | `docker compose up -d` |
| Rebuild after code changes | `docker compose up -d --build shopizer` |
| Follow backend logs | `docker compose logs -f shopizer` |
| MySQL shell | `docker compose exec db mysql -ushopizer -pshopizer SALESMANAGER` |
| Stop, keep data | `docker compose down` |
| Stop and **wipe** DB + uploaded files | `docker compose down -v` |
| Only run the database | `docker compose up -d db` |

Rebuilds are incremental: the Maven repository is kept in a BuildKit cache mount, so dependencies
are only downloaded again when a `pom.xml` changes.

### Persistent data

| Volume | Mounted at | Contents |
| --- | --- | --- |
| `shopizer_db-data` | `/var/lib/mysql` in `db` | MySQL data files |
| `shopizer_shopizer-files` | `/var/lib/shopizer/files` in `shopizer` | Infinispan CMS stores (uploaded product images, store logos, static files) |

Both survive `docker compose down` and are removed by `docker compose down -v`.

### Running the backend from your IDE / Maven against the Compose database

For a faster edit-run loop, run only MySQL in Docker and the application on the host:

```bash
docker compose up -d db
./mvnw clean install -DskipTests
cd sm-shop
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=mysql \
  -Dspring-boot.run.jvmArguments="-Ddb.jdbcUrl=jdbc:mysql://localhost:3307/SALESMANAGER?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC -Ddb.user=shopizer -Ddb.password=shopizer -Dsearch.noindex=true"
```

In an IDE, run `com.salesmanager.shop.application.ShopApplication` with the active profile
`mysql` and the same `-D` options as VM arguments.

### Optional: admin console and storefront

The published Shopizer front-end images can be started next to the backend through Compose
profiles:

```bash
docker compose --profile admin up -d   # Angular admin console -> http://localhost:4200
docker compose --profile shop up -d    # React storefront      -> http://localhost:3000
```

Both run in your browser and call the backend at `http://localhost:${SHOPIZER_PORT}`. They are
upstream images (`shopizerecomm/shopizer-admin`, `shopizerecomm/shopizer-shop-reactjs`) and are
not built from this repository.

## Using the image on its own

```bash
docker build -t shopizer:local .
docker run --rm -p 8080:8080 shopizer:local
```

Without any configuration the image uses the Spring `default` profile with an embedded H2
database stored in the container's working directory (`/var/lib/shopizer`). Mount a volume
there (`-v shopizer-data:/var/lib/shopizer`) to keep the data between runs.

### Image layout

- Stage `build` (`maven:3.9-eclipse-temurin-17`): builds `sm-shop` and the modules it depends on
  with `mvn -pl sm-shop -am package -DskipTests`, then splits the Spring Boot jar into layers
  (`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`). Code changes
  only rebuild the small `application` layer.
- Stage `runtime` (`eclipse-temurin:17-jre-jammy`): the application in `/opt/shopizer` (read-only,
  owned by root), runtime state in `/var/lib/shopizer`, runs as the unprivileged `shopizer` user
  (UID 10001), exposes port 8080 and has a `HEALTHCHECK` on `/actuator/health/ping`.

### Build arguments

| Argument | Default | Purpose |
| --- | --- | --- |
| `MAVEN_IMAGE` | `maven:3.9-eclipse-temurin-17` | Build stage base image |
| `RUNTIME_IMAGE` | `eclipse-temurin:17-jre-jammy` | Runtime stage base image |
| `MAVEN_MIRROR_URL` | _(empty)_ | Route all Maven downloads through a mirror/repository manager |
| `MAVEN_EXTRA_ARGS` | _(empty)_ | Extra flags appended to the `mvn package` command |
| `APP_UID` / `APP_GID` | `10001` | UID/GID of the runtime user |

With Compose, `MAVEN_MIRROR_URL` is read from `.env` or the shell.

## Configuration

Shopizer loads its configuration from the property files of the active Spring profile
(`SPRING_PROFILES_ACTIVE`). Any property from those files can be overridden with an environment
variable **of exactly the same name**, dots included, e.g. `db.jdbcUrl` or `config.cms.contentUrl`.
Spring Boot's usual upper-case form (`DB_JDBCURL`) is *not* recognised for these properties.

The Compose file uses the `mysql` profile and sets:

| Variable | Compose value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `mysql` |
| `db.jdbcUrl` | `jdbc:mysql://db:3306/SALESMANAGER?useSSL=false&allowPublicKeyRetrieval=true&...` |
| `db.user` / `db.password` | `${SHOPIZER_DB_USER}` / `${SHOPIZER_DB_PASSWORD}` |
| `search.noindex` | `true` (no search engine in the local stack) |
| `config.cms.contentUrl` | `${SHOPIZER_CONTENT_URL}` |
| `JAVA_TOOL_OPTIONS` | `${SHOPIZER_JAVA_TOOL_OPTIONS}` |

To override more settings (mail server, payment keys, CMS backend, ...), add them to the
`environment:` block of the `shopizer` service, or create a `docker-compose.override.yml`, which
Compose merges automatically:

```yaml
services:
  shopizer:
    environment:
      config.cms.method: aws
      config.cms.aws.bucket: my-bucket
```

JVM flags go in `JAVA_TOOL_OPTIONS`. The image deliberately has no shell entrypoint:
`sh`/`dash` drop environment variables whose names contain dots, which would silently hide the
`db.*` settings from the JVM.

## Troubleshooting

- **`Could not resolve placeholder 'db.jdbcUrl'`**: the `mysql` profile is active but `db.jdbcUrl`
  was not passed as an environment variable or `-D` system property. Check the exact (dotted)
  variable name, and do not wrap the entrypoint in a shell.
- **Port already in use**: change `SHOPIZER_PORT` / `SHOPIZER_DB_PORT` in `.env`.
- **`429 Too Many Requests` pulling images or Maven artifacts**: point `MYSQL_IMAGE`,
  `MAVEN_IMAGE`/`RUNTIME_IMAGE` (build args) at a registry mirror and set `MAVEN_MIRROR_URL`.
- **Start from a clean database**: `docker compose down -v && docker compose up -d`.
- **Apple Silicon**: all base images used here are multi-arch (`linux/amd64` and `linux/arm64`).
