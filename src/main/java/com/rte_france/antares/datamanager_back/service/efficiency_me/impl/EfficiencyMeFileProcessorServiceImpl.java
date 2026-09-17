package com.rte_france.antares.datamanager_back.service.efficiency_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.efficiency_me.EfficiencyMeFileProcessorService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EfficiencyMeFileProcessorServiceImpl implements EfficiencyMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final EfficiencyMeRepository efficiencyMeRepository;

    @Transactional
    @Override
    public TrajectoryEntity processEfficiencyMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveEfficiencyMeTrajectoryInDb(trajectoryToUse, horizon);
    }

    public TrajectoryEntity saveEfficiencyMeTrajectoryInDb(String trajectoryToUse, String horizon) throws IOException {
        if (horizon == null) {
            throw BusinessException.builder()
                    .message("Horizon must not be null")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (trajectoryToUse.length() > 40) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot exceed 40 characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

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
            
            validateEfficiencyMeExcelFile(workbook, trajectoryToUse, horizon);
            
            Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                    .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.EFFICIENCY_ME.name());

            if (existingTrajectoryOpt.isPresent()) {
                TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
                if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                    throw BusinessException.builder()
                            .message("File already processed with same content : {0}")
                            .errorMessageArguments(List.of(trajectoryToUse))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                TrajectoryEntity newTrajectory = buildNewEfficiencyMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
                newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
                TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
                insertEfficiencyMeData(workbook, horizon, savedTrajectory);
                return savedTrajectory;
            }

            TrajectoryEntity newTrajectory = buildNewEfficiencyMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
            insertEfficiencyMeData(workbook, horizon, savedTrajectory);
            return savedTrajectory;
        }
    }

    private void insertEfficiencyMeData(Workbook workbook, String horizon, TrajectoryEntity trajectory) throws IOException {
        String horizonYear = horizon.matches("^\\d{4}-\\d{4}$") ? horizon.split("-")[1] : horizon;
        processHorizonSheet(workbook, horizonYear, trajectory);
    }

    private void processHorizonSheet(Workbook workbook, String horizonYear, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (sheet == null) return;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            
            String nodeCluster = getCellStringValue(row, 0);
            if (nodeCluster == null || nodeCluster.trim().isEmpty()) continue;
            
            String type = getCellStringValue(row, 1);
            String comments = getCellStringValue(row, 2);
            BigDecimal efficiency = getCellNumericValue(row, 3);
            
            EfficiencyMeEntity efficiencyMe = EfficiencyMeEntity.builder()
                    .nodeCluster(nodeCluster.trim())
                    .type(type)
                    .comments(comments)
                    .efficiency(efficiency)
                    .trajectory(trajectory)
                    .build();
            
            efficiencyMeRepository.save(efficiencyMe);
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
        String directoryByType = antaresDataManagerProperties.getEfficiencyMeDirectory();

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

    private void validateEfficiencyMeExcelFile(Workbook workbook, String trajectoryName, String horizon) throws IOException {
        if (trajectoryName.length() > 40) {
            throw BusinessException.builder()
                    .message("Trajectory name cannot exceed 40 characters")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String horizonYearPlus1 = String.valueOf(Integer.parseInt(horizon.split("-")[1]));
        if (workbook.getSheet(horizonYearPlus1) == null) {
            throw BusinessException.builder()
                    .message("Missing horizon {0} in EFFICIENCY_ME trajectory {1}")
                    .errorMessageArguments(List.of(horizonYearPlus1, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        validateHorizonTab(workbook, horizonYearPlus1, trajectoryName);
    }

    private void validateHorizonTab(Workbook workbook, String horizonYear, String trajectoryName) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (isSheetEmpty(sheet)) {
            throw BusinessException.builder()
                    .message("Missing horizon {0} in EFFICIENCY_ME trajectory {1}")
                    .errorMessageArguments(List.of(horizonYear, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        validateSheetColumns(sheet, trajectoryName);
    }

    private void validateSheetColumns(Sheet sheet, String trajectoryName) {
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            
            String nodeCluster = getCellStringValue(row, 0);
            if (nodeCluster != null && !nodeCluster.trim().isEmpty()) {
                if (nodeCluster.length() > 60) {
                    throw BusinessException.builder()
                            .message("Node/Cluster cannot exceed 60 characters in EFFICIENCY_ME trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                
                BigDecimal efficiency = getCellNumericValue(row, 3);
                if (efficiency == null) {
                    throw BusinessException.builder()
                            .message("Column efficiency must be numeric in EFFICIENCY_ME trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            } else {
                boolean hasOtherData = false;
                for (int i = 1; i < 4; i++) {
                    String cellValue = getCellStringValue(row, i);
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        hasOtherData = true;
                        break;
                    }
                }
                if (hasOtherData) {
                    throw BusinessException.builder()
                            .message("Node/Cluster column must be filled in EFFICIENCY_ME trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
        }
    }

    private boolean isSheetEmpty(Sheet sheet) {
        if (sheet == null) {
            return true;
        }
        if (sheet.getLastRowNum() < 0) {
            return true;
        }
        for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null && row.getLastCellNum() > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameFileWithSameContent(Path trajectoryPath, TrajectoryEntity existingTrajectory) throws IOException {
        String newChecksum = computeChecksumByType(trajectoryPath, TrajectoryType.EFFICIENCY_ME, existingTrajectory.getHorizon(), null);
        return newChecksum.equals(existingTrajectory.getChecksum());
    }

    private TrajectoryEntity buildNewEfficiencyMeTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryPath))
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryPath, TrajectoryType.EFFICIENCY_ME, horizon, null))
                .type(TrajectoryType.EFFICIENCY_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }
}
