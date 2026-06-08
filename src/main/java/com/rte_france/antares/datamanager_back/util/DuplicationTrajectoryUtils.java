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

import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@UtilityClass
public class DuplicationTrajectoryUtils {

    public static final String OTHER_AREA = "OTHERS";

    public static final List<TrajectoryType> SUPPORTED_TRAJECTORY_TYPES = List.of(
            TrajectoryType.LINK,
            TrajectoryType.LOAD,
            TrajectoryType.THERMAL_CAPACITY,
            TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER,
            TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER,
            TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER,
            TrajectoryType.THERMAL_ECONOMIC_PARAMETER,
            TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER
    );

    public record TrajectoryKey(String fileName, String type, String area) {}


    /**
     * Validates that an AREA trajectory is available for the specified horizon.
     *
     * @param trajectoriesAvailable list of available trajectories
     * @param existingStudyTrajectories existing study trajectories
     * @param horizon target horizon for the new study
     * @return the validated area trajectory
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
            List<String> missingTrajectoryKeys,
            TrajectoryEntity areaTrajectory,
            Set<WarningMessageEntity> warningMessages
    ) {
    }


    /**
     * Processes a list of trajectory entities by linking area trajectories,
     * processing other trajectory types, and generating warnings for missing trajectories.
     *
     * @param trajectoriesAvailable    The list of available trajectories to be processed and linked.
     * @param existingStudyTrajectories The set of existing trajectories from the original study.
     * @return A result object containing information about missing trajectory types, missing trajectory keys, 
     * the linked area trajectory, and any warning messages generated during processing.
     * @throws IOException If an input or output operation fails during the linking or processing.
     */
    public static TrajectoryProcessingResult processAndLinkTrajectories(
            TrajectoryEntity areaTrajectory,
            List<TrajectoryEntity> trajectoriesAvailable,
            Set<TrajectoryEntity> existingStudyTrajectories) throws IOException {

        List<String> missingTrajectoryTypes = new ArrayList<>();
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        // Find missing trajectory keys by comparing existing and available trajectories
        List<String> missingTrajectoryKeys = findMissingTrajectoryKeys(existingStudyTrajectories, trajectoriesAvailable);

        return new TrajectoryProcessingResult(missingTrajectoryTypes, missingTrajectoryKeys, areaTrajectory, warningMessages);
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
            LoadFileProcessorServiceImpl loadFileProcessorService,
            List<String> missingTrajectoryTypes) {

        for (TrajectoryType type : SUPPORTED_TRAJECTORY_TYPES) {
            List<TrajectoryEntity> typeTrajectories = getTrajectoriesByType(trajectories, type);

            if (typeTrajectories.isEmpty()) {
                missingTrajectoryTypes.add(type.name());
            } else {
                processTrajectoriesOfType(type, typeTrajectories, studyId, trajectoryService,
                        loadFileProcessorService, missingTrajectoryTypes);
            }
        }
    }

    private static List<TrajectoryEntity> getTrajectoriesByType(List<TrajectoryEntity> trajectories, TrajectoryType type) {
        return trajectories.stream()
                .filter(t -> type.name().equals(t.getType()))
                .toList();
    }

    private static void processTrajectoriesOfType(
            TrajectoryType type,
            List<TrajectoryEntity> typeTrajectories,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            LoadFileProcessorServiceImpl loadFileProcessorService,
            List<String> missingTrajectoryTypes) {

        List<String> failedAttachments = new ArrayList<>();
        
        // Process each trajectory of this type
        for (TrajectoryEntity trajectory : typeTrajectories) {
            trajectoryToBeAttached(trajectory, type, studyId, trajectoryService,
                    loadFileProcessorService, failedAttachments);
        }

        // Determine if type should be marked as missing
        boolean shouldMarkAsMissing = isMultipleTrajectoryType(type) 
                ? failedAttachments.size() == typeTrajectories.size()  // All failed for types that support multiples
                : !failedAttachments.isEmpty();                         // Any failed for single-trajectory types
        
        if (shouldMarkAsMissing) {
            missingTrajectoryTypes.add(type.name());
        }
    }

    private static boolean isMultipleTrajectoryType(TrajectoryType type) {
        return type == TrajectoryType.LOAD ||
                type == TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER ||
                type == TrajectoryType.THERMAL_CAPACITY;
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
     * @param missingTrajectoryTypes   A list to store missing trajectory types if inconsistencies are found.
     */
    private static void trajectoryToBeAttached(
            TrajectoryEntity trajectory,
            TrajectoryType type,
            Integer studyId,
            TrajectoryServiceImpl trajectoryService,
            LoadFileProcessorServiceImpl loadFileProcessorService,
            List<String> missingTrajectoryTypes) {

        try {
            if (controlMissingLoads(trajectory, type, studyId, loadFileProcessorService, missingTrajectoryTypes)) return;

            trajectoryService.linkTrajectoryToStudy(trajectory.getId(), studyId, type);

        } catch (IOException e) {
            missingTrajectoryTypes.add(type.name());
        }
    }

    private static boolean controlMissingLoads(TrajectoryEntity trajectory, TrajectoryType type, Integer studyId, LoadFileProcessorServiceImpl loadFileProcessorService, List<String> missingTrajectoryTypes) {
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
                    return true;
                }
            } else {

                if (!areasLoadWithoutTrajectorySelected.contains(trajectory.getArea().toUpperCase())) {
                    missingTrajectoryTypes.add(type.name());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds missing trajectory keys by comparing existing trajectories with available ones.
     *
     * @param existingTrajectories trajectories from the original study
     * @param trajectoriesAvailable trajectories available for the new horizon
     * @return list of formatted trajectory keys that are missing
     */
    public static List<String> findMissingTrajectoryKeys(Set<TrajectoryEntity> existingTrajectories,
                                                           List<TrajectoryEntity> trajectoriesAvailable) {
        if (CollectionUtils.isEmpty(existingTrajectories)) {
            return Collections.emptyList();
        }

        Set<TrajectoryKey> availableKeys = trajectoriesAvailable.stream()
                .filter(Objects::nonNull)
                .map(DuplicationTrajectoryUtils::toTrajectoryKey)
                .collect(Collectors.toSet());

        return existingTrajectories.stream()
                .filter(Objects::nonNull)
                .map(DuplicationTrajectoryUtils::toTrajectoryKey)
                .filter(key -> !availableKeys.contains(key))
                .map(DuplicationTrajectoryUtils::formatTrajectoryKeyForWarning)
                .distinct()
                .toList();
    }

    /**
     * Converts a TrajectoryEntity to a TrajectoryKey record.
     *
     * @param trajectory the trajectory entity
     * @return a TrajectoryKey containing fileName, type, and area
     */
    private static TrajectoryKey toTrajectoryKey(TrajectoryEntity trajectory) {
        return new TrajectoryKey(trajectory.getFileName(), trajectory.getType(), trajectory.getArea());
    }

    /**
     * Formats a TrajectoryKey for use in warning messages.
     *
     * @param key the trajectory key to format
     * @return formatted string representation of the trajectory key
     */
    private static String formatTrajectoryKeyForWarning(TrajectoryKey key) {
        return String.format("%s[%s,%s]",
                key.type(),
                key.fileName(),
                key.area() == null ? "" : key.area());
    }


}
