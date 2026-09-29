# Multi-stage build for the Shopizer headless backend (sm-shop).
#
#   docker build -t shopizer:local .
#
# See docs/docker.md for the full local workflow (docker compose + MySQL).

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

########################################################################
# Stage 1: build the multi-module reactor and produce the Spring Boot jar
########################################################################
FROM ${MAVEN_IMAGE} AS build

# Optional: route Maven through a mirror / repository manager
# (e.g. --build-arg MAVEN_MIRROR_URL=https://nexus.example.com/repository/maven-public/)
ARG MAVEN_MIRROR_URL=
# Extra Maven CLI flags (e.g. --build-arg MAVEN_EXTRA_ARGS="-DskipTests=false")
ARG MAVEN_EXTRA_ARGS=

WORKDIR /workspace

RUN if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      mkdir -p /root/.m2 && \
      printf '<settings><mirrors><mirror><id>build-mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' \
        "${MAVEN_MIRROR_URL}" > /usr/share/maven/ref/settings-docker.xml; \
    else \
      printf '<settings/>\n' > /usr/share/maven/ref/settings-docker.xml; \
    fi

# Copy the POMs first so the dependency layer is only invalidated when a POM changes.
COPY pom.xml ./
COPY sm-core-model/pom.xml sm-core-model/
COPY sm-core-modules/pom.xml sm-core-modules/
COPY sm-core/pom.xml sm-core/
COPY sm-shop-model/pom.xml sm-shop-model/
COPY sm-shop/pom.xml sm-shop/

COPY sm-core-model/src sm-core-model/src
COPY sm-core-modules/src sm-core-modules/src
COPY sm-core/src sm-core/src
COPY sm-shop-model/src sm-shop-model/src
COPY sm-shop/src sm-shop/src

# The BuildKit cache mount keeps ~/.m2 between builds so dependencies are only downloaded once.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -e -s /usr/share/maven/ref/settings-docker.xml \
      -DskipTests -Djacoco.skip=true ${MAVEN_EXTRA_ARGS} \
      -pl sm-shop -am package \
 && java -Djarmode=layertools -jar sm-shop/target/shopizer.jar extract --destination /workspace/extracted

########################################################################
# Stage 2: slim runtime image
########################################################################
FROM ${RUNTIME_IMAGE} AS runtime

ARG APP_UID=10001
ARG APP_GID=10001

RUN groupadd --system --gid ${APP_GID} shopizer \
 && useradd --system --uid ${APP_UID} --gid shopizer --home-dir /var/lib/shopizer --shell /usr/sbin/nologin shopizer

# Application code lives in /opt/shopizer (read-only, owned by root).
WORKDIR /opt/shopizer

# Spring Boot layers, ordered from least to most frequently changing.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# The "default" Spring profile loads classpath:database.properties, which is not committed.
# Ship the embedded H2 configuration so the image also runs standalone without a database.
COPY sm-shop/src/main/resources/profiles/docker/database.properties BOOT-INF/classes/database.properties

# Runtime state lives in /var/lib/shopizer (the working directory): Shopizer resolves the
# Infinispan CMS stores (./files/...) and the embedded H2 database (./SALESMANAGER) relative to it.
WORKDIR /var/lib/shopizer
COPY --chown=shopizer:shopizer sm-shop/files/ files/
RUN chown shopizer:shopizer /var/lib/shopizer

USER ${APP_UID}:${APP_GID}

# JVM flags go in JAVA_TOOL_OPTIONS (read natively by the JVM). The entrypoint is deliberately
# shell-free: Shopizer reads dotted property names (e.g. db.jdbcUrl) straight from the
# environment, and POSIX shells silently drop variables whose names contain dots.
ENV SPRING_PROFILES_ACTIVE=default \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
  CMD ["curl", "-fsS", "http://localhost:8080/actuator/health/ping"]

ENTRYPOINT ["java", "-cp", "/opt/shopizer", "org.springframework.boot.loader.JarLauncher"]
