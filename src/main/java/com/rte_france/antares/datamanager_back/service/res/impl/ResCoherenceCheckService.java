package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ResTechnologyDistributionEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.res.impl.ResDomainRules.OTHERS_AREA;

/**
 * Service de validation pour vérifier la cohérence entre InstalledPower (IP/RES_CAPACITY)
 * et Technology Distribution (TD/RES_TECHNOLOGY_DISTRIBUTION).
 * Et également la cohérence entre IP et Load Factor (LF/RES_LOAD).
 * Et également la cohérence entre Load Factor (LF/RES_LOAD) et Distribution Technology (DT/RES_TECHNOLOGY_DISTRIBUTION).
 * <p>
 * Règles de contrôle:
 * - Validation conditionnelle basée sur les combinaisons de trajectoires
 * <p>
 * Pour IP:
 * - Les 4 combinaisons requises doivent être complètes :
 * * Area_specific (FR, BE, etc) sans technology
 * * Area_specific avec technology
 * * OTHERS sans technology
 * * OTHERS avec technology
 * <p>
 * Pour TD:
 * - Les 2 combinaisons requises doivent être complètes pour l'area:
 * * Area_specific (FR, BE, etc) sans technology
 * * Area_specific avec technology
 * <p>
 * Pour LF:
 * - Les 4 combinaisons requises doivent être complètes :
 * * Area_specific (FR, BE, etc) sans technology
 * * Area_specific avec technology
 * * OTHERS sans technology
 * * OTHERS avec technology
 * <p>
 * Note: area_specific peut être n'importe quel area défini dans defaultConfigService.fetchAllDefaults()
 * Le contrôle de cohérence IP/TD, IP/LF et LF/DT se lance seulement quand TOUTES les combinaisons existent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResCoherenceCheckService {

    private final TrajectoryRepository trajectoryRepository;
    private final DefaultConfigService defaultConfigService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    /**
     * Valide la cohérence entre les trajectoires IP et TD pour un study donné.
     * Cette validation s'effectue uniquement si on importe une trajectoire qui fait partie
     * des combinaisons requises, et les autres combinaisons doivent déjà exister en BD.
     *
     * @param studyId l'identifiant de l'étude
     * @throws BusinessException si l'import viole les conditions de cohérence
     */
    public void validateIPTDCoherence(Integer studyId) {
        validateIPTDCoherence(studyId, null);
    }

    /**
     * Valide la cohérence entre les trajectoires IP et TD pour un study donné.
     * Permet d'inclure une trajectoire temporaire (en cours d'import) dans la validation.
     *
     * @param studyId                 l'identifiant de l'étude
     * @param trajectoryBeingImported trajectoire optionnelle en cours d'import à inclure dans la validation
     * @throws BusinessException si la cohérence n'est pas respectée
     */
    public void validateIPTDCoherence(Integer studyId, TrajectoryEntity trajectoryBeingImported) {
        log.info("=== Début validation IP/TD (Étude {}) ===", studyId);
        
        if (trajectoryBeingImported == null) {
            log.info("Pas de trajectoire en cours d'import, validation skippée");
            return;
        }

        List<TrajectoryEntity> bdIpTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId);
        List<TrajectoryEntity> bdTdTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId);
        log.info("Trajectoires chargées - IP: {}, TD: {}", bdIpTrajectories.size(), bdTdTrajectories.size());

        String trajectoryType = trajectoryBeingImported.getType();
        log.info("Trajectoire importée - Type: {}, Area: {}", trajectoryType, trajectoryBeingImported.getArea());

        if (!validateTDCoherence(bdTdTrajectories, trajectoryBeingImported) || !validateIPCoherence(bdIpTrajectories, trajectoryBeingImported)) {
            log.info("Prérequis IP/TD non satisfaits, validation clés skippée");
            return;
        }

        if (TrajectoryType.RES_CAPACITY.name().equals(trajectoryType) && !bdTdTrajectories.isEmpty()) {
            // Valider les clés IP/TD
            validateIPTDKeysCoherence(bdIpTrajectories, trajectoryBeingImported, bdTdTrajectories, null);
        } else if (TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name().equals(trajectoryType) && !bdIpTrajectories.isEmpty()) {
            // Valider les clés IP/TD
            validateIPTDKeysCoherence(bdIpTrajectories, null, bdTdTrajectories, trajectoryBeingImported);
        }
    }

     /**
      * Valide que les 4 combinaisons requises d'IP existent après l'import.
      * Ne lève pas d'exception, retourne simplement un booléen.
      *
      * @return true si les 4 combinaisons (area±tech + OTHERS±tech) existent, false sinon
      */
     private boolean validateIPCoherence(List<TrajectoryEntity> bdIpTrajectories, TrajectoryEntity trajectoryBeingImported) {
         return validateFourCombinationCoherence(bdIpTrajectories, trajectoryBeingImported, 
                 TrajectoryType.RES_CAPACITY.name(), "IP");
     }

    /**
     * Valide que les 2 combinaisons requises de TD existent après l'import.
     * Ne lève pas d'exception, retourne simplement un booléen.
     *
     * @return true si les 2 combinaisons (FR sans tech + FR avec tech) existent, false sinon
     */
    public boolean validateTDCoherence(List<TrajectoryEntity> bdTdTrajectories, TrajectoryEntity trajectoryBeingImported) {
        String area = trajectoryBeingImported.getArea();
        String technology = trajectoryBeingImported.getTechnology();
        log.info("Validation TD - Area: {}, Technology: {}", area, technology);

        Set<String> defaultAreas = getDefaultAreas();

        if (!defaultAreas.contains(area.toUpperCase()) && !area.equals(OTHERS_AREA)) {
            log.info("TD avec area {} non reconnu, validation skippée", area);
            return false;
        }

        List<TrajectoryEntity> allTdTrajectories = new ArrayList<>(bdTdTrajectories);
        if (trajectoryBeingImported.getType().equals(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name())) {
            allTdTrajectories.add(trajectoryBeingImported);
        }
        boolean result = hasCompletedTDCombinations(allTdTrajectories, area, technology);
        log.info("Résultat validation TD: {}", result);
        return result;
    }

     /**
      * Vérifie si les 2 combinaisons requises de TD sont présentes pour une area donnée.
      * Les 2 combinaisons = area_specific (sans tech) + area_specific (avec tech)
      * où area_specific peut être FR, BE, DE, etc. (n'importe quel defaultArea)
      *
      * @return true si les 2 combinaisons sont complètes, false sinon
      */
     private boolean hasCompletedTDCombinations(List<TrajectoryEntity> tdTrajectories, String area, String importedTechnology) {
         // Vérifier qu'il existe une trajectoire avec area sans technology
         boolean hasWithoutTech = tdTrajectories.stream().anyMatch(trajectory -> (trajectory.getArea().equals(area) || (area.equals(OTHERS_AREA))) && isBlankOrEmpty(trajectory.getTechnology()));

         // Si importedTechnology est null : vérifier qu'il existe au moins une avec n'importe quelle technology
         if (isBlankOrEmpty(importedTechnology)) {
             boolean hasWithSomeTech = tdTrajectories.stream().anyMatch(trajectory -> (trajectory.getArea().equals(area) || (area.equals(OTHERS_AREA))) && !isBlankOrEmpty(trajectory.getTechnology()));
             return hasWithoutTech && hasWithSomeTech;
         }

         // Si importedTechnology n'est pas null : vérifier qu'il existe une avec cette technology
         boolean hasWithImportedTech = tdTrajectories.stream().anyMatch(trajectory -> (trajectory.getArea().equals(area) || (area.equals(OTHERS_AREA))) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equals(importedTechnology));

         return hasWithoutTech && hasWithImportedTech;
     }

     /**
      * Vérifie si les 4 combinaisons requises existent pour une area donnée.
      * Les 4 combinaisons = area_specific (sans tech) + area_specific (avec tech) + OTHERS (sans tech) + OTHERS (avec tech)
      * où area_specific peut être FR, BE, DE, etc. (n'importe quel defaultArea)
      * Utilisée pour IP (RES_CAPACITY) et LF (RES_LOAD).
      *
      * @param areaParam         filtre sur l'area spécifique requise
      * @return true si les 4 combinaisons sont complètes, false sinon
      */
     private boolean hasCompletedFourCombinations(List<TrajectoryEntity> trajectories, String areaParam, String importedTechnology) {
         // Vérifications communes
         boolean hasAreaWithoutTech = trajectories.stream().anyMatch(trajectory -> !trajectory.getArea().equals(OTHERS_AREA) && isBlankOrEmpty(trajectory.getTechnology()));

         boolean hasOthersWithoutTech = trajectories.stream().anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && isBlankOrEmpty(trajectory.getTechnology()));

         // Si importedTechnology est null : vérifier hasAreaWithoutTech, hasOthersWithoutTech 
         // et au moins deux trajectoires avec la même technology (une pour area, une pour OTHERS)
         if (isBlankOrEmpty(importedTechnology)) {
             // Obtenir les technologies disponibles pour l'area spécifique
             Set<String> areaWithTechList = trajectories.stream()
                     .filter(trajectory -> !trajectory.getArea().equals(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()))
                     .map(TrajectoryEntity::getTechnology)
                     .collect(Collectors.toSet());

             // Obtenir les technologies disponibles pour OTHERS
             Set<String> othersWithTechList = trajectories.stream()
                     .filter(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()))
                     .map(TrajectoryEntity::getTechnology)
                     .collect(Collectors.toSet());

             // Vérifier qu'il existe une technology commune entre l'area et OTHERS
             boolean hasSharedTechnology = areaWithTechList.stream().anyMatch(othersWithTechList::contains);

             return hasAreaWithoutTech && hasOthersWithoutTech && hasSharedTechnology;
         }

         // Si importedTechnology n'est pas null : vérifier les 4 combinaisons requises
         boolean hasAreaWithTech = trajectories.stream().anyMatch(trajectory -> !trajectory.getArea().equals(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equalsIgnoreCase(importedTechnology));

         boolean hasOthersWithTech = trajectories.stream().anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equalsIgnoreCase(importedTechnology));

         return hasAreaWithoutTech && hasAreaWithTech && hasOthersWithoutTech && hasOthersWithTech;
     }


     /**
      * Récupère l'ensemble des areas spécifiques (définis dans DefaultConfig).
      */
     private Set<String> getDefaultAreas() {
         return defaultConfigService.fetchAllDefaults().stream().map(DefaultLoadDTO::getName).map(String::toUpperCase).collect(Collectors.toSet());
     }


    /**
     * Valide que toutes les clés (area/groupe/cluster) de IP existent dans TD.
     * Les prérequis de combinaisons doivent déjà être validés.
     */
     private void validateIPTDKeysCoherence(List<TrajectoryEntity> bdIpTrajectories, TrajectoryEntity ipBeingImported, List<TrajectoryEntity> bdTdTrajectories, TrajectoryEntity tdBeingImported) {
         log.info("=== VALIDATION DES CLÉS IP/TD ===");
         
         final String area;
         String importedTechnology = null;
         List<TrajectoryEntity> allIpTrajectories = new ArrayList<>(bdIpTrajectories);
         if (ipBeingImported != null) {
             area = ipBeingImported.getArea();
             allIpTrajectories.add(ipBeingImported);
             importedTechnology = ipBeingImported.getTechnology();
         } else if (tdBeingImported != null) {
             area = tdBeingImported.getArea();
             importedTechnology = tdBeingImported.getTechnology();
         } else {
             return;
         }

         List<TrajectoryEntity> allTdTrajectories = new ArrayList<>(bdTdTrajectories);
         if (tdBeingImported != null) {
             allTdTrajectories.add(tdBeingImported);
             if (importedTechnology == null) {
                 importedTechnology = tdBeingImported.getTechnology();
             }
         }

         log.info("Extraction technologies disponibles pour area: {}", area);

         // Extraire les technologies disponibles dans les trajectoires TD avec technologie
         // Les technologies doivent exister à la fois avec l'area spécifique ET avec area = OTHERS
         Set<String> technologiesWithSpecificArea = allTdTrajectories.stream()
                 .filter(t -> !t.getArea().equals(OTHERS_AREA) && t.getTechnology() != null && !t.getTechnology().trim().isEmpty())
                 .flatMap(t -> t.getResTechnologyDistributionCapacityEntities() != null ? t.getResTechnologyDistributionCapacityEntities().stream() : Stream.empty())
                 .map(ResTechnologyDistributionEntity::getGroupe)
                 .filter(groupe -> !isBlankOrEmpty(groupe))
                 .collect(Collectors.toSet());

         Set<String> availableTDTechnologies = new HashSet<>(technologiesWithSpecificArea);
         log.info("Technologies disponibles (intersection): {}", availableTDTechnologies);

         Set<String> tdKeys = extractTDKeys(allTdTrajectories, area, availableTDTechnologies, importedTechnology);
         Set<String> ipKeys = extractIPKeysForDT(allIpTrajectories, area, availableTDTechnologies, importedTechnology);
         
         log.info("Clés IP trouvées: {}, Clés TD trouvées: {}", ipKeys.size(), tdKeys.size());

         // Vérifier que chaque clé IP existe dans TD
         Set<String> missingKeys = ipKeys.stream().filter(key -> !tdKeys.contains(key)).collect(Collectors.toSet());

         if (!missingKeys.isEmpty()) {
             String missingKeysStr = String.join(", ", missingKeys);
             log.error("Clés manquantes dans Technology Distribution: {}", missingKeysStr);
             throw BusinessException.builder().message("Cohérence IP/TD échouée. Clés manquantes dans Technology Distribution: {0}").errorMessageArguments(List.of(missingKeysStr)).httpStatus(HttpStatus.BAD_REQUEST).build();
         }

         log.info("✅ Validation des clés IP/TD réussie");
    }

    /**
     * Extrait les clés uniques (area/groupe/cluster) de tous les IP.
     * Retourne les clés liées aux trajectoires avec technologie et les clés des trajectoires
     * sans technologie dont le groupe correspond aux technologies disponibles.
     * Format: "area/groupe/cluster"
     */
    private Set<String> extractIPKeysForDT(List<TrajectoryEntity> ipTrajectories, String area, Set<String> availableTechnologies, String importedTechnology) {
        return getResClusterCapacityEntityStream(ipTrajectories, area, availableTechnologies, importedTechnology)
                .map(entity -> formatKey(entity.getArea(), entity.getGroupe(), entity.getCluster()))
                .collect(Collectors.toSet());
    }

    private Set<String> extractIPKeysForIP(List<TrajectoryEntity> ipTrajectories, String area, Set<String> availableTechnologies, String importedTechnology) {
        return getResClusterCapacityEntityStream(ipTrajectories, area, availableTechnologies, importedTechnology)
                .map(entity -> String.format("%s/%s/%s/%s", entity.getArea(), entity.getGroupe(), entity.getCluster() != null ? entity.getCluster() : "", entity.getPecdZone() != null ? entity.getPecdZone() : ""))
                .collect(Collectors.toSet());
    }

    private static Stream<ResClusterCapacityEntity> getResClusterCapacityEntityStream(List<TrajectoryEntity> ipTrajectories, String area, Set<String> availableTechnologies, String importedTechnology) {
        return ipTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResClusterCapacityEntities() != null ? trajectory.getResClusterCapacityEntities().stream() : Stream.empty())
                .filter(entity -> {
                    boolean areaMatches = entity.getArea().equals(area) || area.equals(ResDomainRules.OTHERS_AREA);
                    if (!areaMatches) return false;
                    
                    // If importedTechnology is null, extract entities with groups in availableTechnologies
                    if (importedTechnology == null || importedTechnology.isEmpty()) {
                        return availableTechnologies.contains(entity.getGroupe());
                    }
                    
                    // If importedTechnology is not null, match it and check if it's available
                    return importedTechnology.equals(entity.getGroupe()) && availableTechnologies.contains(entity.getGroupe());
                });
    }

    /**
     * Extrait les clés uniques (area/groupe/cluster) de tous les TD.
     * Retourne les clés liées aux trajectoires avec technologie et les clés des trajectoires
     * sans technologie dont le groupe correspond aux technologies disponibles.
     * Format: "area/groupe/cluster"
     */
    private Set<String> extractTDKeys(List<TrajectoryEntity> tdTrajectories, String area, Set<String> availableTechnologies, String importedTechnology) {
        return tdTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResTechnologyDistributionCapacityEntities() != null ? trajectory.getResTechnologyDistributionCapacityEntities().stream() : Stream.empty())
                .filter(entity -> {
                    boolean areaMatches = entity.getArea().equals(area) || area.equals(OTHERS_AREA);
                    if (!areaMatches) return false;
                    
                    // If importedTechnology is null, extract entities with groups in availableTechnologies
                    if (importedTechnology == null) {
                        return availableTechnologies.contains(entity.getGroupe());
                    }
                    
                    // If importedTechnology is not null, match it and check if it's available
                    return importedTechnology.equals(entity.getGroupe()) && availableTechnologies.contains(entity.getGroupe());
                })
                .map(entity -> formatKey(entity.getArea(), entity.getGroupe(), entity.getCluster()))
                .collect(Collectors.toSet());
    }

    /**
     * Vérifie si une chaîne est null ou vide/blanche.
     *
     * @param value la chaîne à vérifier
     * @return true si la chaîne est null ou vide, false sinon
     */
    private boolean isBlankOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Formate une clé avec les composants area/groupe/cluster.
     */
    private String formatKey(String area, String groupe, String cluster) {
        return String.format("%s/%s/%s", area != null ? area : "", groupe != null ? groupe : "", cluster != null ? cluster : "");
    }


    /**
     * Valide la cohérence entre les trajectoires IP et LF pour un study donné.
     * Contrôle du Scénario 13: Import/Sélection d'une trajectoire IP avec Trajectoire(s) LF liée(s) à l'étude.
     * <p>
     * La validation s'effectue en deux étapes:
     * 1. Vérifier que les combinaisons requises existent (4 pour IP, 2 pour LF)
     * 2. Vérifier que pour chaque groupe/cluster/area de l'IP, il existe les fichiers correspondants dans le NAS
     *
     * @param studyId l'identifiant de l'étude
     * @throws BusinessException si l'import viole les conditions de cohérence
     */
    public void validateIPLoadFactorCoherence(Integer studyId) {
        validateIPLoadFactorCoherence(studyId, null);
    }

    /**
     * Valide la cohérence entre les trajectoires IP et LF pour un study donné.
     * Permet d'inclure une trajectoire temporaire (en cours d'import) dans la validation.
     *
     * @param studyId                 l'identifiant de l'étude
     * @param trajectoryBeingImported trajectoire optionnelle en cours d'import à inclure dans la validation
     * @throws BusinessException si la cohérence n'est pas respectée
     */
    public void validateIPLoadFactorCoherence(Integer studyId, TrajectoryEntity trajectoryBeingImported) {
        log.info("=== Début validation IP/Load Factor (Étude {}) ===", studyId);
        
        if (trajectoryBeingImported == null) {
            log.info("Pas de trajectoire en cours d'import, validation IP/LF skippée");
            return;
        }

        List<TrajectoryEntity> bdIpTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId);
        List<TrajectoryEntity> bdLfTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId);
        log.info("Trajectoires chargées - IP: {}, LF: {}", bdIpTrajectories.size(), bdLfTrajectories.size());

        String trajectoryType = trajectoryBeingImported.getType();
        log.info("Trajectoire importée - Type: {}, Area: {}", trajectoryType, trajectoryBeingImported.getArea());

        if (TrajectoryType.RES_CAPACITY.name().equals(trajectoryType)) {
            if (!validateIPCoherence(bdIpTrajectories, trajectoryBeingImported) || !validateLFCoherence(bdLfTrajectories, trajectoryBeingImported)) {
                log.info("Prérequis IP/LF non satisfaits, validation clés skippée");
                return;
            }
            if (!bdLfTrajectories.isEmpty()) {
                log.info("Lancement validation fichiers LF...");
                validateIPLFFilesCoherence(bdIpTrajectories, trajectoryBeingImported, bdLfTrajectories);
            }
        } else if (TrajectoryType.RES_LOAD.name().equals(trajectoryType)) {
            if (!validateLFCoherence(bdLfTrajectories, trajectoryBeingImported) || !validateIPCoherence(bdIpTrajectories, trajectoryBeingImported)) {
                log.info("Prérequis IP/LF non satisfaits, validation clés skippée");
                return;
            }
            if (!bdIpTrajectories.isEmpty()) {
                log.info("Lancement validation fichiers LF...");
                validateIPLFFilesCoherence(bdIpTrajectories, trajectoryBeingImported, bdLfTrajectories);
            }
        }
    }

     /**
      * Valide que les 4 combinaisons requises de LF existent après l'import.
      * Ne lève pas d'exception, retourne simplement un booléen.
      * Les 4 combinaisons = area_specific (sans tech) + area_specific (avec tech) + OTHERS (sans tech) + OTHERS (avec tech)
      *
      * @return true si les 4 combinaisons existent, false sinon
      */
     private boolean validateLFCoherence(List<TrajectoryEntity> bdLfTrajectories, TrajectoryEntity trajectoryBeingImported) {
         return validateFourCombinationCoherence(bdLfTrajectories, trajectoryBeingImported, 
                 TrajectoryType.RES_LOAD.name(), "LF");
     }
     
     /**
      * Validation générique pour les trajectoires avec 4 combinaisons requises (area±tech + OTHERS±tech).
      * Utilisée pour IP (RES_CAPACITY) et LF (RES_LOAD).
      */
     private boolean validateFourCombinationCoherence(List<TrajectoryEntity> bdTrajectories,
                                                     TrajectoryEntity trajectoryBeingImported,
                                                     String expectedType,
                                                     String trajectoryLabel) {
         String areaParam = trajectoryBeingImported.getArea();
         String technology = trajectoryBeingImported.getTechnology();

         Set<String> defaultAreas = getDefaultAreas();

         if (OTHERS_AREA.equalsIgnoreCase(areaParam) || defaultAreas.contains(areaParam.toUpperCase())) {
             List<TrajectoryEntity> allTrajectories = new ArrayList<>(bdTrajectories);
             if (trajectoryBeingImported.getType().equals(expectedType)) {
                 allTrajectories.add(trajectoryBeingImported);
             }
             boolean result = hasCompletedFourCombinations(allTrajectories, areaParam, technology);
             log.info("Vérification 4 combinaisons {} (Area: {}): {}", trajectoryLabel, areaParam, result);
             return result;
         } else {
             log.info("Area {} non reconnu pour {}, validation skippée", areaParam, trajectoryLabel);
             return false;
         }
     }


     /**
      * Valide que toutes les clés (area/groupe/cluster) de IP existent dans les fichiers LF du NAS.
     * Contrôle du Scénario 13: vérification des fichiers dans \RES\load factor\...
     * Les prérequis de combinaisons doivent déjà être validés.
     */
       public void validateIPLFFilesCoherence(List<TrajectoryEntity> bdIpTrajectories, TrajectoryEntity trajectoryBeingImported, List<TrajectoryEntity> bdLfTrajectories) {
           log.info("=== DÉBUT VALIDATION FICHIERS IP/LOAD FACTOR ===");
           
           String area = null;
           String horizon = null;
           String  importedTechnology  = null;
           List<TrajectoryEntity> allIpTrajectories = new ArrayList<>(bdIpTrajectories);

           if (trajectoryBeingImported != null) {
               area = trajectoryBeingImported.getArea();
               horizon = trajectoryBeingImported.getHorizon();
               if (TrajectoryType.RES_CAPACITY.name().equals(trajectoryBeingImported.getType())) {
                   allIpTrajectories.add(trajectoryBeingImported);
               }
               bdLfTrajectories.add(trajectoryBeingImported);
               importedTechnology  = trajectoryBeingImported.getTechnology();

           } else if (!allIpTrajectories.isEmpty()) {
               area = allIpTrajectories.getFirst().getArea();
               horizon = allIpTrajectories.getFirst().getHorizon();
           }

           if (isBlankOrEmpty(area) || isBlankOrEmpty(horizon)) {
               log.warn("Area ou horizon non trouvé, validation fichiers LF skippée");
               return;
           }

           log.info("Paramètres validation: Area={}, Horizon={}, Technology={}", area, horizon, importedTechnology);

           // Extraire les technologies disponibles dans les trajectoires LF avec technologie
           // Les technologies doivent exister à la fois avec l'area spécifique ET avec area = OTHERS
          Set<String> technologiesWithSpecificArea = bdLfTrajectories.stream()
                  .filter(t -> !t.getArea().equals(OTHERS_AREA))
                  .map(TrajectoryEntity::getTechnology)
                  .filter(trajectoryTechnology -> !isBlankOrEmpty(trajectoryTechnology))
                  .collect(Collectors.toSet());

          Set<String> technologiesWithOthersArea = bdLfTrajectories.stream()
                  .filter(t -> t.getArea().equals(OTHERS_AREA))
                  .map(TrajectoryEntity::getTechnology)
                  .filter(trajectoryTechnology -> !isBlankOrEmpty(trajectoryTechnology))
                  .collect(Collectors.toSet());

          // Intersection: les technologies présentes dans les deux
          Set<String> availableLFTechnologies = new HashSet<>(technologiesWithSpecificArea);
          availableLFTechnologies.retainAll(technologiesWithOthersArea);

          // Si aucune technologie disponible dans les LF, skip la validation
          if (availableLFTechnologies.isEmpty() && !isBlankOrEmpty(importedTechnology)) {
              log.info("Aucune technologie disponible dans les LF, validation fichiers skippée");
              return;
          }

          // Si importedTechnology est vide, filtrer allIpTrajectories pour garder que les trajectoires 
          // avec la meme technology et présente dans area specific et OTHERS
          if (isBlankOrEmpty(importedTechnology)) {
              Set<String> ipTechnologiesWithSpecificArea = allIpTrajectories.stream()
                      .filter(t -> !t.getArea().equals(OTHERS_AREA))
                      .filter(t -> !isBlankOrEmpty(t.getTechnology()))
                      .map(TrajectoryEntity::getTechnology)
                      .collect(Collectors.toSet());
              
              Set<String> ipTechnologiesWithOthersArea = allIpTrajectories.stream()
                      .filter(t -> t.getArea().equals(OTHERS_AREA))
                      .filter(t -> !isBlankOrEmpty(t.getTechnology()))
                      .map(TrajectoryEntity::getTechnology)
                      .collect(Collectors.toSet());
              
              // Intersection: les technologies présentes dans les deux
              ipTechnologiesWithSpecificArea.retainAll(ipTechnologiesWithOthersArea);
              
              // Filtrer allIpTrajectories pour garder que celles avec une technology commune
              final Set<String> commonTechnologies = ipTechnologiesWithSpecificArea;
              allIpTrajectories = allIpTrajectories.stream()
                      .filter(t -> isBlankOrEmpty(t.getTechnology()) || commonTechnologies.contains(t.getTechnology()))
                      .collect(Collectors.toList());
              availableLFTechnologies.retainAll(commonTechnologies);
          }

         // Extraire les clés IP filtrées par area et technologies disponibles
         Set<String> ipKeys = extractIPKeysForIP(allIpTrajectories, area, availableLFTechnologies, importedTechnology);

        // Pour chaque clé IP, vérifier que le fichier existe dans le répertoire NAS pour au moins une LF
        Set<String> missingFiles = new HashSet<>();
        for (String ipKey : ipKeys) {
            String[] parts = ipKey.split("/", -1);
            if (parts.length >= 4) {
                String areaKey = parts[0];
                String groupe = parts[1];
                String cluster = parts[2];
                String pecdZone = parts[3];

                boolean fileFoundInLF = false;
                for (TrajectoryEntity lfTrajectory : bdLfTrajectories) {
                    // Vérifier si le fichier existe dans le répertoire NAS pour cette LF
                    if (checkIfLoadFactorFileExists(lfTrajectory.getFileName(), areaKey, groupe, cluster, pecdZone, null, horizon)) {
                        fileFoundInLF = true;
                        break;
                    }
                }

                if (!fileFoundInLF) {
                    missingFiles.add(ipKey);
                }
            }
        }

        // Si des fichiers manquent, lever une exception
        if (!missingFiles.isEmpty()) {
            String missingFilesStr = String.join(", ", missingFiles);
            log.error("Fichiers Load Factor manquants pour les clés IP: {}", missingFilesStr);
            throw BusinessException.builder().message("Cohérence IP/Load Factor échouée. Fichiers Load Factor manquants pour les clés: {0}").errorMessageArguments(List.of(missingFilesStr)).httpStatus(HttpStatus.BAD_REQUEST).build();
        }

        log.info("Validation des fichiers Load Factor réussie");
    }

    /**
     * Valide la cohérence entre les trajectoires LF et DT pour un study donné.
     * Permet d'inclure une trajectoire temporaire (en cours d'import) dans la validation.
     *
     * @param studyId                 l'identifiant de l'étude
     * @param trajectoryBeingImported trajectoire optionnelle en cours d'import à inclure dans la validation
     * @throws BusinessException si la cohérence n'est pas respectée
     */
    public void validateLFDTCoherence(Integer studyId, TrajectoryEntity trajectoryBeingImported) {
        log.info("=== DÉBUT VALIDATION COHÉRENCE LF/DT (Étude {}) ===", studyId);
        
        if (trajectoryBeingImported == null) {
            log.info("Pas de trajectoire en cours d'import, validation LF/DT skippée");
            return;
        }

        List<TrajectoryEntity> bdLfTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_LOAD.name(), studyId);
        List<TrajectoryEntity> bdDtTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId);
        log.info("Trajectoires chargées - LF: {}, DT: {}", bdLfTrajectories.size(), bdDtTrajectories.size());

        String trajectoryType = trajectoryBeingImported.getType();
        log.info("Type de trajectoire importée: {}, Area: {}", trajectoryType, trajectoryBeingImported.getArea());

        // Validation conditionnelle basée sur le type de trajectoire à importer
        if (TrajectoryType.RES_LOAD.name().equals(trajectoryType)) {
            // Import d'une LF : vérifier les combinaisons LF et DT
            if (!validateLFCoherence(bdLfTrajectories, trajectoryBeingImported) || !validateTDCoherence(bdDtTrajectories, trajectoryBeingImported)) {
                log.info("Prérequis LF/DT non satisfaits, validation clés skippée");
                return;
            }
            // Si les combinaisons sont complètes et qu'il y a des DT, valider les fichiers
            if (!bdDtTrajectories.isEmpty()) {
                log.info("Lancement validation fichiers LF/DT...");
                validateLFDTFilesCoherence(bdLfTrajectories, trajectoryBeingImported, bdDtTrajectories);
            }
        } else if (TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name().equals(trajectoryType)) {
            // Import d'une DT : vérifier les combinaisons DT et LF
            if (!validateTDCoherence(bdDtTrajectories, trajectoryBeingImported) || !validateLFCoherence(bdLfTrajectories, trajectoryBeingImported)) {
                log.info("Prérequis LF/DT non satisfaits, validation clés skippée");
                return;
            }
            // Si les combinaisons sont complètes et qu'il y a des LF, valider les fichiers
            if (!bdLfTrajectories.isEmpty()) {
                log.info("Lancement validation fichiers LF/DT...");
                validateLFDTFilesCoherence(bdLfTrajectories, trajectoryBeingImported, bdDtTrajectories);
            }
        }
    }


    /**
     * Vérifie si les 2 combinaisons requises de DT sont présentes pour une area donnée.
     * Les 2 combinaisons = area_specific (sans tech) + area_specific (avec tech)
     * où area_specific peut être FR, BE, DE, etc. (n'importe quel defaultArea)
     *
     * @return true si les 2 combinaisons sont complètes, false sinon
     */
    public boolean hasCompletedDTCombinations(List<TrajectoryEntity> dtTrajectories, String area, String importedTechnology) {
        // Vérifier qu'il existe une trajectoire avec area sans technology
        boolean hasDTWithoutTech = dtTrajectories.stream().anyMatch(trajectory -> trajectory.getArea().equals(area) && isBlankOrEmpty(trajectory.getTechnology()));

        // Si importedTechnology est null : vérifier qu'il existe au moins une avec n'importe quelle technology
        if (isBlankOrEmpty(importedTechnology)) {
            boolean hasDTWithSomeTech = dtTrajectories.stream().anyMatch(trajectory -> trajectory.getArea().equals(area) && !isBlankOrEmpty(trajectory.getTechnology()));
            return hasDTWithoutTech && hasDTWithSomeTech;
        }

        // Si importedTechnology n'est pas null : vérifier qu'il existe une avec cette technology
        boolean hasDTWithImportedTech = dtTrajectories.stream().anyMatch(trajectory -> trajectory.getArea().equals(area) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equals(importedTechnology));

        return hasDTWithoutTech && hasDTWithImportedTech;
    }

    /**
     * Valide la cohérence entre les trajectoires LF et DT en vérifiant l'existence des fichiers.
     * Pour chaque groupe/cluster/zone PECD/techno PECD présent dans la DT, vérifie que
     * le fichier correspondant existe dans le répertoire NAS.
     * <p>
     * Règle de comparaison:
     * - Comparer group/cluster/zone PECD/techno PECD des LF avec group/cluster/zone PECD/techno PECD des DT
     * - Vérifier dans chaque répertoire du NAS : \RES\load factor\<trajectoryFileName>\<groupe>\<cluster>\
     * la présence des fichiers nommés <cluster>_<zone PECD>_<techno PECD>_<horizon>.csv
     * <p>
     * Chemin du fichier: \RES\load factor\<trajectoryFileName>\<groupe>\<cluster>\<cluster>_<zone PECD>_<techno PECD>_<horizon>.csv
     */
     public void validateLFDTFilesCoherence(List<TrajectoryEntity> bdLfTrajectories, TrajectoryEntity trajectoryBeingImported, List<TrajectoryEntity> bdDtTrajectories) {
         String area = null;
         String horizon = null;
         List<TrajectoryEntity> allDtTrajectories = new ArrayList<>(bdDtTrajectories);
         String importedTechnology = null;

         // Récupérer l'area, l'horizon et construire les listes complètes selon le type
         if (trajectoryBeingImported != null) {
             area = trajectoryBeingImported.getArea();
             horizon = trajectoryBeingImported.getHorizon();
             if (TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name().equals(trajectoryBeingImported.getType())) {
                 allDtTrajectories.add(trajectoryBeingImported);
             }
             bdLfTrajectories.add(trajectoryBeingImported);
             importedTechnology= trajectoryBeingImported.getTechnology();
         } else if (!allDtTrajectories.isEmpty()) {
             area = allDtTrajectories.getFirst().getArea();
             horizon = allDtTrajectories.getFirst().getHorizon();
         }

          if (isBlankOrEmpty(area) || isBlankOrEmpty(horizon)) {
              log.warn("Area ou horizon non trouvé, validation fichiers LF/DT skippée");
              return;
          }

          // Extraire les technologies disponibles dans les trajectoires DT avec technologie
          // Les technologies doivent exister à la fois avec l'area spécifique ET avec area = OTHERS
          Set<String> availableDTTechnologies = new HashSet<>();
          
          String trajectoryTechnology = trajectoryBeingImported != null ? trajectoryBeingImported.getTechnology() : null;
          if (!isBlankOrEmpty(trajectoryTechnology)) {
              // Si trajectoryBeingImported a une technologie, ne comparer que avec les clés DT ayant cette même technologie
              availableDTTechnologies.add(trajectoryTechnology);
          } else {
              // Extraire les technologies présentes dans les deux areas
              Set<String> technologiesWithSpecificArea = allDtTrajectories.stream()
                      .filter(t -> !t.getArea().equals(OTHERS_AREA))
                      .map(TrajectoryEntity::getTechnology)
                      .filter(techno -> !isBlankOrEmpty(techno))
                      .collect(Collectors.toSet());
              
              // Intersection: les technologies présentes dans les deux
              availableDTTechnologies = new HashSet<>(technologiesWithSpecificArea);
          }

          // Si aucune technologie disponible dans les DT, skip la validation
          if (availableDTTechnologies.isEmpty() && !isBlankOrEmpty(importedTechnology)) {
              log.info("Aucune technologie disponible dans les DT, validation fichiers LF/DT skippée");
              return;
          }

          // Si importedTechnology est vide, filtrer bdLfTrajectories pour garder que les trajectoires 
          // avec la meme technology et présente dans area specific et OTHERS
          if (isBlankOrEmpty(importedTechnology)) {
              Set<String> lfTechnologiesWithSpecificArea = bdLfTrajectories.stream()
                      .filter(t -> !t.getArea().equals(OTHERS_AREA))
                      .filter(t -> !isBlankOrEmpty(t.getTechnology()))
                      .map(TrajectoryEntity::getTechnology)
                      .collect(Collectors.toSet());
              
              Set<String> lfTechnologiesWithOthersArea = bdLfTrajectories.stream()
                      .filter(t -> t.getArea().equals(OTHERS_AREA))
                      .filter(t -> !isBlankOrEmpty(t.getTechnology()))
                      .map(TrajectoryEntity::getTechnology)
                      .collect(Collectors.toSet());
              
              // Intersection: les technologies présentes dans les deux
              lfTechnologiesWithSpecificArea.retainAll(lfTechnologiesWithOthersArea);
              
              // Filtrer bdLfTrajectories pour garder que celles avec une technology commune
              final Set<String> commonLFTechnologies = lfTechnologiesWithSpecificArea;
              bdLfTrajectories = bdLfTrajectories.stream()
                      .filter(t -> isBlankOrEmpty(t.getTechnology()) || commonLFTechnologies.contains(t.getTechnology()))
                      .collect(Collectors.toList());
          }

         // Extraire les clés DT complètes (area/groupe/cluster/pecdZone/pecdTechnology) filtrées par area et technologies disponibles
         Set<String> dtKeysWithPecd = extractDTKeysWithPecd(allDtTrajectories, area, availableDTTechnologies, importedTechnology);

        // Pour chaque clé DT, vérifier que le fichier existe dans le répertoire NAS pour au moins une LF
        Set<String> missingFiles = new HashSet<>();
        for (String dtKey : dtKeysWithPecd) {
            String[] parts = dtKey.split("/", -1);
            if (parts.length >= 5) {
                String groupe = parts[1];
                String cluster = parts[2];
                String pecdZone = parts[3];
                String pecdTechnology = parts[4];

                boolean fileFoundInLF = false;
                for (TrajectoryEntity lfTrajectory : bdLfTrajectories) {
                    // Vérifier si le fichier existe dans le répertoire NAS pour cette LF
                    if (checkIfLoadFactorFileExists(lfTrajectory.getFileName(), null, groupe, cluster, pecdZone, pecdTechnology, horizon)) {
                        fileFoundInLF = true;
                        break;
                    }
                }

                if (!fileFoundInLF) {
                    missingFiles.add(dtKey);
                }
            }
        }

        // Si des fichiers manquent, lever une exception
        if (!missingFiles.isEmpty()) {
            String missingFilesStr = String.join(", ", missingFiles);
            log.error("Fichiers Load Factor manquants pour les clés Distribution Technology: {}", missingFilesStr);
            throw BusinessException.builder().message("Cohérence LF/Distribution Technology échouée. Fichiers Load Factor manquants pour les clés: {0}").errorMessageArguments(List.of(missingFilesStr)).httpStatus(HttpStatus.BAD_REQUEST).build();
        }

        log.info("Validation des fichiers Load Factor pour Distribution Technology réussie");
    }

    /**
     * Extrait les clés uniques (area/groupe/cluster/pecdZone/pecdTechnology) de tous les DT.
     * Retourne les clés filtrées par area et technologies disponibles.
     * Format: "area/groupe/cluster/pecdZone/pecdTechnology"
     */
    private Set<String> extractDTKeysWithPecd(List<TrajectoryEntity> dtTrajectories, String area, Set<String> availableTechnologies, String importedTechnology) {
        return dtTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResTechnologyDistributionCapacityEntities() != null ? trajectory.getResTechnologyDistributionCapacityEntities().stream() : Stream.empty())
                .filter(entity -> {
                    boolean areaMatches = entity.getArea().equals(area) || area.equals(ResDomainRules.OTHERS_AREA);
                    if (!areaMatches) return false;

                    // If importedTechnology is null, extract entities with groups in availableTechnologies
                    if (importedTechnology == null || importedTechnology.isEmpty()) {
                        return availableTechnologies.contains(entity.getGroupe());
                    }

                    // If importedTechnology is not null, match it and check if it's available
                    return importedTechnology.equals(entity.getGroupe()) && availableTechnologies.contains(entity.getGroupe());
                })                .map(entity -> String.format("%s/%s/%s/%s/%s", entity.getArea(), entity.getGroupe(), entity.getCluster() != null ? entity.getCluster() : "", entity.getPecdZone() != null ? entity.getPecdZone() : "", entity.getPecdTechnology() != null ? entity.getPecdTechnology() : ""))
                .collect(Collectors.toSet());
    }

    /**
     * Vérifie si un fichier Load Factor existe dans le répertoire NAS pour LF/DT validation.
     * Chemin: \RES\load factor\<trajectoryFileName>\<groupe>\<cluster>\<cluster>_<pecdZone>_<pecdTechnology>_<horizon>.csv
     *
     * @param trajectoryFileName nom du fichier de la trajectoire LF
     * @param groupe             groupe du cluster
     * @param cluster            nom du cluster
     * @param pecdZone           zone PECD du cluster
     * @param pecdTechnology     technologie PECD du cluster
     * @param horizon            horizon
     * @return true si le fichier existe, false sinon
     */


    public boolean checkIfLoadFactorFileExists(String trajectoryFileName, String area, String groupe, String cluster, String pecdZone, String pecdTechnology, String horizon) {

        try {
            Path nasBasePath = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(antaresDataManagerProperties.getTrajectoryFilePath());

            Path lfDirectoryPath = nasBasePath.resolve("RES").resolve("load factor").resolve(trajectoryFileName).resolve(groupe).resolve(cluster).normalize();

            if (!isSafePath(lfDirectoryPath, nasBasePath)) {
                log.warn("Tentative d'accès à un chemin non autorisé: {}", lfDirectoryPath);
                return false;
            }

            // -------------------------
            // CASE FR + WIND OFFSHORE
            // -------------------------
            if ("FR".equals(area) && "wind_offshore".equals(groupe)) {

                String prefix = cluster + "_" + (!pecdZone.isEmpty() ? pecdZone + "_" : "");
                String suffix = "_" + horizon + ".csv";

                return existsByPrefixAndSuffix(lfDirectoryPath, prefix, suffix);
            }

            // -------------------------
            // CASE FR (autre groupe)
            // -------------------------
            if ("FR".equals(area)) {

                String prefix = cluster + "_" + area;
                String suffix = "_" + horizon + ".csv";

                return existsByPrefixAndSuffix(lfDirectoryPath, prefix, suffix);
            }

            // -------------------------
            // CASE DT
            // -------------------------
            String fileName = String.format("%s_%s_%s_%s.csv", cluster, pecdZone, pecdTechnology, horizon);

            return checkFile(lfDirectoryPath.resolve(fileName), nasBasePath);

        } catch (Exception e) {
            log.warn("Erreur lors de la vérification du fichier Load Factor: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean checkFile(Path filePath, Path base) {
        if (!isSafePath(filePath, base)) {
            log.warn("Tentative d'accès à un fichier non autorisé: {}", filePath);
            return false;
        }

        boolean exists = Files.exists(filePath) && Files.isRegularFile(filePath);

        log.info(exists ? "Fichier Load Factor trouvé: {}" : "Fichier Load Factor non trouvé: {}", filePath);

        return exists;
    }

    private boolean existsByPrefixAndSuffix(Path dir, String prefix, String suffix) {
        try (Stream<Path> files = Files.list(dir)) {

            boolean exists = files.filter(Files::isRegularFile).anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(suffix);
            });

            log.info(exists ? "Fichier trouvé (pattern): prefix={} suffix={}" : "Fichier non trouvé (pattern): prefix={} suffix={}", prefix, suffix);

            return exists;

        } catch (Exception e) {
            log.warn("Erreur recherche fichier pattern: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isSafePath(Path path, Path base) {
        return path.startsWith(base.getParent());
    }

    /**
     * Valide la cohérence entre les trajectoires DT et DZ pour un study donné.
     * Permet d'inclure une trajectoire temporaire (en cours d'import) dans la validation.
     * <p>
     * Règles de contrôle:
     * - DT doit avoir 2 combinaisons (area sans tech + area avec tech) par technologie
     * - DZ doit avoir exactement 1 trajectoire
     * - La clé de comparaison est area/groupe/pecdZone
     * - Vérifie que pour chaque clé (area/groupe/pecdZone) présente dans DT, les données correspondantes existent dans DZ
     *
     * @param studyId                 l'identifiant de l'étude
     * @param trajectoryBeingImported trajectoire optionnelle en cours d'import à inclure dans la validation
     * @throws BusinessException si la cohérence n'est pas respectée
     */
    public void validateDTDZCoherence(Integer studyId, TrajectoryEntity trajectoryBeingImported) {
        log.info("=== DÉBUT VALIDATION COHÉRENCE DT/DZ (Étude {}) ===", studyId);
        
        if (trajectoryBeingImported == null) {
            log.info("Pas de trajectoire en cours d'import, validation DT/DZ skippée");
            return;
        }

        List<TrajectoryEntity> bdDtTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId);
        List<TrajectoryEntity> bdDzTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_ZONAL_DISTRIBUTION.name(), studyId);
        log.info("Trajectoires chargées - DT: {}, DZ: {}", bdDtTrajectories.size(), bdDzTrajectories.size());

        String trajectoryType = trajectoryBeingImported.getType();
        log.info("Type de trajectoire importée: {}, Area: {}", trajectoryType, trajectoryBeingImported.getArea());

        // Validation conditionnelle basée sur le type de trajectoire à importer
        if (TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name().equals(trajectoryType)) {
            log.info("Validation d'une trajectoire DT...");
            // Import d'une DT : vérifier les combinaisons DT (2 par technologie) et DZ (exactement 1)
            if (!validateTDCoherence(bdDtTrajectories, trajectoryBeingImported)) {
                log.info("Prérequis DT non satisfaits (2 combinaisons requises), validation clés DT/DZ skippée");
                return;
            }
            // DZ doit être exactement 1 trajectoire
            if (bdDzTrajectories.size() != 1) {
                log.info("Prérequis DZ non satisfaits (1 trajectoire requise, trouvé: {}), validation clés DT/DZ skippée", bdDzTrajectories.size());
                return;
            }
            log.info("Tous les prérequis satisfaits, lancement validation des clés DT/DZ...");
            // Les combinaisons sont complètes, valider les clés
            validateDTDZKeysCoherence(bdDtTrajectories, trajectoryBeingImported, bdDzTrajectories);
        } else if (TrajectoryType.RES_ZONAL_DISTRIBUTION.name().equals(trajectoryType)) {
            log.info("Validation d'une trajectoire DZ...");
            // Import d'une DZ : vérifier qu'il n'y a qu'une seule DZ au total après import
            List<TrajectoryEntity> allDzTrajectories = new ArrayList<>(bdDzTrajectories);
            allDzTrajectories.add(trajectoryBeingImported);

            if (allDzTrajectories.size() != 1) {
                log.info("Prérequis DZ non satisfaits (1 trajectoire requise, trouvé: {}), validation clés DT/DZ skippée", allDzTrajectories.size());
                return;
            }

            // DT doit avoir 2 combinaisons
            if (bdDtTrajectories.isEmpty()) {
                log.info("Pas de DT trajectoires, validation clés DT/DZ skippée");
                return;
            }

            // Vérifier que DT a les bonnes combinaisons (utiliser la trajectoire DZ pour obtenir l'area)
            String dzArea = trajectoryBeingImported.getArea();
            if (!validateDTCombinationsForDZValidation(bdDtTrajectories, dzArea)) {
                log.info("Prérequis DT non satisfaits (2 combinaisons requises), validation clés DT/DZ skippée");
                return;
            }

            log.info("Tous les prérequis satisfaits, lancement validation des clés DT/DZ...");
            validateDTDZKeysCoherence(bdDtTrajectories, null, allDzTrajectories);
        }
    }

    /**
     * Vérifie que DT a exactement 2 combinaisons (sans tech + avec tech) pour une area donnée.
     */
    private boolean validateDTCombinationsForDZValidation(List<TrajectoryEntity> dtTrajectories, String area) {
        // Vérifier qu'il existe une trajectoire DT avec area sans technology
        boolean hasDTWithoutTech = dtTrajectories.stream().anyMatch(trajectory -> trajectory.getArea().equals(area) && isBlankOrEmpty(trajectory.getTechnology()));

        if (!hasDTWithoutTech) {
            return false;
        }

        // Vérifier qu'il existe une trajectoire DT avec area et une technology
        boolean hasDTWithSomeTech = dtTrajectories.stream().anyMatch(trajectory -> trajectory.getArea().equals(area) && !isBlankOrEmpty(trajectory.getTechnology()));

        return hasDTWithSomeTech;
    }

    /**
     * Extrait les clés uniques (area/groupe/pecdZone) de tous les DT.
     * Format: "area/groupe/pecdZone"
     */
    private Set<String> extractDTKeysForDZComparison(List<TrajectoryEntity> dtTrajectories, String area, Set<String> availableTDTechnologies, String importedTechnology) {
        return dtTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResTechnologyDistributionCapacityEntities() != null ? trajectory.getResTechnologyDistributionCapacityEntities().stream() : Stream.empty())
                .filter(entity -> (entity.getArea().equals(area) || area.equals(ResDomainRules.OTHERS_AREA)) && (importedTechnology == null || (importedTechnology != null && importedTechnology.equals(entity.getGroupe()) && availableTDTechnologies.contains(entity.getGroupe()))))
                .map(entity -> String.format("%s/%s/%s", entity.getArea(), entity.getGroupe(), entity.getPecdZone() != null ? entity.getPecdZone() : ""))
                .collect(Collectors.toSet());
    }

    /**
     * Extrait les clés uniques (area/groupe/pecdZone) de tous les DZ.
     * Format: "area/groupe/pecdZone"
     */
    private Set<String> extractDZKeys(List<TrajectoryEntity> dzTrajectories, String area) {
        return dzTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResZonalDistributionCapacityEntities() != null ? trajectory.getResZonalDistributionCapacityEntities().stream() : Stream.empty())
                .filter(entity -> entity.getArea().equals(area) || area.equals(ResDomainRules.OTHERS_AREA))
                .map(entity -> String.format("%s/%s/%s", entity.getArea(), entity.getGroupe() != null ? entity.getGroupe() : "", entity.getPecdZone() != null ? entity.getPecdZone() : ""))
                .collect(Collectors.toSet());
    }

    /**
     * Valide que toutes les clés (area/groupe/pecdZone) de DT existent dans DZ.
     * Les prérequis doivent déjà être validés.
     */
    private void validateDTDZKeysCoherence(List<TrajectoryEntity> bdDtTrajectories, TrajectoryEntity dtBeingImported, List<TrajectoryEntity> bdDzTrajectories) {
        log.info("=== VALIDATION DES CLÉS DT/DZ ===");
        
        String area = null;
        String importedTechnology = null;
        List<TrajectoryEntity> allDtTrajectories = new ArrayList<>(bdDtTrajectories);
        if (dtBeingImported != null) {
            area = dtBeingImported.getArea();
            allDtTrajectories.add(dtBeingImported);
            if (importedTechnology == null) {
                importedTechnology = dtBeingImported.getTechnology();
            }
        } else if (!allDtTrajectories.isEmpty()) {
            area = allDtTrajectories.getFirst().getArea();
        }

         if (area == null || area.trim().isEmpty()) {
             log.warn("Area non trouvé, validation clés DT/DZ skippée");
             return;
         }

         final String finalArea = area;
         log.info("Extraction clés DT/DZ pour area: {}", area);
         
         // Extraire les clés DT et DZ filtrées par area
         // Extraire les technologies disponibles dans les trajectoires DT avec technologie
         // Les technologies doivent exister à la fois avec l'area spécifique ET avec area = OTHERS
         Set<String> technologiesWithSpecificArea = allDtTrajectories.stream()
                 .filter(t -> t.getArea().equals(finalArea))
                 .map(TrajectoryEntity::getTechnology)
                 .filter(trajectoryTechnology -> !isBlankOrEmpty(trajectoryTechnology))
                 .collect(Collectors.toSet());

         // Intersection: les technologies présentes dans les deux
         Set<String> availableTDTechnologies = new HashSet<>(technologiesWithSpecificArea);
         log.info("Technologies disponibles: {}", availableTDTechnologies);
         
         Set<String> dtKeys = extractDTKeysForDZComparison(allDtTrajectories, area, availableTDTechnologies, importedTechnology);
        Set<String> dzKeys = extractDZKeys(bdDzTrajectories, area);
        
        log.info("Clés DT trouvées: {}, Clés DZ trouvées: {}", dtKeys.size(), dzKeys.size());

        // Vérifier que chaque clé DT existe dans DZ
        Set<String> missingKeys = dtKeys.stream().filter(key -> !dzKeys.contains(key)).collect(Collectors.toSet());

        if (!missingKeys.isEmpty()) {
            String missingKeysStr = String.join(", ", missingKeys);
            log.error("Clés manquantes dans Distribution Zonal (DZ): {}", missingKeysStr);
            throw BusinessException.builder().message("Cohérence DT/DZ échouée. Clés manquantes dans Distribution Zonal: {0}").errorMessageArguments(List.of(missingKeysStr)).httpStatus(HttpStatus.BAD_REQUEST).build();
        }

        log.info("✅ Validation des clés DT/DZ réussie");
    }
}
