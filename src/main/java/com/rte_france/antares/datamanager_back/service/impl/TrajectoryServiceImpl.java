package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


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

    private static final Map<TrajectoryType, String> FILE_EXTENSIONS = new EnumMap<>(TrajectoryType.class);

    static {
        FILE_EXTENSIONS.put(TrajectoryType.LOAD, ".txt");
    }

    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon) throws IOException {
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
            case LINK -> linkFileProcessorService.processLinkFile(trajectoryFilePath, horizon);
            case THERMAL_CAPACITY -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, trajectoryType);
            case THERMAL_PARAMETER -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalParameters, trajectoryType);
            case THERMAL_COST -> thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalCosts, trajectoryType);
            case LOAD -> loadFileProcessorService.processLoadFile(trajectoryFilePath, horizon);
            default -> throw new IllegalArgumentException("The provided trajectory type is not supported.");
        };
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType trajectoryType, String horizon, String fileNameStartsWith) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(trajectoryType.name(), horizon, fileNameStartsWith);
    }

    public List<FsTrajectoryDTO> findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType trajectoryType) {
        Path directory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(trajectoryType.name().toLowerCase());


        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
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
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndIds(String trajectoryType, List<Integer> trajectoryIds) {
        return trajectoryRepository.findByTypeAndIdIn(trajectoryType, trajectoryIds).stream()
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

    @Transactional
    public TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) {
        StudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> new ResourceNotFoundException("Study not found"));

        TrajectoryEntity trajectory = trajectoryRepository.findById(trajectoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Trajectory not found"));

        // Vérifier si une trajectoire du même type est déjà associée à l'étude
        Optional<StudyTrajectoryEntity> existingLink = study.getStudyTrajectoryEntities().stream()
                .filter(studyTrajectory -> studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                .findFirst();

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

}
