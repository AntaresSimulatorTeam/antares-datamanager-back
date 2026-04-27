package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.HydroSeriesType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroFileProcessorService;
import com.rte_france.antares.datamanager_back.service.res.*;
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

    protected static final String HYDRO_SERIES_FILE_FORMAT = ".csv";
    protected static final String LITERAL_STRING = "%s/%s/%s";

    @Override
    public TrajectoryEntity processHydroSeriesFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
            boolean isCivilYear
    ) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.HYDRO_SERIES,
                areaParam,
                null
        );
        Path trajectoryFilePath = directoryPath.resolve(trajectoryToUse).normalize();

        if (!trajectoryFilePath.startsWith(directoryPath)) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(TrajectoryType.HYDRO_SERIES.name(), trajectoryToUse, trajectoryFilePath, horizon, areaParam, null);

        List<HydroSeriesEntity> entities = new ArrayList<>();
        try (Stream<Path> stream = Files.list(trajectoryFilePath)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().startsWith(HYDRO_SERIES_PREFIX_MAX_POWER))
                    .toList();

            Path fileMaxPowerPath = null;
            if (files.size() == 1) {
                fileMaxPowerPath = files.getFirst();
            } else if (files.isEmpty()) {
                if (files.isEmpty()) {
                        throw BusinessException.builder()
                                .message("No maxpower file found for HYDRO series trajectory.")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build();
                }
            } else {
                if (files.size() > 1) {
                    throw BusinessException.builder()
                            .message("Plusieurs fichiers ont pour préfixe " + HYDRO_SERIES_PREFIX_MAX_POWER + " :")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
            if (fileMaxPowerPath != null) {
                List<String> studyAreas = loadStudyAreas(studyId);
                validateMaxPowerFile(fileMaxPowerPath, trajectoryToUse,horizon, areaParam, studyAreas, false, TrajectoryType.HYDRO_SERIES);
                HydroSeriesEntity entity = buildHydroSeriesEntity(fileMaxPowerPath.getFileName().toString(), null);
                entities.add(entity);
            }
        }

        // récupération des trajectoires dans les sous-dossiers
        for (var entry : REQUIRED_SERIES.entrySet()) {
            String directory = entry.getKey();
            Path filePath = trajectoryFilePath
                    .resolve(directory)
                    .normalize();
            // Ensure the path is real and validated before using Files.walk
            Path realPath = filePath.toRealPath();

            //find csv files in technologyPath directory
            try (var filesStream = Files.walk(realPath, 1)) {
                SeriesConfig config = entry.getValue();
                List<String> prefixes = config.prefixes();

                List<Path> files = filesStream
                        .filter(Files::isRegularFile)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.matches("^[^_]+(?:_[^_]+){1,2}_\\d+-\\d+\\.csv$"))
                        .filter(name -> {
                            String base = name.substring(0, name.length() - 4); // retire .csv
                            String[] parts = base.split("_");

                            if (parts.length < 3) {
                                return false;
                            }

                            String horizonFile = parts[parts.length - 1];
                            String area = parts[parts.length - 2];
                            String prefix = String.join("_", Arrays.copyOf(parts, parts.length - 2));

                            return prefixes.contains(prefix)
                                    && areaParam.equals(area)
                                    && horizon.equals(horizonFile);
                        })
                        .sorted()
                        .map(realPath::resolve)
                        .toList();

                    if (files.isEmpty()) {
                        throw BusinessException.builder()
                                .message("No files found for HYDRO series trajectory in "+ directory + " folder.")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build();
                    }

                List<HydroSeriesEntity> entitiesByType = files.stream()
                        .map(file -> buildHydroSeriesEntity(file.getFileName().toString(), config.type()))
                        .toList();
                entities.addAll(entitiesByType);
            } catch (IOException e) {
                throw BusinessException.builder()
                        .message("Could not import HYDRO series trajectory")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            entities.forEach(e -> e.setTrajectory(trajectory));
            trajectory.setHydroSeriesEntities(entities);
        }

        return trajectoryRepository.save(trajectory);
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
            boolean isCivilYear,
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

    private List<String> loadStudyAreas(Integer studyId) {
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }
}