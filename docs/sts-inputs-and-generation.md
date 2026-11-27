# Données STS : format d'entrée et génération

Cette page décrit le traitement des trajectoires de stockage de court terme **STS classique** par `antares-datamanager-back` : fichiers attendus, transformations, contrôles et données intégrées au JSON de génération. Le périmètre **STS-ME / ME est exclu**.

Les noms de fichiers et les messages d'erreur ci-dessous reflètent le comportement implémenté dans le dépôt. Les messages en anglais sont conservés tels quels pour pouvoir les rapprocher des réponses de l'API.

## Import d'une trajectoire

L'import est exposé par `POST /v1/trajectory/st-storage`. Il reçoit les paramètres `area`, `technology`, `trajectoryToUse`, `horizon` (format `AAAA-AAAA`), `studyId` et `isCivilYear`. Le fichier de trajectoire est recherché sous :

```text
<NAS>/<PEGASE_TRAJECTORY_FILE_PATH>/<PEGASE_STS_DIRECTORY>/<technology>/clusters/
```

Le nom doit commencer par `cluster_<technology>_`. Les recherches de répertoire et de fichier sont insensibles à la casse ; le classeur de trajectoire peut être `.xlsx` ou `.xls`. Le classeur doit contenir une feuille nommée par l'année de fin de l'horizon demandé, par exemple `2030` pour `2029-2030`.

### Feuille de capacité

La première ligne de la feuille d'horizon contient les colonnes suivantes. Leur ordre n'est pas imposé et les noms sont comparés sans tenir compte de la casse.

| Colonne | Contenu / contrôle |
| --- | --- |
| `Area` | Zone de l'étude ; obligatoire pour chaque ligne non vide. |
| `Name` | Nom du cluster ; obligatoire. |
| `Group` | Groupe du cluster ; obligatoire. |
| `Injection` | Valeur numérique. |
| `Withdrawal` | Valeur numérique. |
| `Storage` | Valeur numérique. |
| `Efficiency_injection` | Valeur numérique comprise entre 0 et 1. |
| `Efficiency_withdrawal` | Valeur numérique. |
| `Initial_level` | Valeur numérique comprise entre 0 et 1. |
| `Initial_level_optim` | Booléen. |
| `Enabled` | Booléen. |
| `Series` | Booléen ; demande le chargement des fichiers de séries temporelles du cluster. |
| `Constraints` | Booléen ; active l'association des paramètres et séries de contraintes additionnelles. |
| `Allow_overflow` | Booléen obligatoire. |

Les booléens acceptent les cellules Excel booléennes, `0`/`1` et les chaînes reconnues par le parseur (`true`/`false`, `vrai`/`faux`, `oui`/`non`, `yes`/`no`, sans distinction de casse). Les lignes vides sont ignorées ; le traitement ne conserve que les zones de l'étude correspondant à la zone sélectionnée, ou toutes les zones pertinentes lorsque `area=OTHERS`. Une zone déjà importée est ignorée dans ce dernier mode.

### Séries temporelles des clusters

Si `Series=true`, les fichiers sont recherchés sous :

```text
<NAS>/<PEGASE_TRAJECTORY_FILE_PATH>/<PEGASE_STS_DIRECTORY>/<technology>/series/
  <nom-de-trajectoire-sans-préfixe-cluster_technology_>/<cluster>/<area>/
```

Les cinq fichiers requis (noms comparés sans tenir compte de la casse) sont :

| Fichier | Série |
| --- | --- |
| `inflows.xlsx` | Apports |
| `lower_curve.xlsx` | Courbe basse |
| `Pmax_injection.xlsx` | Puissance maximale d'injection |
| `Pmax_soutirage.xlsx` | Puissance maximale de soutirage |
| `upper_curve.xlsx` | Courbe haute |

Trois séries sont facultatives : `cost.xlsx`, `variation_cost.xlsx` et `level_cost.xlsx`. Chaque fichier Excel est lu sur la feuille correspondant à l'horizon de génération : la première ligne fournit les noms des colonnes et un maximum de 8 784 lignes est lu. Les séries sont converties en matrices numériques puis écrites en fichiers Arrow dans le répertoire de sortie STS configuré ; les noms générés sont référencés dans les propriétés du cluster. Les cellules vides ou non numériques d'une matrice Excel sont interprétées comme `0.0` par le lecteur de séries.

### Contraintes additionnelles

Les contraintes additionnelles se trouvent sous :

```text
<NAS>/<PEGASE_TRAJECTORY_FILE_PATH>/<PEGASE_STS_DIRECTORY>/<technology>/constraints/
```

Le classeur de paramètres `Additional-constraints.xlsx` est facultatif. Lorsqu'il est présent, il doit contenir les deux feuilles `parameters` et `hours` (noms sensibles à la casse) et des données autres que l'en-tête dans chacune. Les en-têtes sont vérifiés sans tenir compte de la casse ; leur ordre n'est pas imposé.

| Feuille | En-têtes requis | Signification |
| --- | --- | --- |
| `parameters` | `name`, `zone`, `cluster`, `variable`, `operator`, `enabled` | Décrit un paramètre pour une zone et un cluster. `name`, `zone` et `cluster` doivent être renseignés ; `enabled` est converti en booléen et vaut `false` par défaut si sa valeur est absente ou illisible. |
| `hours` | `name`, `zone`, `cluster`, `occurrence`, `start`, `end` | Associe au paramètre correspondant ses plages horaires par occurrence. Les trois premières valeurs doivent correspondre à une entrée de `parameters` ; `occurrence`, `start` et `end` sont convertis en entiers (les cellules numériques décimales sont tronquées à la conversion). |

Les lignes des autres zones de l'étude sont filtrées selon la zone demandée (`OTHERS` inclut les zones de l'étude). Les heures `start` et `end` sont converties en une liste inclusive d'heures par occurrence lors de la génération ; une occurrence sans plage devient une liste vide. Le classeur de séries de contraintes est nommé d'après la trajectoire, après retrait du préfixe `cluster_<technology>_`, avec l'extension `.xlsx`. Sa feuille d'horizon contient en en-tête les noms de colonnes correspondant aux paramètres (comparaison insensible à la casse) et les colonnes utilisées pour la génération se terminent par le suffixe de zone `_AREA`, par exemple `_FR`. Les colonnes retrouvées sont exportées en matrices Arrow ; les noms générés sont ajoutés à la liste de séries de contraintes du cluster.

Le code associe le chemin de ce classeur aux paramètres retenus. Il ne valide pas systématiquement sa présence à l'import ; lors de la génération, un classeur absent est ignoré.

## Transformations et données générées

À l'import, chaque ligne STS retenue devient une entité de stockage liée à la trajectoire, à la zone et à la technologie. Les valeurs de capacité, rendements, niveau initial et indicateurs sont converties dans leurs types métier. Le nom de trajectoire, l'horizon, la technologie, la version et l'empreinte du fichier sont également enregistrés. Une trajectoire déjà traitée avec le même contenu est refusée ; un contenu différent produit une nouvelle version.

Lors de la génération d'étude, les clusters dont la somme `Injection + Withdrawal + Storage` est nulle ou négative sont écartés. Les autres sont regroupés sous leur clé `ZONE_nomDuCluster`, par exemple `FR_battery_1`, et attachés à l'objet de zone `sts`. La structure produite pour un cluster suit cette forme :

```json
{
  "FR_battery_1": {
    "properties": {
      "enabled": true,
      "group": "battery",
      "injection_nominal_capacity": 100,
      "withdrawal_nominal_capacity": 80,
      "reservoir_capacity": 500,
      "efficiency": 0.9,
      "efficiency_withdrawal": 0.9,
      "initial_level": 0.5,
      "initial_level_optim": false,
      "allow_overflow": false
    },
    "series": {
      "series": ["inflows.xlsx.<identifiant>.arrow", "lower_curve.xlsx.<identifiant>.arrow"]
    },
    "constraintParameters": {
      "paramètre": {
        "variable": "…",
        "operator": "…",
        "enabled": "true",
        "hours": [[1, 2, 3]]
      }
    },
    "stsConstraintsSeriesList": ["daily_min_FR.csv.<identifiant>.arrow"]
  }
}
```

Les champs de contraintes sont omis lorsqu'ils sont vides ; de même, les séries ne contiennent que les fichiers effectivement chargés. `hours` est une liste de listes indexées par occurrence. L'exemple est illustratif : les valeurs numériques et noms de séries dépendent des fichiers fournis et de la configuration de sortie.

## Contrôles et messages d'erreur

Les messages ci-dessous sont les modèles levés par les contrôles STS classiques et les traitements de génération associés. Les `{0}`, `{1}`, etc. sont remplacés dans la réponse API par les arguments correspondants. Dans les messages construits par concaténation, les valeurs dynamiques sont indiquées entre chevrons.

### Fichier et feuille de capacité

| Contrôle | Message |
| --- | --- |
| Préfixe de trajectoire incorrect | ` {0} Trajectory name must start with : {1} ` |
| Classeur ambigu pour une même trajectoire | `STS cluster file is ambiguous for trajectory {0}` |
| Feuille d'horizon absente | `Horizon {0} does not exist in the {1} trajectory {2}` |
| En-têtes manquants | `Missing columns {0} in {1} trajectory {2}` |
| Aucune donnée pour l'horizon (le texte entre chevrons représente la valeur d'horizon concaténée au message) | `No ST Storage data found in the file for horizon: <horizon>` |
| Zone absente d'une ligne | `Area is missing in STS trajectory <trajectoryFileName> for row: <rowIndex>` |
| Nom de cluster absent | `No valid cluster name found in the trajectory {0} for area {1} and horizon {2}` |
| Groupe absent | `No valid cluster group found in the trajectory {0} for area {1} and horizon {2}` |
| Valeur de capacité ou de rendement non numérique | `Values Injection, Withdrawal, Storage, Efficiency_injection, Efficiency_withdrawal and Initial_level must be numeric in STS trajectory {0}` |
| `Efficiency_injection` ou `Initial_level` hors de `[0, 1]` | `Values Efficiency_injection and Initial_level must be between 0 and 1 in STS trajectory {0}` |
| Valeur booléenne invalide dans `Initial_level_optim`, `Enabled`, `Series` ou `Constraints` | `Values Initial_level_optim, Enabled, Series and Constraints must be boolean in STS trajectory {0}` |
| `Allow_overflow` vide | `Empty values found for Allow_overflow for area {0}, horizon {1} in {2} trajectory` |
| `Allow_overflow` non booléen | `Waiting for boolean value(s) in column(s) Allow_overflow for area {0} in {1} trajectory` |
| Aucune zone de la trajectoire AREA dans les lignes STS | `None of the areas of trajectory AREA are present in STS trajectory <trajectoryFileName>` |
| Zone sélectionnée absente de la colonne `Area` | `Selected area {0} is not present in the 'node' column of STS trajectory {1}` |
| Import `OTHERS` sans nouvelle zone | `No new STS areas to import for trajectory {0}; all study areas are already covered` |
| Contenu déjà importé | `File already processed  with same content : {0}` |

### Fichiers de séries des clusters

| Contrôle | Message |
| --- | --- |
| Fichiers requis manquants quand `Series=true` | `Can not import : Missing TS files: {0} for trajectory {1}` |
| Fichier facultatif ambigu pendant l'import | `Ambiguous STS series file: {0}` |
| Fichier facultatif qui n'est pas un fichier régulier | `Invalid STS file: {0}` |
| Fichier requis manquant à la génération | `Required STS series file not found: {0}` |
| Fichier requis invalide ou ambigu à la génération | `Invalid or ambiguous required STS series file {0} in {1}` |
| Fichier facultatif invalide ou ambigu à la génération | `Invalid or ambiguous optional STS series file {0} in {1}` |
| Échec de lecture d'une série | Le message de `BusinessException` du lecteur est propagé avec le suffixe ` for series in {chemin} for STS {groupe}` ; les autres exceptions sont renvoyées avec leur message. |
| Échec d'accès au répertoire des séries | `Failed to list STS series files` |
| Interruption de la génération des séries | `Interrupted while generating STS series files` |
| Échec technique de génération des séries | `Failed to generate STS series files` |
| Interruption de la génération des séries de contraintes | `Interrupted while generating STS constraints series files` |
| Échec technique de génération des séries de contraintes | `Failed to generate STS constraints series files` |

### Classeur de contraintes additionnelles

| Contrôle | Message |
| --- | --- |
| Feuille `parameters` ou `hours` absente | `Missing parameters or hours data in STS {0} Additional Constraints file` |
| Feuille sans données après l'en-tête | `No data found in {0} tab in STS {1} Additional Constraint file` |
| En-têtes manquants dans `parameters` ou `hours` | `Missing columns {0} in {1} tab in STS {2} Additional Constraint file` |
| `name`, `zone` ou `cluster` vide | `Values name, zone and cluster must not be empty in {0} tab STS {1} Additional Constraint` |
| Paramètre de `hours` absent de `parameters` | `Parameter: name={0}, zone={1}, cluster={2}, not found in parameters sheet` |
| `occurrence`, `start` ou `end` non numérique | `Values for node {0} / cluster {1} are not numeric in STS Additional Constraint {2}` |
| Deux paramètres ont le même `name` et le même `cluster` (comparaison insensible à la casse) | `Selected area/cluster {0}/{1} not present in the 'zone' column of {2} tab in STS {3} Additional Constraint file` |
| Heures incomplètes à la génération | `Invalid hours for constraint {0}: occurrence/start/end must be set` |
| Heure de début après l'heure de fin | `Invalid hours for constraint {0}: start must be <= end` |
| `Allow_overflow` absent à la génération | `Allow_overflow is missing for STS area {0}, cluster {1}` |

Les erreurs d'accès disque ou de configuration et les erreurs techniques génériques peuvent produire d'autres messages ; ils ne sont pas des contrôles de format répertoriés ci-dessus. Les exceptions métier sont renvoyées par l'API avec leur type, code d'erreur et statut HTTP.
