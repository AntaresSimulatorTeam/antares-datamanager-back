package com.rte_france.antares.datamanager_back.service.constraint_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.constraint_me.ConstraintMeFileProcessorService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConstraintMeFileProcessorServiceImpl implements ConstraintMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final GroupAreaDescRepository groupAreaDescRepository;
    private final GroupClusterDescRepository groupClusterDescRepository;
    private final MeConstraintRepository meConstraintRepository;

    @Transactional
    @Override
    public TrajectoryEntity processConstraintMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveConstraintMeTrajectoryInDb(trajectoryToUse, horizon);
    }

    public TrajectoryEntity saveConstraintMeTrajectoryInDb(String trajectoryToUse, String horizon) throws IOException {
        if (horizon == null) {
            throw BusinessException.builder()
                    .message("Horizon must not be null")
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
            
            validateConstraintMeExcelFile(workbook, trajectoryToUse, horizon);
            
            Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                    .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.CONSTRAINT_ME.name());

            if (existingTrajectoryOpt.isPresent()) {
                TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
                if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                    throw BusinessException.builder()
                            .message("File already processed with same content {0}")
                            .errorMessageArguments(List.of(trajectoryToUse))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                TrajectoryEntity newTrajectory = buildNewConstraintMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
                newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
                TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
                insertConstraintMeData(workbook, horizon, savedTrajectory);
                return savedTrajectory;
            }

            TrajectoryEntity newTrajectory = buildNewConstraintMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
            insertConstraintMeData(workbook, horizon, savedTrajectory);
            return savedTrajectory;
        }
    }

    private void insertConstraintMeData(Workbook workbook, String horizon, TrajectoryEntity trajectory) throws IOException {
        String horizonYear = horizon.matches("^\\d{4}-\\d{4}$") ? horizon.split("-")[1] : horizon;
        
        processListAreaDescSheet(workbook, trajectory);
        processListClusterDescSheet(workbook, trajectory);
        processHorizonSheet(workbook, horizonYear, trajectory);
    }

    private void processListAreaDescSheet(Workbook workbook, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet("listArea_desc");
        if (sheet == null) return;

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return;

        int lastCellNum = headerRow.getLastCellNum();
        
        for (int colIdx = 0; colIdx < lastCellNum; colIdx++) {
            String groupName = getCellStringValue(headerRow, colIdx);
            if (groupName == null || groupName.trim().isEmpty()) continue;

            GroupAreaDescEntity groupAreaDesc = GroupAreaDescEntity.builder()
                    .groupName(groupName)
                    .trajectory(trajectory)
                    .areas(new ArrayList<>())
                    .build();
            groupAreaDesc = groupAreaDescRepository.save(groupAreaDesc);

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                
                String area = getCellStringValue(row, colIdx);
                if (area != null && !area.trim().isEmpty()) {
                    ListAreaDescEntity listAreaDesc = ListAreaDescEntity.builder()
                            .area(area.trim())
                            .groupArea(groupAreaDesc)
                            .build();
                    groupAreaDesc.getAreas().add(listAreaDesc);
                }
            }

            if (!groupAreaDesc.getAreas().isEmpty()) {
                groupAreaDescRepository.save(groupAreaDesc);
            }
        }
    }

    private void processListClusterDescSheet(Workbook workbook, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet("listCluster_desc");
        if (sheet == null) return;

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return;

        int lastCellNum = headerRow.getLastCellNum();
        
        for (int colIdx = 0; colIdx < lastCellNum; colIdx++) {
            String groupName = getCellStringValue(headerRow, colIdx);
            if (groupName == null || groupName.trim().isEmpty()) continue;

            GroupClusterDescEntity groupClusterDesc = GroupClusterDescEntity.builder()
                    .groupName(groupName)
                    .trajectory(trajectory)
                    .clusters(new ArrayList<>())
                    .build();
            groupClusterDesc = groupClusterDescRepository.save(groupClusterDesc);

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                
                String cluster = getCellStringValue(row, colIdx);
                if (cluster != null && !cluster.trim().isEmpty()) {
                    ListClusterDescEntity listClusterDesc = ListClusterDescEntity.builder()
                            .cluster(cluster.trim())
                            .groupCluster(groupClusterDesc)
                            .build();
                    groupClusterDesc.getClusters().add(listClusterDesc);
                }
            }

            if (!groupClusterDesc.getClusters().isEmpty()) {
                groupClusterDescRepository.save(groupClusterDesc);
            }
        }
    }

    private void processHorizonSheet(Workbook workbook, String horizonYear, TrajectoryEntity trajectory) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (sheet == null) return;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            
            String name = getCellStringValue(row, 0);
            if (name == null || name.trim().isEmpty()) continue;
            
            MeConstraintEntity constraint = MeConstraintEntity.builder()
                    .name(name)
                    .enabled("YES".equalsIgnoreCase(getCellStringValue(row, 1)))
                    .sign(getCellStringValue(row, 2))
                    .temporality(getCellStringValue(row, 3))
                    .type(getCellStringValue(row, 4))
                    .comments(getCellStringValue(row, 5))
                    .noeud1Gauche(getCellStringValue(row, 6))
                    .noeud2Gauche(getCellStringValue(row, 7))
                    .clusterGauche(getCellStringValue(row, 8))
                    .noeud1Droite(getCellStringValue(row, 9))
                    .noeud2Droite(getCellStringValue(row, 10))
                    .clusterDroite(getCellStringValue(row, 11))
                    .trajectory(trajectory)
                    .build();
            
            meConstraintRepository.save(constraint);
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

    private Path buildTrajectoryPath(String trajectoryToUse) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = antaresDataManagerProperties.getConstraintMeDirectory();

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

    private void validateConstraintMeExcelFile(Workbook workbook, String trajectoryName, String horizon) throws IOException {
        List<String> missingTabs = new ArrayList<>();
        
        if (workbook.getSheet("listArea_desc") == null) {
            missingTabs.add("listArea_desc");
        }
        if (workbook.getSheet("listCluster_desc") == null) {
            missingTabs.add("listCluster_desc");
        }
        
        String horizonYearPlus1 = String.valueOf(Integer.parseInt(horizon.split("-")[1]));
        if (workbook.getSheet(horizonYearPlus1) == null) {
            missingTabs.add(horizonYearPlus1);
        }
        
        if (!missingTabs.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing tab {0} in CONSTRAINTS_ME trajectory {1}")
                    .errorMessageArguments(List.of(String.join(", ", missingTabs), trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        validateListAreaDescTab(workbook, trajectoryName);
        validateListClusterDescTab(workbook, trajectoryName);
        validateHorizonTab(workbook, horizonYearPlus1, trajectoryName);
    }

    private void validateListAreaDescTab(Workbook workbook, String trajectoryName) {
        Sheet sheet = workbook.getSheet("listArea_desc");
        if (isSheetEmpty(sheet)) {
            throw BusinessException.builder()
                    .message("listArea_desc Tab can't be empty in CONSTRAINTS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        List<String> emptyColumns = new ArrayList<>();
        for (int colIdx = 0; colIdx < sheet.getRow(0).getLastCellNum(); colIdx++) {
            String columnHeader = sheet.getRow(0).getCell(colIdx).getStringCellValue();
            if (columnHeader != null && !columnHeader.trim().isEmpty()) {
                boolean hasData = false;
                for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row != null && row.getCell(colIdx) != null) {
                        hasData = true;
                        break;
                    }
                }
                if (!hasData) {
                    emptyColumns.add(columnHeader);
                }
            }
        }
        
        if (!emptyColumns.isEmpty()) {
            throw BusinessException.builder()
                    .message("{0} column in listArea_desc tab can't be empty in CONSTRAINTS_ME trajectory {1}")
                    .errorMessageArguments(List.of(String.join(", ", emptyColumns), trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateListClusterDescTab(Workbook workbook, String trajectoryName) {
        Sheet sheet = workbook.getSheet("listCluster_desc");
        if (isSheetEmpty(sheet)) {
            throw BusinessException.builder()
                    .message("listCluster_desc Tab can't be empty in CONSTRAINTS_ME trajectory {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateHorizonTab(Workbook workbook, String horizonYear, String trajectoryName) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (isSheetEmpty(sheet)) {
            throw BusinessException.builder()
                    .message("{0} Tab can't be empty in CONSTRAINTS_ME trajectory {1}")
                    .errorMessageArguments(List.of(horizonYear, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
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
        String newChecksum = computeChecksumByType(trajectoryPath, TrajectoryType.CONSTRAINT_ME, existingTrajectory.getHorizon(), null);
        return newChecksum.equals(existingTrajectory.getChecksum());
    }

    private TrajectoryEntity buildNewConstraintMeTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
       return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryPath))
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryPath, TrajectoryType.CONSTRAINT_ME, horizon, null))
                .type(TrajectoryType.CONSTRAINT_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }
}
