package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@UtilityClass
public class DuplicationTrajectoryUtils {

    public static final String OTHER_AREA = "OTHERS";

    public static final List<TrajectoryType> SUPPORTED_TRAJECTORY_TYPES = List.of(
            TrajectoryType.AREA,
            TrajectoryType.LINK,
            TrajectoryType.LOAD,
            TrajectoryType.THERMAL_CAPACITY,
            TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER,
            TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER,
            TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER,
            TrajectoryType.THERMAL_ECONOMIC_PARAMETER,
            TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER
    );


    /**
     * Validates that an AREA trajectory is available for the specified horizon.
     *
     * @param trajectoriesAvailable     area
     * @param existingStudyTrajectories
     * @param horizon
     */
    public TrajectoryEntity validateAreaTrajectoryForDuplication(List<TrajectoryEntity> trajectoriesAvailable, Set<TrajectoryEntity> existingStudyTrajectories, String horizon) {

        TrajectoryEntity availableAreaTrajectory = trajectoriesAvailable.stream()
                .filter(t -> TrajectoryType.AREA.name().equals(t.getType()))
                .findFirst().orElse(null);

        // Find the first AREA trajectory in existingStudyTrajectories that is NOT in availableAreaNames
        TrajectoryEntity existingAreaTrajectory = existingStudyTrajectories.stream()
                .filter(t -> TrajectoryType.AREA.name().equals(t.getType()))
                .findFirst()
                .orElse(null);

        if (availableAreaTrajectory == null) {
            throw BusinessException.builder()
                    .message("AREA trajectory {0} does not exist for horizon {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .errorMessageArguments(List.of(existingAreaTrajectory !=null ? existingAreaTrajectory.getFileName() : "", horizon))
                    .build();
        }
        return availableAreaTrajectory;
    }


    /**
     * Finds a trajectory of a specific type in a list of trajectories.
     *
     * @param trajectories List of trajectories to search in
     * @param type         Type of trajectory to look for
     * @return Optional containing the trajectory if found
     */
    public static Optional<TrajectoryEntity> findTrajectoryByType(List<TrajectoryEntity> trajectories, TrajectoryType type) {
        return trajectories.stream()
                .filter(t -> type.name().equals(t.getType()))
                .findFirst();
    }

    /**
     * Prepares parameters for a missing trajectories warning message.
     *
     * @param missingTrajectoryTypes List of missing trajectory types
     * @param horizon                Time horizon
     * @return List of parameters for a warning message
     */
    public static List<String> prepareWarningMessageParams(List<String> missingTrajectoryTypes, String horizon) {
        return Arrays.asList(
                String.join(",", missingTrajectoryTypes),
                horizon
        );
    }

    public record TrajectoryProcessingResult(
            List<String> missingTrajectoryTypes,
            TrajectoryEntity areaTrajectory,
            Set<WarningMessageEntity> warningMessages
    ) {
    }


    /**
     * Processes a list of trajectory entities by linking area trajectories,
     * processing other trajectory types, and generating warnings for missing trajectories.
     *
     * @param trajectoriesAvailable    The list of available trajectories to be processed and linked.
     * @param studyDTO                 The DTO representing the study for which the trajectories are being processed.
     * @param trajectoryService        The service used for trajectory-related operations.
     * @param createdBy                The user or system identifier that initiated the process.
     * @return A result object containing information about missing trajectory types, the linked area trajectory,
     * and any warning messages generated during processing.
     * @throws IOException If an input or output operation fails during the linking or processing.
     */
    public static TrajectoryProcessingResult processAndLinkTrajectories(
            List<TrajectoryEntity> trajectoriesAvailable,
            StudyDTO studyDTO,
            TrajectoryServiceImpl trajectoryService,
            LoadFileProcessorServiceImpl loadFileProcessorService, String createdBy) throws IOException {

        List<String> missingTrajectoryTypes = new ArrayList<>();
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        // Area trajectory linked to study
        TrajectoryEntity areaTrajectory = findAndLinkAreaTrajectory(
                trajectoriesAvailable,
                studyDTO.getId(),
                trajectoryService);

        // Process other trajectory types
        processRemainingTrajectoryTypes(
                trajectoriesAvailable,
                studyDTO.getId(),
                trajectoryService,
                warningMessages,
                loadFileProcessorService,
                missingTrajectoryTypes,
                createdBy);

        return new TrajectoryProcessingResult(missingTrajectoryTypes, areaTrajectory, warningMessages);
    }


    private static TrajectoryEntity findAndLinkAreaTrajectory(
            List<TrajectoryEntity> trajectories,
            Integer studyId,
            TrajectoryService trajectoryService) throws IOException {

        TrajectoryEntity areaTrajectory = trajectories.stream()
                .filter(t -> TrajectoryType.AREA.name().equals(t.getType()))
                .findFirst()
                .orElseThrow();

        trajectoryService.linkTrajectoryToStudy(
                areaTrajectory.getId(),
                studyId,
                TrajectoryType.AREA);

        return areaTrajectory;
    }

    private static void processRemainingTrajectoryTypes(
            List<TrajectoryEntity> trajectories,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            Set<WarningMessageEntity> warningMessages,
            LoadFileProcessorServiceImpl loadFileProcessorService,
            List<String> missingTrajectoryTypes,
            String createdBy) {

        for (TrajectoryType type : SUPPORTED_TRAJECTORY_TYPES) {
            if (type == TrajectoryType.AREA) continue;

            List<TrajectoryEntity> typeTrajectories = trajectories.stream()
                    .filter(t -> type.name().equals(t.getType()))
                    .toList();

            if (typeTrajectories.isEmpty()) {
                missingTrajectoryTypes.add(type.name());

            } else {
                if (type == TrajectoryType.LOAD || type == TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER || type == TrajectoryType.THERMAL_CAPACITY) {
                    // LOAD we can have several trajectories for one study
                    // Track if any LOAD trajectory was successfully linked
                    List<String> tempMissingTypes = new ArrayList<>();

                    typeTrajectories.forEach(trajectory ->
                            trajectoryToBeAttached(
                                    trajectory,
                                    type,
                                    studyId,
                                    trajectoryService,
                                    loadFileProcessorService,
                                    warningMessages,
                                    tempMissingTypes,
                                    createdBy
                            )
                    );

                    // Only add LOAD to missing types if all trajectories failed (count equals total)
                    if (tempMissingTypes.size() == typeTrajectories.size()) {
                        missingTrajectoryTypes.add(type.name());
                    }
                } else {
                    trajectoryToBeAttached(
                            typeTrajectories.getFirst(),
                            type,
                            studyId,
                            trajectoryService,
                            loadFileProcessorService,
                            warningMessages,
                            missingTrajectoryTypes,
                            createdBy
                    );
                }
            }
        }
    }

    /**
     * Handles attached or processing a trajectory based on the specified type and study ID.
     * Ensures the trajectory is associated with the study and performs coherence checks
     * for specific trajectory types. If the operation fails or an inconsistency is detected,
     * adds the type to the list of missing trajectory types.
     *
     * @param trajectory               The trajectory entity to be processed or attached.
     * @param type                     The type of trajectory to process, such as LOAD or LINK.
     * @param studyId                  The identifier of the study to which the trajectory is attached.
     * @param trajectoryService        The service responsible for trajectory-related operations.
     * @param loadFileProcessorService The service used to process load areas for trajectories.
     * @param warningMessages          A set of warning messages that may be generated during processing.
     * @param missingTrajectoryTypes   A list to store missing trajectory types if inconsistencies are found.
     * @param createdBy                The identifier of the user or system that initiated this operation.
     */
    private static void trajectoryToBeAttached(
            TrajectoryEntity trajectory,
            TrajectoryType type,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            LoadFileProcessorServiceImpl loadFileProcessorService,
            Set<WarningMessageEntity> warningMessages,
            List<String> missingTrajectoryTypes,
            String createdBy) {

        try {
            if (type == TrajectoryType.LOAD && trajectory.getArea() != null) {
                List<String> areasLoadWithoutTrajectorySelected = loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId);

                if (OTHER_AREA.equals(trajectory.getArea())) {

                    Set<String> loadAreas = trajectory.getLoadEntities().stream()
                            .map(LoadEntity::getArea)
                            .map(String::toUpperCase)
                            .collect(Collectors.toSet());

                    boolean hasValidArea = areasLoadWithoutTrajectorySelected.stream()
                            .anyMatch(loadAreas::contains);

                    if (!hasValidArea) {
                        missingTrajectoryTypes.add(type.name());
                        return;
                    }
                } else {

                    if (!areasLoadWithoutTrajectorySelected.contains(trajectory.getArea().toUpperCase())) {
                        missingTrajectoryTypes.add(type.name());
                        return;
                    }
                }
            }

            trajectoryService.linkTrajectoryToStudy(trajectory.getId(), studyId, type);

            if (type == TrajectoryType.LINK) {
                trajectoryService.checkLinkCoherence(studyId, warningMessages, trajectory, createdBy);
            }

        } catch (IOException e) {
            missingTrajectoryTypes.add(type.name());
        }
    }


}
