package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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

import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.*;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResFileProcessorServiceImpl implements ResFileProcessorService {
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;

    private final ResTypeService resTypeService;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final TrajectoryServiceImpl trajectoryService;
    private final PathSecurityUtil pathSecurityUtil;
    private final ResCoherenceCheckService resCoherenceCheckService;

    protected static final String GROUP_COLUMN = "Group";
    protected static final String CLUSTER_COLUMN = "Cluster";
    protected static final String AREA_COLUMN = "Area";
    protected static final String PECD_ZONE_COLUMN = "PECD_Zone";
    protected static final String TO_USE = "ToUse";
    protected static final String[] REQUIRED_CLUSTER_COLUMNS = {
            TO_USE, AREA_COLUMN, GROUP_COLUMN, CLUSTER_COLUMN, "Category"};
    protected static final String[] REQUIRED_OFFSHORE_CLUSTER_COLUMNS = {
            TO_USE, AREA_COLUMN, PECD_ZONE_COLUMN, GROUP_COLUMN, CLUSTER_COLUMN};
    protected static final String[] REQUIRED_TECHNOLOGY_DISTRIBUTION_COLUMNS = {
            GROUP_COLUMN, CLUSTER_COLUMN, AREA_COLUMN, PECD_ZONE_COLUMN, "Techno_PECD"};
    protected static final String[] REQUIRED_ZONAL_DISTRIBUTION_COLUMNS = {
            AREA_COLUMN, "PECD_zone", GROUP_COLUMN};
    protected static final String OFFSHORE = "offshore";
    protected static final String FILE_FORMAT = ".xlsx";
    protected static final String FILE_NOT_FOUND = "File not found: ";
    protected static final String LITERAL_STRING = "%s/%s/%s";

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
        boolean isFR = "FR".equalsIgnoreCase(areaParam);
        List<Path> files = resolveFiles(isFR, trajectoryToUse, areaParam, technologyParam);
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
                .orElse(null);
          Path referencePath = isFR ? files.get(0).getParent() : files.get(0);

           validateNoDuplicateCapacityRows(aggregated, trajectoryToUse);

           // Construire la trajectoire complète AVANT la validation
           TrajectoryEntity trajectory = buildCompleteTrajectory(horizon, areaParam, technology, referencePath, TrajectoryType.RES_CAPACITY, aggregated);

           // Valider la cohérence IP/TD AVANT le save (inclut la trajectoire en cours d'import)
           resCoherenceCheckService.validateIPTDCoherence(studyId, trajectory);

           // Valider la cohérence IP/LF (Scénario 13) AVANT le save (contrôle des fichiers dans le NAS)
           resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, trajectory);

           // Sauvegarder uniquement si validation OK
           return trajectoryRepository.save(trajectory);
     }

    @Override
    public TrajectoryEntity processLoadFactorResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology) throws IOException {
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        String loadDirectory = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, area, null);

        validatePathFromTrajectoryRoot(loadDirectory, trajectoryToUse);

        Path trajectoryFolder = basePath
                .resolve(loadDirectory)
                .resolve(trajectoryToUse)
                .normalize();

        // Validate and get the trajectory file path
        Path trajectoryFilePath = validateAndGetTrajectoryFilePath(trajectoryFolder, trajectoryToUse, technology, loadDirectory);

        validateNoMalformedZonalFiles(trajectoryFilePath, trajectoryToUse);

        // Build the trajectory entity
        TrajectoryEntity trajectory = buildLoadFactorMiscTrajectory(trajectoryToUse, trajectoryFilePath, horizon, area, technology);

        // Validate coherence
        resCoherenceCheckService.validateIPLoadFactorCoherence(studyId, trajectory);
        resCoherenceCheckService.validateLFDTCoherence(studyId, trajectory);

        return trajectoryRepository.save(trajectory);
    }

    private void validateNoMalformedZonalFiles(Path trajectoryFolder, String trajectoryToUse) throws IOException {
        int maxDepth = 3;

        try (var pathStream = Files.find(trajectoryFolder, maxDepth,
                ResDomainRules::isBaselineTrajectoryFile)) {

            Optional<Path> invalidFile = pathStream
                    .filter(p -> ResDomainRules.containsMalformedZonalToken(p.getFileName().toString()))
                    .findFirst();

            if (invalidFile.isPresent()) {
                String badFileName = invalidFile.get().getFileName().toString();
                String zonalAreasStr = String.join(", ", ResDomainRules.ZONAL_AREAS);
                String exampleZone = ResDomainRules.ZONAL_AREAS.iterator().next() + "01";

                throw BusinessException.builder()
                        .message("Invalid file detected in trajectory {0}. Zonal areas ({1}) must include a PECD zone (e.g. {2}). File found: {3}")
                        .errorMessageArguments(List.of(
                                trajectoryToUse,
                                zonalAreasStr,
                                exampleZone,
                                badFileName
                        ))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    /**
     * Validates trajectory path and returns the file path to use.
     * If technology is specified, returns the technology folder.
     * If technology is not specified, validates all 4 required technologies exist.
     */
    private Path validateAndGetTrajectoryFilePath(Path trajectoryFolder, String trajectoryToUse, String technology, String loadDirectory) throws IOException {
        if (technology != null && !technology.isBlank()) {
            validatePathFromTrajectoryRoot(loadDirectory, trajectoryToUse, technology);
            Path technologyFolder = resolveTechnologyFolder(trajectoryFolder, technology);
            
            // Validate that at least one subdirectory contains CSV files
            validateSubdirectoryHasCsvFiles(technologyFolder, technology);
            
            return technologyFolder;
        } else {
            // Validate all 4 required technologies exist
            checkAllRequiredTechnologiesExist(trajectoryFolder, trajectoryToUse);
            return trajectoryFolder;
        }
    }

    /**
     * Validates that at least one subdirectory under the given path contains CSV files.
     */
    private void validateSubdirectoryHasCsvFiles(Path technologyFolder, String technology) throws IOException {
        List<Path> subFolders = getAllSubdirectories(technologyFolder);
        
        boolean hasValidSubFolder = subFolders.stream()
                .anyMatch(subFolder -> {
                    try {
                        return containsCsvFile(subFolder);
                    } catch (IOException e) {
                        return false;
                    }
                });
        
        if (!hasValidSubFolder) {
            throw BusinessException.builder()
                    .message("No subdirectory with CSV files found for technology: " + technology)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validatePathFromTrajectoryRoot(String... pathSegments) {
        String relativePath = String.join("/", pathSegments);
        try {
            pathSecurityUtil.validatePathFromBaseDir(relativePath, AntaresDataManagerProperties::getTrajectoryFilePath);
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Invalid trajectory path: {0}")
                    .errorMessageArguments(List.of(relativePath))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
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

          // Construire la trajectoire complète AVANT la validation
          TrajectoryEntity trajectory = buildCompleteTrajectory(horizon, areaParam, technology, filePath, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, result);
          
          // Valider la cohérence IP/TD AVANT le save (inclut la trajectoire en cours d'import)
          resCoherenceCheckService.validateIPTDCoherence(studyId, trajectory);
          resCoherenceCheckService.validateLFDTCoherence(studyId, trajectory);
        resCoherenceCheckService.validateDTDZCoherence(studyId, trajectory);
          
          // Sauvegarder uniquement si validation OK
          return trajectoryRepository.save(trajectory);
     }

     @Transactional
     @Override
     public TrajectoryEntity processZonalDistributionResFile(
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
                 TrajectoryType.RES_ZONAL_DISTRIBUTION,
                 areaParam,
                 null
         );

         validatePrefixIfNeeded(areaParam, trajectoryToUse, TrajectoryType.RES_ZONAL_DISTRIBUTION, RES_ZONAL_DISTRIBUTION_PREFIX);

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
                     TrajectoryType.RES_ZONAL_DISTRIBUTION
             );
         } catch (IOException e) {
             throw BusinessException.builder()
                     .message("Could not import RES zonal distribution trajectory")
                     .httpStatus(HttpStatus.BAD_REQUEST)
                     .build();
         }

         // Construire la trajectoire complète AVANT la validation
         TrajectoryEntity trajectory = buildCompleteTrajectory(horizon, areaParam, technology, filePath, TrajectoryType.RES_ZONAL_DISTRIBUTION, result);
         
         // Valider la cohérence entre Distribution Technology et Distribution Zonal (clé: area/group/zone PECD)
         resCoherenceCheckService.validateDTDZCoherence(studyId, trajectory);
         
         // Sauvegarder uniquement si validation OK
         return trajectoryRepository.save(trajectory);
     }


    /**
     * Vérifie si trajectoryFolder contient les 4 répertoires technologies requises.
     * Pour chaque technologie, récupère tous les sous-répertoires et vérifie 
     * qu'au moins un contient des fichiers CSV.
     */
    public void checkAllRequiredTechnologiesExist(Path trajectoryFolder, String trajectoryToUse) throws IOException {
        // Get the list of 4 required technologies
        List<String> requiredTechnologies = resTypeService.getAllResTypes()
                .stream()
                .map(ResTypeEntity::getCode)
                .toList();

        List<String> missingTechnologies = new ArrayList<>();

        if (!Files.exists(trajectoryFolder)) {
            throw BusinessException.builder()
                    .message("Trajectory folder not found for load factor res trajectory: " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check each required technology folder exists
        for (String technology : requiredTechnologies) {
            try {
                // Get the actual technology folder path (this validates that it exists)
                Path techFolder = resolveTechnologyFolder(trajectoryFolder, technology);
                
                // Get all subdirectories under the technology folder
                List<Path> subFolders = getAllSubdirectories(techFolder);
                
                // Check if at least one subdirectory contains CSV files
                boolean hasValidSubFolder = subFolders.stream()
                        .anyMatch(subFolder -> {
                            try {
                                return containsCsvFile(subFolder);
                            } catch (IOException e) {
                                return false;
                            }
                        });
                
                if (!hasValidSubFolder) {
                    missingTechnologies.add(technology);
                }
            } catch (BusinessException e) {
                // Technology folder not found
                missingTechnologies.add(technology);
            }
        }

        if (!missingTechnologies.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing required technologies for load factor misc trajectory '" + trajectoryToUse
                            + "'. The following technologies are required with at least one subdirectory containing CSV files: "
                            + String.join(", ", missingTechnologies))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Returns all subdirectories under the given path (depth 1).
     */
    private static List<Path> getAllSubdirectories(Path parentPath) throws IOException {
        try (var walk = Files.walk(parentPath, 1)) {
            return walk
                    .filter(Files::isDirectory)
                    .filter(p -> !p.equals(parentPath))
                    .toList();
        }
    }

    private TrajectoryEntity buildLoadFactorMiscTrajectory(String trajectoryToUse, Path trajectoryFilePath, String horizon, String area, String technology) throws IOException {
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

    /**
     * Resolves the technology folder path under trajectoryFolder.
     * Returns trajectoryFolder/technology
     */
    private static Path resolveTechnologyFolder(Path trajectoryFolder, String technology) {
        Objects.requireNonNull(trajectoryFolder, "trajectoryFolder must not be null");
        Objects.requireNonNull(technology, "technology must not be null");
        
        Path techFolder = trajectoryFolder.resolve(technology).normalize();
        
        if (!Files.exists(techFolder)) {
            throw BusinessException.builder()
                    .message("No technology folder found for: " + technology)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        return techFolder;
    }
    // ...existing code...

    private static boolean containsCsvFile(Path directory) throws IOException {
        try (var filesStream = Files.walk(directory)) {
            return filesStream
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"));
        }
    }

    private List<Path> resolveFiles(boolean isFR, String trajectoryToUse, String areaParam, String technology) throws IOException {
        Path directoryPath = trajectoryService.normalizeAndValidateDirectory(
                TrajectoryType.RES_CAPACITY,
                isFR ? "FR" : areaParam,
                null
        );

        if (isFR) {
            Path folderPath = directoryPath.resolve(trajectoryToUse).normalize();

            if (!folderPath.startsWith(directoryPath)) {
                throw BusinessException.builder()
                        .message("Invalid trajectory path: " + trajectoryToUse)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

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
                requiredColumns = REQUIRED_TECHNOLOGY_DISTRIBUTION_COLUMNS;
            }
            if (trajectoryType == TrajectoryType.RES_ZONAL_DISTRIBUTION) {
                requiredColumns = REQUIRED_ZONAL_DISTRIBUTION_COLUMNS;
            }
            Sheet sheet = getSheetOrThrow(workbook, filePath, indexSheet);
            Row header = getHeaderOrThrow(sheet, filePath, trajectoryType);

            validateHeaderColumns(header, sheet, requiredColumns, trajectoryToUse, trajectoryType);

            int yearColIndex = resolveYearColumnIndex(header, horizon, trajectoryType, trajectoryToUse, requiredColumns.length, isCivilYear);

            ResRowProcessingContext context = ResRowProcessingContext.builder().studyAreas(studyAreas).areaParam(areaParam).yearColIndex(yearColIndex).trajectoryToUse(trajectoryToUse).technology(technology).trajectoryType(trajectoryType).build();

            ResRowProcessingResult result = processRows(sheet, context, isOffshoreTechnology, requiredColumns, trajectoryType);

            validateAreas(studyAreas, areaParam, result.fileAreas(), trajectoryToUse, trajectoryType);
            if (trajectoryType != TrajectoryType.RES_ZONAL_DISTRIBUTION) {
                validateTechnologyPresence(technology, result.fileTechnologies(), trajectoryType, trajectoryToUse, areaParam);
            }
            validateInvalidCombos(result.invalidCombos(), trajectoryToUse, trajectoryType);

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
        ResRowProcessingResult result = getResRowProcessingResult(trajectoryType);

        Iterator<Row> rows = sheet.rowIterator();
        rows.next(); // skip header

        while (rows.hasNext()) {
            Row row = rows.next();

            if (!ExcelCommonValidator.isRowEmpty(row)) {
                allRowsEmpty = false;
                switch (trajectoryType) {
                    case TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION -> {
                            List<String> technologies = List.of();
                            if (context.getTechnology() == null || context.getTechnology().isBlank()) {
                                var resTypeEntities = resTypeService.getAllResTypes();
                                technologies = resTypeEntities.stream().map(ResTypeEntity::getCode).toList();
                            }
                            processResTechnoDistributionCapacityRow(context, (ResRowProcessingTechnologyDistributionResult) result, row, requiredColumns, technologies);
                    }
                    case TrajectoryType.RES_ZONAL_DISTRIBUTION ->
                            processResZonalDistributionRow(context, (ResRowProcessingZonalDistributionResult) result, row, requiredColumns);
                    default ->
                            processResIPCapacityRow(context, (ResRowProcessingCapacityResult) result, row, isOffshore, requiredColumns);
                }
            }
        }

        validateEmptyRows(allRowsEmpty, trajectoryType, context.getTrajectoryToUse());

        return result;
    }

    private @NonNull ResRowProcessingResult getResRowProcessingResult(TrajectoryType trajectoryType) {
        ResRowProcessingResult result;
        switch (trajectoryType) {
            case TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION ->
                    result = new ResRowProcessingTechnologyDistributionResult(new ArrayList<>(),
                            new StringBuilder(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new HashSet<>());
            case TrajectoryType.RES_ZONAL_DISTRIBUTION ->
                    result = new ResRowProcessingZonalDistributionResult(
                            new ArrayList<>(),
                            new StringBuilder(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new HashSet<>()
                    );

            default ->
                    result = new ResRowProcessingCapacityResult(
                            new ArrayList<>(),
                            new StringBuilder(),
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new HashSet<>()
                    );
        }
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

        // Lecture des colonnes
        String col2 = getStringCell(row, 2);
        String col3 = getStringCell(row, 3);
        String col4 = getStringCell(row, 4);
        String group = isOffshoreTechnology ? col3 : col2;
        String cluster = isOffshoreTechnology ? col4 : col3;

        if (!shouldProcessArea(context, result, area, group)) return;

        Object[] values = isOffshoreTechnology
                ? new Object[] { toUse, area, col2, group, cluster }
                : new Object[] { toUse, area, group, cluster, col4 };

        validateEmptyRequiredColumns(context, requiredColumns, values);

        String combo = LITERAL_STRING.formatted(area, group, cluster);

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

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(group).append("|")
                .append(cluster).append("|")
                .append(capacityByYear).append("|");
    }

    /**
     * Onshore RES groups (solar_pv, wind_onshore, solar_thermo) have no zone breakdown,
     * so a row is uniquely identified by area/group/cluster. wind_offshore is split by
     * PECD zone instead, so the zone replaces the area in the uniqueness key.
     */
    private void validateNoDuplicateCapacityRows(ResRowProcessingResult result, String trajectoryToUse) {
        switch (result) {
            case ResRowProcessingCapacityResult cap -> {
                Set<String> seenCombos = new HashSet<>();
                for (ResClusterCapacityEntity entity : cap.entities()) {
                    checkNotDuplicate(entity, seenCombos, trajectoryToUse);
                }
            }
            default -> { }
        }
    }

    private void checkNotDuplicate(ResClusterCapacityEntity entity, Set<String> seenCombos, String trajectoryToUse) {
        boolean isOffshore = ResGroupEnum.WIND_OFFSHORE.value().equals(ResGroupEnum.normalizeForGenerator(entity.getGroupe()));
        String fieldName = isOffshore ? "PECD zone" : "area";
        String fieldValue = isOffshore ? entity.getPecdZone() : entity.getArea();

        String key = buildComboKey(fieldValue, entity.getGroupe(), entity.getCluster());
        if (!seenCombos.add(key)) {
            throw BusinessException.builder()
                    .message("Duplicate row detected in RES capacity trajectory {0} for {1} {2}, group {3}, cluster {4}."
                            + " Only one row is allowed per {1}/group/cluster combination for the selected horizon.")
                    .errorMessageArguments(List.of(trajectoryToUse, fieldName, fieldValue, entity.getGroupe(), entity.getCluster()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private String buildComboKey(String first, String second, String third) {
        return Objects.toString(first, "").trim().toUpperCase(Locale.ROOT) + "|"
                + Objects.toString(second, "").trim().toUpperCase(Locale.ROOT) + "|"
                + Objects.toString(third, "").trim().toUpperCase(Locale.ROOT);
    }

    private void processResTechnoDistributionCapacityRow(
            ResRowProcessingContext context,
            ResRowProcessingTechnologyDistributionResult result,
            Row row,
            String[] requiredColumns,
            List<String> technologies
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
        if (context.getAreaParam() != null) {
            result.addArea(area);
            if ( area == null || !area.equalsIgnoreCase(context.getAreaParam())) return;
        }
        
        boolean hasTechnology = context.getTechnology() != null && !context.getTechnology().isBlank();
        if (!hasTechnology) {
            if (!technologies.contains(group)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(group, context.getAreaParam(), context.getTrajectoryToUse()))
                        .message("Wrong group {0} in the 'node' column of {1} trajectory {2}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        } else if (!context.getTechnology().equalsIgnoreCase(group)) {
            return;
        }
        result.addTechnologies(context.getTechnology());

        validateEmptyRequiredColumns(context, requiredColumns, group, cluster, area, pecdZone, pecdTechno);

        String combo = LITERAL_STRING.formatted(pecdZone, group, cluster);

        Double capacityByYear = parseDoubleValue(row, context.getYearColIndex(), combo, result);

        if (capacityByYear == null) return;

        ResTechnologyDistributionEntity entity = ResTechnologyDistributionEntity.builder()
                .area(area)
                .groupe(group)
                .cluster(cluster)
                .pecdZone(pecdZone)
                .pecdTechnology(pecdTechno)
                .capacityByYear(capacityByYear)
                .build();

        result.addEntity(entity);

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(group).append("|")
                .append(cluster).append("|")
                .append(pecdZone).append("|")
                .append(pecdTechno).append("|")
                .append(capacityByYear).append("|");
    }

    private void processResZonalDistributionRow(
            ResRowProcessingContext context,
            ResRowProcessingZonalDistributionResult result,
            Row row,
            String[] requiredColumns
    ) {
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // Lecture des colonnes principales
        String area = getStringCell(row, 0);
        String pecdZone = getStringCell(row, 1);
        String group = getStringCell(row, 2);

        // Check if pecd_zone starts with default area
        // Check if groupe is equal to technology
        if (context.getAreaParam() != null && area != null) {
            result.addArea(area);
            if (!area.equalsIgnoreCase(context.getAreaParam())) return;
        }

        validateEmptyRequiredColumns(context, requiredColumns, area, pecdZone, group);

        String combo = LITERAL_STRING.formatted(area, pecdZone, group);

        BigDecimal numericValue = parseStringPercentValue(row, context.getYearColIndex(), combo, result);
        if (numericValue == null) return;

        ResZonalDistributionEntity entity = ResZonalDistributionEntity.builder()
                .area(area)
                .pecdZone(pecdZone)
                .groupe(group)
                .capacityByYear(numericValue)
                .build();

        result.addEntity(entity);

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(group).append("|")
                .append(pecdZone).append("|")
                .append(numericValue).append("|");
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

    private Double parseDoubleValue(
            Row row,
            int yearColIndex,
            String combo,
            ResRowProcessingResult result
    ) {
        Object cellVal = getCellValue(row, yearColIndex);

        BigDecimal bd = null;

        if (cellVal instanceof Number num) {
            bd = BigDecimal.valueOf(num.doubleValue());
        } else if (cellVal instanceof String str) {
            try {
                bd = new BigDecimal(str);
            } catch (NumberFormatException ignored) {
                result.invalidCombos().add(combo);
                return null;
            }
        } else {
            result.invalidCombos().add(combo);
            return null;
        }

        return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
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

    private BigDecimal parseStringPercentValue(
            Row row,
            int yearColIndex,
            String combo,
            ResRowProcessingResult result
    ) {
        Object cellVal = getCellValue(row, yearColIndex);

        if (cellVal instanceof Number) {
            try {
                return new BigDecimal(cellVal.toString());
            } catch (NumberFormatException ignored) {
                result.invalidCombos().add(combo);
            }
        }

        if (cellVal instanceof String str) {
            try {
                return parsePercentage(str);
            } catch (NumberFormatException ignored) {
                result.invalidCombos().add(combo);
            }
        }

        result.invalidCombos().add(combo);
        return null;
    }

    public static BigDecimal parsePercentage(String raw) {
        if (raw == null) return null;

        String cleaned = raw.trim()
                .replace("%", "")
                .replace(",", ".");

        if (cleaned.isEmpty()) return null;

        BigDecimal value = new BigDecimal(cleaned);

        if (raw.contains("%")) {
            value = value.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        }

        // Arrondi final à 2 décimales
        return value.setScale(2, RoundingMode.HALF_UP);
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
        String checksum = calculateChecksum(result.getChecksumBuilder().toString());
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

            case ResRowProcessingZonalDistributionResult zonal -> {
                zonal.entities().forEach(e -> e.setTrajectory(trajectory));
                trajectory.setResZonalDistributionCapacityEntities(zonal.entities());
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
        TrajectoryEntity trajectory = buildCompleteTrajectory(
                horizon,
                areaParam,
                technology,
                filePath,
                trajectoryType,
                result
        );
        
        return trajectoryRepository.save(trajectory);
    }

    /**
     * Construit une trajectoire complète avec tous ses éléments.
     */
    private TrajectoryEntity buildCompleteTrajectory(
            String horizon,
            String areaParam,
            String technology,
            Path filePath,
            TrajectoryType trajectoryType,
            ResRowProcessingResult result
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

            case ResRowProcessingZonalDistributionResult zonal ->
                    trajectory.setResZonalDistributionCapacityEntities(zonal.entities());
        }

        return trajectory;
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
                default -> throw new IllegalStateException("Unexpected value: " + right);
            };

            default -> right;
        };
    }
}