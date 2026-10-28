FROM adoptopenjdk/openjdk11-openj9:alpine
RUN mkdir /opt/app
RUN mkdir /files
COPY target/shopizer.jar /opt/app
COPY SALESMANAGER.h2.db /
COPY ./files /files
# JWT_SECRET (>= 64 random bytes) must be provided at runtime, e.g. docker run -e JWT_SECRET=...
CMD ["java", "-jar", "/opt/app/shopizer.jar"]