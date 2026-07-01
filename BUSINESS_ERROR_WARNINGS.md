# Liste des Erreurs Business et Avertissements

Ce document recense les erreurs (`BusinessException`) et les avertissements (`WarningCode`) possibles dans l'application, classés par catégorie et par moment d'occurrence (Import des données ou Génération de l'étude).

## Sommaire
- [Général](#général)
- [Areas (Zones)](#areas-zones)
- [Links (Liens)](#links-liens)
- [Thermal (Thermique)](#thermal-thermique)
- [RES (Renouvelables)](#res-renouvelables)
- [Hydro (Hydraulique)](#hydro-hydraulique)
- [STS (Stockage Court Terme)](#sts-stockage-court-terme)
- [Load (Consommation)](#load-consommation)
- [Misc (Divers)](#misc-divers)

---

## Général
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `data.not.found` | Global | Élément non trouvé dans la base de données. |
| `Sheet {0} not found in file: {1}` | Import | L'onglet spécifié est absent du fichier Excel. |
| `File {0} does not contain a valid header row` | Import | La ligne d'en-tête est absente ou invalide. |
| `Invalid column(s) name(s): {0}` | Import | Noms de colonnes incorrects dans le fichier. |
| `Columns: {0} not found` | Import | Colonnes obligatoires manquantes dans le fichier. |
| `Value too long for {0}(s) : {1}` | Import | La valeur dépasse la longueur maximale autorisée (ex: 10 caractères pour une zone). |
| `duplication.missing_trajectories` | Génération | Lors d'une duplication, certaines trajectoires sont manquantes pour l'horizon donné. |

## Areas (Zones)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `Waiting for Numeric values in {0} columns for area(s) {1}` | Import | Valeurs non numériques dans les colonnes de coûts (Spilled/Unsupplied). |
| `Waiting for positive Numeric values in {0} columns for area(s) {1}` | Import | Valeurs négatives non autorisées pour les coûts. |
| `Duplicate value for area(s): {0}` | Import | Une même zone est présente plusieurs fois dans le fichier AREAS. |

## Links (Liens)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `Waiting for Numeric Value(s) in column(s) {0} for link(s) {1}` | Import | Les capacités des liens doivent être numériques. |
| `Waiting for Positive Value(s) in column(s) {0} for link(s) {1}` | Import | Les capacités des liens doivent être positives. |
| `links.all_values_zero` | Import (Warning) | Zone(s) isolée(s) : toutes les capacités du lien sont à zéro. |
| `links.unilateral_values_zero` | Import (Warning) | Liens unilatéraux à zéro détectés. |
| `links.area_not_present` | Import (Warning) | Des zones définies dans la trajectoire AREA sont absentes du fichier LINKS. |
| `areas.not_alphabetically_ordered` | Import (Warning) | Les zones d'un lien (Area1-Area2) doivent être classées par ordre alphabétique. |
| `Duplicate value in column 'Name'... Values: {0} are considered identical` | Import | Doublon de lien (ex: A-B et B-A sont identiques). |

## Thermal (Thermique)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `Missing costs/rate data in trajectory {0}` | Import | Fichier de coûts thermiques incomplet (onglets 'costs' ou 'rate' manquants). |
| `Horizon does not exist in THERMAL Costs trajectory` | Import | L'horizon de l'étude est absent du fichier de coûts thermiques. |
| `No data for horizon {0} in THERMAL Costs trajectory` | Import | L'horizon est présent mais ne contient aucune donnée. |
| `Clusters : {0} are not in {1} trajectory` | Import | Clusters présents dans la puissance installée mais absents des paramètres techniques (Common/Specific). |
| `Fuel {0} does not exist in Trajectory {1} for horizon {2}` | Import | Le combustible défini en paramètres techniques est absent de la trajectoire de coûts économiques. |
| `thermal.installed_power_missing_areas` | Génération (Warning) | Zones manquantes dans la puissance installée thermique. |
| `thermal.specific_param_missing_areas` | Génération (Warning) | Zones manquantes dans les paramètres spécifiques thermiques. |

## RES (Renouvelables)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `No load-factor series found for area {0}, group {1}, cluster {2}` | Génération | Série de facteur de charge (TS) manquante pour un cluster RES. |
| `Invalid zonal distribution... sum must not be over 100%` | Génération | La somme des coefficients de distribution zonale dépasse 1. |
| `Invalid technology distribution... sum must not be over 100%` | Génération | La somme des coefficients de distribution technologique dépasse 1. |
| `Missing FR aggregation data / mapping` | Génération | Données d'agrégation France manquantes pour le groupe/cluster RES. |
| `Multiple load-factor series found for area {0}...` | Génération | Conflit : plusieurs fichiers TS correspondent au même cluster. |

## Hydro (Hydraulique)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `Missing folder {0} in Hydro trajectory` | Import | Dossier obligatoire (inflows, mingen, reservoir_levels) manquant. |
| `Missing maxpower file (maxpower_{0})` | Import | Fichier de puissance maximale hydro manquant. |
| `Missing file hydroAllocation or hydroParameters` | Import | Fichiers techniques hydro manquants dans la trajectoire. |
| `Missing MOD file ({0})` | Import | Fichiers de modulation (MOD) manquants pour les zones ayant des données mingen ou reservoir. |
| `Allocation column... must be filled and of numeric type` | Import | Erreur de format dans le fichier d'allocation hydro. |

## STS (Stockage Court Terme)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `Trajectory name must start with : cluster_{technology}_` | Import | Nom de fichier STS invalide. |
| `No valid cluster name/group found` | Import | Nom ou groupe de cluster manquant dans le fichier STS. |
| `Values Efficiency_injection and Initial_level must be between 0 and 1` | Import | Erreur de plage de valeurs pour les rendements/niveaux STS. |
| `Missing TS files / Additional constraint files` | Import | Fichiers de séries temporelles ou de contraintes additionnelles manquants sur le NAS. |
| `sts_missing_areas` | Génération (Warning) | Zones manquantes pour le stockage court terme. |

## Load (Consommation)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `load.missing_trajectories_for_areas` | Génération (Warning) | Fichiers de consommation manquants pour certaines zones. |

## Misc (Divers)
| Message / Code | Moment | Description |
| :--- | :--- | :--- |
| `The trajectory file name must start with installedMisc_` | Import | Nom de fichier MISC invalide. |
| `Group {0} is not a valid group` | Import | Groupe MISC inconnu (doit être Biomass, Biogas, Waste, etc.). |
| `Load factor file not found for group {0}` | Import | Fichier de facteur de charge MISC absent du NAS. |
| `Load factor file {0} is missing area {1}` | Import | Zone absente du fichier de facteur de charge MISC. |
