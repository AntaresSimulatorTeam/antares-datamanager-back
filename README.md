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
- [Import Adequacy Patch](#import-adequacy-patch)

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

### Import Adequacy Patch

L'import d'une trajectoire Adequacy Patch est exposé par `POST /v1/trajectory/adequacy-patch`.

#### Entrées

L'endpoint attend les paramètres suivants :

| Paramètre | Format / rôle |
| --- | --- |
| `trajectoryToUse` | Nom du sous-répertoire de la trajectoire, non vide et limité à 60 caractères (hors préfixe reconnu, le cas échéant). |
| `horizon` | Horizon au format `YYYY-YYYY` (par exemple `2023-2024`). |
| `studyId` | Identifiant de l'étude à laquelle rattacher la trajectoire. |
| `isCivilYear` | Booléen obligatoire. Il est transmis au service mais n'est pas utilisé par le traitement actuel. |

Les quatre paramètres sont obligatoires. Les fichiers sont recherchés sous le répertoire Adequacy Patch défini par la configuration (`nasDirectory` + `trajectoryFilePath` + `adequacyDirectory`), dans le sous-répertoire nommé `trajectoryToUse`. Ce sous-répertoire doit contenir un classeur Excel `default.xlsx` (la casse du nom de fichier est ignorée).

Le classeur doit contenir les feuilles suivantes :

| Feuille | Colonnes attendues |
| --- | --- |
| `perimetre` | Colonne A : nom de zone (`area`), colonne B : mode. La première ligne est ignorée. |
| `settings` | Colonne A : nom du paramètre, colonne B : valeur. Les noms sont convertis en minuscules et les tirets sont remplacés par des underscores. |

Les paramètres reconnus dans `settings` sont `include_adq_patch` (booléen), `price_taking_order` (texte), `include_hurdle_cost_csr` (booléen), `check_csr_cost_function` (booléen), `threshold_initiate_curtailment_sharing_rule` (entier), `threshold_display_local_matching_rule_violations` (entier), `threshold_csr_variable_bounds_relaxation` (entier), `redispatch` (booléen) et `set_to_null_ntc_from_physical_out_to_physical_in_for_first_step` (booléen).

#### Contrôles et traitement

- Le nom de trajectoire est refusé s'il est composé uniquement d'espaces ou dépasse la limite de longueur. La résolution du chemin empêche de sortir du répertoire autorisé, y compris via un lien symbolique.
- L'horizon doit respecter le motif `YYYY-YYYY`.
- Le fichier `default.xlsx` et les deux feuilles sont obligatoires. Chaque feuille doit fournir des données exploitables : au moins une zone dans `perimetre` et au moins un paramètre reconnu avec une valeur dans `settings`.
- Un identifiant d'étude inexistant fait échouer l'import. Une trajectoire déjà importée avec le même nom, le même type, le même horizon et le même contenu est refusée ; si son contenu a changé, une nouvelle version est créée.
- Les lignes de `perimetre` dont la zone est vide sont ignorées. Dans `settings`, seules les clés reconnues et les valeurs non nulles sont prises en compte ; les erreurs de conversion d'une valeur sont journalisées.

Le service calcule les métadonnées et l'empreinte de la trajectoire, lit les données des feuilles, rattache la trajectoire à l'étude, puis persiste les données dans une transaction. En cas de succès, l'API renvoie la trajectoire créée avec le statut HTTP `201 Created`. Les erreurs de requête ou de structure de classeur renvoient notamment un statut `400 Bad Request`.

#### Persistance en base de données

Les métadonnées et la version de la trajectoire sont enregistrées dans `trajectory`. Les données extraites sont enregistrées dans :

- `adequacy_patch_mode` : une ligne par zone, avec `area`, `mode` et une clé étrangère `trajectory_id` vers `trajectory.id`.
- `adequacy_patch_settings` : les paramètres de la feuille `settings`, avec une clé étrangère `trajectory_id` vers `trajectory.id`.

La relation entre la trajectoire importée et l'étude est enregistrée dans la table d'association `scenario_trajectory`.
