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
    private static final String SETTINGS_FILE_SUFFIX = ".xlsx";

    // General Parameters Keys
    private static final String KEY_MODE = "Mode";
    private static final String KEY_HORIZON = "Horizon";
    private static final String KEY_NUMBER_OF_MC_YEAR = "Number of MC year";
    private static final String KEY_FIRST_DAY = "First day";
    private static final String KEY_LAST_DAY = "Last day";
    private static final String KEY_1ST_JANUARY = "1st january";
    private static final String KEY_YEAR = "Year";
    private static final String KEY_WEEK = "Week";
    private static final String KEY_LEAP_YEAR = "Leap Year";
    private static final String KEY_YEAR_BY_YEAR = "Year-by-year";
    private static final String KEY_SYNTHESIS = "Synthesis";
    private static final String KEY_BUILDING_MODE = "Building mode";
    private static final String KEY_SELECTION_MODE = "Selection mode";
    private static final String KEY_THEMATIC_TRIMMING = "Thematic trimming";
    private static final String KEY_GEOGRAPHIC_TRIMMING = "Geographic trimming";
    private static final String KEY_NBTIMESERIESTHERMAL = "Nbtimeseriesthermal";
    private static final String KEY_STORENEWSET = "Storenewset";

    // Optimization Parameters Keys
    private static final String KEY_SIMPLEX_OPTIMIZATION_RANGE = "simplex optimization range";
    private static final String KEY_TRANSMISSION_CAPACITIES = "transmission capacities";
    private static final String KEY_BINDING_CONSTRAINTS = "binding constraints";
    private static final String KEY_HURDLE_COSTS = "hurdle costs";
    private static final String KEY_THERMAL_CLUSTERS_MIN_STABLE_POWER = "thermal clusters min stable power";
    private static final String KEY_THERMAL_CLUSTERS_MIN_UD_TIME = "thermal clusters min U/D time";
    private static final String KEY_DAY_AHEAD_RESERVE = "day ahead reserve";
    private static final String KEY_STRATEGIC_RESERVE = "strategic reserve";
    private static final String KEY_SPINNING_RESERVE = "spinning reserve";
    private static final String KEY_PRIMARY_RESERVE = "primary reserve";
    private static final String KEY_EXPORT_MPS = "export mps";
    private static final String KEY_UNFEASIBLE_PROBLEM_BEHAVIOR = "Unfeasible problem behavios";

    // Advanced Parameters Keys
    private static final String KEY_HYDRO_HEURISTIC_POLICY = "hydro heuristic policy";
    private static final String KEY_HYDRO_PRICING_MODE = "hydro pricing mode";
    private static final String KEY_POWER_FLUCTUATIONS = "power fluctuations";
    private static final String KEY_SHEDDING_POLICY = "shedding policy";
    private static final String KEY_UNIT_COMMITMENT_MODE = "unit commitment mode";
    private static final String KEY_NUMBER_OF_CORES_MODE = "Simulation cores";
    private static final String KEY_RENEWABLE_GENERATION_MODELLING = "renewable generation modeling";
    private static final String KEY_ACCURACY_ON_CORRELATION = "accuracy on correlation";
    private static final String KEY_ACCURATE_SHAVE_PEAKS_INCLUDE_SHORT_TERM_STORAGE = "accurate shave peaks include short term storage";
    private static final String KEY_INITIAL_RESERVOIR_LEVELS = "Initial reservoir levels 2";


    // Seeds Parameters Keys
    private static final String KEY_SEED_TS_GEN_THERMAL = "Thermal time-series generation";
    private static final String KEY_SEED_TS_NUMBERS = "Time-series draws (MC scenario builder)";
    private static final String KEY_SEED_UNSUPPLIED_ENERGY_COSTS = "Noise on unsupplied energy costs";
    private static final String KEY_SEED_SPILLED_ENERGY_COSTS = "Noise on spilled energy costs";
    private static final String KEY_SEED_THERMAL_COSTS = "Noise on thermal plants costs";
    private static final String KEY_SEED_HYDRO_COSTS = "Noise on virtual hydro costs";
    private static final String KEY_SEED_INITIAL_RESERVOIR_LEVELS = "Initial reservoir levels";

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

    public Map<String, String> calculateSheetChecksums(Workbook workbook) throws IOException {
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

    public String calculateSheetChecksum(Sheet sheet) {
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

    public String calculateCombinedChecksum(Map<String, String> sheetChecksums) {
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

    public String getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return UNKNOWN_USER;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return UNKNOWN_USER;
    }

    public void importGeneralParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("General parameters");
        if (sheet == null) {
            log.warn("Sheet 'General parameters' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsGeneralParametersEntity entity = SettingsGeneralParametersEntity.builder()
                .mode(ParameterValueConverter.getStringValue(dataMap, KEY_MODE))
                .horizon(ParameterValueConverter.getStringValue(dataMap, KEY_HORIZON))
                .nbYears(ParameterValueConverter.getIntValue(dataMap, KEY_NUMBER_OF_MC_YEAR))
                .simulationStart(ParameterValueConverter.getIntValue(dataMap, KEY_FIRST_DAY))
                .simulationEnd(ParameterValueConverter.getIntValue(dataMap, KEY_LAST_DAY))
                .januaryFirst(ParameterValueConverter.getStringValue(dataMap, KEY_1ST_JANUARY))
                .firstMonthInYear(ParameterValueConverter.getStringValue(dataMap, KEY_YEAR))
                .firstWeekDay(ParameterValueConverter.getStringValue(dataMap, KEY_WEEK))
                .leapYear(ParameterValueConverter.getBooleanValue(dataMap, KEY_LEAP_YEAR))
                .yearByYear(ParameterValueConverter.getBooleanValue(dataMap, KEY_YEAR_BY_YEAR))
                .simulationSynthesis(ParameterValueConverter.getBooleanValue(dataMap, KEY_SYNTHESIS))
                .buildingMode(ParameterValueConverter.getStringValue(dataMap, KEY_BUILDING_MODE))
                .userPlaylist(ParameterValueConverter.getBooleanValue(dataMap, KEY_SELECTION_MODE))
                .thematicTrimming(ParameterValueConverter.getBooleanValue(dataMap, KEY_THEMATIC_TRIMMING))
                .geographicTrimming(ParameterValueConverter.getBooleanValue(dataMap, KEY_GEOGRAPHIC_TRIMMING))
                .nbTimeseriesThermal(ParameterValueConverter.getIntValue(dataMap, KEY_NBTIMESERIESTHERMAL))
                .storeNewSet(ParameterValueConverter.getBooleanValue(dataMap, KEY_STORENEWSET))
                .trajectory(trajectory)
                .build();


        generalParametersRepository.save(entity);
        log.debug("General parameters imported successfully (checksum: {})", sheetChecksums.get("General parameters"));
    }

    public void importOptimizationParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Optimization preferences");
        if (sheet == null) {
            log.warn("Sheet 'Optimization preferences' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);


        SettingsOptimizationParametersEntity entity = SettingsOptimizationParametersEntity.builder()
                .simplexRange(ParameterValueConverter.getStringValue(dataMap, KEY_SIMPLEX_OPTIMIZATION_RANGE))
                .transmissionCapacities(ParameterValueConverter.getStringValue(dataMap, KEY_TRANSMISSION_CAPACITIES))
                .includeConstraints(ParameterValueConverter.getBooleanValue(dataMap, KEY_BINDING_CONSTRAINTS))
                .includeHurdlecosts(ParameterValueConverter.getBooleanValue(dataMap, KEY_HURDLE_COSTS))
                .includeTcMinstablepower(ParameterValueConverter.getBooleanValue(dataMap, KEY_THERMAL_CLUSTERS_MIN_STABLE_POWER))
                .includeTcMinUdTime(ParameterValueConverter.getBooleanValue(dataMap, KEY_THERMAL_CLUSTERS_MIN_UD_TIME))
                .includeDayahead(ParameterValueConverter.getBooleanValue(dataMap, KEY_DAY_AHEAD_RESERVE))
                .includeStrategicreserve(ParameterValueConverter.getBooleanValue(dataMap, KEY_STRATEGIC_RESERVE))
                .includeSpinningreserve(ParameterValueConverter.getBooleanValue(dataMap, KEY_SPINNING_RESERVE))
                .includePrimaryreserve(ParameterValueConverter.getBooleanValue(dataMap, KEY_PRIMARY_RESERVE))
                .includeExportmps(ParameterValueConverter.getStringValue(dataMap, KEY_EXPORT_MPS))
                .includeUnfeasibleProblemBehavior(ParameterValueConverter.getStringValue(dataMap, KEY_UNFEASIBLE_PROBLEM_BEHAVIOR))
                .trajectory(trajectory)
                .build();

        optimizationParametersRepository.save(entity);
        log.debug("Optimization parameters imported successfully (checksum: {})", sheetChecksums.get("Optimization preferences"));
    }

    public void importAdvancedParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Advanced parameters");
        if (sheet == null) {
            log.warn("Sheet 'Advanced parameters' not found");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsAdvancedParametersEntity entity = SettingsAdvancedParametersEntity.builder()
                .hydroHeuristicPolicy(ParameterValueConverter.getStringValue(dataMap, KEY_HYDRO_HEURISTIC_POLICY))
                .hydroPricingMode(ParameterValueConverter.getStringValue(dataMap, KEY_HYDRO_PRICING_MODE))
                .powerFluctuations(ParameterValueConverter.getStringValue(dataMap, KEY_POWER_FLUCTUATIONS))
                .sheddingPolicy(ParameterValueConverter.getStringValue(dataMap, KEY_SHEDDING_POLICY))
                .unitCommitmentMode(ParameterValueConverter.getStringValue(dataMap, KEY_UNIT_COMMITMENT_MODE))
                .numberOfCoresMode(ParameterValueConverter.getStringValue(dataMap, KEY_NUMBER_OF_CORES_MODE))
                .renewableGenerationModelling(ParameterValueConverter.getStringValue(dataMap, KEY_RENEWABLE_GENERATION_MODELLING))
                .accuracyOnCorrelation(ParameterValueConverter.getStringValue(dataMap, KEY_ACCURACY_ON_CORRELATION))
                .accurateShavePeaksIncludeShortTermtorage(ParameterValueConverter.getBooleanValue(dataMap, KEY_ACCURATE_SHAVE_PEAKS_INCLUDE_SHORT_TERM_STORAGE))
                .initialReservoirLevels(ParameterValueConverter.getStringValue(dataMap, KEY_INITIAL_RESERVOIR_LEVELS))
                .trajectory(trajectory)
                .build();

        advancedParametersRepository.save(entity);
        log.debug("Advanced parameters imported successfully (checksum: {})", sheetChecksums.get("Advanced parameters"));
    }

    public void importSeedsParameters(Workbook workbook, TrajectoryEntity trajectory, Map<String, String> sheetChecksums) {
        Sheet sheet = workbook.getSheet("Advanced parameters");
        if (sheet == null) {
            log.warn("Sheet 'Advanced parameters' not found for seeds");
            return;
        }

        Map<String, Object> dataMap = readParametersSheet(sheet);

        SettingsSeedsParametersEntity entity = SettingsSeedsParametersEntity.builder()
                .seedTsgenThermal(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_TS_GEN_THERMAL))
                .seedTsnumbers(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_TS_NUMBERS))
                .seedUnsuppliedEnergyCosts(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_UNSUPPLIED_ENERGY_COSTS))
                .seedSpilledEnergyCosts(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_SPILLED_ENERGY_COSTS))
                .seedThermalCosts(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_THERMAL_COSTS))
                .seedHydroCosts(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_HYDRO_COSTS))
                .seedInitialReservoirLevels(ParameterValueConverter.getIntValue(dataMap, KEY_SEED_INITIAL_RESERVOIR_LEVELS))
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
                String baseKey = keyObj.toString().trim().toLowerCase().replaceAll("\\s+", "-");
                // En cas de doublon, ne pas écraser la valeur : créer une nouvelle clé suffixée (_2, _3, ...)
                String key = baseKey;
                int suffix = 2;
                while (dataMap.containsKey(key)) {
                    key = baseKey + "-" + suffix++;
                }
                if (!key.equals(baseKey)) {
                    log.warn("Duplicate key '{}' found in sheet '{}', stored as '{}'", baseKey, sheet.getSheetName(), key);
                }
                dataMap.put(key, valueObj);
            }
        }

        return dataMap;
    }

}
