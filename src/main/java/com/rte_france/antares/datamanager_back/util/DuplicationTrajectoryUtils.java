package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
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
     * @param trajectoriesAvailable The list of available trajectories to be processed and linked.
     * @param studyDTO The DTO representing the study for which the trajectories are being processed.
     * @param trajectoryService The service used for trajectory-related operations.
     * @param createdBy The user or system identifier that initiated the process.
     * @return A result object containing information about missing trajectory types, the linked area trajectory,
     *         and any warning messages generated during processing.
     * @throws IOException If an input or output operation fails during the linking or processing.
     */
    public static TrajectoryProcessingResult processAndLinkTrajectories(
            List<TrajectoryEntity> trajectoriesAvailable,
            StudyDTO studyDTO,
            TrajectoryServiceImpl trajectoryService,
            String createdBy) throws IOException {

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
            List<String> missingTrajectoryTypes,
            String createdBy) {

        for (TrajectoryType type : SUPPORTED_TRAJECTORY_TYPES) {
            if (type == TrajectoryType.AREA) continue;

            trajectories.stream()
                    .filter(t -> type.name().equals(t.getType()))
                    .findFirst()
                    .ifPresentOrElse(
                            trajectory -> trajectoryForLinks(
                                    trajectory,
                                    type,
                                    studyId,
                                    trajectoryService,
                                    warningMessages,
                                    missingTrajectoryTypes,
                                    createdBy
                            ),
                            () -> missingTrajectoryTypes.add(type.name())
                    );
        }
    }

    private static void trajectoryForLinks(
            TrajectoryEntity trajectory,
            TrajectoryType type,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            Set<WarningMessageEntity> warningMessages,
            List<String> missingTrajectoryTypes,
            String createdBy) {

        try {
            trajectoryService.linkTrajectoryToStudy(trajectory.getId(), studyId, type);
            if (type == TrajectoryType.LINK) {
                trajectoryService.checkLinkCoherence(studyId, warningMessages, trajectory, createdBy);
            }
        } catch (IOException e) {
            missingTrajectoryTypes.add(type.name());
        }


    }

    /**
     * Determines the maximum year from a given horizon value string.
     * The horizon value is expected to be in the format of either "YYYY" or "YYYY-YYYY".
     * If the given input is null, empty, or invalid, an empty string is returned.
     *
     * @param horizonValue the input string representing the horizon year(s).
     *                     It can either be a single year or a range of years separated by a hyphen.
     * @return the maximum year in the input string as a string.
     *         Returns an empty string if the input is null, empty, or invalid.
     */
    public static String getMaxHorizonYear(String horizonValue) {
        if (horizonValue == null || horizonValue.trim().isEmpty()) {
            return "";
        }

        String[] years = horizonValue.trim().split("-");
        if (years.length == 1) {
            return years[0].trim();
        }

        int maxYear = Math.max(
                Integer.parseInt(years[0].trim()),
                Integer.parseInt(years[1].trim())
        );

        return String.valueOf(maxYear);
    }

}