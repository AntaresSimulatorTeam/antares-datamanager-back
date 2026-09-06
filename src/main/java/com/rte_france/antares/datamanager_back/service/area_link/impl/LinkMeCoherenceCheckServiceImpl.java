package com.rte_france.antares.datamanager_back.service.area_link.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.LinkMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.area_link.LinkMeCoherenceCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of LinkMeCoherenceCheckService.
 * Validates LINK_ME trajectory coherence against AREA and AREA_ME trajectories.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkMeCoherenceCheckServiceImpl implements LinkMeCoherenceCheckService {

    private final LinkMeRepository linkMeRepository;
    private final TrajectoryRepository trajectoryRepository;

    @Override
    public void validateLinkMeCoherence(Integer studyId, TrajectoryEntity trajectory) throws BusinessException {
        log.info("Validating LINK_ME coherence for trajectory ID: {} and study ID: {}", trajectory.getId(), studyId);

        // Get all LINK_ME entities for this trajectory
        List<LinkMeEntity> linkMeEntities = trajectory.getLinkMeEntities();

        if (linkMeEntities == null || linkMeEntities.isEmpty()) {
            log.debug("No LINK_ME entities found for trajectory ID: {}", trajectory.getId());
            return;
        }

        // Get AREA and AREA_ME trajectories for the study
        List<TrajectoryEntity> areaTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId);
        List<TrajectoryEntity> areaMeTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId);

        // If neither AREA nor AREA_ME is linked to the study, don't validate
        if (areaTrajectories.isEmpty() && areaMeTrajectories.isEmpty()) {
            log.info("No AREA or AREA_ME trajectories linked to study ID: {}. Skipping LINK_ME coherence check.", studyId);
            return;
        }

        // Get all nodeFrom and nodeTo values
        Set<String> nodeFromSet = new HashSet<>();
        Set<String> nodeToSet = new HashSet<>();

        for (LinkMeEntity linkMeEntity : linkMeEntities) {
            if (linkMeEntity.getNodeFrom() != null) {
                nodeFromSet.add(linkMeEntity.getNodeFrom().toUpperCase());
            }
            if (linkMeEntity.getNodeTo() != null) {
                nodeToSet.add(linkMeEntity.getNodeTo().toUpperCase());
            }
        }

        // Extract area names from AREA trajectory
        Set<String> areaNames = extractAreaNames(areaTrajectories);

        // Extract area names from AREA_ME trajectory
        Set<String> areaMeNames = extractAreaNames(areaMeTrajectories);

        // Validation RG1: nodeFrom must exist in AREA or AREA_ME
        Set<String> missingNodeFromAreas = validateNodeFromAreas(nodeFromSet, areaNames, areaMeNames);
        if (!missingNodeFromAreas.isEmpty()) {
            String missingAreasStr = String.join(", ", missingNodeFromAreas);
            log.error("Missing areas in nodeFrom: {}", missingAreasStr);
            throw BusinessException.builder()
                    .message("Areas {0} in LINKS_ME file is (are) not present in either AREAS or AREAS_ME trajectory")
                    .errorMessageArguments(List.of(missingAreasStr))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Validation RG2: nodeTo must exist in AREA_ME only
        Set<String> missingNodeToAreas = validateNodeToAreas(nodeToSet, areaMeNames);
        if (!missingNodeToAreas.isEmpty()) {
            String missingAreasStr = String.join(", ", missingNodeToAreas);
            log.error("Missing areas in nodeTo: {}", missingAreasStr);
            throw BusinessException.builder()
                    .message("Areas {0} in LINKS_ME file is (are) not present in AREAS_ME trajectory")
                    .errorMessageArguments(List.of(missingAreasStr))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        log.info("LINK_ME coherence validation passed for trajectory ID: {}", trajectory.getId());
    }

    /**
     * Extracts area names from trajectory entities.
     * For AREA trajectories, extracts names from AreaConfigEntities.
     * For AREA_ME trajectories, extracts area column values from the trajectory data.
     *
     * @param trajectories the trajectory entities
     * @return set of area names in uppercase
     */
    private Set<String> extractAreaNames(List<TrajectoryEntity> trajectories) {
        Set<String> areaNames = new HashSet<>();

        for (TrajectoryEntity trajectory : trajectories) {
            // For AREA trajectories, use the existing area configurations
            if (trajectory.getAreaConfigEntities() != null) {
                trajectory.getAreaConfigEntities().stream()
                        .map(ac -> ac.getArea().getName().toUpperCase())
                        .forEach(areaNames::add);
            }
        }

        return areaNames;
    }

    /**
     * Validates that all nodeFrom areas exist in either AREA or AREA_ME.
     *
     * @param nodeFromSet set of nodeFrom areas to validate
     * @param areaNames set of AREA trajectory areas
     * @param areaMeNames set of AREA_ME trajectory areas
     * @return set of missing areas
     */
    private Set<String> validateNodeFromAreas(Set<String> nodeFromSet, Set<String> areaNames, Set<String> areaMeNames) {
        Set<String> allValidAreas = new HashSet<>(areaNames);
        allValidAreas.addAll(areaMeNames);

        return nodeFromSet.stream()
                .filter(nodeFrom -> !allValidAreas.contains(nodeFrom))
                .collect(Collectors.toSet());
    }

    /**
     * Validates that all nodeTo areas exist in AREA_ME.
     *
     * @param nodeToSet set of nodeTo areas to validate
     * @param areaMeNames set of AREA_ME trajectory areas
     * @return set of missing areas
     */
    private Set<String> validateNodeToAreas(Set<String> nodeToSet, Set<String> areaMeNames) {
        return nodeToSet.stream()
                .filter(nodeTo -> !areaMeNames.contains(nodeTo))
                .collect(Collectors.toSet());
    }
}
