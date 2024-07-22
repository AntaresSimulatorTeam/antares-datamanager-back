# ANTARES - PEGASE-BACK

Microservice pegase-back.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Functional Description](#functional-description)
- [Technical Description](#technical-description)
- [Quick Start](#quick-strt)
- [Testing](#testing)a
- [API](#api)

**********************
### Features

This microservice is the Antares pegase-back engine.

Mapping translation (fr) -> (en):
* Zone , pays  -> area


*************
### Requirements

- An IDE with at least JDK 21 installed

*********************
### functional-Description
The main objective of PEGASE-BACK is to allow the user to load area, link, load, thermal.... data into a database in order to keep traceability and make the data re-usable.
this brick will then expose its data for the generation of studies.
*********************
### Technical Description

This application is made using JAVA language (version 21 or more recent) and uses SpringBoot framework.
The main dependencies are:
* Maven for the application build
* Lombok for automatic code generation
* Springboot for Spring web framework dependencies

The maven project is structured as follows:
- [controller] folder : API controllers for the frontend application
- [dto] folder : data transfer objects lightened for exposition to the frontend application
- [mapper] folder : mappers to transform entities objects in dto, and reciprocally
- [repository] folder : database interaction classes, using JpaRepository
    - [model] folder : data model entities, based on elasticsearch standard
- [service] folder : data exposition, connectivity and configuration services of the application
    - [model] folder : business model entities
    - [impl] folder : service interface implementation

************
### Quick start

#### Local installation

- Clone the project from GitLab to your computer, and import the project in your IDE
- Configure the environment variable SPRING_PROFILES_ACTIVE to "localhost" in your IDE.
- Run the main application using localhost profile

************
### Testing

The tests are in the src/test/java folder. You can run them with JUnit.

************
### API

Swagger documentation :
* env local : [http://localhost:8093/swagger-ui/index.html](http://localhost:8093/swagger-ui/index.html)
