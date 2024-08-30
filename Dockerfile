FROM  inca.rte-france.com/antares/jdk21:latest

MAINTAINER ANTARES
RUN pwd
COPY /home/jenkins/workspace/antares/pegase-back-ci/target/antares-datamanager-back-1.0.0-SNAPSHOT.jar app.jar

# Set the active Spring profile
ENV SPRING_PROFILES_ACTIVE=localhost
ENTRYPOINT ["java","-jar","/app.jar"]
