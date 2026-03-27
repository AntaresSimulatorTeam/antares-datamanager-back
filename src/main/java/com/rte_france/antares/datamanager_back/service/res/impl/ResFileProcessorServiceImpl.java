package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ResTechnologyDistributionEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.RES_CAPACITY_PREFIX;
import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.RES_TECHNOLOGY_DISTRIBUTION_PREFIX;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResFileProcessorServiceImpl implements ResFileProcessorService {
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final TrajectoryServiceImpl trajectoryService;

    protected static final String GROUP_COLUMN = "Group";
    protected static final String CLUSTER_COLUMN = "Cluster";
    protected static final String[] REQUIRED_CLUSTER_COLUMNS = {
            "ToUse", "Area", GROUP_COLUMN, CLUSTER_COLUMN, "Category"};
    protected static final String[] REQUIRED_OFFSHORE_CLUSTER_COLUMNS = {
            "ToUse", "Area", GROUP_COLUMN, CLUSTER_COLUMN, "PECD_Zone"};
    protected static final String[] REQUIRED_TECHNOLOGY_DISTRIBUTION_COLUMNS = {
            GROUP_COLUMN, CLUSTER_COLUMN, "PECD_Zone", "Techno_PECD"};
    protected static final String OFFSHORE = "offshore";
    protected static final String FILE_FORMAT = ".xlsx";
    protected static final String FILE_NOT_FOUND = "File not found: ";

    @Transactional
    @Override
    public TrajectoryEntity processInstalledResFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
            String technology,
            boolean isCivilYear
    ) throws IOException {

        List<String> studyAreas = loadStudyAreas(studyId);
        String technologyParam = technology != null ? toSnakeCase(technology): null;

        List<Path> files = resolveFiles(trajectoryToUse, areaParam, technologyParam);
        ResRowProcessingResult aggregated = files.stream()
                .map(file -> {
                    try {
                        return processResCapacityFile(
                                file,
                                file.getFileName().toString(),
                                horizon,
                                areaParam,
                                technologyParam,
                                studyAreas,
                                isCivilYear,
                                TrajectoryType.RES_CAPACITY);
                    } catch (IOException e) {
                        throw BusinessException.builder()
                                .message("Could not process RES installed power trajectory")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build();
                    }
                })
                .reduce(this::merge)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Could not import RES installed power trajectory")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build()
                );

        // Le dernier fichier traité donne le dossier ou fichier de référence
        Path referencePath = files.size() == 1 ? files.get(0) : files.get(0).getParent();

        return saveTrajectory(horizon, areaParam, technology, referencePath, aggregated, TrajectoryType.RES_CAPACITY);
    }

    @Override
    public TrajectoryEntity processLoadFactorResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology) throws Exception {
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        
        Path trajectoryFilePath = basePath
                .resolve(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, area, null))
                .resolve(trajectoryToUse)
                .resolve(technology).resolve(technology)
                .normalize();

        // Validate path to prevent directory traversal attacks
        validatePathSecurity(basePath, trajectoryFilePath, trajectoryToUse);
        
        checkExistingTs(trajectoryFilePath, trajectoryToUse);
        TrajectoryEntity trajectory = buildLoadFactorMiscTrajectory(trajectoryToUse,trajectoryFilePath, horizon, area, technology);
        return trajectoryRepository.save(trajectory);
    }

    @Transactional
    @Override
    public TrajectoryEntity processTechnologyDistributionResFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            String areaParam,
            String technology,
            boolean isCivilYear
    ) throws IOException {

        List<String> studyAreas = loadStudyAreas(studyId);
        String technologyParam = technology != null ? toSnakeCase(technology): null;

        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION,
                areaParam,
                null
        );

        validatePrefixIfNeeded(areaParam, trajectoryToUse, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, RES_TECHNOLOGY_DISTRIBUTION_PREFIX);

        String fileName = trajectoryToUse.endsWith(FILE_FORMAT) ? trajectoryToUse : trajectoryToUse + FILE_FORMAT;
        Path filePath = directoryPath.resolve(fileName).normalize();

        if (!filePath.startsWith(directoryPath)) {
            throw BusinessException.builder()
                    .message(FILE_NOT_FOUND + filePath)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        ResRowProcessingResult result = null;
        try {
            result = processResCapacityFile(
                    filePath,
                    filePath.getFileName().toString(),
                    horizon,
                    areaParam,
                    technologyParam,
                    studyAreas,
                    isCivilYear,
                    TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION
            );
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Could not import RES technology distribution trajectory")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }


        return saveTrajectory(horizon, areaParam, technology, filePath, result, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION);
    }

    private static void validatePathSecurity(Path basePath, Path trajectoryFilePath, String trajectoryToUse) throws IOException {

        if (!basePath.endsWith("/")) {
            basePath = basePath.resolve("");
        }
        if (!trajectoryFilePath.startsWith(basePath)) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private static void checkExistingTs(Path trajectoryFilePath, String trajectoryToUse) throws IOException {
        // technologyPath directory must contain at least one ts .csv file
        if(Files.exists(trajectoryFilePath)) {
            // Ensure the path is real and validated before using Files.walk
            Path realPath = trajectoryFilePath.toRealPath();
            
            //find csv files in technologyPath directory
            try (var filesStream = Files.walk(realPath, 1)) {
                boolean hasCsv = filesStream
                        .filter(Files::isRegularFile)
                        .anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".csv"));
                if (!hasCsv) {
                    throw BusinessException.builder()
                            .message("No csv file found in technology folder for load factor misc trajectory: " + trajectoryToUse)
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }

        } else {
            throw BusinessException.builder()
                    .message("No technology folder found for load factor misc trajectory: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private TrajectoryEntity buildLoadFactorMiscTrajectory(String trajectoryToUse, Path trajectoryFilePath, String horizon, String area, String technology) throws Exception {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String checksum = calculateDirectoryChecksum(trajectoryFilePath);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryFilePath))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .checksum(checksum)
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(trajectoryFilePath).toMillis()),
                        ZoneId.systemDefault()))
                .horizon(civilToChevalHorizon(horizon))
                .area(area)
                .technology(technology)
                .type(TrajectoryType.RES_LOAD.name())
                .hasTimeSeries(true)
                .build();

        var existingTrajectory = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                trajectoryToUse,
                TrajectoryType.RES_LOAD.name(),
                horizon,
                area,
                technology);

        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                throwAlreadyProcessedFileException(trajectoryFilePath);
            } else {
                trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else {
            trajectory.setVersion(1);
        }

        return trajectory;
    }
    private List<Path> resolveFiles(String trajectoryToUse, String areaParam, String technology) throws IOException {

        boolean isFR = "FR".equalsIgnoreCase(areaParam);

        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.RES_CAPACITY,
                isFR ? "FR" : areaParam,
                null
        );

        if (isFR) {
            Path folderPath = directoryPath.resolve(trajectoryToUse).normalize();
            
            List<Path> files;
            try {
                files = findFilesFromDepthWithPrefix(folderPath, RES_CAPACITY_PREFIX, 2, technology);
            } catch (IOException e) {
                // Catch et lève la BusinessException comme avant
                throw BusinessException.builder()
                        .message("No FR res capacity file found in directory: " + folderPath)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            return files;

        } else {
            validatePrefixIfNeeded(areaParam, trajectoryToUse, TrajectoryType.RES_CAPACITY, RES_CAPACITY_PREFIX);
            
            String fileName = trajectoryToUse.endsWith(FILE_FORMAT) ? trajectoryToUse : trajectoryToUse + FILE_FORMAT;
            Path filePath = directoryPath.resolve(fileName).normalize();
            
            if (!filePath.startsWith(directoryPath)) {
                throw BusinessException.builder()
                        .message(FILE_NOT_FOUND + filePath)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            return List.of(filePath);
        }
    }

    public ResRowProcessingResult processResCapacityFile(
            Path filePath,
            String trajectoryToUse,
            String horizon,
            String areaParam,
            String technology,
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
            
            int indexSheet = 0;
            String[] requiredColumns = REQUIRED_CLUSTER_COLUMNS;
            boolean isOffshoreTechnology = false;
            if (trajectoryType == TrajectoryType.RES_CAPACITY) {
                isOffshoreTechnology = trajectoryToUse.contains(OFFSHORE);
                if (isOffshoreTechnology) {
                    requiredColumns = REQUIRED_OFFSHORE_CLUSTER_COLUMNS;
                }
            }
            if (trajectoryType == TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION) {
                indexSheet = 1;
                requiredColumns = REQUIRED_TECHNOLOGY_DISTRIBUTION_COLUMNS;
            }
            Sheet sheet = getSheetOrThrow(workbook, filePath, indexSheet);
            Row header = getHeaderOrThrow(sheet, filePath);

            validateHeaderColumns(header, sheet, requiredColumns, trajectoryToUse);
            
            int yearColIndex = resolveYearColumnIndex(header, horizon, trajectoryToUse, isCivilYear);

            ResRowProcessingContext context = new ResRowProcessingContext(studyAreas, areaParam, yearColIndex, trajectoryToUse, technology);
            
            ResRowProcessingResult result = processRows(sheet, context, isOffshoreTechnology, requiredColumns, trajectoryType);

            validateAreas(studyAreas, areaParam, result.fileAreas(), trajectoryToUse, trajectoryType);
            if (technology != null) {
                validateTechnologyPresence(technology, result.fileTechnologies(), trajectoryType, trajectoryToUse);
            }
            validateInvalidCombos(result.invalidCombos(), trajectoryToUse);

            return result;
        }
    }

    private ResRowProcessingResult processRows(
            Sheet sheet,
            ResRowProcessingContext context,
            boolean isOffshore,
            String[] requiredColumns,
            TrajectoryType trajectoryType
    ) {
        boolean allRowsEmpty = true;
        ResRowProcessingResult result;
        if (trajectoryType == TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION) {
            result = new ResRowProcessingTechnologyDistributionResult(
                    new ArrayList<>(),
                    new StringBuilder(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new HashSet<>()
            );
        } else {
            result = new ResRowProcessingCapacityResult(
                    new ArrayList<>(),
                    new StringBuilder(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new HashSet<>()
            );
        }
        
        Iterator<Row> rows = sheet.rowIterator();
        rows.next(); // skip header

        while (rows.hasNext()) {
            Row row = rows.next();

            if (!ExcelCommonValidator.isRowEmpty(row)) {
                allRowsEmpty = false;
                if (trajectoryType == TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION) {
                    processResDistributionCapacityRow(context, (ResRowProcessingTechnologyDistributionResult) result, row, requiredColumns);
                } else {
                    processResIPCapacityRow(context, (ResRowProcessingCapacityResult) result, row, isOffshore, requiredColumns);   
                }
            }
        }

        validateEmptyRows(allRowsEmpty, trajectoryType);

        return result;
    }

    private void processResIPCapacityRow(
            ResRowProcessingContext context,
            ResRowProcessingCapacityResult result,
            Row row,
            boolean isOffshoreTechnology,
            String[] requiredColumns
    ) {
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        Boolean toUse = ExcelCommonValidator.getBooleanCellValue(row.getCell(0)).orElse(null);
        if (Boolean.FALSE.equals(toUse)) return;

        String area = getStringCell(row, 1);
        // Lecture des colonnes principales
        String col2 = getStringCell(row, 2);
        String col3 = getStringCell(row, 3);
        String col4 = getStringCell(row, 4);
        String group = isOffshoreTechnology ? col3 : col2;
        String cluster = isOffshoreTechnology ? col4 : col3;

        if (!shouldProcessArea(context, result, area, group)) return;

        validateEmptyRequiredColumns(context, requiredColumns, toUse, area, col2, col3, col4);

        String combo = "%s/%s/%s".formatted(area, group, cluster);

        Number numericValue = parseNumericValue(row, context.getYearColIndex(), combo, result);
        if (numericValue == null) return;

        BigDecimal capacityByYear = BigDecimal.valueOf(numericValue.doubleValue());

        ResClusterCapacityEntity entity = ResClusterCapacityEntity.builder()
                .toUse(toUse)
                .area(area)
                .groupe(group)
                .cluster(cluster)
                .capacityByYear(capacityByYear)
                .build();

        if (isOffshoreTechnology) {
            entity.setPecdZone(col2);
        } else {
            entity.setCategory(col4);
        }

        result.addEntity(entity);
        appendChecksum(result, area, group, cluster, isOffshoreTechnology ? col2 : col4, capacityByYear, toUse);
    }

    private void processResDistributionCapacityRow(
            ResRowProcessingContext context,
            ResRowProcessingTechnologyDistributionResult result,
            Row row,
            String[] requiredColumns
    ) {
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // Lecture des colonnes principales
        String group = getStringCell(row, 0);
        String cluster = getStringCell(row, 1);
        String area = getStringCell(row, 2);
        String pecdZone = getStringCell(row, 3);
        String pecdTechno = getStringCell(row, 4);
        
        // Check if pecd_zone starts with default area
        // Check if groupe is equal to technology
        if (context.getAreaParam() != null ) {
            result.addArea(area);
            if (!area.equalsIgnoreCase(context.getAreaParam())) return;
        }
        
        if (context.getTechnology() != null && !context.getTechnology().isBlank() && !context.getTechnology().equalsIgnoreCase(group)) return;
        result.addTechnologies(context.getTechnology());

        validateEmptyRequiredColumns(context, requiredColumns, group, cluster, pecdZone, pecdTechno);

        String combo = "%s/%s/%s".formatted(pecdZone, group, cluster);

        Number numericValue = parseNumericValue(row, context.getYearColIndex(), combo, result);
        if (numericValue == null) return;
        
        int capacityByYear = numericValue.intValue();

        ResTechnologyDistributionEntity entity = ResTechnologyDistributionEntity.builder()
                .area(area)
                .groupe(group)
                .cluster(cluster)
                .pecdZone(pecdZone)
                .pecdTechnology(pecdTechno)
                .capacityByYear(capacityByYear)
                .build();

        result.addEntity(entity);
        appendChecksum(result, group, cluster, pecdZone, pecdTechno, capacityByYear, true);
    }

    private boolean shouldProcessArea(ResRowProcessingContext context, ResRowProcessingResult result, String area, String technology) {

        String areaParam = context.getAreaParam();
        String areaStr = Objects.toString(area, "");

        String technologyParam = context.getTechnology();
        String technologyStr = Objects.toString(technology, "");

        // 1. Filtre par area
        if (areaParam != null) {
            result.addArea(area);

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
        
        // 2. Filtre par technology
        if (technologyParam != null && !technologyParam.isBlank() && !technologyParam.equalsIgnoreCase(technologyStr)) {
            return false;
        }
        
        result.addTechnologies(technologyParam);
        return true;
    }

    private void validateEmptyRequiredColumns(
            ResRowProcessingContext context,
            String[] requiredColumns,
            Object... values
    ) {
        List<String> missing = new ArrayList<>();

        for (int i = 0; i < requiredColumns.length; i++) {
            if (values[i] == null) {
                missing.add(requiredColumns[i]);
            }
        }

        if (!missing.isEmpty()) {
            throw BusinessException.builder()
                    .message(String.join(", ", missing)
                            + " values can't be empty in Res trajectory "
                            + context.getTrajectoryToUse())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private Number parseNumericValue(
            Row row,
            int yearColIndex,
            String combo,
            ResRowProcessingResult result
    ) {
        Object cellVal = getCellValue(row, yearColIndex);

        if (cellVal instanceof Number num) {
            return num;
        }

        if (cellVal instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                result.invalidCombos().add(combo);
            }
        }

        result.invalidCombos().add(combo);
        return null;
    }

    private void appendChecksum(
            ResRowProcessingResult result,
            String area,
            String group,
            String cluster,
            String categoryOrZone,
            Number capacityByYear,
            Boolean toUse
    ) {
        result.checksum()
                .append(area).append("|")
                .append(group).append("|")
                .append(cluster).append("|")
                .append(categoryOrZone).append("|")
                .append(capacityByYear).append("|")
                .append(toUse).append("\n");
    }
    
    private Optional<TrajectoryEntity> findExistingTrajectory(Path path, String horizon, String area, TrajectoryType trajectoryType, String technology) {
        return trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name()),
                trajectoryType.name(),
                horizon,
                area,
                technology);
    }

    private TrajectoryEntity buildResTrajectory(String horizon, String areaParam, String technology, Path filePath, TrajectoryType trajectoryType, ResRowProcessingResult result) throws IOException {
        String checksum = calculateChecksum(result.checksum().toString());
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(filePath, horizon, areaParam, trajectoryType, technology);
        TrajectoryEntity trajectory = buildInstalledResTrajectory(filePath, horizon, areaParam, technology, trajectoryType);

        if (existingTrajectory.isPresent() && existingTrajectory.get().getChecksum() != null) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                // use Utils since method moved
                throwAlreadyProcessedFileException(filePath);
            } else {
                trajectory.setChecksum(checksum);
                trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else if (existingTrajectory.isEmpty()) {
            trajectory.setChecksum(checksum);
            trajectory.setVersion(1);
        }

        switch (result) {
            case ResRowProcessingCapacityResult cap -> {
                cap.entities().forEach(e -> e.setTrajectory(trajectory));
                trajectory.setResClusterCapacityEntities(cap.entities());
            }

            case ResRowProcessingTechnologyDistributionResult dist -> {
                dist.entities().forEach(e -> e.setTrajectory(trajectory));
                trajectory.setResTechnologyDistributionCapacityEntities(dist.entities());
            }
        }

        return trajectory;
    }

    private TrajectoryEntity buildInstalledResTrajectory(Path trajectoryFilePath, String horizon, String area, String technology, TrajectoryType trajectoryType) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        return buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, trajectoryType, area, technology, null);
    }

    private List<String> loadStudyAreas(Integer studyId) {
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }

    public TrajectoryEntity saveTrajectory(
            String horizon,
            String areaParam,
            String technology,
            Path filePath,
            ResRowProcessingResult result,
            TrajectoryType trajectoryType
    ) throws IOException {
        TrajectoryEntity trajectory = buildResTrajectory(
                horizon,
                areaParam,
                technology,
                filePath,
                trajectoryType,
                result
        );

        switch (result) {
            case ResRowProcessingCapacityResult cap ->
                    trajectory.setResClusterCapacityEntities(cap.entities());

            case ResRowProcessingTechnologyDistributionResult dist ->
                    trajectory.setResTechnologyDistributionCapacityEntities(dist.entities());
        }
        
        return trajectoryRepository.save(trajectory);
    }

    private ResRowProcessingResult merge(ResRowProcessingResult left, ResRowProcessingResult right) {
        return switch (left) {
            case ResRowProcessingCapacityResult(
                    var entitiesLeft,
                    var checksumLeft,
                    var fileAreasLeft,
                    var fileTechLeft,
                    var invalidCombosLeft) -> switch (right) {
                case ResRowProcessingCapacityResult(
                        var entitiesRight,
                        var checksumRight,
                        var fileAreasRight,
                        var fileTechRight,
                        var invalidCombosRight
                ) -> {
                    entitiesLeft.addAll(entitiesRight);
                    checksumLeft.append(checksumRight);
                    fileAreasLeft.addAll(fileAreasRight);
                    fileTechLeft.addAll(fileTechRight);
                    invalidCombosLeft.addAll(invalidCombosRight);
                     yield left;
                }
                case ResRowProcessingTechnologyDistributionResult ignored ->  throw new IllegalArgumentException("Cannot merge different result types");
            };

            default -> right;
        };
    }
}