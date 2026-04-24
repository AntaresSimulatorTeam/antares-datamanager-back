package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.HydroSeriesType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroFileProcessorService;
import com.rte_france.antares.datamanager_back.service.res.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.*;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroFileProcessorServiceImpl implements HydroFileProcessorService {
    private final TrajectoryRepository trajectoryRepository;
    private final TrajectoryServiceImpl trajectoryService;

    protected static final String HYDRO_SERIES_PREFIX_MAX_POWER = "maxpower_";
    protected static final String HYDRO_SERIES_INFLOWS = "inflows";
    protected static final String HYDRO_SERIES_INFLOWS_ROR = "ror";
    protected static final String HYDRO_SERIES_INFLOWS_MOD = "mod";
    protected static final String HYDRO_SERIES_MINGEN = "mingen";
    protected static final String HYDRO_SERIES_RESERVOIR_LEVELS = "reservoir_levels";

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
                TrajectoryType.RES_CAPACITY,
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
        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(TrajectoryType.HYDRO_SERIES.name(), trajectoryToUse,trajectoryFilePath, horizon, areaParam, null);

        List<HydroSeriesEntity> entities = new ArrayList<>();
        // récupérer et traiter les contrôles de maxpower
        // prefix check
        if (!startsWithIgnoreCase(trajectoryToUse, HYDRO_SERIES_PREFIX_MAX_POWER)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(HYDRO_SERIES_PREFIX_MAX_POWER))
                    .message("The trajectory file name must start with {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        Path fileMaxPowerPath = trajectoryService.getTrajectoryFilePath(TrajectoryType.HYDRO_SERIES, trajectoryToUse, null);

        HydroSeriesEntity entity = buildHydroSeriesEntity(fileMaxPowerPath.toString(), null);
        entities.add(entity);

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
                        .filter(name -> name.matches("^[^_]+_[^_]+_[^_]+\\.csv$"))
                        .filter(name -> {
                            String base = name.substring(0, name.length() - 4);
                            String[] parts = base.split("_");
                            if (parts.length < 3) {
                                return false;
                            }
                            String prefix = parts[0];
                            String area = parts[1];
                            String horizonFile = parts[2];
                            return prefixes.contains(prefix)
                                    && areaParam.equals(area)
                                    && horizon.equals(horizonFile);
                        })
                        .sorted()
                        .map(realPath::resolve)
                        .toList();

//                    if (files.isEmpty()) {
//                        throw BusinessException.builder()
//                                .message("No csv file found in folder for HYDRO series trajectory: " + trajectoryToUse)
//                                .httpStatus(HttpStatus.BAD_REQUEST)
//                                .build();
//                    }

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
        // list de nom de ts_name + HydroSeriesType
        return entity;
    }
}