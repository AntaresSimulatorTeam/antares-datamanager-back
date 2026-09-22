package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
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
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroParametersMeFileProcessorServiceImpl implements HydroParametersMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final HydroParametersMeRepository hydroParametersMeRepository;
    private final HydroAllocationMeRepository hydroAllocationMeRepository;
    private final AreaRepository areaRepository;
    private final UserService userService;
    private final PathSecurityUtil pathSecurityUtil;


    private static final String PARAM_HYDRO_ME_FILE = "param_hydro_ME.xlsx";
    private static final String HYDRO_ALLOCATION_ME_FILE = "hydroAllocation_ME.xlsx";

    @Transactional
    @Override
    public TrajectoryEntity processHydroParametersMeDirectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveHydroParametersMeTrajectoryInDb(trajectoryToUse, horizon, studyId);
    }

    public TrajectoryEntity saveHydroParametersMeTrajectoryInDb(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        Path trajectoryDir =  pathSecurityUtil.resolveSafePath(
                properties -> Path.of(properties.getNasDirectory(), properties.getTrajectoryFilePath(), properties.getHydroParametersMeDirectory(), trajectoryToUse)
        );
        Path paramHydroPath = trajectoryDir.resolve(PARAM_HYDRO_ME_FILE);
        Path hydroAllocationPath = trajectoryDir.resolve(HYDRO_ALLOCATION_ME_FILE);

        // Validate files exist
        if (!Files.exists(paramHydroPath)) {
            throw BusinessException.builder()
                    .message("Missing required file: {0}")
                    .errorMessageArguments(List.of(PARAM_HYDRO_ME_FILE))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (!Files.exists(hydroAllocationPath)) {
            throw BusinessException.builder()
                    .message("Missing required file: {0}")
                    .errorMessageArguments(List.of(HYDRO_ALLOCATION_ME_FILE))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check if already exists with same content
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.HYDRO_PARAMETERS_ME.name());

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if (isSameFileWithSameContent(trajectoryDir, existingTrajectory)) {
                throw BusinessException.builder()
                        .message("File already processed with same content : {0}")
                        .errorMessageArguments(List.of(trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            TrajectoryEntity newTrajectory = buildNewHydroParametersMeTrajectory(trajectoryToUse, horizon, trajectoryDir, userNni);
            newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
            TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
            
            // Parse and insert data in single pass for each file
            parseAndInsertParamHydroMe(paramHydroPath, trajectoryToUse, horizon, savedTrajectory.getId());
            parseAndInsertHydroAllocationMe(hydroAllocationPath, trajectoryToUse, horizon, savedTrajectory.getId(), studyId);
            
            return savedTrajectory;
        }

        // New trajectory
        TrajectoryEntity newTrajectory = buildNewHydroParametersMeTrajectory(trajectoryToUse, horizon, trajectoryDir, userNni);
        TrajectoryEntity savedTrajectory = trajectoryRepository.save(newTrajectory);
        
        // Parse and insert data in single pass for each file
        parseAndInsertParamHydroMe(paramHydroPath, trajectoryToUse, horizon, savedTrajectory.getId());
        parseAndInsertHydroAllocationMe(hydroAllocationPath, trajectoryToUse, horizon, savedTrajectory.getId(), studyId);
        
        return savedTrajectory;
    }

    private void parseAndInsertParamHydroMe(Path filePath, String trajectoryName, String horizon, Integer trajectoryId) throws IOException {
        String horizonYear = extractHorizonYear(horizon);
        List<HydroParametersMeEntity> entitiesToInsert = new ArrayList<>();

        try (InputStream fis = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Check if sheet exists for horizon
            Sheet sheet = workbook.getSheet(horizonYear);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in HYDRO_ME Param Hydro trajectory {1}")
                        .errorMessageArguments(List.of(horizonYear, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Single pass: validate and collect data
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // Validate Node column (A) - must be filled
                Cell nodeCell = row.getCell(0);
                String nodeName = getCellStringValue(nodeCell);
                if (nodeName == null || nodeName.trim().isEmpty()) {
                    throw BusinessException.builder()
                            .message("Node column must be filled in HYDRO_ME Param Hydro trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                // Validate Node length <= 60 characters
                if (nodeName.length() > 60) {
                    throw BusinessException.builder()
                            .message("Node cannot exceed 60 characters in HYDRO_ME Param Hydro trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                // Validate numeric columns: D (inter.daily.breakdown), E (inter.monthly.breakdown), F (initialize.reservoir.date), I (pumping.efficiency)
                validateNumericColumn(row, 3, "inter.daily.modulation", trajectoryName);
                validateNumericColumn(row, 4, "inter.monthly.breakdown", trajectoryName);
                validateNumericColumn(row, 5, "initialize.reservoir.date", trajectoryName);
                validateNumericColumn(row, 8, "pumping.efficiency", trajectoryName);

                // Validate boolean columns
                validateBooleanColumn(row, 6, "reservoir management", trajectoryName);
                validateBooleanColumn(row, 7, "follow.load", trajectoryName);
                validateBooleanColumn(row, 8, "use.heuristic", trajectoryName);
                validateBooleanColumn(row, 9, "use.water", trajectoryName);
                validateBooleanColumn(row, 10, "hard.bounds", trajectoryName);
                validateBooleanColumn(row, 11, "power.to.level", trajectoryName);

                // Build entity and add to list for batch insert
                HydroParametersMeEntity entity = HydroParametersMeEntity.builder()
                        .trajectoryId(trajectoryId)
                        .node(nodeName)
                        .interMonthlyCorrelation(getNumericCellValue(row.getCell(1)))
                        .interDailyBreakdown(getNumericCellValue(row.getCell(2)))
                        .intraDailyModulation(getNumericCellValue(row.getCell(3)))
                        .interMonthlyBreakdown(getNumericCellValue(row.getCell(4)))
                        .initializeReservoirDate(getIntCellValue(row.getCell(5)))
                        .leewayLow(getNumericCellValue(row.getCell(6)))
                        .leewayUp(getNumericCellValue(row.getCell(7)))
                        .pumpingEfficiency(getNumericCellValue(row.getCell(8)))
                        .reservoirManagement(getBooleanCellValue(row.getCell(9)))
                        .followLoad(getBooleanCellValue(row.getCell(10)))
                        .useHeuristic(getBooleanCellValue(row.getCell(11)))
                        .useWater(getBooleanCellValue(row.getCell(12)))
                        .hardBounds(getBooleanCellValue(row.getCell(13)))
                        .useLeeway(getBooleanCellValue(row.getCell(14)))
                        .powerToLevel(getBooleanCellValue(row.getCell(15)))
                        .build();
                
                entitiesToInsert.add(entity);
            }
        }

        // Batch insert all validated entities
        if (!entitiesToInsert.isEmpty()) {
            hydroParametersMeRepository.saveAll(entitiesToInsert);
            log.info("Inserted {} hydro parameters for trajectory {}", entitiesToInsert.size(), trajectoryName);
        }
    }

    private void parseAndInsertHydroAllocationMe(Path filePath, String trajectoryName, String horizon, Integer trajectoryId, Integer studyId) throws IOException {
        String horizonYear = extractHorizonYear(horizon);
        List<HydroAllocationMeEntity> entitiesToInsert = new ArrayList<>();

        try (InputStream fis = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Check if sheet exists for horizon
            Sheet sheet = workbook.getSheet(horizonYear);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Missing horizon {0} in HYDRO_ME Param Allocation trajectory {1}")
                        .errorMessageArguments(List.of(horizonYear, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Get header row to extract node names
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.builder()
                        .message("Missing header row in HYDRO_ME Param Allocation trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Load all valid areas and nodes for validation
            List<String> validAreas = areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA.toString())
                    .stream()
                    .map(area -> area.getName().trim().toLowerCase(Locale.ROOT))
                    .toList();

            List<HydroParametersMeEntity> hydroParams = hydroParametersMeRepository.findByTrajectoryId(trajectoryId);
            Set<String> validNodes = hydroParams.stream()
                    .map(param -> param.getNode().trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            Set<String> missingAreasOrNodes = new HashSet<>();
            Set<String> missingNodesInHeader = new HashSet<>();

            // Single pass: validate and collect data
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // Validate load column (A) - must be filled
                Cell loadCell = row.getCell(0);
                String areaName = getCellStringValue(loadCell);
                if (areaName == null || areaName.trim().isEmpty()) {
                    throw BusinessException.builder()
                            .message("load column must be filled in HYDRO_ME Param Allocation trajectory {0}")
                            .errorMessageArguments(List.of(trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                // RG1: Validate that area/node from load column exists in AREA or HYDRO_ME Param
                String area = areaName.trim().toLowerCase(Locale.ROOT);
                if (!validAreas.contains(area) && !validNodes.contains(area)) {
                    missingAreasOrNodes.add(area);
                }

                // Validate node columns (from B onwards) are numeric and build entities
                for (int cellIndex = 1; cellIndex < row.getLastCellNum(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        if (!isNumeric(cell)) {
                            String columnLetter = getCellColumnLetter(cellIndex);
                            throw BusinessException.builder()
                                    .message("Column {0} must be numeric in HYDRO_ME Param Allocation trajectory {1}")
                                    .errorMessageArguments(List.of(columnLetter, trajectoryName))
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                        if(getNumericCellValue(cell) != null && getNumericCellValue(cell).compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }

                        // Get node name from header
                        String nodeName = getCellStringValue(headerRow.getCell(cellIndex));

                        // RG2: Validate that node from header is present in HYDRO_ME Param
                        if (!validNodes.contains(nodeName.trim().toLowerCase(Locale.ROOT))) {
                            missingNodesInHeader.add(nodeName.trim());
                        }

                        // Build entity
                        HydroAllocationMeEntity entity = HydroAllocationMeEntity.builder()
                                .trajectoryId(trajectoryId)
                                .area(areaName)
                                .node(nodeName)
                                .allocationCoefficient(getNumericCellValue(cell))
                                .build();

                        entitiesToInsert.add(entity);
                    }
                }
            }

            // Throw error if RG1 validation failed
            if (!missingAreasOrNodes.isEmpty()) {
                String missingNames = String.join(", ", missingAreasOrNodes);
                throw BusinessException.builder()
                        .message("Missing Areas/nodes {0} in AREA or HYDRO_ME Param trajectory in HYDRO_ME Param Allocation trajectory {1}")
                        .errorMessageArguments(List.of(missingNames, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Throw error if RG2 validation failed
            if (!missingNodesInHeader.isEmpty()) {
                String missingNames = String.join(", ", missingNodesInHeader);
                throw BusinessException.builder()
                        .message("Missing Nodes {0} in HYDRO_ME Param trajectory in HYDRO_ME Param Allocation trajectory {1}")
                        .errorMessageArguments(List.of(missingNames, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }

        // Batch insert all validated entities
        if (!entitiesToInsert.isEmpty()) {
            hydroAllocationMeRepository.saveAll(entitiesToInsert);
            log.info("Inserted {} hydro allocations for trajectory {}", entitiesToInsert.size(), trajectoryName);
        }
    }

    private void validateNumericColumn(Row row, int cellIndex, String columnName, String trajectoryName) {
        Cell cell = row.getCell(cellIndex);
        if (cell != null && cell.getCellType() != CellType.BLANK) {
            if (!isNumeric(cell)) {
                throw BusinessException.builder()
                        .message("Column {0} must be numeric in HYDRO_ME Param Hydro trajectory {1}")
                        .errorMessageArguments(List.of(columnName, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    private void validateBooleanColumn(Row row, int cellIndex, String columnName, String trajectoryName) {
        Cell cell = row.getCell(cellIndex);
        if (cell != null && cell.getCellType() != CellType.BLANK) {
            String value = getCellStringValue(cell).toLowerCase().trim();
            if (!value.isEmpty() && !value.matches("^(true|false|0|1|yes|no)$")) {
                throw BusinessException.builder()
                        .message("Column {0} must be boolean in HYDRO_ME Param Hydro trajectory {1}")
                        .errorMessageArguments(List.of(columnName, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    private String extractHorizonYear(String horizon) {
        // horizon format: YYYY-YYYY+1, extract YYYY+1
        String[] parts = horizon.split("-");
        if (parts.length == 2) {
            return parts[1];
        }
        return horizon;
    }

    private TrajectoryEntity buildNewHydroParametersMeTrajectory(String trajectoryName, String horizon, Path trajectoryDir, String userNni) throws IOException {
        return TrajectoryEntity.builder()
                .fileName(trajectoryName)
                .fileSize(Files.size(trajectoryDir))
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryDir).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryDir, TrajectoryType.HYDRO_PARAMETERS_ME, horizon, null))
                .type(TrajectoryType.HYDRO_PARAMETERS_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }


    private boolean isNumeric(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return true;
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                Double.parseDouble(cell.getStringCellValue());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((int) cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return "";
    }

    private BigDecimal getNumericCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue();
                if (value.isEmpty()) return null;
                return new BigDecimal(value);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private Integer getIntCellValue(Cell cell) {
        BigDecimal decimal = getNumericCellValue(cell);
        return decimal != null ? decimal.intValue() : null;
    }

    private Boolean getBooleanCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        String value = getCellStringValue(cell).toLowerCase().trim();
        if (value.isEmpty()) return null;
        return value.matches("^(true|1|yes)$");
    }

    private String getCellColumnLetter(int cellIndex) {
        StringBuilder sb = new StringBuilder();
        int temp = cellIndex;
        while (temp >= 0) {
            sb.insert(0, (char) ('A' + (temp % 26)));
            temp = temp / 26 - 1;
        }
        return sb.toString();
    }
}
