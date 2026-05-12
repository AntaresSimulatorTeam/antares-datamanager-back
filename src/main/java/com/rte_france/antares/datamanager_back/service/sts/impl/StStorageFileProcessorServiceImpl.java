package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import com.rte_france.antares.datamanager_back.service.sts.StStorageFileProcessorService;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageFileProcessorServiceImpl implements StStorageFileProcessorService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;
    private final StStorageConstraintsFileProcessorService stStorageConstraintsFileProcessorService;


    private static final Integer SERIES_INDEX = 11;
    private static final Integer CONSTRAINTS_INDEX = 12;
    private static final String ELECTRICAL_VEHICLE = "EV";
    private static final String SERIES_FILES = "series";
    private static final String CONSTRAINTS_FILES = "constraints";
    private static final String EXCEL_EXTENSION = ".xlsx";
    private static final String STS_TRAJECTORY_PREFIX = "cluster_";
    public static final String OTHERS_AREA = "OTHERS";


    @Transactional
    @Override
    public TrajectoryEntity processStStorageFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String areaParam, String technology) throws IOException {
        final String stsTrajectoryPrefix = STS_TRAJECTORY_PREFIX+ technology.toLowerCase() + "_";
        if (!trajectoryToUse.toLowerCase().startsWith(stsTrajectoryPrefix)) {
            throw BusinessException.builder().message(" {0} Trajectory name must start with : {1} ").errorMessageArguments(List.of(trajectoryToUse, stsTrajectoryPrefix)).build();
        }

        Path trajectoryFilePath = findTrajectoryFileCaseInsensitive(trajectoryToUse, technology);

        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream().map(a -> a.getName().toUpperCase()).toList();

        List<StStorageEntity> stStorageEntityList = buildStStorageLines(horizon.split("-")[1], trajectoryFilePath, areaParam, technology, studyAreas, studyId);
        if (stStorageEntityList.isEmpty()) {
            throw BusinessException.builder().message("No ST Storage data found in the file for horizon: " + horizon).build();
        }

        boolean isSeriesTrue = stStorageEntityList.stream()
                .anyMatch(entity -> Boolean.TRUE.equals(entity.getSeries()));

        TrajectoryEntity trajectoryEntity = buildStStorageTrajectory(trajectoryFilePath, horizon, areaParam, technology, isSeriesTrue);

        stStorageEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        return trajectoryRepository.save(trajectoryEntity);
    }

    public Path findTrajectoryFileCaseInsensitive(String trajectoryFileName, String technology) throws IOException {
        Path root = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS root not found: " + root);
        }

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("clusters");

        try (Stream<Path> s = Files.list(techDir)) {
            java.util.Optional<Path> file = s.filter(Files::isRegularFile).filter(p -> {
                String fn = p.getFileName().toString();
                return fn.equalsIgnoreCase(trajectoryFileName + EXCEL_EXTENSION) || fn.equalsIgnoreCase(trajectoryFileName + ".xls");
            }).findFirst();

            if (file.isPresent()) {
                return file.get();
            } else {
                throw new NoSuchFileException("Trajectory file not found in " + techDir + " for '" + trajectoryFileName + "'");
            }
        }
    }


    private TrajectoryEntity buildStStorageTrajectory(Path trajectoryFilePath, String horizon, String areaParam, String technology, Boolean hasSeries) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()), TrajectoryType.STS.name(), horizon, areaParam, technology);

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon.split("-")[1], createdBy, TrajectoryType.STS, areaParam, technology, hasSeries);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon.split("-")[1], createdBy, TrajectoryType.STS, areaParam, technology, hasSeries);
        }

        return trajectory;
    }


    private List<StStorageEntity> buildStStorageLines(String horizon, Path trajectoryFilePath, String areaParam, String technology,
                                                      List<String> studyAreas, Integer studyId)throws IOException {
        List<StStorageEntity> results = new ArrayList<>();
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();
        // cache already parsed additional constraints once per file path
        Map<Path, List<StConstraintsParameterEntity>> constraintsParamsCache = new HashMap<>();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryFilePath, TrajectoryType.STS.name());

            String[] expectedColumns = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
            checkMissingColumns(sheet, expectedColumns, trajectoryFileName, TrajectoryType.STS);


            boolean foundStudyArea = false;

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) { // skip header
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                String rowArea = getStringCellValue(row, 0);
                String clusterName = getStringCellValue(row, 1);
                String groupName = getStringCellValue(row, 2);
                // Zone must be present
                if (rowArea == null || rowArea.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, String.valueOf(r)))
                            .message("Area is missing in STS trajectory " + trajectoryFileName + " for row: " + r)
                            .build();
                }

                if (!shouldIncludeRow(rowArea, areaParam) || !studyAreas.contains(rowArea.toUpperCase())) {
                    continue;
                }
                foundStudyArea = true;

                // Cluster name is mandatory
                if (clusterName == null || clusterName.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, rowArea, horizon))
                            .message("No valid cluster name found in the trajectory {0} for area {1} and horizon {2}")
                            .build();
                }

                // Group name is mandatory
                if (groupName == null || groupName.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, rowArea, horizon))
                            .message("No valid cluster group found in the trajectory {0} for area {1} and horizon {2}")
                            .build();
                }


                validateNumericRange(row, 3, 8, trajectoryFileName);
                validateZeroToOneRange(row, trajectoryFileName);
                validateBooleanRange(row, 9, 12, trajectoryFileName);

                StStorageEntity entity = mapRowToEntity(row, trajectoryFilePath, technology, rowArea,
                        clusterName, trajectoryFileName, studyAreas, areaParam, horizon, studyId, constraintsParamsCache);
                results.add(entity);
            }

            // The selected area must be present in the file's 'node' column, except when area equals OTHERS
            validateSelectedAreaPresence(areaParam, results, trajectoryFileName);

            if (!foundStudyArea) {
                throw BusinessException.builder()
                        .message("None of the areas of trajectory AREA are present in STS trajectory " + trajectoryFileName)
                        .build();
            }
        }
        return results;
    }

    private boolean shouldIncludeRow(String rowArea, String areaParam) {
        return rowArea.equalsIgnoreCase(areaParam) || areaParam.equals(OTHERS_AREA);
    }

    private String getStringCellValue(Row row, int idx) {
        Cell cell = row.getCell(idx);
        return cell == null ? null : cell.getStringCellValue();
    }

    private void validateNumericRange(Row row, int fromIdx, int toIdx, String trajectoryFileName) {
        for (int idx = fromIdx; idx <= toIdx; idx++) {
            Cell numericCell = row.getCell(idx);
            if (!isNumericCell(numericCell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName))
                        .message("Values Injection, Withdrawal, Storage, Efficiency_injection, Efficiency_withdrawal and Initial_level must be numeric in STS trajectory {0}")
                        .build();
            }
        }
    }

    private void validateZeroToOneRange(Row row, String trajectoryFileName) {
        int[] indices = {6, 8}; // Efficiency_injection (G), Initial_level (I)
        for (int idx : indices) {
            double value = row.getCell(idx).getNumericCellValue();
            if (value < 0 || value > 1) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName))
                        .message("Values Efficiency_injection and Initial_level must be between 0 and 1 in STS trajectory {0}")
                        .build();
            }
        }
    }

    private void validateBooleanRange(Row row, int fromIdx, int toIdx, String trajectoryFileName) {
        for (int idx = fromIdx; idx <= toIdx; idx++) {
            Cell cell = row.getCell(idx);
            if (!isBooleanCell(cell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName))
                        .message("Values Initial_level_optim, Enabled, Series and Constraints must be boolean in STS trajectory {0}")
                        .build();
            }
        }
    }

    private boolean isBooleanStringValue(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "false".equals(normalized)
                || "1".equals(normalized)
                || "0".equals(normalized);
    }

    private boolean isBooleanCell(Cell cell) {
        if (cell == null) return false;
        CellType t = cell.getCellType();
        if (t == CellType.BOOLEAN) return true;
        if (t == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return Double.compare(value, 0d) == 0 || Double.compare(value, 1d) == 0;
        }
        if (t == CellType.FORMULA) {
            CellType cachedType = cell.getCachedFormulaResultType();
            if (cachedType == CellType.BOOLEAN) return true;
            if (cachedType == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                return Double.compare(value, 0d) == 0 || Double.compare(value, 1d) == 0;
            }
            if (cachedType == CellType.STRING) {
                return isBooleanStringValue(cell.getStringCellValue());
            }
            return false;
        }
        if (t == CellType.STRING) {
            return isBooleanStringValue(cell.getStringCellValue());
        }
        return false;
    }

    private StStorageEntity mapRowToEntity(Row row, Path trajectoryFilePath, String technology, String rowAreaName, String clusterName,
                                           String trajectoryFileName,
                                           List<String> studyAreas,
                                           String areaParam,
                                           String horizon,
                                           Integer studyId,
                                           Map<Path, List<StConstraintsParameterEntity>> constraintsParamsCache) throws IOException {
        StStorageEntity stStorageEntity = new StStorageEntity();

        // Series/TS files handling
        Boolean stsSeriesFilesFlag = getBooleanCell(row, SERIES_INDEX);
        if (stsSeriesFilesFlag) {
            Path stsTsPath = buildStsOptionalFilesPath(trajectoryFilePath, rowAreaName, technology, clusterName, SERIES_FILES);
            List<String> missingFiles = isOptionalStsFileMissing(stsTsPath, SERIES_FILES, null);
            if (!missingFiles.isEmpty()) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(String.join(", ", missingFiles), trajectoryFileName))
                        .message("Can not import : Missing TS files: {0} for trajectory {1}")
                        .build();
            }
            stStorageEntity.setTsPath(stsTsPath.toString());
        }
        Boolean stsConstraintsFlag = getBooleanCell(row, CONSTRAINTS_INDEX);
        if (stsConstraintsFlag) {
            log.info("STS constraints enabled for trajectory={}, area={}, cluster={}", trajectoryFileName, rowAreaName, clusterName);
            handleStsConstraints(
                    trajectoryFilePath,
                    rowAreaName,
                    technology,
                    clusterName,
                    trajectoryFileName,
                    areaParam,
                    studyAreas,
                    stStorageEntity,
                    horizon,
                    studyId,
                    constraintsParamsCache
            );
        }
        stStorageEntity.setArea(rowAreaName);
        stStorageEntity.setName(clusterName);
        stStorageEntity.setGroupe(getStringCellValue(row, 2));
        stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
        stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
        stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
        stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setEfficiencyWithdrawal(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
        stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(8).getNumericCellValue()));
        stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 9));
        stStorageEntity.setEnabled(getBooleanCell(row, 10));
        stStorageEntity.setSeries(stsSeriesFilesFlag);
        stStorageEntity.setConstraintsFlag(stsConstraintsFlag);
        return stStorageEntity;
    }

    private void validateSelectedAreaPresence(String areaParam, List<StStorageEntity> results, String trajectoryFileName) {
        Set<String> fileAreas = results.stream()
                .map(StStorageEntity::getArea)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        if (!areaParam.isBlank() && !OTHERS_AREA.equals(areaParam) && !fileAreas.contains(areaParam.toUpperCase())) {
            throw BusinessException.builder()
                    .message("Selected area {0} is not present in the 'node' column of STS trajectory {1}")
                    .errorMessageArguments(List.of(areaParam, trajectoryFileName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    public Path buildStsOptionalFilesPath(Path trajectoryFilePath, String areaParam, String technology, String clusterName, String optionalFilesType) throws IOException {


        Path root = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsDirectory());
        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve(optionalFilesType);
        if(SERIES_FILES.equals(optionalFilesType)) {
            // \\\'STS\\<tech>\\series\\<trajectoire>\\<nom du cluster>\\<area>\\*
            Path trajectoryDir = findChildDirectoryIgnoreCase(techDir, getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()));
            Path clusterSeriesDir = findChildDirectoryIgnoreCase(trajectoryDir, clusterName);
            return clusterSeriesDir.resolve(areaParam).normalize();
        }

        else if(CONSTRAINTS_FILES.equals(optionalFilesType)) {
            // \\\'STS\\<tech>\\constraints\\
            return techDir;
        }

        throw new IllegalArgumentException("Invalid optionalFilesType: " + optionalFilesType);
    }

    private static List<String> isOptionalStsFileMissing(Path pathForFilesSts, String optionalFilesType, String trajectoryFileName) {
        if (SERIES_FILES.equals(optionalFilesType)) {
            String[] requiredFiles = StsTsFile.allFileNames();
            if (!Files.exists(pathForFilesSts) || !Files.isDirectory(pathForFilesSts)) {
                log.warn("ST Storage series directory not found: {}", pathForFilesSts);
                return Arrays.asList(requiredFiles);
            }

            File[] files = pathForFilesSts.toFile().listFiles();
            if (files == null || files.length == 0) {
                log.warn("Unable to list files in ST Storage series directory: {}", pathForFilesSts);
                return Arrays.asList(requiredFiles);
            }

            return areRequiredFilesMissing(files, requiredFiles);
        }

        if (CONSTRAINTS_FILES.equals(optionalFilesType)) {
            String constraintsCapacityFile = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFileName, TrajectoryType.STS.name()) + EXCEL_EXTENSION;
            String[] requiredFiles = {StsTsFile.ADDITIONAL_CONSTRAINTS.fileName(), constraintsCapacityFile};
            if (!Files.exists(pathForFilesSts) || !Files.isDirectory(pathForFilesSts)) {
                log.warn("ST Storage constraints directory not found: {}", pathForFilesSts);
                return Arrays.asList(requiredFiles);
            }

            File[] files = pathForFilesSts.toFile().listFiles();
            if (files == null || files.length == 0) {
                log.warn("Unable to list files in ST Storage constraints directory: {}", pathForFilesSts);
                return Arrays.asList(requiredFiles);
            }
            return areRequiredFilesMissing(files, requiredFiles);
        }

        return Collections.emptyList();
    }

    private static List<String> areRequiredFilesMissing(File[] files, String[] requiredFiles) {
        List<String> missingFiles = new ArrayList<>();
        for (String req : requiredFiles) {
            boolean found = Arrays.stream(files)
                    .anyMatch(f -> f.getName().equalsIgnoreCase(req));
            if (!found) {
                missingFiles.add(req);
            }
        }
        return missingFiles;
    }
    private boolean isRowEmpty(Row row) {
        for (int c = 0; c <= 12; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private Boolean getBooleanCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return false;
        if (cell.getCellType() == CellType.BOOLEAN || cell.getCellType() == CellType.FORMULA) return cell.getBooleanCellValue();
        String s = cell.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return false;
        return "true".equals(s) || "1".equals(s);
    }



    public void handleStsConstraints(Path trajectoryFilePath,
                                     String rowAreaName,
                                     String technology,
                                     String clusterName,
                                     String trajectoryFileName,
                                     String areaParam,
                                     List<String> studyAreas,
                                     StStorageEntity storage,
                                     String horizon,
                                     Integer studyId,
                                     Map<Path, List<StConstraintsParameterEntity>> constraintsParamsCache) throws IOException {


        List<String> existingAreas = trajectoryRepository.findAreasByStudyIdAndType(studyId, "STS");


        Path constraintsPath = buildStsOptionalFilesPath(
                trajectoryFilePath,
                rowAreaName,
                technology,
                clusterName,
                CONSTRAINTS_FILES
        );

        List<String> missingFiles = isOptionalStsFileMissing(
                constraintsPath,
                CONSTRAINTS_FILES,
                trajectoryFileName
        );

        if (!missingFiles.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing Additional constraint files {0} for trajectory {1} for {2}")
                    .errorMessageArguments(List.of(
                            String.join(", ", missingFiles),
                            trajectoryFileName,
                            rowAreaName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path additionalConstraintsPath = constraintsPath.resolve(
                StsTsFile.ADDITIONAL_CONSTRAINTS.fileName()
        );
        log.info("Reading STS additional constraints file={} for trajectory={}, area={}, cluster={}",
                additionalConstraintsPath,
                trajectoryFileName,
                rowAreaName,
                clusterName);

        List<StConstraintsParameterEntity> parsedTemplateParams;
        try {
            parsedTemplateParams = constraintsParamsCache.computeIfAbsent(additionalConstraintsPath, path -> {
                try {
                    Integer trajectoryId = storage.getTrajectory() != null ? storage.getTrajectory().getId() : null;
                    return stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(path, rowAreaName, studyAreas, technology, clusterName, trajectoryFileName, trajectoryId);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }


        // clone cached templates (for concurrency)
        List<StConstraintsParameterEntity> newParams = deepCopyParams(parsedTemplateParams);


        boolean isOthers = OTHERS_AREA.equalsIgnoreCase(areaParam);
        // Filters parameters by zone matching areaParam or exclusion from existing areas
        List<StConstraintsParameterEntity> filteredNewParams = newParams.stream()
                .filter(p -> {
                    if (!isOthers) {
                        return p.getZone().equalsIgnoreCase(areaParam);
                    } else {

                        return !existingAreas.contains(p.getZone());
                    }
                })
                .toList();
        log.info("STS additional constraints parsed for trajectory={}, area={}, cluster={}: templateCount={}, filteredCount={}",
                trajectoryFileName,
                rowAreaName,
                clusterName,
                parsedTemplateParams.size(),
                filteredNewParams.size());

        filteredNewParams.forEach(p -> p.setStorage(storage));


        mergeWithPriority(storage, filteredNewParams);


        String constraintsCapacityFileName =
                getFileNameWithoutExtensionAndWithoutPrefix(
                        trajectoryFileName,
                        TrajectoryType.STS.name()
                ) + EXCEL_EXTENSION;
        Path fullConstraintsPath = constraintsPath.resolve(constraintsCapacityFileName);
        storage.setConstraintsPath(fullConstraintsPath.toString());
    }



    /**
     * Merges the new parameters into the storage entity's parameter list, ensuring that
     * any existing parameters with the same key are replaced by the new parameters.
     *
     * @param storage the storage entity containing the existing list of parameters
     * @param newParams a list of new parameters to be merged with the existing parameters
     */
    private void mergeWithPriority(StStorageEntity storage,
                                   List<StConstraintsParameterEntity> newParams) {

        if (storage.getParameters() == null) {
            storage.setParameters(new ArrayList<>());
        }

        Map<String, StConstraintsParameterEntity> map = storage.getParameters().stream()
                .collect(Collectors.toMap(
                        this::buildKey,
                        Function.identity(),
                        (a, b) -> a
                ));

        for (StConstraintsParameterEntity newParam : newParams) {
            String key = buildKey(newParam);
            map.put(key, newParam); // remplace si doublon
        }

        storage.setParameters(new ArrayList<>(map.values()));
    }

    private String buildKey(StConstraintsParameterEntity p) {
        return (p.getName() + "|" + p.getZone() + "|" + p.getCluster()).toLowerCase();
    }

    private List<StConstraintsParameterEntity> deepCopyParams(List<StConstraintsParameterEntity> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().map(this::deepCopyParam).toList();
    }

    private StConstraintsParameterEntity deepCopyParam(StConstraintsParameterEntity source) {
        StConstraintsParameterEntity copy = new StConstraintsParameterEntity();
        copy.setName(source.getName());
        copy.setZone(source.getZone());
        copy.setCluster(source.getCluster());
        copy.setVariable(source.getVariable());
        copy.setOperator(source.getOperator());
        copy.setEnabled(source.getEnabled());

        List<StConstraintsHoursEntity> copiedHours =
                Optional.ofNullable(source.getHours()).orElse(List.of()).stream().map(hour -> {
                    StConstraintsHoursEntity h =
                            new StConstraintsHoursEntity();
                    h.setOccurrence(hour.getOccurrence());
                    h.setStartHour(hour.getStartHour());
                    h.setEndHour(hour.getEndHour());
                    h.setParameter(copy);
                    return h;
                }).toList();
        copy.setHours(new ArrayList<>(copiedHours));
        return copy;
    }
}
