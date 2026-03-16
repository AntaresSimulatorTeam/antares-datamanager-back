package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;

import com.rte_france.antares.datamanager_back.mapper.MiscGenMapper;
import com.rte_france.antares.datamanager_back.mapper.StStorageMapper;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.misc.MiscGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.util.ColumnSplitWriter;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
public class MiscGenerationAssemblerServiceImpl  implements MiscGenerationAssemblerService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;
    private final MiscFileProcessorServiceImpl miscFileProcessorService;
    private final AreaRepository areaRepository;

    public MiscGenerationAssemblerServiceImpl(
            AreaRepository areaRepository,
            MiscFileProcessorServiceImpl miscFileProcessorService,
            NasFileService nasFileService,
            AntaresDataManagerProperties antaresDataManagerProperties,
            TimeSeriesReader timeSeriesReader) {
        this.areaRepository = areaRepository;
        this.miscFileProcessorService = miscFileProcessorService;
        this.nasFileService = nasFileService;
        this.antaresDataManagerProperties = antaresDataManagerProperties;
        this.timeSeriesReader = timeSeriesReader;
    }

    private static final String OTHER_AREA = "OTHERS";
    @Override
    public Map<String, List<MiscGenerationDTO>> assembleMiscProperties(StudyEntity studyEntity) {
        List<Path> generatedFiles = createSplitMiscGenFiles(studyEntity);
        Map<String, List<String>> filesByArea = new HashMap<>();
        for (Path path : generatedFiles) {
            String fileName = path.getFileName().toString();
            // Expected fileName: AREA_GROUP.UUID.arrow or AREA.UUID.arrow
            String area = fileName.split("[_.]")[0].toUpperCase();
            filesByArea.computeIfAbsent(area, k -> new ArrayList<>()).add(fileName);
        }

        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .map(TrajectoryEntity::getMiscClusterCapacityEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(c -> c.getCapacityByYear() != null && c.getCapacityByYear().compareTo(java.math.BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        misc -> misc.getArea().toUpperCase(),
                        Collectors.mapping(
                                misc -> {
                                    MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(misc);
                                    dto.setMiscGenTsList(filesByArea.getOrDefault(misc.getArea().toUpperCase(), Collections.emptyList()));
                                    return dto;
                                },
                                Collectors.toList()
                        )
                ));
    }



    private List<Path> createSplitMiscGenFiles(StudyEntity study) {
        String horizon = study.getHorizon();
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajPath = antaresDataManagerProperties.getTrajectoryFilePath();
        String miscLoadDir = antaresDataManagerProperties.getMiscLoadDirectory();

        // 1. Group trajectories by type and by area (area)
        Map<String, TrajectoryEntity> miscCapacityByArea = study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .filter(t -> t.getArea() != null)
                .collect(Collectors.toMap(
                        t -> t.getArea().toUpperCase(),
                        t -> t,
                        (t1, t2) -> t1
                ));

        Map<String, TrajectoryEntity> miscLoadByArea = study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_LOAD.name().equals(t.getType()))
                .filter(t -> t.getArea() != null)
                .collect(Collectors.toMap(
                        t -> t.getArea().toUpperCase(),
                        t -> t,
                        (t1, t2) -> t1
                ));

        List<Path> allGeneratedFiles = new ArrayList<>();
        Set<String> processedAreas = new HashSet<>();

        // 2. Specific areas treatment
        Set<String> unitAreas = new HashSet<>(miscCapacityByArea.keySet());
        unitAreas.remove(OTHER_AREA.toUpperCase());

        for (String area : unitAreas) {
            TrajectoryEntity capacityTraj = miscCapacityByArea.get(area);
            TrajectoryEntity loadTraj = miscLoadByArea.get(area);

            if (loadTraj != null && loadTraj.getFileName() != null) {
                allGeneratedFiles.addAll(processTrajectoryPair(study, horizon, nasDir, trajPath, miscLoadDir, area, capacityTraj, loadTraj, Set.of()));
                processedAreas.add(area);
            }
        }

        // 3.OTHERS case
        TrajectoryEntity capacityOthers = miscCapacityByArea.get(OTHER_AREA.toUpperCase());
        TrajectoryEntity loadOthers = miscLoadByArea.get(OTHER_AREA.toUpperCase());

        if (capacityOthers != null && loadOthers != null && loadOthers.getFileName() != null) {
            allGeneratedFiles.addAll(processTrajectoryPair(study, horizon, nasDir, trajPath, miscLoadDir, OTHER_AREA, capacityOthers, loadOthers, processedAreas));
        }

        return allGeneratedFiles;
    }

    private List<Path> processTrajectoryPair(StudyEntity study, String horizon, String nasDir, String trajectoryName, String miscLoadDir, String area, TrajectoryEntity capacityTraj, TrajectoryEntity loadTraj, Set<String> processedAreas) {
        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupMap;
        if (capacityTraj != null && capacityTraj.getId() != null) {
            groupMap = miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(capacityTraj.getId());
        } else {
            groupMap = miscFileProcessorService.getAreasByGroupClusterByStudyId(study.getId(), area);
        }

        if (groupMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> generatedFiles = new ArrayList<>();
        Path baseMiscLoadPath = Path.of(nasDir).resolve(trajectoryName).resolve(miscLoadDir).resolve(loadTraj.getFileName());

        for (Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> entry : groupMap.entrySet()) {
            MiscFileProcessorServiceImpl.GroupClusterKey key = entry.getKey();
            Set<String> areas = entry.getValue().stream()
                    .filter(a -> !processedAreas.contains(a.toUpperCase()))
                    .collect(Collectors.toSet());

            if (areas.isEmpty()) continue;

            Path tsFilePath = MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(horizon, baseMiscLoadPath, key);

            if (Files.exists(tsFilePath)) {
                try {
                    generatedFiles.addAll(splitMiscGenLoadFiles(tsFilePath, areas, horizon, key.groupe()));
                } catch (IOException e) {
                    log.error("Error splitting file {}", tsFilePath, e);
                }
            } else {
                log.debug("Load factor file not found for trajectory {}: {}", loadTraj.getFileName(), tsFilePath);
            }
        }
        return generatedFiles;
    }




    public List<Path> splitMiscGenLoadFiles(Path file, Set<String> areas, String horizon, String groupName) throws IOException {
        Set<String> allowedAreas = areas.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<Path> generatedFiles = new ArrayList<>();
        String fileName = file.getFileName().toString().toLowerCase();

        TimeSeriesMatrix matrix;
        TimeSeriesReader localReader = (this.timeSeriesReader != null) ? this.timeSeriesReader : new TimeSeriesReader();

        try {
            if (fileName.endsWith(".xlsx")) {
                matrix = localReader.readFromXlsx(file, horizon);
            } else if (fileName.endsWith(".txt") || fileName.endsWith(".csv")) {
                matrix = localReader.readFromTxt(file);
            } else {
                return generatedFiles;
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }

        if (matrix.columns().isEmpty()) {
            return generatedFiles;
        }

        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();

        for (var column : matrix.columns()) {
            String colName = column.name() != null ? column.name().trim() : "";
            if (colName.isEmpty()) continue;

            if (allowedAreas.contains(colName.toLowerCase())) {
                TimeSeriesMatrix singleColMatrix = new TimeSeriesMatrix(List.of(column));

                String baseName = groupName != null && !groupName.isEmpty() ? colName.toUpperCase() + "_" + groupName : colName.toUpperCase();
                String outputFileName = nasFileService.saveMatrixToNas(singleColMatrix, baseName, outputDir);
                generatedFiles.add(Path.of(outputDir). resolve(outputFileName));
            }
        }

        return generatedFiles;
    }


    private String getBaseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }


}
