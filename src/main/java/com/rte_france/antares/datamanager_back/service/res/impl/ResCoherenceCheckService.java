package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service de validation pour vérifier la cohérence entre InstalledPower (IP/RES_CAPACITY)
 * et Technology Distribution (TD/RES_TECHNOLOGY_DISTRIBUTION).
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
 * Note: area_specific peut être n'importe quel area défini dans defaultConfigService.fetchAllDefaults()
 * Le contrôle de cohérence IP/TD se lance seulement quand TOUTES les combinaisons existent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResCoherenceCheckService {

    private final TrajectoryRepository trajectoryRepository;
    private final DefaultConfigService defaultConfigService;

    private static final String OTHERS_AREA = "OTHERS";

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
        // Pas de validation si pas de trajectoire à importer
        if (trajectoryBeingImported == null) {
            log.debug("Pas de trajectoire en cours d'import, validation skippée");
            return;
        }

        List<TrajectoryEntity> bdIpTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_CAPACITY.name(), studyId);
        List<TrajectoryEntity> bdTdTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name(), studyId);

        String trajectoryType = trajectoryBeingImported.getType();


        if (!validateTDCoherence(bdTdTrajectories, trajectoryBeingImported) || !validateIPCoherence(bdIpTrajectories, trajectoryBeingImported)) {
            log.debug("Prérequis IP non satisfaits, validation clés skippée");
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
        String areaParam = trajectoryBeingImported.getArea();
        String technology = trajectoryBeingImported.getTechnology();

        // Obtenir la liste des areas spécifiques (définis dans DefaultConfig)
        Set<String> defaultAreas = getDefaultAreas();

        // Identifier quelle combinaison on importe
        if (OTHERS_AREA.equalsIgnoreCase(areaParam) || defaultAreas.contains(areaParam.toUpperCase())) {
            // On importe OTHERS, construire la liste avec cette nouvelle IP
            List<TrajectoryEntity> allIpTrajectories = new ArrayList<>(bdIpTrajectories);
            allIpTrajectories.add(trajectoryBeingImported);
            // Vérifier que les 4 combinaisons complètes existent
            return hasCompletedIPCombinations(allIpTrajectories, areaParam, technology);
        }  else {
            // Area non reconnu, pas de validation
            log.debug("Area {} non reconnu pour IP, validation skippée", areaParam);
            return true; // On laisse passer
        }
    }

    /**
     * Valide que les 2 combinaisons requises de TD existent après l'import.
     * Ne lève pas d'exception, retourne simplement un booléen.
     *
     * @return true si les 2 combinaisons (FR sans tech + FR avec tech) existent, false sinon
     */
    private boolean validateTDCoherence(List<TrajectoryEntity> bdTdTrajectories, TrajectoryEntity trajectoryBeingImported) {
        String area = trajectoryBeingImported.getArea();
        String technology = trajectoryBeingImported.getTechnology();

        // Obtenir la liste des areas spécifiques
        Set<String> defaultAreas = getDefaultAreas();

        // TD ne concerne que FR ou autres areas spécifiques
        if (!defaultAreas.contains(area.toUpperCase())) {
            log.debug("TD avec area {} non reconnu, validation skippée", area);
            return true; // On laisse passer
        }

        // On importe TD, construire la liste avec cette nouvelle TD
        List<TrajectoryEntity> allTdTrajectories = new ArrayList<>(bdTdTrajectories);
        allTdTrajectories.add(trajectoryBeingImported);

        // Vérifier que les 2 combinaisons complètes existent pour cet area
        return hasCompletedTDCombinations(allTdTrajectories, area, technology);
    }

    /**
     * Valide que les 4 combinaisons requises d'IP sont présentes.
     * Les 4 combinaisons = area_specific (sans tech) + area_specific (avec tech) + OTHERS (sans tech) + OTHERS (avec tech)
     * où area_specific peut être FR, BE, DE, etc. (n'importe quel defaultArea)
     *
     * @param areaParam filtre sur l'area spécifique requise
     * @return true si les 4 combinaisons sont complètes, false sinon
     */
    private boolean hasCompletedIPCombinations(List<TrajectoryEntity> ipTrajectories, String areaParam, String importedTechnology) {
        // Vérifications communes
        boolean hasAreaWithoutTech = ipTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(areaParam) && isBlankOrEmpty(trajectory.getTechnology()));

        boolean hasOthersWithoutTech = ipTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && isBlankOrEmpty(trajectory.getTechnology()));

        // Si importedTechnology est null : vérifier hasAreaWithoutTech, hasOthersWithoutTech 
        // et au moins deux trajectoires avec la même technology (une pour area, une pour OTHERS)
        if (isBlankOrEmpty(importedTechnology)) {
            // Obtenir les technologies disponibles pour l'area spécifique
            Set<String> areaWithTechList = ipTrajectories.stream()
                    .filter(trajectory -> trajectory.getArea().equalsIgnoreCase(areaParam) && !isBlankOrEmpty(trajectory.getTechnology()))
                    .map(TrajectoryEntity::getTechnology)
                    .collect(Collectors.toSet());

            // Obtenir les technologies disponibles pour OTHERS
            Set<String> othersWithTechList = ipTrajectories.stream()
                    .filter(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()))
                    .map(TrajectoryEntity::getTechnology)
                    .collect(Collectors.toSet());

            // Vérifier qu'il existe une technology commune entre l'area et OTHERS
            boolean hasSharedTechnology = areaWithTechList.stream()
                    .anyMatch(othersWithTechList::contains);

            return hasAreaWithoutTech && hasOthersWithoutTech && hasSharedTechnology;
        }

        // Si importedTechnology n'est pas null : vérifier les 4 combinaisons requises
        boolean hasAreaWithTech = ipTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(areaParam) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equalsIgnoreCase(importedTechnology));

        boolean hasOthersWithTech = ipTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equalsIgnoreCase(OTHERS_AREA) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equalsIgnoreCase(importedTechnology));

        return hasAreaWithoutTech && hasAreaWithTech && hasOthersWithoutTech && hasOthersWithTech;
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
        boolean hasTDWithoutTech = tdTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equals(area) && isBlankOrEmpty(trajectory.getTechnology()));

        // Si importedTechnology est null : vérifier qu'il existe au moins une avec n'importe quelle technology
        if (isBlankOrEmpty(importedTechnology)) {
            boolean hasTDWithSomeTech = tdTrajectories.stream()
                    .anyMatch(trajectory -> trajectory.getArea().equals(area) && !isBlankOrEmpty(trajectory.getTechnology()));
            return hasTDWithoutTech && hasTDWithSomeTech;
        }

        // Si importedTechnology n'est pas null : vérifier qu'il existe une avec cette technology
        boolean hasTDWithImportedTech = tdTrajectories.stream()
                .anyMatch(trajectory -> trajectory.getArea().equals(area) && !isBlankOrEmpty(trajectory.getTechnology()) && trajectory.getTechnology().equals(importedTechnology));

        return hasTDWithoutTech && hasTDWithImportedTech;
    }

    /**
     * Récupère l'ensemble des areas spécifiques (définis dans DefaultConfig).
     */
    private Set<String> getDefaultAreas() {
        return defaultConfigService.fetchAllDefaults().stream()
                .map(DefaultLoadDTO::getName)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    /**
     * Valide que toutes les clés (area/groupe/cluster) de IP existent dans TD.
     * Les prérequis de combinaisons doivent déjà être validés.
     */
    private void validateIPTDKeysCoherence(List<TrajectoryEntity> bdIpTrajectories,
                                           TrajectoryEntity ipBeingImported,
                                           List<TrajectoryEntity> bdTdTrajectories,
                                           TrajectoryEntity tdBeingImported) {
        String area = null;
        // Construire la liste complète des IP (BD + nouvelle si c'est une IP)
        List<TrajectoryEntity> allIpTrajectories = new ArrayList<>(bdIpTrajectories);
        if (ipBeingImported != null) {
            area = ipBeingImported.getArea();
            allIpTrajectories.add(ipBeingImported);
        }

        // Construire la liste complète des TD (BD + nouvelle si c'est une TD)
        List<TrajectoryEntity> allTdTrajectories = new ArrayList<>(bdTdTrajectories);
        if (tdBeingImported != null) {
            area = tdBeingImported.getArea();
            allTdTrajectories.add(tdBeingImported);
        }



        // Extraire les technologies disponibles dans les trajectoires TD avec technologie
        Set<String> availableTDTechnologies = allTdTrajectories.stream()
                .map(TrajectoryEntity::getTechnology)
                .filter(trajectoryTechnology -> !isBlankOrEmpty(trajectoryTechnology))
                .collect(Collectors.toSet());
        Set<String> tdKeys = extractTDKeys(allTdTrajectories, area, availableTDTechnologies);


        Set<String> ipKeys = extractIPKeys(allIpTrajectories, area, availableTDTechnologies);


        // Vérifier que chaque clé IP existe dans TD
        Set<String> missingKeys = ipKeys.stream()
                .filter(key -> !tdKeys.contains(key))
                .collect(Collectors.toSet());

        if (!missingKeys.isEmpty()) {
            String missingKeysStr = String.join(", ", missingKeys);
            log.error("Clés manquantes dans Technology Distribution: {}", missingKeysStr);
            throw BusinessException.builder()
                    .message("Cohérence IP/TD échouée. Clés manquantes dans Technology Distribution: {0}")
                    .errorMessageArguments(List.of(missingKeysStr))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        log.info("Validation des clés IP/TD réussie");
    }

    /**
     * Extrait les clés uniques (area/groupe/cluster) de tous les IP.
     * Retourne les clés liées aux trajectoires avec technologie et les clés des trajectoires
     * sans technologie dont le groupe correspond aux technologies disponibles.
     * Format: "area/groupe/cluster"
     */
    private Set<String> extractIPKeys(List<TrajectoryEntity> ipTrajectories, String area, Set<String> availableTechnologies) {
        return ipTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResClusterCapacityEntities() != null
                        ? trajectory.getResClusterCapacityEntities().stream()
                        : Stream.empty())
                .filter(entity -> (entity.getArea().equals(area)|| area.equals(OTHERS_AREA)) && availableTechnologies.contains(entity.getGroupe()))
                .map(entity -> formatKey(entity.getArea(), entity.getGroupe(), entity.getCluster()))
                .collect(Collectors.toSet());
    }

    /**
     * Extrait les clés uniques (area/groupe/cluster) de tous les TD.
     * Retourne les clés liées aux trajectoires avec technologie et les clés des trajectoires
     * sans technologie dont le groupe correspond aux technologies disponibles.
     * Format: "area/groupe/cluster"
     */
    private Set<String> extractTDKeys(List<TrajectoryEntity> tdTrajectories, String area, Set<String> availableTechnologies) {
        // Extraire les technologies disponibles dans les trajectoires TD avec technologie
        
        return tdTrajectories.stream()
                .flatMap(trajectory -> trajectory.getResTechnologyDistributionCapacityEntities() != null
                        ? trajectory.getResTechnologyDistributionCapacityEntities().stream()
                        : Stream.empty())
                .filter(entity -> (entity.getArea().equals(area) || area.equals(OTHERS_AREA)) && availableTechnologies.contains(entity.getGroupe()))
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
        return String.format("%s/%s/%s",
                area != null ? area : "",
                groupe != null ? groupe : "",
                cluster != null ? cluster : ""
        );
    }
}
