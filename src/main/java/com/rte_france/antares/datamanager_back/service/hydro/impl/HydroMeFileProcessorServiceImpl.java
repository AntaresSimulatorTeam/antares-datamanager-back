package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroMeFileProcessorServiceImpl implements HydroMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final HydroCapacityMeRepository hydroCapacityMeRepository;

    private static final String NODE_COLUMN = "Node";
    private static final String RESERVOIR_CAPACITY_COLUMN = "Reservoir Capacity [MWh]";
    private static final String GENERATING_PMAX_TIMESTEP_COLUMN = "Generating Pmax - timestep (daily/annual)";
    private static final String GENERATING_PMAX_COLUMN = "Generating Pmax [MW]";
    private static final String HOURS_GENERATING_PMAX_COLUMN = "hours at generating Pmax";
    private static final String PUMPING_PMAX_TIMESTEP_COLUMN = "Pumping Pmax - timestep (daily/annual)";
    private static final String PUMPING_PMAX_COLUMN = "Pumping Pmax [MW]";
    private static final String HOURS_PUMPING_PMAX_COLUMN = "hours at pumping Pmax";

    private static final String GENERATING_PMAX_DAILY_TS_DIR = "Generating Pmax daily ts";
    private static final String PUMPING_PMAX_DAILY_TS_DIR = "Pumping Pmax daily ts";

    @Transactional
    @Override
    public TrajectoryEntity processHydroCapacityMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveHydroCapacityMeTrajectoryInDb(trajectoryToUse, horizon);
    }

    public TrajectoryEntity saveHydroCapacityMeTrajectoryInDb(String trajectoryToUse, String horizon) throws IOException {

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse);
        
        try (InputStream fis = Files.newInputStream(trajectoryPath);
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            validateHydroCapacityMeExcelFile(workbook, trajectoryToUse, horizon);
            
            Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                    .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.HYDRO_CAPACITY_ME.name());

            if (existingTrajectoryOpt.isPresent()) {
                TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
                if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                    throw BusinessException.builder()
                            .message("File already processed with same content : {0}")
                            .errorMessageArguments(List.of(trajectoryToUse))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                TrajectoryEntity newTrajectory = buildNewHydroCapacityMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
                newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
                TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
                insertHydroCapacityMeData(workbook, horizon, trajectoryPath, savedTrajectory);
                return savedTrajectory;
            }

            TrajectoryEntity newTrajectory = buildNewHydroCapacityMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
            insertHydroCapacityMeData(workbook, horizon, trajectoryPath, savedTrajectory);
            return savedTrajectory;
        }
    }

    private void insertHydroCapacityMeData(Workbook workbook, String horizon, Path trajectoryPath, TrajectoryEntity trajectory) throws IOException {
        String horizonYear = horizon.matches("^\\d{4}-\\d{4}$") ? String.valueOf(Integer.parseInt(horizon.split("-")[0]) + 1) : horizon;
        processHorizonSheet(workbook, horizonYear, trajectory, trajectoryPath);
    }

    private void processHorizonSheet(Workbook workbook, String horizonYear, TrajectoryEntity trajectory, Path trajectoryPath) throws IOException {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (sheet == null) return;

        boolean hasGeneratingDaily = false;
        boolean hasPumpingDaily = false;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            
            String node = getCellStringValue(row, 0);
            String generatingTimestep = getCellStringValue(row, 2);
            String pumpingTimestep = getCellStringValue(row, 5);

            // Validation during single pass
            if (node != null && !node.trim().isEmpty()) {
                if (node.length() > 60) {
                    throw BusinessException.builder()
                            .message("Node column cannot exceed 60 characters in HYDRO_ME Capacity trajectory " + trajectory.getFileName())
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                validateNumericColumn(row, 1, RESERVOIR_CAPACITY_COLUMN, trajectory.getFileName());
                validateTimestepValue(generatingTimestep, GENERATING_PMAX_TIMESTEP_COLUMN, trajectory.getFileName());
                validateNumericColumn(row, 3, GENERATING_PMAX_COLUMN, trajectory.getFileName());
                validateNumericColumn(row, 4, HOURS_GENERATING_PMAX_COLUMN, trajectory.getFileName());
                validateTimestepValue(pumpingTimestep, PUMPING_PMAX_TIMESTEP_COLUMN, trajectory.getFileName());
                validateNumericColumn(row, 6, PUMPING_PMAX_COLUMN, trajectory.getFileName());
                validateNumericColumn(row, 7, HOURS_PUMPING_PMAX_COLUMN, trajectory.getFileName());

                // Track daily timesteps
                if ("daily".equalsIgnoreCase(generatingTimestep)) {
                    hasGeneratingDaily = true;
                }
                if ("daily".equalsIgnoreCase(pumpingTimestep)) {
                    hasPumpingDaily = true;
                }
                
                // Data extraction and persistence in same pass
                BigDecimal reservoirCapacity = getCellNumericValue(row, 1);
                BigDecimal generatingPmax = getCellNumericValue(row, 3);
                BigDecimal hoursAtGeneratingPmax = getCellNumericValue(row, 4);
                BigDecimal pumpingPmax = getCellNumericValue(row, 6);
                BigDecimal hoursAtPumpingPmax = getCellNumericValue(row, 7);
                
                HydroCapacityMeEntity hydroCapacityMe = HydroCapacityMeEntity.builder()
                        .node(node.trim())
                        .reservoirCapacity(reservoirCapacity)
                        .generatingPmaxTimestep(generatingTimestep)
                        .generatingPmax(generatingPmax)
                        .hoursAtGeneratingPmax(hoursAtGeneratingPmax)
                        .pumpingPmaxTimestep(pumpingTimestep)
                        .pumpingPmax(pumpingPmax)
                        .hoursAtPumpingPmax(hoursAtPumpingPmax)
                        .trajectory(trajectory)
                        .build();
                
                hydroCapacityMeRepository.save(hydroCapacityMe);
            } else {
                boolean hasOtherData = false;
                for (int i = 1; i < 8; i++) {
                    String cellValue = getCellStringValue(row, i);
                    if ((cellValue != null && !cellValue.trim().isEmpty())) {
                        hasOtherData = true;
                        break;
                    }
                }
                if (hasOtherData) {
                    throw BusinessException.builder()
                            .message("Node column must be filled in HYDRO_ME Capacity trajectory " + trajectory.getFileName())
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
        }

        // Validate time series directories
        if (hasGeneratingDaily) {
            validateTimeSeriesDirectory(trajectoryPath, GENERATING_PMAX_DAILY_TS_DIR, trajectory.getFileName());
        }
        if (hasPumpingDaily) {
            validateTimeSeriesDirectory(trajectoryPath, PUMPING_PMAX_DAILY_TS_DIR, trajectory.getFileName());
        }
    }

    private void validateTimeSeriesDirectory(Path trajectoryPath, String tsDirectoryName, String trajectoryName) throws IOException {
        Path tsDirectory = trajectoryPath.getParent().resolve(tsDirectoryName);
        if (!Files.exists(tsDirectory)) {
            throw BusinessException.builder()
                    .message("Missing " + trajectoryName + " for " + tsDirectoryName + " - timestep in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String timestepType = tsDirectoryName.contains("Generating") ? "Generating Pmax" : "Pumping Pmax";
        
        // Check if file matching trajectory name exists in the time series directory
        Path tsFile = tsDirectory.resolve(trajectoryName + ".xlsx");
        if (!Files.exists(tsFile)) {
            throw BusinessException.builder()
                    .message("Missing " + trajectoryName + " for " + timestepType + " - timestep in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private String getCellStringValue(Row row, int cellIndex) {
        if (row.getCell(cellIndex) == null) return null;
        try {
            return row.getCell(cellIndex).getStringCellValue();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getCellNumericValue(Row row, int cellIndex) {
        if (row.getCell(cellIndex) == null) return null;
        try {
            return BigDecimal.valueOf(row.getCell(cellIndex).getNumericCellValue());
        } catch (Exception e) {
            return null;
        }
    }

    private Path buildTrajectoryPath(String trajectoryToUse) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = antaresDataManagerProperties.getHydroCapacityMeDirectory();

        if (nasDir == null || trajFilePath == null || directoryByType == null) {
            throw BusinessException.builder()
                    .message("Antares path configuration is incomplete")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path baseDirectory = Path.of(nasDir)
                .resolve(trajFilePath)
                .resolve(directoryByType)
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse+".xlsx").normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }
            return trajectoryFilePath;
    }

    private void validateHydroCapacityMeExcelFile(Workbook workbook, String trajectoryName, String horizon) throws IOException {
        String horizonYear = String.valueOf(Integer.parseInt(horizon.split("-")[1]));
        if (workbook.getSheet(horizonYear) == null) {
            throw BusinessException.builder()
                    .message("Missing horizon " + horizonYear + " in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        Sheet sheet = workbook.getSheet(horizonYear);
        if (isSheetEmpty(sheet)) {
            throw BusinessException.builder()
                    .message("Missing horizon " + horizonYear + " in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateNumericColumn(Row row, int columnIndex, String columnName, String trajectoryName) {
        BigDecimal value = getCellNumericValue(row, columnIndex);
        if (value == null) {
            throw BusinessException.builder()
                    .message("Column " + columnName + " must be numeric in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateTimestepValue(String value, String columnName, String trajectoryName) {
        if (value == null || value.trim().isEmpty()) return;
        if (!("daily".equalsIgnoreCase(value) || "annual".equalsIgnoreCase(value))) {
            throw BusinessException.builder()
                    .message("Column " + columnName + " must be 'annual' or 'daily' only in HYDRO_ME Capacity trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private boolean isSheetEmpty(Sheet sheet) {
        return sheet == null || sheet.getLastRowNum() <= 0;
    }

    private boolean isSameFileWithSameContent(Path trajectoryPath, TrajectoryEntity existingTrajectory) throws IOException {
        String newChecksum = computeChecksumByType(trajectoryPath, TrajectoryType.HYDRO_CAPACITY_ME, existingTrajectory.getHorizon(), null);
        return newChecksum.equals(existingTrajectory.getChecksum());
    }

    private TrajectoryEntity buildNewHydroCapacityMeTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryPath))
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryPath, TrajectoryType.HYDRO_CAPACITY_ME, horizon, null))
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }
}
