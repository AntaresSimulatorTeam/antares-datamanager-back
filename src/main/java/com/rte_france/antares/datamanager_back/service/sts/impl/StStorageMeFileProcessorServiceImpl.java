package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.sts.StStorageMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageMeFileProcessorServiceImpl implements StStorageMeFileProcessorService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final WarningRepository warningRepository;
    private final StudyRepository studyRepository;

    private static final Integer SERIES_INDEX_ME = 10;
    private static final String EXCEL_EXTENSION = ".xlsx";
    private static final int NODE_MAX_LENGTH = 60;
    private static final int NAME_MAX_LENGTH = 40;
    private static final int GROUP_MAX_LENGTH = 20;
    private static final String[] REQUIRED_FILES = {"lower_curve.xlsx", "Pmax_injection.xlsx", "Pmax_soutirage.xlsx", "upper_curve.xlsx"};

    @Transactional
    @Override
    public TrajectoryEntity processStStorageMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {

        Path trajectoryFilePath = findTrajectoryFileCaseInsensitive(trajectoryToUse);

        Map<String, List<String>> missingFilesMeKeysMap = new LinkedHashMap<>();
        List<StStorageEntity> stStorageEntityList = buildStStorageMeLines(horizon.split("-")[1], trajectoryFilePath, studyId, missingFilesMeKeysMap);
        List<String> areasMeInStorage = stStorageEntityList.stream()
                .map(StStorageEntity::getArea)
                .toList();

        StudyEntity study = (studyId != null && studyRepository != null) ? studyRepository.findById(studyId).orElse(null) : null;
        if (study != null && study.getTrajectories() != null) {
            List<String> areasMeInStudy = study.getTrajectories().stream()
                    .filter(t -> TrajectoryType.AREA_ME.name().equals(t.getType()))
                    .filter(t -> t.getAreaConfigEntities() != null)
                    .flatMap(t -> t.getAreaConfigEntities().stream())
                    .map(AreaConfigEntity::getArea)
                    .filter(Objects::nonNull)
                    .map(AreaEntity::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Set<String> areasStudyUpperCase = areasMeInStudy.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            String unexpectedAreas = areasMeInStorage.stream()
                    .map(String::toUpperCase)
                    .filter(area -> !areasStudyUpperCase.contains(area))
                    .collect(Collectors.joining(", "));

            if (!unexpectedAreas.isEmpty()) {
                throw BusinessException.builder()
                        .message("Area(s): {0} in sts_me trajectory {1}, are not present in areas_me for study {2}")
                        .errorMessageArguments(List.of(
                                unexpectedAreas.toString(),
                                trajectoryFilePath.getFileName().toString(),
                                study.getName() != null ? study.getName() : String.valueOf(studyId)
                        ))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
       }
        TrajectoryEntity savedTrajectory = saveTrajectoryImport(stStorageEntityList, trajectoryFilePath, horizon);

        saveMissingColumnWarning(study, savedTrajectory, missingFilesMeKeysMap);

        return savedTrajectory;
    }

    private TrajectoryEntity saveTrajectoryImport(List<StStorageEntity> stStorageEntityList, Path trajectoryFilePath,
                                                   String horizon) throws IOException {
        if (stStorageEntityList.isEmpty()) {
            throw createValidationError("No ST Storage data found in the file for horizon: {0}", List.of(horizon));
        }

        boolean isSeriesTrue = stStorageEntityList.stream()
                .anyMatch(entity -> Boolean.TRUE.equals(entity.getSeries()));

        TrajectoryEntity trajectoryEntity = buildStStorageMeTrajectory(trajectoryFilePath, horizon, isSeriesTrue);

        stStorageEntityList.forEach(stStorageEntity -> stStorageEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        return trajectoryRepository.save(trajectoryEntity);
    }

    private List<StStorageEntity> buildStStorageMeLines(String horizonYear, Path trajectoryFilePath, Integer studyId, Map<String, List<String>> missingFilesMeKeysMap) throws IOException {
        List<StStorageEntity> stStorageEntityList = new ArrayList<>();
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();
        Map<Path, Set<String>> headersCache = new HashMap<>();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizonYear);
            if (sheet == null) {
                throw createValidationError("Missing horizon {0} in ST_STORAGE_ME Clusters trajectory {1}", List.of(horizonYear, trajectoryFileName));
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmptyMe(row)) continue;

                String node = row.getCell(0).getStringCellValue();
                String name = row.getCell(1).getStringCellValue();
                String group = row.getCell(2).getStringCellValue();

                // Validations
                validateMeNodeField(node, trajectoryFileName);
                validateMeFieldLength(node, NODE_MAX_LENGTH, "Node", trajectoryFileName);
                validateMeFieldLength(name, NAME_MAX_LENGTH, "Name", trajectoryFileName);
                validateMeFieldLength(group, GROUP_MAX_LENGTH, "Group", trajectoryFileName);

                validateNumericRangeMe(row, trajectoryFileName);
                validateMeInitialLevelRange(row, trajectoryFileName);
                validateBooleanRangeMe(row, trajectoryFileName);

                StStorageEntity stStorageEntity = mapRowToEntityMe(row, trajectoryFilePath, node, name, group, horizonYear, headersCache, missingFilesMeKeysMap);
                stStorageEntityList.add(stStorageEntity);
            }
        }

        return stStorageEntityList;
    }

    private StStorageEntity mapRowToEntityMe(Row row, Path trajectoryFilePath, String node, String name, String group,
                                            String horizonYear, Map<Path, Set<String>> headersCache, Map<String, List<String>> missingFilesMeKeysMap) throws IOException {
        StStorageEntity stStorageEntity = new StStorageEntity();

        Boolean hasSeriesMe = getBooleanCell(row, SERIES_INDEX_ME);
        if (hasSeriesMe) {
            Path seriesPath = buildStsOptionalFilesPathMe(trajectoryFilePath);
            List<String> missingFiles = isOptionalStsFileMissingMe(seriesPath);
            if (!missingFiles.isEmpty()) {
                throw createValidationError("Missing file(s) {0} in ST_STORAGE ME Series trajectory {1}",
                        List.of(String.join(", ", missingFiles), name));
            }
            stStorageEntity.setTsPath(seriesPath.toString());
            checkMissingColumnsInMeSeries(seriesPath, horizonYear, node, name, headersCache, missingFilesMeKeysMap);
        }

        stStorageEntity.setArea(node);
        stStorageEntity.setName(name);
        stStorageEntity.setGroupe(group);
        stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
        stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
        stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
        stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setEfficiencyWithdrawal(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
        stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 8));
        stStorageEntity.setEnabled(getBooleanCell(row, 9));
        stStorageEntity.setSeries(hasSeriesMe);
        stStorageEntity.setConstraintsFlag(getBooleanCell(row, 11));
        return stStorageEntity;
    }

    private void checkMissingColumnsInMeSeries(Path seriesPath, String horizonYear, String node, String name,
                                               Map<Path, Set<String>> headersCache, Map<String, List<String>> missingFilesMeKeysMap) {
        String meKey = buildMeKey(node, name);
        String meKeyLower = meKey.toLowerCase(Locale.ROOT);
        List<String> headersMissingForFiles = new ArrayList<>();
        for (String fileName : REQUIRED_FILES) {
            Path filePath = seriesPath.resolve(fileName);
            Set<String> headers = headersCache.computeIfAbsent(filePath, p -> readHeadersFromExcel(p, horizonYear));
            if (!headers.contains(meKeyLower)) {
                headersMissingForFiles.add(fileName);
                missingFilesMeKeysMap.put(meKey, headersMissingForFiles );
            }
        }
    }

    private String buildMeKey(String node, String name) {
        String area = node != null ? node.toUpperCase(Locale.ROOT) : "";
        String clusterName = name != null ? name : "";
        return area + "." + clusterName;
    }

    private Set<String> readHeadersFromExcel(Path filePath, String sheetName) {
        if (!Files.exists(filePath)) {
            return Collections.emptySet();
        }
        try {
            if (Files.size(filePath) == 0) {
                return Collections.emptySet();
            }
        } catch (IOException e) {
            return Collections.emptySet();
        }

        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                return Collections.emptySet();
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return Collections.emptySet();
            }
            Set<String> headers = new HashSet<>();
            for (Cell cell : headerRow) {
                if (cell != null) {
                    String value = cell.getCellType() == CellType.STRING
                            ? cell.getStringCellValue()
                            : cell.toString();
                    if (value != null && !value.isBlank()) {
                        headers.add(value.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            return headers;
        } catch (Exception e) {
            log.warn("Could not read headers from file {}: {}", filePath, e.getMessage());
            return Collections.emptySet();
        }
    }

    private void saveMissingColumnWarning(
            StudyEntity study,
            TrajectoryEntity trajectory,
            Map<String, List<String>> missingFilesMeKeysMap) {

        if (missingFilesMeKeysMap == null || missingFilesMeKeysMap.isEmpty()) {
            return;
        }

        Integer studyId = study != null ? study.getId() : null;
        Integer trajectoryId = trajectory != null ? trajectory.getId() : null;

        String createdBy = (userService != null
                && userService.getCurrentUserDetails() != null
                && userService.getCurrentUserDetails().getNni() != null)
                ? userService.getCurrentUserDetails().getNni()
                : UNKNOWN_USER;

        missingFilesMeKeysMap.forEach((meKey, missingFiles) -> {

            String warningContent = "Missing " + meKey + " in file(s): " + String.join(", ", missingFiles)
                    + ". Default time series will be used.";

            log.warn(
                    "Missing {} in file(s) {}. Default time series will be used.",
                    meKey,
                    String.join(", ", missingFiles)

            );

            if (studyId != null && trajectoryId != null) {
                boolean warningExists = warningRepository
                        .existsByWarningContentAndTrajectoryIdAndStudyId(
                                warningContent,
                                trajectoryId,
                                studyId
                        );

                if (!warningExists) {
                    WarningMessageEntity warning = WarningMessageEntity.builder()
                            .warningContent(warningContent)
                            .warningLevel(WarningLevel.WARNING_LEVEL)
                            .secondTrajectory(null)
                            .warningCode(WarningCode.STS_ME_MISSING_COLUMNS)
                            .study(study)
                            .trajectory(trajectory)
                            .creationDate(LocalDateTime.now())
                            .createdBy(createdBy)
                            .isAck(false)
                            .build();

                    warningRepository.save(warning);
                }
            }
        });
    }

    private Path buildStsOptionalFilesPathMe(Path trajectoryFilePath) {
        String trajectoryFileName = trajectoryFilePath.getFileName().toString().split("\\.")[0];
        Path seriesRoot = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsMeSeriesDirectory());
        return seriesRoot.resolve(trajectoryFileName);
    }

    private List<String> isOptionalStsFileMissingMe(Path optionalFilesPath) {
        List<String> missingFiles = new ArrayList<>();

            String[] requiredFiles = {"lower_curve.xlsx", "Pmax_injection.xlsx", "Pmax_soutirage.xlsx", "upper_curve.xlsx"};
            for (String fileName : requiredFiles) {
                if (!Files.exists(optionalFilesPath.resolve(fileName))) {
                    missingFiles.add(fileName);
                }
        }

        return missingFiles;
    }


    private void validateMeNodeField(String node, String trajectoryFileName) {
        if (node == null || node.isEmpty()) {
            throw createValidationError("Node column must be filled in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
        }
    }

    private void validateMeFieldLength(String fieldValue, int maxLength, String fieldName, String trajectoryFileName) {
        if (fieldValue != null && fieldValue.length() > maxLength) {
            throw createValidationError("{0} cannot exceed {1} characters in ST_STORAGE_ME Clusters trajectory {2}",
                    List.of(fieldName, String.valueOf(maxLength), trajectoryFileName));
        }
    }

    private void validateNumericRangeMe(Row row, String trajectoryFileName) {
        for (int idx = 3; idx <= 7; idx++) {
            Cell numericCell = row.getCell(idx);
            if (!isNumericCell(numericCell)) {
                throw createValidationError("Columns Injection [MW], Withdrawal [MW], Storage [MWh], Efficiency and initial_level must be numeric in ST_STORAGE_ME Clusters trajectory {0}",
                        List.of(trajectoryFileName));
            }
        }
    }

    private void validateMeInitialLevelRange(Row row, String trajectoryFileName) {
        double initialLevel = row.getCell(7).getNumericCellValue();
        if (initialLevel < 0 || initialLevel > 1) {
            throw createValidationError("initial_level must be between 0 and 1 in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
        }
    }

    private void validateBooleanRangeMe(Row row, String trajectoryFileName) {
        for (int idx = 8; idx <= 10; idx++) {
            Cell cell = row.getCell(idx);
            if (!isBooleanCell(cell)) {
                throw createValidationError("Columns initial_level_optim and Series must be boolean in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
            }
        }
    }

    private boolean isRowEmptyMe(Row row) {
        for (int c = 0; c <= 9; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }


    private Boolean getBooleanCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return false;
        if (cell.getCellType() == CellType.BOOLEAN || cell.getCellType() == CellType.FORMULA) return cell.getBooleanCellValue();
        String s = cell.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return false;
        return "true".equals(s) || "1".equals(s);
    }

    public Path findTrajectoryFileCaseInsensitive(String trajectoryFileName) throws IOException {
        Path root = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsMeDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS_ME root not found: " + root);
        }

        try (Stream<Path> s = Files.list(root)) {
            Optional<Path> file = s.filter(Files::isRegularFile).filter(p -> {
                String fileName = p.getFileName().toString();
                return fileName.equalsIgnoreCase(trajectoryFileName) ||
                        fileName.equalsIgnoreCase(trajectoryFileName + EXCEL_EXTENSION);
            }).findFirst();

            if (file.isPresent()) {
                return file.get();
            }
        }

        throw new NoSuchFileException("Trajectory file not found: " + trajectoryFileName + " in " + root);
    }

    private TrajectoryEntity buildStStorageMeTrajectory(Path trajectoryFilePath, String horizon, Boolean hasSeries) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String trajectoryName = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS_ME.name());

        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.STS_ME.name());

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon.split("-")[1],
                    createdBy, TrajectoryType.STS_ME, null, null, hasSeries);
        } else {
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon.split("-")[1], createdBy,
                    TrajectoryType.STS_ME, null, null, hasSeries);
        }

        return trajectory;
    }

    private BusinessException createValidationError(String message, List<String> args) {
        return BusinessException.builder()
                .message(message)
                .errorMessageArguments(args)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }
}
