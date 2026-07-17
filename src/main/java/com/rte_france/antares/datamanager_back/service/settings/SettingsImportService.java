package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.SETTINGS;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsImportService {

    private static final String UNKNOWN_USER = "UNKNOWN";
    private static final String GENERAL_DATA_FILE_PREFIX = "general_data_";
    private static final String SETTINGS_FILE_SUFFIX = ".xlsx";

    private final SettingsGeneralParametersRepository generalParametersRepository;
    private final SettingsOptimizationParametersRepository optimizationParametersRepository;
    private final SettingsAdvancedParametersRepository advancedParametersRepository;
    private final SettingsSeedsParametersRepository seedsParametersRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    @Transactional
    public TrajectoryEntity importSettings(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException {
        // Build the trajectory path following the NuclearFileProcessorServiceImpl pattern
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        
        // Get the trajectory settings directory from the configuration
        String settingsDirectory = antaresDataManagerProperties.getTrajectorySettingsDirectory();
        
        // Full path to trajectory folder
        Path trajectoryFolder = basePath
                .resolve(settingsDirectory)
                .normalize();
        
        log.info("Loading trajectory settings from: {}", trajectoryFolder);
        
        // Check trajectory folder exists
        if (!Files.isDirectory(trajectoryFolder)) {
            throw BusinessException.builder()
                    .message("Trajectory settings folder not found: {0}")
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Build settings file path
        String settingsFileName = trajectoryToUse + SETTINGS_FILE_SUFFIX;
        Path filePath = trajectoryFolder.resolve(settingsFileName);

        // Check settings file exists
        if (!Files.isRegularFile(filePath)) {
            throw BusinessException.builder()
                    .message("Settings file not found: {0}")
                    .errorMessageArguments(List.of(filePath.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            // Calculate checksums per sheet
            Map<String, String> sheetChecksums = calculateSheetChecksums(workbook);
            String combinedChecksum = calculateCombinedChecksum(sheetChecksums);

            // Build trajectory entity
            TrajectoryEntity trajectory = buildSettingsTrajectory(
                    trajectoryToUse, 
                    filePath, 
                    horizon, 
                    area, 
                    combinedChecksum
            );

            // Check if already processed
            Optional<TrajectoryEntity> existingTrajectory = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                    trajectoryToUse,
                    SETTINGS.name(),
                    horizon,
                    area
            );

            if (existingTrajectory.isPresent()) {
                if (existingTrajectory.get().getChecksum().equals(combinedChecksum)) {
                    throw BusinessException.builder()
                            .message("Settings file already processed with same checksum: {0}")
                            .errorMessageArguments(List.of(filePath.toString()))
                            .httpStatus(HttpStatus.CONFLICT)
                            .build();
                } else {
                    trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
                }
            } else {
                trajectory.setVersion(1);
            }

            // Save trajectory
            TrajectoryEntity savedTrajectory = trajectoryRepository.save(trajectory);

            // Import settings
            importGeneralParameters(workbook, savedTrajectory, sheetChecksums);
            importOptimizationParameters(workbook, savedTrajectory, sheetChecksums);
            importAdvancedParameters(workbook, savedTrajectory, sheetChecksums);
            importSeedsParameters(workbook, savedTrajectory, sheetChecksums);

            log.info("Successfully imported trajectory settings from: {} (version: {}, checksum: {})", 
                    filePath, trajectory.getVersion(), combinedChecksum);
            
            return savedTrajectory;
        }
    }

    private Map<String, String> calculateSheetChecksums(Workbook workbook) throws IOException {
        Map<String, String> sheetChecksums = new HashMap<>();
        List<String> listOfSheet = List.of("General parameters", "Optimization preferences", "Advanced parameters");
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            if(listOfSheet.contains(sheetName)) {
                String checksum = calculateSheetChecksum(sheet);
                sheetChecksums.put(sheetName, checksum);
                log.debug("Sheet '{}' checksum: {}", sheetName, checksum);
            }
        }
        
        return sheetChecksums;
    }

    private String calculateSheetChecksum(Sheet sheet) {
        StringBuilder sheetContent = new StringBuilder();
        
        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }
            
            for (int cellNum = 0; cellNum < row.getLastCellNum(); cellNum++) {
                Object cellValue = Utils.getCellValue(row, cellNum);
                if (cellValue != null) {
                    sheetContent.append(cellValue).append("|");
                }
            }
            sheetContent.append("\n");
        }
        
        return DigestUtils.sha256Hex(sheetContent.toString());
    }

    private String calculateCombinedChecksum(Map<String, String> sheetChecksums) {
        StringBuilder combined = new StringBuilder();
        sheetChecksums.values().forEach(combined::append);
        return DigestUtils.sha256Hex(combined.toString());
    }

    private TrajectoryEntity buildSettingsTrajectory(String trajectoryToUse, Path filePath, String horizon, String area, String checksum) throws IOException {
        String createdBy = getCurrentUser();
        
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(filePath))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .checksum(checksum)
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
                        ZoneId.systemDefault()))
                .horizon(horizon)
                .area(area)
                .type(SETTINGS.name())
                .hasTimeSeries(false)
                .build();
        
        return trajectory;
    }

    private String getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return UNKNOWN_USER;
    }

    private void importGeneralParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("General parameters");
        if (sheet == null) {
            log.warn("Sheet 'General parameters' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsGeneralParametersEntity entity = SettingsGeneralParametersEntity.builder()
                .mode(getStringValue(dataMap, "Mode"))
                .horizon(getStringValue(dataMap, "Horizon"))
                .nbYears(getIntValue(dataMap, "Number of MC year"))
                .simulationStart(getIntValue(dataMap, "First day"))
                .simulationEnd(getIntValue(dataMap, "Last day"))
                .januaryFirst(getStringValue(dataMap, "1st january"))
                .firstMonthInYear(getStringValue(dataMap, "Year"))
                .firstWeekDay(getStringValue(dataMap, "Week"))
                .leapYear(getBooleanValue(dataMap, "Leap Year"))
                .yearByYear(getBooleanValue(dataMap, "Year-by-year"))
                .simulationSynthesis(getBooleanValue(dataMap, "Synthesis"))
                .buildingMode(getStringValue(dataMap, "Building mode"))
                .userPlaylist(getBooleanValue(dataMap, "Selection mode"))
                .thematicTrimming(getBooleanValue(dataMap, "Thematic trimming"))
                .geographicTrimming(getBooleanValue(dataMap, "Geographic trimming"))
                .nbTimeseriesThermal(getIntValue(dataMap, "Nbtimeseriesthermal"))
                .storeNewSet(getBooleanValue(dataMap, "Storenewset"))
                .trajectory(trajectory)
                .build();


        generalParametersRepository.save(entity);
        log.debug("General parameters imported successfully (checksum: {})", sheetChecksums.get("General parameters"));
    }

    private void importOptimizationParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Optimization preferences");
        if (sheet == null) {
            log.warn("Sheet 'Optimization preferences' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);


        SettingsOptimizationParametersEntity entity = SettingsOptimizationParametersEntity.builder()
                .simplexRange(getStringValue(dataMap, "simplex optimization range"))
                .transmissionCapacities(getStringValue(dataMap, "transmission capacities"))
                .includeConstraints(getBooleanValue(dataMap, "binding constraints"))
                .includeHurdlecosts(getBooleanValue(dataMap, "hurdle costs"))
                .includeTcMinstablepower(getBooleanValue(dataMap, "thermal clusters min stable power"))
                .includeTcMinUdTime(getBooleanValue(dataMap, "thermal clusters min U/D time"))
                .includeDayahead(getBooleanValue(dataMap, "day ahead reserve"))
                .includeStrategicreserve(getBooleanValue(dataMap, "strategic reserve"))
                .includeSpinningreserve(getBooleanValue(dataMap, "spinning reserve"))
                .includePrimaryreserve(getBooleanValue(dataMap, "primary reserve"))
                .includeExportmps(getStringValue(dataMap, "export mps"))
                .includeUnfeasibleProblemBehavior(getStringValue(dataMap, "unfeasible problem behavior"))
                .trajectory(trajectory)
                .build();

        optimizationParametersRepository.save(entity);
        log.debug("Optimization parameters imported successfully (checksum: {})", sheetChecksums.get("Optimization preferences"));
    }

    private void importAdvancedParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Advanced parameters");
        if (sheet == null) {
            log.warn("Sheet 'Advanced parameters' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsAdvancedParametersEntity entity = SettingsAdvancedParametersEntity.builder()
                .hydroHeuristicPolicy(getStringValue(dataMap, "hydro heuristic policy"))
                .hydroPricingMode(getStringValue(dataMap, "hydro pricing mode"))
                .powerFluctuations(getStringValue(dataMap, "power fluctuations"))
                .sheddingPolicy(getStringValue(dataMap, "shedding policy"))
                .unitCommitmentMode(getStringValue(dataMap, "unit commitment mode"))
                .numberOfCoresMode(getStringValue(dataMap, "number of cores mode"))
                .renewableGenerationModelling(getStringValue(dataMap, "renewable generation modelling"))
                .accuracyOnCorrelation(getStringValue(dataMap, "accuracy on correlation"))
                .accurateShavePeaksIncludeShortTermtorage(getBooleanValue(dataMap, "accurate shave peaks include short term storage"))
                .trajectory(trajectory)
                .build();

        advancedParametersRepository.save(entity);
        log.debug("Advanced parameters imported successfully (checksum: {})", sheetChecksums.get("Advanced parameters"));
    }

    private void importSeedsParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Advanced parameters");
        if (sheet == null) {
            log.warn("Sheet 'Advanced parameters' not found for seeds");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsSeedsParametersEntity entity = SettingsSeedsParametersEntity.builder()
                .seedTsgenThermal(getIntValue(dataMap, "Thermal time-series generation"))
                .seedTsnumbers(getIntValue(dataMap, "Time-series draws (MC scenario builder)"))
                .seedUnsuppliedEnergyCosts(getIntValue(dataMap, "Noise on unsupplied energy costs"))
                .seedSpilledEnergyCosts(getIntValue(dataMap, "Noise on spilled energy costs"))
                .seedThermalCosts(getIntValue(dataMap, "Noise on thermal plants costs"))
                .seedHydroCosts(getIntValue(dataMap, "Noise on virtual hydro costs"))
                .seedInitialReservoirLevels(getIntValue(dataMap, "Initial reservoir levels"))
                .trajectory(trajectory)
                .build();

        seedsParametersRepository.save(entity);
        log.debug("Seeds parameters imported successfully (checksum: {})", sheetChecksums.get("Advanced parameters"));
    }

    private Map<String, Object> readParametersSheet(Sheet sheet) {
        Map<String, Object> dataMap = new HashMap<>();
        
        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null || row.getLastCellNum() < 2) {
                continue;
            }

            Object keyObj = Utils.getCellValue(row, 0);
            Object valueObj = Utils.getCellValue(row, 1);

            if (keyObj != null && valueObj != null) {
                // Normaliser la clé : minuscules et remplacer les espaces par des traits d'union
                String key = keyObj.toString().trim().toLowerCase().replaceAll("\\s+", "-");
                dataMap.put(key, valueObj);
            }
        }

        return dataMap;
    }

    private String getStringValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key);
        return value != null ? value.toString().trim() : null;
    }

    private Integer getIntValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("Could not convert value to integer for key '{}': {}", key, value);
            return null;
        }
    }

    private Boolean getBooleanValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String strValue = value.toString().trim().toLowerCase();
        return "true".equals(strValue) || "yes".equals(strValue) || "1".equals(strValue);
    }
}
