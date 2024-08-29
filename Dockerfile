FROM inca.rte-france.com/rtsi/openjdk:21-alpine3201.5-apm

MAINTAINER ANTARES

COPY target/*.jar app.jar

# Set the active Spring profile
ENV SPRING_PROFILES_ACTIVE=localhost
ENTRYPOINT ["java","-jar","/app.jar"]
