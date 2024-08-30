FROM inca.rte-france.com/apsu/envoy-openjdk21-alpine:v1.27.0-apm

MAINTAINER ANTARES

COPY target/*.jar app.jar

# Set the active Spring profile
ENV SPRING_PROFILES_ACTIVE=localhost
ENTRYPOINT ["java","-jar","/app.jar"]
