package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.trajectoryData.TrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.mapper.LinkMapper;
import com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalFileProcessorService thermalFileProcessorService;

    private final LoadFileProcessorService loadFileProcessorService;

    private final StudyRepository studyRepository;

    private final StudyTrajectoryRepository studyTrajectoryRepository;

    private final AreaConfigRepository areaConfigRepository;

    private final LinkRepository linkRepository;

    private final WarningMessageRepository warningMessageRepository;

    private static final Map<TrajectoryType, String> FILE_EXTENSIONS = new EnumMap<>(TrajectoryType.class);

    static {
        FILE_EXTENSIONS.put(TrajectoryType.LOAD, ".txt");
    }

    /**
     * Processes a trajectory file based on the given type, file name, horizon, and study ID.
     *
     * @param trajectoryType   the type of the trajectory
     * @param trajectoryToUse  the name of the trajectory file to use
     * @param horizon          the horizon period in the format yyyy-yyyy
     * @param studyId          the ID of the study
     * @return the processed TrajectoryEntity
     * @throws IOException if an I/O error occurs
     */
    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        //build the file path
        Path baseDirectory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                // TODO: Change thermalCapacityArea according to the file tree structure
                .resolve(getDirectoryByTrajectoryType(trajectoryType, ""))
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        //download the file
        var fileExtension = FILE_EXTENSIONS.getOrDefault(trajectoryType, ".xlsx");
        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse + fileExtension).normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }

        return switch (trajectoryType) {
            case AREA -> areaFileProcessorService.processAreaFile(trajectoryFilePath, horizon);
            case LINK -> linkFileProcessorService.processLinkFile(trajectoryFilePath, horizon,studyId);
            case THERMAL_CAPACITY -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, trajectoryType);
            case THERMAL_PARAMETER -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalParameters, trajectoryType);
            case THERMAL_COST -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalCosts, trajectoryType);
            case LOAD -> loadFileProcessorService.processLoadFile(trajectoryFilePath, horizon);
            default -> throw new IllegalArgumentException("The provided trajectory type is not supported.");
        };
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameContains) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(trajectoryType.name(), horizon, fileNameContains);
    }

    /**
     * Finds trajectories by type from the NAS directory.
     *
     * @param trajectoryType the type of the trajectory
     * @return a list of FsTrajectoryDTO representing the trajectories
     */
    public List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String fileNameContains) {
        Path directory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(trajectoryType.name().toLowerCase());

        try (var stream = Files.list(directory)) {
            var trajectories = stream
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return FsTrajectoryDTO.builder()
                                    .fileName(path.getFileName().toString())
                                    .lastModifiedDate(Files.getLastModifiedTime(path)
                                            .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                                    .type(trajectoryType.name())
                                    .build();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(dto -> fileNameContains == null || dto.getFileName().toLowerCase().contains(fileNameContains.toLowerCase()))
                    .toList();

            var latestTrajectories = extractKeyFromColumnByComparator(
                    trajectories,
                    FsTrajectoryDTO::getFileName,
                    Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate).reversed()
            );

            return sortedByComparator(latestTrajectories.values(), Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate).reversed());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> List<T> sortedByComparator(Collection<T> collection, Comparator<T> comparator) {
        return collection.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private static <T, U> Map<T, U> extractKeyFromColumnByComparator(Collection<U> entities, Function<U, T> keyExtractor, Comparator<U> comparator) {
        if (entities == null) {
            return Map.of();
        }
        return entities.stream()
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        BinaryOperator.maxBy(comparator)
                ));
    }

    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId).stream()
                .map(TrajectoryMapper::toTrajectoryDTO)
                .toList();
    }

    private String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String thermalCapacityArea) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case THERMAL_COST -> antaressDataManagerProperties.getThermalCostDirectory();
            case THERMAL_CAPACITY -> Path.of(antaressDataManagerProperties.getThermalCapacityDirectory())
                    .resolve(thermalCapacityArea)
                    .toString();
            case THERMAL_PARAMETER -> antaressDataManagerProperties.getThermalParameterDirectory();
            case LOAD -> antaressDataManagerProperties.getLoadDirectory();
            case MISC ->
                    throw new IllegalArgumentException("No directory defined for TrajectoryType: " + trajectoryType);
            default -> throw new IllegalArgumentException("Invalid TrajectoryType: " + trajectoryType);
        };
    }

    /**
     * Links a trajectory to a study. If a trajectory of the same type is already linked to the study,
     * the existing link is removed before creating the new link.
     *
     * @param trajectoryId the ID of the trajectory to link
     * @param studyId      the ID of the study to link the trajectory to
     * @param type         the type of the trajectory
     * @return the linked TrajectoryEntity
     * @throws ResourceNotFoundException if the study or trajectory is not found
     */
    @Transactional
    public TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>(); // Nouvelle instance locale

        StudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> new ResourceNotFoundException("Study not found"));

        TrajectoryEntity trajectory = trajectoryRepository.findById(trajectoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Trajectory not found"));

        // Vérifier si une trajectoire du même type est déjà associée à l'étude
        Optional<StudyTrajectoryEntity> existingLink = study.getStudyTrajectoryEntities().stream()
                .filter(studyTrajectory -> studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                .findFirst();


        // check area link
        checkLinkAreaCoherence(studyId, warningMessageEntities, trajectory);


        // Supprimer l'ancienne association si elle existe
        existingLink.ifPresent(studyTrajectoryRepository::delete);

        // Créer une nouvelle association
        StudyTrajectoryEntity newStudyTrajectoryEntity = StudyTrajectoryEntity.builder()
                .id(StudyTrajectoryKey.builder()
                        .trajectoryId(trajectoryId)
                        .scenarioId(studyId)
                        .build())
                .studyEntity(study)
                .trajectory(trajectory)
                .build();

        StudyTrajectoryEntity savedStudyTrajectoryEntity = studyTrajectoryRepository.save(newStudyTrajectoryEntity);

        return savedStudyTrajectoryEntity.getTrajectory();
    }

    public void checkLinkAreaCoherence(Integer studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory) {
        if (trajectory.getType().equals(TrajectoryType.LINK.name())) {
            var listLink = trajectory.getLinkEntities();
            List<String> areasSavedForScenario = linkFileProcessorService.findListArea(studyId);
            listLink.forEach(link -> linkFileProcessorService.validateLinkAreas(link.getName(), areasSavedForScenario));
            linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(listLink, areasSavedForScenario, warningMessageEntities);
        } else if (trajectory.getType().equals(TrajectoryType.AREA.name())) {
            List<String> areasSavedForScenario = trajectory.getAreaConfigEntities().stream()
                    .map(area -> area.getArea().getName())
                    .toList();
            List<LinkEntity> listLink = linkFileProcessorService.findListLink(studyId);
            listLink.forEach(link -> linkFileProcessorService.validateLinkAreas(link.getName(), areasSavedForScenario));
            linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(listLink, areasSavedForScenario, warningMessageEntities);
        }
        warningMessageEntities.forEach(warning -> warning.setTrajectory(trajectory));
        warningMessageRepository.saveAll(warningMessageEntities);
    }

    @Override
    public void unlinkTrajectoryFromStudy(Integer trajectoryId, Integer studyId) {
        studyTrajectoryRepository.findById(StudyTrajectoryKey.builder()
                        .trajectoryId(trajectoryId)
                        .scenarioId(studyId)
                        .build())
                .ifPresentOrElse(studyTrajectoryRepository::delete,
                        () -> {
                            throw new ResourceNotFoundException("Link not found");
                        });
    }


    @Override
    public List<TrajectoryDataDTO> getTrajectoryDataByTypeAndId(TrajectoryType trajectoryType, Integer trajectoryId) {
        return switch (trajectoryType) {
            case AREA -> areaConfigRepository.findAreaConfigByTrajectoryId(trajectoryId)
                    .stream()
                    .map(AreaMapper::toAreaTrajectoryDataDTO)
                    .collect(Collectors.toList());

            case LINK -> linkRepository.findLinkEntitiesByTrajectoryIdIs(trajectoryId)
                    .stream()
                    .map(LinkMapper::toLinkTrajectoryDataDTO)
                    .collect(Collectors.toList());

            default -> throw new UnsupportedOperationException("TrajectoryType " + trajectoryType + " is not supported.");
        };
    }
}
