# Runtime image for the Shopizer API. Build the jar first (./mvnw -B install), then from sm-shop/:
#   docker build -t shopizer .
FROM eclipse-temurin:21-jre

ARG APP_UID=10001
RUN groupadd --system --gid ${APP_UID} shopizer \
 && useradd --system --uid ${APP_UID} --gid shopizer --home-dir /opt/app --no-create-home --shell /usr/sbin/nologin shopizer

WORKDIR /opt/app

# The default Spring profile reads classpath:database.properties; the docker profile points it at the bundled H2 file.
COPY --chown=shopizer:shopizer src/main/resources/profiles/docker/database.properties config/database.properties
COPY --chown=shopizer:shopizer SALESMANAGER.mv.db SALESMANAGER.mv.db
COPY --chown=shopizer:shopizer files files
COPY --chown=shopizer:shopizer target/shopizer.jar shopizer.jar

USER shopizer
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Dloader.path=/opt/app/config", "-cp", "/opt/app/shopizer.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"]
