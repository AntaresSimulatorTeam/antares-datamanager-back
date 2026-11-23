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
- Add `--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED` as a VM argument in your IDE.
- Run the main application using localhost profile

************
### Testing

The tests are in the src/test/java folder. You can run them with JUnit.

************
### API

Swagger documentation :
* env local : [http://localhost:8093/swagger-ui/index.html](http://localhost:8093/swagger-ui/index.html)

#### Controller endpoints

The following endpoints are exposed by the REST controllers. Request and response schemas are available in Swagger.

| Controller | Base path | Endpoints |
|---|---|---|
| StudyController | `/v1/study` | `GET /search`, `GET /keywords/search`, `POST /`, `DELETE /{id}`, `POST /generate`, `GET /{id}`, `POST /duplicate`, `PUT /{id}` |
| ProjectController | `/v1/project` | `GET /pinned`, `PUT /unpin`, `POST /pin`, `GET /search`, `GET /{id}`, `DELETE /{id}`, `GET /autocomplete`, `POST /`, `PUT /` |
| FileController | `/v1/files` | `GET /{filename}`, `POST /` (multipart upload) |
| DefaultConfigController | `/v1/default_config` | `GET /load`, `GET /thermal-technology-display` |
| WarningController | `/v1/warnings` | `GET /`, `PUT /{id}/ack` |
| UserController | `/v1/user` | `GET /{nni}`, `POST /list` |
| TrajectoryController | `/v1/trajectory` | `GET /db`, `GET /fs`, `POST /`, `POST /load`, `GET /`, `PUT /attach`, `DELETE /detach`, `POST /detach/batch`, `DELETE /detach/all`, `GET /trajectoryData`, `GET /count/warning/{id}` |
| AdequacyController | `/v1/trajectory` | `POST /adequacy-patch` |
| DsrController | `/v1/trajectory` | `POST /dsr-cluster`, `POST /dsr-capacity-modulation` |
| FlowbasedController | `/v1/trajectory` | `POST /flowbased` |
| HydroController | `/v1/trajectory` | `POST /hydro-series`, `POST /hydro-technical-parameters` |
| MiscController | `/v1/trajectory` | `POST /installed-misc`, `POST /load-factor-misc` |
| MultiEnergyController | `/v1/trajectory` | `POST /load-me`, `POST /constraint-me`, `POST /efficiency-me`, `POST /hydro-capacity-me`, `POST /hydro-parameters-me`, `POST /thermal-me`, `POST /hydro-ts-me`, `POST /hydro-water-values-me`, `POST /hydro-reservoir-levels-me` |
| NuclearController | `/v1/trajectory` | `POST /nuclear-modulation`, `POST /nuclear-lt`, `POST /nuclear-ts-erp`, `POST /nuclear-ts-smr`, `POST /nuclear-talon` |
| P2GController | `/v1/trajectory` | `POST /capacity-cost-p2g`, `POST /modulation-p2g` |
| ResController | `/v1/trajectory` | `GET /res-types`, `POST /load-factor-res`, `POST /installed-power-res`, `POST /technology-distribution-res`, `POST /zonal-distribution-res` |
| ScenarioBuilderController | `/v1/trajectory` | `POST /scenario-builder` |
| SettingsController | `/v1/trajectory` | `POST /settings` |
| StStorageController | `/v1/trajectory` | `POST /st-storage`, `POST /st-storage-me` |
| ThermalController | `/v1/trajectory` | `POST /thermal-capacity`, `POST /thermal-common-parameter`, `POST /thermal-specific-parameter`, `POST /thermal-modulation-parameter`, `POST /thermal-economic-costs`, `POST /thermal-economic-parameter`, `POST /param-modulation/check` |
