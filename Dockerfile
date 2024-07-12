FROM openjdk:21-jdk

MAINTAINER ANTARES

COPY target/*.jar app.jar

# Set the active Spring profile
ENV SPRING_PROFILES_ACTIVE=localhost
ENTRYPOINT ["java","-jar","/app.jar"]
