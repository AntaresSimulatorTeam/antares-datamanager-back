package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.NuclearModulationParameterRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.NuclearModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EmptyFileException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.rte_france.antares.datamanager_back.util.Utils.calculateDirectoryChecksum;
import static com.rte_france.antares.datamanager_back.util.Utils.getFileChecksum;
import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;

@Slf4j
@Service
@RequiredArgsConstructor
public class NuclearFileProcessorServiceImpl implements NuclearFileProcessorService {

    private static final String UNKNOWN_USER = "UNKNOWN";
    private static final String PARAMETERS_FILE_PREFIX = "Parameters_modNuc_";
    private static final String PARAMETERS_FILE_SUFFIX = ".xlsx";

    private final TrajectoryRepository trajectoryRepository;
    private final NuclearModulationParameterRepository nuclearModulationParameterRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final PathSecurityUtil pathSecurityUtil;

    @Transactional
    @Override
    public TrajectoryEntity processNuclearModulationFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException {
        
        // Build the trajectory path
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        
        // Get the nuclear modulation directory from the configuration
        String modulationDirectory = antaresDataManagerProperties.getNuclearModulationDirectory();

        // Validate path security
        validatePathFromTrajectoryRoot(modulationDirectory, trajectoryToUse);
        
        // Full path to trajectory folder
        Path trajectoryFolder = basePath
                .resolve(modulationDirectory)
                .resolve(trajectoryToUse)
                .normalize();

        // Check trajectory folder exists
        if (!Files.isDirectory(trajectoryFolder)) {
            throw BusinessException.builder()
                    .message("Nuclear modulation trajectory folder not found: {0}")
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Build parameters file path
        String parametersFileName = PARAMETERS_FILE_PREFIX + trajectoryToUse + PARAMETERS_FILE_SUFFIX;
        Path parametersFilePath = trajectoryFolder.resolve(parametersFileName);

        // Check parameters file exists
        if (!Files.isRegularFile(parametersFilePath)) {
            throw BusinessException.builder()
                    .message("Parameters file not found: {0}")
                    .errorMessageArguments(List.of(parametersFileName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Extract horizon year (e.g., "2030-2031" -> "2031")
        String horizonYear = horizon.split("-")[1];

        // Validate time series files in TS_modulation directory first
        validateTimeSeriesFiles(trajectoryFolder, trajectoryToUse, horizon);
        
        // Read parameters and extract modulation values
        List<NuclearModulationParameterEntity> modulationParameters = readNuclearModulationParameters(
                parametersFilePath, trajectoryToUse, horizonYear);

        // Calculate checksum for the entire directory
        String checksum = calculateDirectoryChecksum(trajectoryFolder);

        // Build trajectory entity
        TrajectoryEntity trajectory = buildNuclearModulationTrajectory(
                trajectoryToUse, trajectoryFolder, horizon, checksum, area);

        // Check if trajectory already exists with this checksum
        var existingTrajectory = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryToUse,
                horizon,
                TrajectoryType.NUCLEAR_FR_MODULATION.name());

        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                throw BusinessException.builder()
                        .message("Nuclear modulation trajectory {0} with the same checksum already exists")
                        .errorMessageArguments(List.of(trajectoryToUse))
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

        // Save modulation parameters
        for (NuclearModulationParameterEntity param : modulationParameters) {
            param.setTrajectory(savedTrajectory);
            nuclearModulationParameterRepository.save(param);
        }

        log.info("Nuclear modulation trajectory {} imported successfully (version: {})",
                trajectoryToUse, savedTrajectory.getVersion());

        return savedTrajectory;
    }

    /**
     * Validate time series files in TS_modulation directory
     */
    private void validateTimeSeriesFiles(Path trajectoryFolder, String trajectoryToUse, String horizon) {
        Path tsModulationDir = trajectoryFolder.resolve("TS_modulation");
        
        // Check if TS_modulation directory exists
        if (!Files.isDirectory(tsModulationDir)) {
            throw BusinessException.builder()
                    .message("TS_modulation directory not found at: {0}")
                    .errorMessageArguments(List.of(tsModulationDir.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        // Extract horizon year (e.g., "2030-2031" -> "2031")
        String horizonYear = horizon.split("-")[1];
        
        // Array of modulation types to check
        String[] modulationTypes = {"daily", "hourly", "weekly"};
        
        for (String modulationType : modulationTypes) {
            String fileName = trajectoryToUse + "_" + modulationType + ".xlsx";
            Path filePath = tsModulationDir.resolve(fileName);
            
            // Check if file exists
            if (!Files.isRegularFile(filePath)) {
                throw BusinessException.builder()
                        .message("Time series file not found: {0}")
                        .errorMessageArguments(List.of(fileName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
        
        log.info("Time series files validation successful for trajectory {}", trajectoryToUse);
    }

    /**
     * Read nuclear modulation parameters from Excel file
     */
    private List<NuclearModulationParameterEntity> readNuclearModulationParameters(
            Path parametersFilePath, String trajectoryName, String horizonYear) {

        List<NuclearModulationParameterEntity> parameters = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(parametersFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // Get the sheet with the trajectory name
            Sheet sheet = workbook.getSheet(trajectoryName);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Sheet {0} not found in parameters file")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Find the column index for the horizon year
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.builder()
                        .message("Header row not found in parameters sheet")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            int horizonColumnIndex = -1;
            for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                Object headerValue = getCellValue(headerRow, cellIndex);
                if (headerValue != null) {
                    String headerValueStr;
                    if (headerValue instanceof Number) {
                        // Handle numeric values like 2025.0 by converting to integer
                        headerValueStr = String.valueOf(((Number) headerValue).longValue());
                    } else {
                        headerValueStr = headerValue.toString();
                    }
                    
                    if (headerValueStr.equals(horizonYear)) {
                        horizonColumnIndex = cellIndex;
                        break;
                    }
                }
            }

            if (horizonColumnIndex == -1) {
                throw BusinessException.builder()
                        .message("Horizon {0} not found in parameters file")
                        .errorMessageArguments(List.of(horizonYear))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Read the three modulation values
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Object typeObj = getCellValue(row, 0);
                if (typeObj == null) continue;

                String type = typeObj.toString();
                Object valueObj = getCellValue(row, horizonColumnIndex);

                if (valueObj == null) continue;

                double value;
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue();
                } else {
                    try {
                        value = Double.parseDouble(valueObj.toString().replace(",", "."));
                    } catch (NumberFormatException e) {
                        log.warn("Skipping invalid value for type {}: {}", type, valueObj);
                        continue;
                    }
                }

                // Only keep the three modulation types
                if (isValidModulationType(type)) {
                    parameters.add(NuclearModulationParameterEntity.builder()
                            .type(type)
                            .value(BigDecimal.valueOf(value))
                            .build());
                }
            }

            if (parameters.isEmpty()) {
                throw BusinessException.builder()
                        .message("No valid modulation parameters found in file")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

        } catch (EmptyFileException e) {
            throw BusinessException.builder()
                    .message("Parameters file is empty or invalid: {0}")
                    .errorMessageArguments(List.of(parametersFilePath.getFileName().toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error reading nuclear modulation parameters file: {0}")
                    .errorMessageArguments(List.of(e.getMessage()))
                    .cause(e)
                    .build();
        }

        return parameters;
    }

    /**
     * Check if type is a valid modulation type
     */
    private boolean isValidModulationType(String type) {
        return type != null && (
                type.equals("nucFR_modul_hourly") ||
                type.equals("nucFR_modul_daily") ||
                type.equals("nucFR_modul_weekly")
        );
    }

    /**
     * Build trajectory entity
     */
    private TrajectoryEntity buildNuclearModulationTrajectory(
            String trajectoryName, Path trajectoryFolder, String horizon, String checksum, String area) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? 
                userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;

        return TrajectoryEntity.builder()
                .fileName(trajectoryName)
                .fileSize(calculateDirectorySize(trajectoryFolder))
                .checksum(checksum)
                .type(TrajectoryType.NUCLEAR_FR_MODULATION.name())
                .horizon(horizon)
                .area(area)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(trajectoryFolder).toMillis()),
                        ZoneId.systemDefault()))
                .createdBy(createdBy)
                .hasTimeSeries(true)
                .build();
    }

    /**
     * Calculate total size of directory recursively
     */
    private Long calculateDirectorySize(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    @Transactional
    @Override
    public TrajectoryEntity processNuclearLongTermFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException {
        
        // Build the trajectory path
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        
        // Get the nuclear long-term directory from the configuration
        String ltDirectory = antaresDataManagerProperties.getNuclearLtDirectory();

        // Validate path security
        validatePathFromTrajectoryRoot(ltDirectory, trajectoryToUse);
        
        // Full path to trajectory folder
        Path trajectoryFolder = basePath
                .resolve(ltDirectory)
                .resolve(trajectoryToUse)
                .normalize();

        // Check trajectory folder exists
        if (!Files.isDirectory(trajectoryFolder)) {
            throw BusinessException.builder()
                    .message("Nuclear long-term trajectory folder not found: {0}")
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Build simulation file path: Simu_<horizon>.xlsx
        String simulationFileName = "Simu_" + horizon + ".xlsx";
        Path simulationFilePath = trajectoryFolder.resolve(simulationFileName);

        // Check simulation file exists
        if (!Files.isRegularFile(simulationFilePath)) {
            throw BusinessException.builder()
                    .message("Simulation file not found: {0}")
                    .errorMessageArguments(List.of(simulationFileName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }


        // Calculate checksum for the Excel simulation file
        String checksum = getFileChecksum(simulationFilePath.toString());

        // Build trajectory entity
        TrajectoryEntity trajectory = buildNuclearLongTermTrajectory(
                trajectoryToUse, trajectoryFolder, horizon, checksum, area);

        // Check if trajectory already exists with this checksum
        var existingTrajectory = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryToUse,
                horizon,
                TrajectoryType.NUCLEAR_FR_TS_LONG_TERM.name());

        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                throw BusinessException.builder()
                        .message("Nuclear long-term trajectory {0} with the same checksum already exists")
                        .errorMessageArguments(List.of(trajectoryToUse))
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

        log.info("Nuclear long-term trajectory {} imported successfully (version: {})",
                trajectoryToUse, savedTrajectory.getVersion());

        return savedTrajectory;
    }

    /**
     * Build trajectory entity for nuclear long-term
     */
    private TrajectoryEntity buildNuclearLongTermTrajectory(
            String trajectoryName, Path trajectoryFolder, String horizon, String checksum, String area) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? 
                userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;

        return TrajectoryEntity.builder()
                .fileName(trajectoryName)
                .fileSize(calculateDirectorySize(trajectoryFolder))
                .checksum(checksum)
                .type(TrajectoryType.NUCLEAR_FR_TS_LONG_TERM.name())
                .horizon(horizon)
                .area(area)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(trajectoryFolder).toMillis()),
                        ZoneId.systemDefault()))
                .createdBy(createdBy)
                .hasTimeSeries(true)
                .build();
    }

    @Transactional
    @Override
    public TrajectoryEntity processNuclearTsErpFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException {
        return processNuclearTsFile(trajectoryToUse, horizon, studyId, area, 
                antaresDataManagerProperties.getNuclearEprDirectory(), TrajectoryType.NUCLEAR_FR_TS_ERP);
    }

    @Transactional
    @Override
    public TrajectoryEntity processNuclearTsSmrFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException {
        return processNuclearTsFile(trajectoryToUse, horizon, studyId, area, 
                antaresDataManagerProperties.getNuclearSmrDirectory(), TrajectoryType.NUCLEAR_FR_TS_SMR);
    }

    /**
     * Generic method to process nuclear time series (EPR/SMR) files
     */
    private TrajectoryEntity processNuclearTsFile(String trajectoryToUse, String horizon, Integer studyId, 
            String area, String directoryPath, TrajectoryType trajectoryType) throws IOException {
        
        // Build the full path to the file using NAS directory and trajectory file path
        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        
        // If trajectoryToUse doesn't have an extension, add .xlsx
        String fileName = trajectoryToUse.endsWith(".xlsx") ? trajectoryToUse : trajectoryToUse + ".xlsx";
        
        Path filePath = basePath
                .resolve(directoryPath)
                .resolve(fileName)
                .normalize();

        // Check file exists
        if (!Files.isRegularFile(filePath)) {
            throw BusinessException.builder()
                    .message("Nuclear trajectory file not found: {0}")
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Calculate checksum for the Excel file
        String checksum = getFileChecksum(filePath.toString());

        // Build trajectory entity
        TrajectoryEntity trajectory = buildNuclearTsTrajectory(
                trajectoryToUse, filePath, horizon, checksum, area, trajectoryType);

        // Check if trajectory already exists with this checksum
        var existingTrajectory = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryToUse,
                horizon,
                trajectoryType.name());

        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                throw BusinessException.builder()
                        .message("Nuclear {0} trajectory {1} with the same checksum already exists")
                        .errorMessageArguments(List.of(trajectoryType.name(), trajectoryToUse))
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

        log.info("Nuclear {} trajectory {} imported successfully (version: {})",
                trajectoryType.name(), trajectoryToUse, savedTrajectory.getVersion());

        return savedTrajectory;
    }

    /**
     * Build trajectory entity for nuclear time series (EPR/SMR)
     */
    private TrajectoryEntity buildNuclearTsTrajectory(
            String fileName, Path filePath, String horizon, String checksum, String area, TrajectoryType trajectoryType) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? 
                userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;

        return TrajectoryEntity.builder()
                .fileName(fileName)
                .fileSize(Files.size(filePath))
                .checksum(checksum)
                .type(trajectoryType.name())
                .horizon(horizon)
                .area(area)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
                        ZoneId.systemDefault()))
                .createdBy(createdBy)
                .hasTimeSeries(true)
                .build();
    }

    /**
     * Validate path from trajectory root for security
     */
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
}

