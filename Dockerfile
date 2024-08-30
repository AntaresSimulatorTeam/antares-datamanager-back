FROM  inca.rte-france.com/antares/jdk21:latest

MAINTAINER ANTARES
RUN pwd
COPY server/target/*.jar app.jar

# Set the active Spring profile
ENV SPRING_PROFILES_ACTIVE=localhost
ENTRYPOINT ["java","-jar","/app.jar"]
