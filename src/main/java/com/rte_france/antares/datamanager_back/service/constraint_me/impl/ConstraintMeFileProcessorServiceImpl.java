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

    // Excel sheet names
    private static final String SHEET_LIST_AREA_DESC = "listArea_desc";
    private static final String SHEET_LIST_CLUSTER_DESC = "listCluster_desc";
    private static final String FILE_EXTENSION = ".xlsx";
    
    // Column indices for constraint data
    private static final int COL_NAME = 0;
    private static final int COL_ENABLED = 1;
    private static final int COL_SIGN = 2;
    private static final int COL_TEMPORALITY = 3;
    private static final int COL_TYPE = 4;
    private static final int COL_COMMENTS = 5;
    private static final int COL_NOEUD1_GAUCHE = 6;
    private static final int COL_NOEUD2_GAUCHE = 7;
    private static final int COL_CLUSTER_GAUCHE = 8;
    private static final int COL_NOEUD1_DROITE = 9;
    private static final int COL_NOEUD2_DROITE = 10;
    private static final int COL_CLUSTER_DROITE = 11;
    
    // Validation messages
    private static final String MSG_HORIZON_REQUIRED = "Horizon must not be null";
    private static final String MSG_USER_NNI_REQUIRED = "User NNI could not be determined";
    private static final String MSG_FILE_ALREADY_PROCESSED = "File already processed with same content {0}";
    private static final String MSG_CONFIG_INCOMPLETE = "Antares path configuration is incomplete";
    private static final String MSG_PATH_TRAVERSAL = "Path is outside of the target directory";
    private static final String MSG_MISSING_TABS = "Missing tab {0} in CONSTRAINTS_ME trajectory {1}";
    private static final String MSG_AREA_NOT_IN_STUDY = "Area {0} in group {1} does not exist in study areas";
    private static final String MSG_NODE_MUST_BELONG_AREA_ME = "Node {0} in {1} tab must belong to AREA_ME trajectory";
    private static final String MSG_NODE_MUST_BELONG_AREA_DESC = "Node {0} in {1} tab must belong to listArea_desc tab";
    private static final String MSG_CLUSTER_MUST_BELONG_DESC = "Cluster {0} in {1} tab must belong to listCluster_desc tab";
    private static final String MSG_TAB_EMPTY = "{0} Tab cant be empty in CONSTRAINTS_ME trajectory {1}";
    private static final String MSG_COLUMN_EMPTY = "{0} column in listArea_desc tab can't be empty in CONSTRAINTS_ME trajectory {1}";
    private static final String ENABLED_VALUE = "YES";
    private static final String HORIZON_PATTERN = "^\\d{4}-\\d{4}$";

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final GroupAreaDescRepository groupAreaDescRepository;
    private final GroupClusterDescRepository groupClusterDescRepository;
    private final MeConstraintRepository meConstraintRepository;
    private final AreaRepository areaRepository;

    @Transactional(rollbackFor = {IOException.class})
    @Override
    public TrajectoryEntity processConstraintMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveConstraintMeTrajectoryInDb(trajectoryToUse, horizon, studyId);
    }

    public TrajectoryEntity saveConstraintMeTrajectoryInDb(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        if (horizon == null) {
            throw BusinessException.builder()
                    .message(MSG_HORIZON_REQUIRED)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message(MSG_USER_NNI_REQUIRED)
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse);
        
        try (InputStream fis = Files.newInputStream(trajectoryPath);
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            validateConstraintMeExcelFile(workbook, trajectoryToUse, horizon);
            
            Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                    .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.CONSTRAINT_ME.name());

            if (existingTrajectoryOpt.isPresent()) {
                return processExistingTrajectory(existingTrajectoryOpt.get(), trajectoryToUse, horizon, 
                                               trajectoryPath, userNni, workbook, studyId);
            }

            return processNewTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni, workbook, studyId);
        }
    }
    
    private TrajectoryEntity processExistingTrajectory(TrajectoryEntity existingTrajectory, String trajectoryToUse, 
                                                      String horizon, Path trajectoryPath, String userNni, 
                                                      Workbook workbook, Integer studyId) throws IOException {
        if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
            throw BusinessException.builder()
                    .message(MSG_FILE_ALREADY_PROCESSED)
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        TrajectoryEntity newTrajectory = buildNewConstraintMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
        TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
        insertConstraintMeData(workbook, horizon, savedTrajectory, studyId);
        return savedTrajectory;
    }
    
    private TrajectoryEntity processNewTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, 
                                                 String userNni, Workbook workbook, Integer studyId) throws IOException {
        TrajectoryEntity newTrajectory = buildNewConstraintMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
        insertConstraintMeData(workbook, horizon, savedTrajectory, studyId);
        return savedTrajectory;
    }

    private void insertConstraintMeData(Workbook workbook, String horizon, TrajectoryEntity trajectory, Integer studyId) throws IOException {
        String horizonYear = extractHorizonYear(horizon);
        
        Set<String> groupAreaNames = processListAreaDescSheet(workbook, trajectory, studyId);
        Set<String> groupClusterNames = processListClusterDescSheet(workbook, trajectory);
        processHorizonSheet(workbook, horizonYear, trajectory, studyId, groupAreaNames, groupClusterNames);
    }

    private Set<String> processListAreaDescSheet(Workbook workbook, TrajectoryEntity trajectory, Integer studyId) {
        Set<String> groupAreaNames = new HashSet<>();
        Sheet sheet = workbook.getSheet(SHEET_LIST_AREA_DESC);
        if (sheet == null) return groupAreaNames;

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return groupAreaNames;

        Set<String> studyAreaNames = buildStudyAreaNameSet(studyId);
        int lastCellNum = headerRow.getLastCellNum();
        
        for (int colIdx = 0; colIdx < lastCellNum; colIdx++) {
            String groupName = getCellStringValue(headerRow, colIdx);
            if (isEmptyString(groupName)) continue;

            GroupAreaDescEntity groupAreaDesc = createGroupAreaDesc(groupName, trajectory);
            groupAreaDescRepository.save(groupAreaDesc);
            groupAreaNames.add(groupName.toLowerCase(Locale.ROOT));

            processAreaDescColumn(sheet, colIdx, groupAreaDesc, studyAreaNames, groupName);

            if (!groupAreaDesc.getAreas().isEmpty()) {
                groupAreaDescRepository.save(groupAreaDesc);
            }
        }
        
        return groupAreaNames;
    }
    
    private Set<String> buildStudyAreaNameSet(Integer studyId) {
        Set<String> studyAreaNames = new HashSet<>();
        areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA.name())
                .forEach(area -> studyAreaNames.add(area.getName().toLowerCase(Locale.ROOT)));
        return studyAreaNames;
    }
    
    private GroupAreaDescEntity createGroupAreaDesc(String groupName, TrajectoryEntity trajectory) {
        return GroupAreaDescEntity.builder()
                .groupName(groupName)
                .trajectory(trajectory)
                .areas(new ArrayList<>())
                .build();
    }
    
    private void processAreaDescColumn(Sheet sheet, int colIdx, GroupAreaDescEntity groupAreaDesc, 
                                       Set<String> studyAreaNames, String groupName) {
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            
            String area = getCellStringValue(row, colIdx);
            if (isEmptyString(area)) continue;
            
            String trimmedArea = area.trim().toLowerCase(Locale.ROOT);
            if (!studyAreaNames.contains(trimmedArea)) {
                throw BusinessException.builder()
                        .message(MSG_AREA_NOT_IN_STUDY)
                        .errorMessageArguments(List.of(area, groupName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            groupAreaDesc.getAreas().add(createListAreaDesc(trimmedArea, groupAreaDesc));
        }
    }
    
    private ListAreaDescEntity createListAreaDesc(String area, GroupAreaDescEntity groupAreaDesc) {
        return ListAreaDescEntity.builder()
                .area(area)
                .groupArea(groupAreaDesc)
                .build();
    }

    private Set<String> processListClusterDescSheet(Workbook workbook, TrajectoryEntity trajectory) {
        Set<String> groupClusterNames = new HashSet<>();
        Sheet sheet = workbook.getSheet(SHEET_LIST_CLUSTER_DESC);
        if (sheet == null) return groupClusterNames;

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return groupClusterNames;

        int lastCellNum = headerRow.getLastCellNum();
        
        for (int colIdx = 0; colIdx < lastCellNum; colIdx++) {
            String groupName = getCellStringValue(headerRow, colIdx);
            if (isEmptyString(groupName)) continue;

            GroupClusterDescEntity groupClusterDesc = createGroupClusterDesc(groupName, trajectory);
            groupClusterDescRepository.save(groupClusterDesc);
            groupClusterNames.add(groupName.toLowerCase(Locale.ROOT));

            processClusterDescColumn(sheet, colIdx, groupClusterDesc);

            if (!groupClusterDesc.getClusters().isEmpty()) {
                groupClusterDescRepository.save(groupClusterDesc);
            }
        }
        
        return groupClusterNames;
    }
    
    private GroupClusterDescEntity createGroupClusterDesc(String groupName, TrajectoryEntity trajectory) {
        return GroupClusterDescEntity.builder()
                .groupName(groupName)
                .trajectory(trajectory)
                .clusters(new ArrayList<>())
                .build();
    }
    
    private void processClusterDescColumn(Sheet sheet, int colIdx, GroupClusterDescEntity groupClusterDesc) {
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            
            String cluster = getCellStringValue(row, colIdx);
            if (isEmptyString(cluster)) continue;
            
            groupClusterDesc.getClusters().add(createListClusterDesc(cluster.trim(), groupClusterDesc));
        }
    }
    
    private ListClusterDescEntity createListClusterDesc(String cluster, GroupClusterDescEntity groupClusterDesc) {
        return ListClusterDescEntity.builder()
                .cluster(cluster)
                .groupCluster(groupClusterDesc)
                .build();
    }

    private void processHorizonSheet(Workbook workbook, String horizonYear, TrajectoryEntity trajectory, Integer studyId, 
                                    Set<String> groupAreaNames, Set<String> groupClusterNames) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (sheet == null) return;

        Set<String> areaMeNodes = extractNodesFromAreaMeTrajectory(studyId);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            
            String name = getCellStringValue(row, COL_NAME);
            if (isEmptyString(name)) continue;
            
            ConstraintRowData constraintData = extractConstraintRowData(row);
            
            validateConstraintData(constraintData, areaMeNodes, groupAreaNames, groupClusterNames, horizonYear);
            saveConstraint(constraintData, name, trajectory);
        }
    }
    
    private ConstraintRowData extractConstraintRowData(Row row) {
        return ConstraintRowData.builder()
                .enabled(ENABLED_VALUE.equalsIgnoreCase(getCellStringValue(row, COL_ENABLED)))
                .sign(getCellStringValue(row, COL_SIGN))
                .temporality(getCellStringValue(row, COL_TEMPORALITY))
                .type(getCellStringValue(row, COL_TYPE))
                .comments(getCellStringValue(row, COL_COMMENTS))
                .noeud1Gauche(getCellStringValue(row, COL_NOEUD1_GAUCHE))
                .noeud2Gauche(getCellStringValue(row, COL_NOEUD2_GAUCHE))
                .clusterGauche(getCellStringValue(row, COL_CLUSTER_GAUCHE))
                .noeud1Droite(getCellStringValue(row, COL_NOEUD1_DROITE))
                .noeud2Droite(getCellStringValue(row, COL_NOEUD2_DROITE))
                .clusterDroite(getCellStringValue(row, COL_CLUSTER_DROITE))
                .build();
    }
    
    private void validateConstraintData(ConstraintRowData data, Set<String> areaMeNodes, 
                                       Set<String> groupAreaNames, Set<String> groupClusterNames, String horizonYear) {
        validateNode(data.getNoeud1Gauche(), areaMeNodes, horizonYear, MSG_NODE_MUST_BELONG_AREA_ME);
        validateNode(data.getNoeud2Gauche(), areaMeNodes, horizonYear, MSG_NODE_MUST_BELONG_AREA_ME);
        validateCluster(data.getClusterGauche(), groupClusterNames, horizonYear, MSG_CLUSTER_MUST_BELONG_DESC);
        validateNode(data.getNoeud1Droite(), groupAreaNames, horizonYear, MSG_NODE_MUST_BELONG_AREA_DESC);
        validateNode(data.getNoeud2Droite(), groupAreaNames, horizonYear, MSG_NODE_MUST_BELONG_AREA_DESC);
        validateCluster(data.getClusterDroite(), groupClusterNames, horizonYear, MSG_CLUSTER_MUST_BELONG_DESC);
    }
    
    private void validateNode(String node, Set<String> validNodes, String horizonYear, String errorMsg) {
        if (!isEmptyString(node) && !validNodes.contains(node.trim().toLowerCase(Locale.ROOT))) {
            throw BusinessException.builder()
                    .message(errorMsg)
                    .errorMessageArguments(List.of(node.trim(), horizonYear))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
    
    private void validateCluster(String cluster, Set<String> validClusters, String horizonYear, String errorMsg) {
        if (!isEmptyString(cluster) && !validClusters.contains(cluster.trim().toLowerCase(Locale.ROOT))) {
            throw BusinessException.builder()
                    .message(errorMsg)
                    .errorMessageArguments(List.of(cluster.trim(), horizonYear))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
    
    private void saveConstraint(ConstraintRowData data, String name, TrajectoryEntity trajectory) {
        MeConstraintEntity constraint = MeConstraintEntity.builder()
                .name(name)
                .enabled(data.isEnabled())
                .sign(data.getSign())
                .temporality(data.getTemporality())
                .type(data.getType())
                .comments(data.getComments())
                .noeud1Gauche(data.getNoeud1Gauche())
                .noeud2Gauche(data.getNoeud2Gauche())
                .clusterGauche(data.getClusterGauche())
                .noeud1Droite(data.getNoeud1Droite())
                .noeud2Droite(data.getNoeud2Droite())
                .clusterDroite(data.getClusterDroite())
                .trajectory(trajectory)
                .build();
        
        meConstraintRepository.save(constraint);
    }

    private boolean isEmptyString(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    private String extractHorizonYear(String horizon) {
        if (horizon.matches(HORIZON_PATTERN)) {
            return horizon.split("-")[1];
        }
        return horizon;
    }

    private Set<String> extractNodesFromAreaMeTrajectory(Integer studyId) {
        Set<String> nodes = new HashSet<>();
        areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA_ME.name())
                .forEach(area -> nodes.add(area.getName().toLowerCase(Locale.ROOT)));
        return nodes;
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
                    .message(MSG_CONFIG_INCOMPLETE)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path baseDirectory = Path.of(nasDir)
                .resolve(trajFilePath)
                .resolve(directoryByType)
                .normalize();

        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse + FILE_EXTENSION).normalize();
        
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException(MSG_PATH_TRAVERSAL);
        }

        return trajectoryFilePath;
    }

    private void validateConstraintMeExcelFile(Workbook workbook, String trajectoryName, String horizon) throws IOException {
        List<String> missingTabs = collectMissingTabs(workbook, horizon);
        
        if (!missingTabs.isEmpty()) {
            throw BusinessException.builder()
                    .message(MSG_MISSING_TABS)
                    .errorMessageArguments(List.of(String.join(", ", missingTabs), trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        
        String horizonYear = extractHorizonYear(horizon);
        validateListAreaDescTab(workbook, trajectoryName);
        validateListClusterDescTab(workbook, trajectoryName);
        validateHorizonTab(workbook, horizonYear, trajectoryName);
    }
    
    private List<String> collectMissingTabs(Workbook workbook, String horizon) {
        List<String> missingTabs = new ArrayList<>();
        
        if (workbook.getSheet(SHEET_LIST_AREA_DESC) == null) {
            missingTabs.add(SHEET_LIST_AREA_DESC);
        }
        if (workbook.getSheet(SHEET_LIST_CLUSTER_DESC) == null) {
            missingTabs.add(SHEET_LIST_CLUSTER_DESC);
        }
        
        String horizonYear = extractHorizonYear(horizon);
        if (workbook.getSheet(horizonYear) == null) {
            missingTabs.add(horizonYear);
        }
        
        return missingTabs;
    }

    private void validateListAreaDescTab(Workbook workbook, String trajectoryName) {
        Sheet sheet = workbook.getSheet(SHEET_LIST_AREA_DESC);
        if (isSheetEmpty(sheet)) {
            throwSheetEmptyException(SHEET_LIST_AREA_DESC, trajectoryName);
        }
        
        List<String> emptyColumns = findEmptyColumns(sheet);
        if (!emptyColumns.isEmpty()) {
            throw BusinessException.builder()
                    .message(MSG_COLUMN_EMPTY)
                    .errorMessageArguments(List.of(String.join(", ", emptyColumns), trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateListClusterDescTab(Workbook workbook, String trajectoryName) {
        Sheet sheet = workbook.getSheet(SHEET_LIST_CLUSTER_DESC);
        if (isSheetEmpty(sheet)) {
            throwSheetEmptyException(SHEET_LIST_CLUSTER_DESC, trajectoryName);
        }
    }

    private void validateHorizonTab(Workbook workbook, String horizonYear, String trajectoryName) {
        Sheet sheet = workbook.getSheet(horizonYear);
        if (isSheetEmpty(sheet)) {
            throwSheetEmptyException(horizonYear, trajectoryName);
        }
    }
    
    private void throwSheetEmptyException(String sheetName, String trajectoryName) {
        throw BusinessException.builder()
                .message(MSG_TAB_EMPTY)
                .errorMessageArguments(List.of(sheetName, trajectoryName))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }
    
    private List<String> findEmptyColumns(Sheet sheet) {
        List<String> emptyColumns = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return emptyColumns;
        
        for (int colIdx = 0; colIdx < headerRow.getLastCellNum(); colIdx++) {
            String columnHeader = getCellStringValue(headerRow, colIdx);
            if (!isEmptyString(columnHeader) && !hasColumnData(sheet, colIdx)) {
                emptyColumns.add(columnHeader);
            }
        }
        
        return emptyColumns;
    }
    
    private boolean hasColumnData(Sheet sheet, int colIdx) {
        for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row != null && row.getCell(colIdx) != null) {
                return true;
            }
        }
        return false;
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
    
    // Helper class to encapsulate constraint row data
    @lombok.Data
    @lombok.Builder
    private static class ConstraintRowData {
        private boolean enabled;
        private String sign;
        private String temporality;
        private String type;
        private String comments;
        private String noeud1Gauche;
        private String noeud2Gauche;
        private String clusterGauche;
        private String noeud1Droite;
        private String noeud2Droite;
        private String clusterDroite;
    }
}
