FROM eclipse-temurin:21-jre
RUN mkdir -p /opt/app /files
COPY target/shopizer.jar /opt/app
COPY SALESMANAGER.h2.db /
COPY ./files /files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/app/shopizer.jar"]
