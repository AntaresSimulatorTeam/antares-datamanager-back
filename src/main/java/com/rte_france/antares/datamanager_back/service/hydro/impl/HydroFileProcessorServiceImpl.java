package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.HydroSeriesType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroFileProcessorServiceImpl implements HydroFileProcessorService {
    private final TrajectoryRepository trajectoryRepository;
    private final TrajectoryServiceImpl trajectoryService;
    private final AreaRepository areaRepository;

    protected static final String HYDRO_SERIES_PREFIX_MAX_POWER = "maxpower_";
    protected static final String HYDRO_SERIES_INFLOWS = "inflows";
    protected static final String HYDRO_SERIES_INFLOWS_ROR = "ror";
    protected static final String HYDRO_SERIES_INFLOWS_MOD = "mod";
    protected static final String HYDRO_SERIES_MINGEN = "mingen";
    protected static final String HYDRO_SERIES_RESERVOIR_LEVELS = "reservoir_levels";
    protected static final String FILE_NOT_FOUND = "Not found";

    public record SeriesConfig(HydroSeriesType type, List<String> prefixes) {}
    protected static final Map<String, SeriesConfig> REQUIRED_SERIES = Map.of(
            HYDRO_SERIES_INFLOWS,
            new SeriesConfig(HydroSeriesType.INFLOWS, List.of(HYDRO_SERIES_INFLOWS_ROR, HYDRO_SERIES_INFLOWS_MOD)),

            HYDRO_SERIES_MINGEN,
            new SeriesConfig(HydroSeriesType.MINGEN, List.of(HYDRO_SERIES_MINGEN)),

            HYDRO_SERIES_RESERVOIR_LEVELS,
            new SeriesConfig(HydroSeriesType.RESERVOIR_LEVELS, List.of(HYDRO_SERIES_RESERVOIR_LEVELS))
    );

    @Override
    public TrajectoryEntity processHydroSeriesFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
            boolean isCivilYear
    ) throws IOException {

        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.HYDRO_SERIES, areaParam, null
        );

        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);

        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(
                TrajectoryType.HYDRO_SERIES.name(),
                trajectoryToUse,
                trajectoryFilePath,
                horizon,
                areaParam,
                null
        );

        List<HydroSeriesEntity> entities = new ArrayList<>();
        
        processMaxPowerFile(trajectoryFilePath, trajectoryToUse, horizon, areaParam, studyId, entities);
        processRequiredSeries(trajectoryFilePath, horizon, areaParam, entities, trajectory);

        trajectory.setHydroSeriesEntities(entities);
        return trajectoryRepository.save(trajectory);
    }

    private Path validateAndResolveTrajectoryPath(Path directoryPath, String trajectoryToUse) throws BusinessException {
        Path trajectoryFilePath = directoryPath.resolve(trajectoryToUse).normalize();

        if (!trajectoryFilePath.startsWith(directoryPath)) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return trajectoryFilePath;
    }

    private void processMaxPowerFile(
            Path trajectoryFilePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            Integer studyId,
            List<HydroSeriesEntity> entities
    ) throws IOException {

        Path fileMaxPowerPath = findMaxPowerFile(trajectoryFilePath);

        if (fileMaxPowerPath != null) {
            List<String> studyAreas = loadStudyAreas(studyId);
            validateMaxPowerFile(fileMaxPowerPath, trajectoryToUse, horizon, areaParam, studyAreas, TrajectoryType.HYDRO_SERIES);

            entities.add(buildHydroSeriesEntity(fileMaxPowerPath.getFileName().toString(), null));
        }
    }

    private Path findMaxPowerFile(Path trajectoryFilePath) throws IOException, BusinessException {
        try (Stream<Path> stream = Files.list(trajectoryFilePath)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().startsWith(HYDRO_SERIES_PREFIX_MAX_POWER))
                    .toList();

            if (files.isEmpty()) {
                throw BusinessException.builder()
                        .message("No maxpower file found for HYDRO series trajectory.")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            return files.getFirst();
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path for maxpower")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void processRequiredSeries(
            Path trajectoryFilePath,
            String horizon,
            String areaParam,
            List<HydroSeriesEntity> entities,
            TrajectoryEntity trajectory
    ) throws IOException, BusinessException {

        for (var entry : REQUIRED_SERIES.entrySet()) {
            String directory = entry.getKey();
            SeriesConfig config = entry.getValue();

            Path seriesDirectoryPath = trajectoryFilePath.resolve(directory).normalize();

            if (!Files.isDirectory(seriesDirectoryPath)) {
                continue;
            }

            Path realPath = seriesDirectoryPath.toRealPath();

            List<Path> files = findSeriesFiles(realPath, horizon, areaParam, config);

            if (files.isEmpty()) {
                throw BusinessException.builder()
                        .message("No files found for HYDRO series trajectory in " + directory)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            files.stream()
                    .map(f -> buildHydroSeriesEntity(f.getFileName().toString(), config.type()))
                    .forEach(e -> {
                        e.setTrajectory(trajectory);
                        entities.add(e);
                    });
        }
    }

    private List<Path> findSeriesFiles(
            Path realPath,
            String horizon,
            String areaParam,
            SeriesConfig config
    ) throws IOException {

        try (Stream<Path> stream = Files.walk(realPath, 1)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isValidSeriesFile(p.getFileName().toString(), horizon, areaParam, config.prefixes()))
                    .sorted()
                    .toList();
        }
    }

    private boolean isValidSeriesFile(String name, String horizon, String areaParam, List<String> prefixes) {
        if (!name.matches("^[^_]+(?:_[^_]+){1,2}_\\d+-\\d+\\.csv$")) {
            return false;
        }

        String base = name.substring(0, name.length() - 4);
        String[] parts = base.split("_");

        String horizonFile = parts[parts.length - 1];
        String area = parts[parts.length - 2];
        String prefix = String.join("_", Arrays.copyOf(parts, parts.length - 2));

        return prefixes.contains(prefix)
                && areaParam.equals(area)
                && horizon.equals(horizonFile);
    }

    private HydroSeriesEntity buildHydroSeriesEntity(String fileName, HydroSeriesType type) {
        HydroSeriesEntity entity = new HydroSeriesEntity();
        entity.setType(String.valueOf(type));
        entity.setTsName(fileName);
        return entity;
    }

    public void validateMaxPowerFile(
            Path filePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            List<String> studyAreas,
            TrajectoryType trajectoryType
    ) throws IOException {

        // Validate that the file path is trusted and points to a regular file
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw BusinessException.builder()
                    .message(FILE_NOT_FOUND + filePath)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Normalize the path to avoid traversal or symlink tricks
        Path normalizedFile = filePath.toRealPath();

        try (InputStream is = Files.newInputStream(normalizedFile);
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = getRequiredSheet(workbook, horizon, filePath);
            Row header = getHeaderOrThrow(sheet, filePath, TrajectoryType.HYDRO_SERIES);
            List<String> headerAreas = new ArrayList<>();

            DataFormatter formatter = new DataFormatter(); 

            for (int i = 1; i < header.getLastCellNum(); i++) { 
                Cell cell = header.getCell(i);
                String value = formatter.formatCellValue(cell);
                headerAreas.add(value);
            }
            
            validateAreas(studyAreas, areaParam, headerAreas, trajectoryToUse, trajectoryType);
        }
    }

    public List<String> loadStudyAreas(Integer studyId) {
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }
}