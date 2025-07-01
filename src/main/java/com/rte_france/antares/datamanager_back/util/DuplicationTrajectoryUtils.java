package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.*;



@Slf4j
@UtilityClass
public class DuplicationTrajectoryUtils {

    private static final List<TrajectoryType> SUPPORTED_TRAJECTORY_TYPES = List.of(
            TrajectoryType.AREA,
            TrajectoryType.LINK,
            TrajectoryType.LOAD
    );
     LoadFileProcessorServiceImpl loadFileProcessorService;


    /**
     * Validates  AREA trajectory from a list of trajectories.
     *
     * @param trajectories List of trajectories to check
     * @param horizon      to include in an error message if AREA trajectory is not found
     * @throws BusinessException if AREA trajectory does not exist for the given horizon
     */
    public static void validateAreaTrajectory(List<TrajectoryEntity> trajectories, String horizon) {
        trajectories.stream()
                .filter(t -> TrajectoryType.AREA.name().equals(t.getType()))
                .findFirst()
                .orElseThrow(() -> BusinessException.builder()
                        .message("Duplicated study : AREA trajectory does not exist for horizon {0}. No duplication done")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessageArguments(List.of(horizon))
                        .build());

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

    /**
     * Retrieves the list of trajectories for the specified horizon.
     * Throws a BusinessException if the provided list of trajectories is empty.
     *
     * @param trajectories the list of TrajectoryEntity objects to be retrieved
     * @param horizon      the time horizon used in the error message if no trajectories are found
     * @return the provided list of TrajectoryEntity objects
     * @throws BusinessException if no trajectories are available for the specified horizon
     */
    public static List<TrajectoryEntity> getTrajectoriesForHorizon(List<TrajectoryEntity> trajectories, String horizon) {

        if (trajectories.isEmpty()) {
            throw BusinessException.builder()
                    .message("Duplicated study : No trajectory for horizon {0}. Cannot duplicate")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .errorMessageArguments(List.of(horizon))
                    .build();
        }
        return trajectories;
    }

    public record TrajectoryProcessingResult(
            List<String> missingTrajectoryTypes,
            TrajectoryEntity areaTrajectory,
            Set<WarningMessageEntity> warningMessages
    ) {}


    /**
     * Processes a list of trajectory entities by linking area trajectories,
     * processing other trajectory types, and generating warnings for missing trajectories.
     *
     * @param trajectoriesAvailable    The list of available trajectories to be processed and linked.
     * @param studyDTO                 The DTO representing the study for which the trajectories are being processed.
     * @param trajectoryService        The service used for trajectory-related operations.
     * @param loadFileProcessorService
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
            if (type == TrajectoryType.LOAD) {
                // LOAD we can have several trajectories for one study
                typeTrajectories.forEach(trajectory ->
                        trajectoryForLinks(
                                trajectory,
                                type,
                                studyId,
                                trajectoryService,
                                loadFileProcessorService,
                                warningMessages,
                                missingTrajectoryTypes,
                                createdBy
                        )
                );
            } else {

                trajectoryForLinks(
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

    private static void trajectoryForLinks(
            TrajectoryEntity trajectory,
            TrajectoryType type,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            LoadFileProcessorServiceImpl loadFileProcessorService,
            Set<WarningMessageEntity> warningMessages,
            List<String> missingTrajectoryTypes,
            String createdBy) {

        try {

            if (type == TrajectoryType.LOAD && trajectory.getLoadArea() != null) {
                List<String> availableAreas = loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId);

                if (!availableAreas.contains(trajectory.getLoadArea().toUpperCase())) {
                    missingTrajectoryTypes.add(type.name());
                    return;
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
