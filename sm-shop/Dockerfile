FROM eclipse-temurin:21-jre
RUN mkdir /opt/app
RUN mkdir /files
COPY target/shopizer.jar /opt/app
COPY SALESMANAGER.mv.db /
COPY ./files /files
ENV SPRING_PROFILES_ACTIVE=docker
CMD ["java", "-jar", "/opt/app/shopizer.jar"]
