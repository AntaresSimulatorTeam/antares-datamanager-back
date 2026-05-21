package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.HydroSeriesType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.*;
import com.rte_france.antares.datamanager_back.service.res.*;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
    private final HydroCoherenceCheckService hydroCoherenceCheckService;

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

    public static final TrajectoryType[] HYDRO_TYPES = {TrajectoryType.HYDRO_ALLOCATION, TrajectoryType.HYDRO_PARAMETERS};
    protected static final String HYDRO_TECHNICAL_PREFIX_ALLOCATION = "hydroAllocation_";
    protected static final String HYDRO_COLUMN = "hydro";
    protected static final String LOAD_COLUMN = "load";
    protected static final String ALLOCATION_COLUMN = "allocation";
    protected static final String[] REQUIRED_HYDRO_ALLOCATION_COLUMNS = {
            HYDRO_COLUMN, LOAD_COLUMN, ALLOCATION_COLUMN};

    protected static final String HYDRO_TECHNICAL_PREFIX_PARAMETERS = "hydroParameters_";
    protected static final String NODE_COLUMN = "node";
    protected static final String INTER_DAILY_BREAKDOWN_COLUMN = "inter.daily.breakdown";
    protected static final String INTER_DAILY_MODULATION_COLUMN = "intra.daily.modulation";
    protected static final String INTER_MONTHLY_BREAKDOWN_COLUMN = "inter.monthly.breakdown";
    protected static final String INITIALIZE_RESERVOIR_DATE_COLUMN = "initialize.reservoir.date";
    protected static final String PUMPING_EFFICIENCY_COLUMN = "pumping.efficiency";
    protected static final String RESERVOIR_COLUMN = "reservoir";
    protected static final String RESERVOIR_CAPACITY_COLUMN = "reservoir.capacity";
    protected static final String FOLLOW_LOAD_COLUMN = "follow.load";
    protected static final String USE_WATER_COLUMN = "use.water";
    protected static final String[] REQUIRED_HYDRO_PARAMETERS_COLUMNS = {
            NODE_COLUMN, INTER_DAILY_BREAKDOWN_COLUMN, INTER_DAILY_MODULATION_COLUMN, INTER_MONTHLY_BREAKDOWN_COLUMN, INITIALIZE_RESERVOIR_DATE_COLUMN,PUMPING_EFFICIENCY_COLUMN, RESERVOIR_COLUMN, RESERVOIR_CAPACITY_COLUMN, FOLLOW_LOAD_COLUMN, USE_WATER_COLUMN};
    protected static final String[] REQUIRED_HYDRO_PARAMETERS_NUMERIC_COLUMNS = {
            INTER_DAILY_BREAKDOWN_COLUMN, INTER_DAILY_MODULATION_COLUMN, INTER_MONTHLY_BREAKDOWN_COLUMN, INITIALIZE_RESERVOIR_DATE_COLUMN, PUMPING_EFFICIENCY_COLUMN, RESERVOIR_CAPACITY_COLUMN};
    protected static final String[] REQUIRED_HYDRO_PARAMETERS_BOOLEAN_COLUMNS = {RESERVOIR_COLUMN, FOLLOW_LOAD_COLUMN, USE_WATER_COLUMN};
                    
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
        List<String> studyAreas = loadStudyAreas(studyId);
        List<HydroSeriesEntity> entities = new ArrayList<>();
        List<String> filesNameFinal = new ArrayList<>(List.of());
        List<String> filesName;
     
        var areaList = Objects.equals(areaParam, OTHERS_AREA) ? studyAreas : List.of(areaParam);
        var hasOnlyRorFile= true;
        for (var area : areaList) {
            filesName = processRequiredSeries(trajectoryFilePath, horizon, area, studyAreas);
            if(!checkHydroFileConsistency(filesName, area)) hasOnlyRorFile = false;
            filesNameFinal.addAll(filesName);
        }
        String maxPowerFileName = null;
        if (!hasOnlyRorFile) {
            List<String> areasInHydroSeriesModFiles = filesNameFinal.stream()
                    .filter(file -> file.startsWith(HYDRO_SERIES_INFLOWS_MOD))
                    .map(s -> s.split("_")[1])
                    .toList();
            maxPowerFileName = processMaxPowerFile(trajectoryFilePath, trajectoryToUse, horizon, areaParam, areasInHydroSeriesModFiles);
            if (studyId != null) {
                hydroCoherenceCheckService.checkHydroSeriesTrajectoriesConsistency(studyId, areasInHydroSeriesModFiles, areaParam, trajectoryToUse);
            }
        }
        if (filesNameFinal.isEmpty()) {
                throw BusinessException.builder()
                        .message("Missing files in trajectory Hydro Series trajectory")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
        }
        // create entities
        filesNameFinal.stream().map(this::buildHydroSeriesEntity)
                .forEach(entities::add);
                
        if (maxPowerFileName != null) {
            entities.add(buildHydroSeriesEntity(maxPowerFileName));
        }
        
        trajectory.setHydroSeriesEntities(entities);
        entities.forEach(entity -> entity.setTrajectory(trajectory));
        return trajectoryRepository.save(trajectory);
    }

    @Override
    public TrajectoryEntity processHydroTechnicalParametersFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
            boolean isCivilYear
    ) throws IOException {

        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, areaParam, null
        );

        Path trajectoryFilePath = validateAndResolveTrajectoryPath(directoryPath, trajectoryToUse);

        TrajectoryEntity trajectory = trajectoryService.buildDirectoryTrajectory(
                TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name(),
                trajectoryToUse,
                trajectoryFilePath,
                horizon,
                areaParam,
                null
        );

        Map<TrajectoryType, Path> pathFiles = findHydroFiles(trajectoryFilePath);

        List<HydroAllocationEntity> allocationEntities;
        List<HydroParametersEntity> parametersEntities;
        
        for (var type : HYDRO_TYPES) {
            var result = processFile(pathFiles.get(type), trajectoryToUse, horizon, areaParam, studyId, type);
            switch (result) {
                case HydroAllocationRowProcessingResult allocation -> {
                    allocationEntities = allocation.entities();
                    allocationEntities.forEach(entity -> entity.setTrajectory(trajectory));
                    trajectory.setHydroAllocationEntities(allocationEntities);
                }

                case HydroParametersRowProcessingResult parameters -> {
                    parametersEntities = parameters.entities();
                    parametersEntities.forEach(entity -> entity.setTrajectory(trajectory));
                    trajectory.setHydroParametersEntities(parametersEntities);
                }
                
                case null ->
                    throw BusinessException.builder()
                            .message("Could not process Hydro Technical parameters trajectory")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
            }
        }
        
        return trajectoryRepository.save(trajectory);
    }

    private Path validateAndResolveTrajectoryPath(Path directoryPath, String trajectoryToUse) throws IOException, BusinessException {
        Path realDirectoryPath = directoryPath.toRealPath();
        Path trajectoryFilePath = realDirectoryPath.resolve(trajectoryToUse).normalize();

        if (!trajectoryFilePath.startsWith(realDirectoryPath)) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        Path realTrajectoryFilePath = trajectoryFilePath.toRealPath();
        if (!realTrajectoryFilePath.startsWith(realDirectoryPath)) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return realTrajectoryFilePath;
    }

    private String processMaxPowerFile(
            Path trajectoryFilePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            List<String> areasInHydroSeriesModFiles
    ) throws IOException {

        Path fileMaxPowerPath = findMaxPowerFile(trajectoryFilePath);
        
        validateMaxPowerFile(fileMaxPowerPath, trajectoryToUse, horizon, areaParam, areasInHydroSeriesModFiles, TrajectoryType.HYDRO_SERIES);
        return fileMaxPowerPath.getFileName().toString();
    }

    private Path findMaxPowerFile(Path trajectoryFilePath) throws BusinessException {
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

    private HydroTechnicalParametersRowProcessingResult processFile(
            Path trajectoryFilePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            Integer studyId,
            TrajectoryType trajectoryType
    ) throws IOException {

        if (trajectoryFilePath != null) {
            List<String> studyAreas = loadStudyAreas(studyId);
            return processTechnicalParametersFile(trajectoryFilePath, trajectoryToUse, horizon, areaParam, studyAreas, trajectoryType, studyId);
        }
        return null;
    }

    private Map<TrajectoryType, Path> findHydroFiles(Path trajectoryFilePath) throws BusinessException {
        Map<TrajectoryType, Path> result = new EnumMap<>(TrajectoryType.class);

        try (Stream<Path> stream = Files.list(trajectoryFilePath)) {
            List<Path> allFiles = stream.toList();

            for (var type : HYDRO_TYPES) {
                String prefix = type == TrajectoryType.HYDRO_ALLOCATION
                        ? HYDRO_TECHNICAL_PREFIX_ALLOCATION
                        : HYDRO_TECHNICAL_PREFIX_PARAMETERS;

                Path match = allFiles.stream()
                        .filter(p -> p.getFileName().toString().startsWith(prefix+trajectoryFilePath.getFileName().toString()))
                        .findFirst()
                        .orElse(null);

                result.put(type, match);
            }

        } catch (IOException e) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryFilePath.toString()))
                    .message("Invalid trajectory path {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (result.values().stream().anyMatch(Objects::isNull)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryFilePath.getFileName().toString()))
                    .message("Missing file hydroAllocation or hydroParameters in trajectory Hydro Technical parameters trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return result;
    }

    private List<String> processRequiredSeries(
            Path trajectoryFilePath,
            String horizon,
            String areaParam,
            List<String> studyAreas
    ) throws IOException, BusinessException {

        Path realTrajectoryFilePath = trajectoryFilePath.toRealPath();
        List<String> filesName = new ArrayList<>(List.of());
        
        for (var entry : REQUIRED_SERIES.entrySet()) {
            String directory = entry.getKey();
            SeriesConfig config = entry.getValue();

            Path seriesDirectoryPath = trajectoryFilePath.resolve(directory).normalize();
            Path realSeriesDirectoryPath = seriesDirectoryPath.toRealPath();

            if (!Files.isDirectory(seriesDirectoryPath) || !isPathWithinDirectory(realTrajectoryFilePath, realSeriesDirectoryPath)) {
                continue;
            }
            
            List<Path> pathFiles = findSeriesFiles(realSeriesDirectoryPath, horizon, areaParam, config, studyAreas);
            if (pathFiles.isEmpty()) {
                continue;
            }

            List<String> filesNameString = pathFiles.stream()
                    .map(f -> f.getFileName().toString())
                    .toList();
            filesName.addAll(filesNameString);
        }
        return filesName;
    }

    private boolean checkHydroFileConsistency(List<String> filesName, String area) throws BusinessException {
        boolean hasMingen = false;
        boolean hasReservoirLevels = false;
        boolean hasMod = false;

        for (String fileName : filesName) {
            if (fileName.startsWith(HYDRO_SERIES_MINGEN+'_'+area)) {
                hasMingen = true;
            }
            if (fileName.startsWith(HYDRO_SERIES_RESERVOIR_LEVELS+'_'+area)) {
                hasReservoirLevels = true;
            }
            if (fileName.startsWith(HYDRO_SERIES_INFLOWS_MOD+'_'+area)) {
                hasMod = true;
            }
        }

        if ((hasMingen && !hasMod) || (hasReservoirLevels && !hasMod)) {
            throw BusinessException.builder()
                    .message("Missing MOD file in trajectory Hydro Series trajectory")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return !hasMingen && !hasMod && !hasReservoirLevels;
    }

    private boolean isPathWithinDirectory(Path parentDirectoryRealPath, Path childRealPath) {
        return childRealPath.startsWith(parentDirectoryRealPath);
    }

    private List<Path> findSeriesFiles(
            Path realPath,
            String horizon,
            String areaParam,
            SeriesConfig config,
            List<String> studyAreas
    ) throws IOException {

        try (Stream<Path> stream = Files.walk(realPath, 1)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isValidSeriesFile(p.getFileName().toString(), horizon, areaParam, config.prefixes(), studyAreas))
                    .sorted()
                    .toList();
        }
    }

    private boolean isValidSeriesFile(String name, String horizon, String areaParam, List<String> prefixes, List<String> studyAreas) {
        if (!name.matches("^[^_]+(?:_[^_]+){1,2}_\\d+-\\d+\\.csv$")) {
            return false;
        }

        String base = name.substring(0, name.length() - 4);
        String[] parts = base.split("_");

        String horizonFile = parts[parts.length - 1];
        String area = parts[parts.length - 2];
        String prefix = String.join("_", Arrays.copyOf(parts, parts.length - 2));

        boolean areaMatches = Objects.equals(areaParam, OTHERS_AREA)
                ? studyAreas.contains(area)
                : areaParam.equals(area);

        return prefixes.contains(prefix)
                && areaMatches
                && horizon.equals(horizonFile);
    }
    
    private String getFiletype(String fileName) {
        if (fileName.startsWith(HYDRO_SERIES_INFLOWS_MOD) || fileName.startsWith(HYDRO_SERIES_INFLOWS_ROR)) {
            return HydroSeriesType.INFLOWS.name();
        }
        if (fileName.startsWith(HYDRO_SERIES_MINGEN)) {
            return HydroSeriesType.MINGEN.name();
        }
        if (fileName.startsWith(HYDRO_SERIES_RESERVOIR_LEVELS)) {
            return HydroSeriesType.RESERVOIR_LEVELS.name();
        }
        return null;
    }

    private HydroSeriesEntity buildHydroSeriesEntity(String fileName) {
        HydroSeriesEntity entity = new HydroSeriesEntity();
        entity.setType(getFiletype(fileName));
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
                    .message("Missing maxpower file in trajectory Hydro Series trajectory")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Normalize the path to avoid traversal or symlink tricks
        Path normalizedFile = filePath.toRealPath();

        try (InputStream is = Files.newInputStream(normalizedFile);
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryToUse, TrajectoryType.HYDRO_SERIES.name());
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

    public HydroTechnicalParametersRowProcessingResult processTechnicalParametersFile(
            Path filePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            List<String> studyAreas,
            TrajectoryType trajectoryType,
            Integer studyId
    ) throws IOException {

        // Validate that the file path is trusted and points to a regular file
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryType.name()))
                    .message("{0} file path is not valid")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Normalize the path to avoid traversal or symlink tricks
        Path normalizedFile = filePath.toRealPath();
        var context = ResRowProcessingContext.builder().studyAreas(studyAreas).areaParam(areaParam).trajectoryToUse(trajectoryToUse).trajectoryType(trajectoryType).build();
        String[] requiredColumns = trajectoryType == TrajectoryType.HYDRO_ALLOCATION ? REQUIRED_HYDRO_ALLOCATION_COLUMNS : REQUIRED_HYDRO_PARAMETERS_COLUMNS;

        try (InputStream is = Files.newInputStream(normalizedFile);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryToUse, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name());
            checkMissingColumns(sheet, requiredColumns, trajectoryToUse, trajectoryType);

            HydroTechnicalParametersRowProcessingResult result = processRows(sheet, context, trajectoryType, studyId, areaParam, trajectoryToUse);

            validateAreas(studyAreas, areaParam, result.fileAreas(), trajectoryToUse, trajectoryType);

            return result;
        }
    }

    private HydroTechnicalParametersRowProcessingResult processRows(
            Sheet sheet,
            ResRowProcessingContext context,
            TrajectoryType trajectoryType,
            Integer studyId,
            String areaParam,
            String trajectoryToUse
    ) {
        HydroTechnicalParametersRowProcessingResult result = getHydroRowProcessingResult(trajectoryType);

        Iterator<Row> rows = sheet.rowIterator();
        rows.next(); // skip header

        List<Row> nonEmptyRows = new ArrayList<>();
        while (rows.hasNext()) {
            Row row = rows.next();
            if (!ExcelCommonValidator.isRowEmpty(row)) {
                String area = getStringCell(row, 0);

                if (!result.fileAreas().contains(area)) {
                    result.addArea(area);
                }

                nonEmptyRows.add(row);
            }
        }

        if (studyId != null) {
            hydroCoherenceCheckService.checkHydroTPTrajectoriesConsistency(studyId, result.fileAreas(), areaParam, trajectoryToUse, trajectoryType.name());
        }

        for (Row row : nonEmptyRows) {
            if (trajectoryType == TrajectoryType.HYDRO_ALLOCATION) {
                processHydroAllocationRow(context, (HydroAllocationRowProcessingResult) result, row);
            } else {
                processHydroParametersRow(context, (HydroParametersRowProcessingResult) result, row);
            }
        }

        validateEmptyRows(nonEmptyRows.isEmpty(), trajectoryType, context.getTrajectoryToUse());

        return result;
    }

    private void processHydroAllocationRow(
            ResRowProcessingContext context,
            HydroAllocationRowProcessingResult result,
            Row row
    ) {
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // Lecture des colonnes
        String area = getStringCell(row, 0);
        String load = getStringCell(row, 1);
        String allocation = getStringNumberCell(row, 2);

        if (!shouldProcessArea(context, area)) return;

        Object[] values = new Object[] { allocation };

        validateEmptyRequiredColumns(context, new String[]{ALLOCATION_COLUMN}, true, values);

        HydroAllocationEntity entity = HydroAllocationEntity.builder()
                .hydro(area)
                .load(load)
                .allocation(Integer.parseInt(allocation))
                .build();

        result.addEntity(entity);

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(load).append("|")
                .append(allocation).append("|");
    }

    private void processHydroParametersRow(
            ResRowProcessingContext context,
            HydroParametersRowProcessingResult result,
            Row row
    ) {
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // Lecture des colonnes
        String area = getStringCell(row, 0);
        String interDailyBreakdown = getStringNumberCell(row, 1);
        String interDailyModulation = getStringNumberCell(row, 2);
        String interMonthlyBreakdown = getStringNumberCell(row, 3);
        String initializeReservoirDate = getStringNumberCell(row, 4);
        String pumpingEfficiency = getStringNumberCell(row, 5);
        Boolean reservoir = ExcelCommonValidator.getBooleanCellValue(row.getCell(6)).orElse(null);
        String reservoirCapacity = getStringNumberCell(row, 7);
        Boolean followLoad = ExcelCommonValidator.getBooleanCellValue(row.getCell(8)).orElse(null);
        Boolean useWater = ExcelCommonValidator.getBooleanCellValue(row.getCell(9)).orElse(null);

        if (!shouldProcessArea(context, area)) return;

        Object[] numericValues = new Object[] { interDailyBreakdown, interDailyModulation, interMonthlyBreakdown, initializeReservoirDate, pumpingEfficiency, reservoirCapacity };
        Object[] booleanValues = new Object[] { reservoir, followLoad, useWater };
        
        validateEmptyRequiredColumns(context, REQUIRED_HYDRO_PARAMETERS_NUMERIC_COLUMNS, true, numericValues);
        validateEmptyRequiredColumns(context, REQUIRED_HYDRO_PARAMETERS_BOOLEAN_COLUMNS, false, booleanValues);

        BigDecimal reservoirCapacityValue = BigDecimal.valueOf(Long.parseLong(reservoirCapacity));

        HydroParametersEntity entity = HydroParametersEntity.builder()
                .node(area)
                .interDailyBreakdown(Integer.parseInt(interDailyBreakdown))
                .interDailyModulation(Integer.valueOf(interDailyModulation))
                .interMonthlyBreakdown(Integer.valueOf(interMonthlyBreakdown))
                .initializeReservoirDate(Integer.valueOf(initializeReservoirDate))
                .pumpingEfficiency(Integer.valueOf(pumpingEfficiency))
                .reservoir(reservoir)
                .reservoirCapacity(reservoirCapacityValue)
                .followLoad(followLoad)
                .useWater(useWater)
                .build();

        result.addEntity(entity);

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(interDailyBreakdown).append("|")
                .append(interDailyModulation).append("|")
                .append(interMonthlyBreakdown).append("|")
                .append(initializeReservoirDate).append("|")
                .append(pumpingEfficiency).append("|")
                .append(reservoir).append("|")
                .append(followLoad).append("|")
                .append(useWater).append("|");                
    }

    public static boolean isInteger(String s) {
        if (s == null) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBooleanStringValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "false".equals(normalized);
    }

    public void validateEmptyRequiredColumns(
            ResRowProcessingContext context,
            String[] requiredColumns,
            boolean isNumeric,
            Object... values
    ) {
        List<String> missing = new ArrayList<>();

        for (int i = 0; i < requiredColumns.length; i++) {
            if (i >= values.length || values[i] == null || (isNumeric && values[i] instanceof String s && !isInteger(s)) || (!isNumeric && values[i] instanceof String str && !isBooleanStringValue(str))) {
                missing.add(requiredColumns[i]);
            }
        }
        
        if (!missing.isEmpty()) {
            String columnsLabel;
            if (context.getTrajectoryType() == TrajectoryType.HYDRO_ALLOCATION) {
                columnsLabel = "Allocation column";
            } else if (isNumeric) {
                columnsLabel = "Column(s) 2 to 6 and 8";
            } else {
                columnsLabel = "Column(s) 7, 9 and 10";
            }

            String typeLabel = getErrorMessageLabelFromType(context.getTrajectoryType().name());
            String valuesLabel = isNumeric ? "filled and of numeric type" : "boolean values";
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(columnsLabel, typeLabel, context.getTrajectoryToUse(), valuesLabel))
                    .message("{0} in the {1} trajectory {2} must be {3}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private @NonNull HydroTechnicalParametersRowProcessingResult getHydroRowProcessingResult(TrajectoryType trajectoryType) {
        HydroTechnicalParametersRowProcessingResult result;
        switch (trajectoryType) {
            case TrajectoryType.HYDRO_ALLOCATION ->
                    result = new HydroAllocationRowProcessingResult(new ArrayList<>(),
                            new StringBuilder(),
                            new ArrayList<>());
            default ->
                    result = new HydroParametersRowProcessingResult(
                            new ArrayList<>(),
                            new StringBuilder(),
                            new ArrayList<>());
        }
        return result;
    }

    public List<String> loadStudyAreas(Integer studyId) {
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }

    private boolean shouldProcessArea(ResRowProcessingContext context, String area) {

        String areaParam = context.getAreaParam();
        String areaStr = Objects.toString(area, "");

        // 1. Filtre par area
        if (areaParam != null) {

            // Cas normal : areaParam != OTHERS
            if (!OTHERS_AREA.equalsIgnoreCase(areaParam)
                    && !areaParam.equalsIgnoreCase(areaStr)) {
                return false;
            }

            // Cas OTHERS : area doit être dans studyAreas
            if (OTHERS_AREA.equalsIgnoreCase(areaParam)
                    && !context.getStudyAreas().contains(areaStr.toUpperCase())) {
                return false;
            }
        }
        
        return true;
    }
}