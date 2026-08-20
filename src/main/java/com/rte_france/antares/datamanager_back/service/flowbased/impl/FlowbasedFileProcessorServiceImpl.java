package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.service.flowbased.FlowbasedFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedFileProcessorServiceImpl implements FlowbasedFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    private static final String[] REQUIRED_FILES = {
            "Flowbased_nodes_links.xlsx",
            "IdTypDays.csv",
            "second_member.txt",
            "weight.txt"
    };

    @Override
    public void validateRequiredFiles(Path trajectoryFilePath) {
        List<String> missingFiles = new ArrayList<>();

        for (String fileName : REQUIRED_FILES) {
            Path filePath = trajectoryFilePath.resolve(fileName);
            if (!Files.exists(filePath)) {
                missingFiles.add(fileName);
            }
        }

        if (!missingFiles.isEmpty()) {
            throw BusinessException.builder()
                    .message("Required files are missing: " + String.join(", ", missingFiles))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @Transactional
    @Override
    public TrajectoryEntity processFlowbasedFiles(Path trajectoryFilePath, String trajectoryToUse, Integer studyId, String horizon) {
        validateRequiredFiles(trajectoryFilePath);
        
        List<FlowbasedVirtualNodesEntity> virtualNodes = buildFlowbasedVirtualNodesList(trajectoryFilePath);
        List<FlowbasedLinkCapacityEntity> linkCapacities = buildFlowbasedLinkCapacityList(trajectoryFilePath);
        List<FlowbasedTypeDayEntity> typeDays = buildFlowbasedTypeDayList(trajectoryFilePath);

        String checksum;
        LocalDateTime lastModificationContentDate;
        try {
            checksum = Utils.calculateFlowbasedFilesChecksum(trajectoryFilePath);
            lastModificationContentDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Files.getLastModifiedTime(trajectoryFilePath).toMillis()),
                    ZoneId.systemDefault());
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Error processing flowbased files: " + e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        var existingTrajectory = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                trajectoryToUse,
                TrajectoryType.FLOWBASED.name(),
                horizon,
                null,
                null);


        TrajectoryEntity trajectory = createTrajectory(trajectoryToUse, checksum, lastModificationContentDate, horizon);
        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                throw BusinessException.builder()
                        .message("Trajectory already processed with same checksum")
                        .httpStatus(HttpStatus.CONFLICT)
                        .build();
            } else {
                trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else {
            trajectory.setVersion(1);
        }

        final TrajectoryEntity savedTrajectory = trajectoryRepository.save(trajectory);
        
        virtualNodes.forEach(vn -> vn.setTrajectory(savedTrajectory));
        linkCapacities.forEach(lc -> lc.setTrajectory(savedTrajectory));
        typeDays.forEach(td -> td.setTrajectory(savedTrajectory));
        
        savedTrajectory.setFlowbasedVirtualNodes(virtualNodes);
        savedTrajectory.setFlowbasedLinkCapacities(linkCapacities);
        savedTrajectory.setFlowbasedTypeDays(typeDays);

        return trajectoryRepository.save(savedTrajectory);
    }

    @Override
    public List<FlowbasedVirtualNodesEntity> buildFlowbasedVirtualNodesList(Path trajectoryFilePath) {
        List<FlowbasedVirtualNodesEntity> result = new ArrayList<>();
        Path excelPath = trajectoryFilePath.resolve("Flowbased_nodes_links.xlsx");

        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("nodes");
            if (sheet == null) {
                throw new IOException("Sheet 'nodes' not found in Flowbased_nodes_links.xlsx");
            }

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getStringCellValue(row, 0);
                if (name == null || name.isEmpty()) continue;

                FlowbasedVirtualNodesEntity entity = FlowbasedVirtualNodesEntity.builder()
                        .name(name)
                        .build();
                result.add(entity);
            }
        } catch (IOException e) {
            log.error("Error reading Flowbased_nodes_links.xlsx - nodes sheet", e);
            throw BusinessException.builder()
                    .message("Error reading Flowbased_nodes_links.xlsx (nodes sheet): " + e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return result;
    }

    @Override
    public List<FlowbasedLinkCapacityEntity> buildFlowbasedLinkCapacityList(Path trajectoryFilePath) {
        List<FlowbasedLinkCapacityEntity> result = new ArrayList<>();
        Path excelPath = trajectoryFilePath.resolve("Flowbased_nodes_links.xlsx");

        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("links");
            if (sheet == null) {
                throw new IOException("Sheet 'links' not found in Flowbased_nodes_links.xlsx");
            }

            // Get header row
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndexMap = buildColumnIndexMap(headerRow);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getStringCellValue(row, columnIndexMap.getOrDefault("name", 0));
                if (name == null || name.isEmpty()) continue;

                // Extract all 8 MW values with their types
                IntegerCellValue winterHPDirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("winter_HP_direct_MW", 1));
                IntegerCellValue winterHPIndirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("winter_HP_indirect_MW", 2));
                IntegerCellValue winterHCDirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("winter_HC_direct_MW", 3));
                IntegerCellValue winterHCIndirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("winter_HC_indirect_MW", 4));
                IntegerCellValue summerHPDirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("summer_HP_direct_MW", 5));
                IntegerCellValue summerHPIndirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("summer_HP_indirect_MW", 6));
                IntegerCellValue summerHCDirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("summer_HC_direct_MW", 7));
                IntegerCellValue summerHCIndirect = getIntegerCellValueWithType(row, columnIndexMap.getOrDefault("summer_HC_indirect_MW", 8));

                // Determine overall type
                FlowbasedLinkCapacityType overallType = determineOverallType(
                        winterHPDirect, winterHPIndirect, winterHCDirect, winterHCIndirect,
                        summerHPDirect, summerHPIndirect, summerHCDirect, summerHCIndirect);

                FlowbasedLinkCapacityEntity entity = FlowbasedLinkCapacityEntity.builder()
                        .name(name)
                        .winterHPDirectMW(winterHPDirect.getValue())
                        .winterHPIndirectMW(winterHPIndirect.getValue())
                        .winterHCDirectMW(winterHCDirect.getValue())
                        .winterHCIndirectMW(winterHCIndirect.getValue())
                        .summerHPDirectMW(summerHPDirect.getValue())
                        .summerHPIndirectMW(summerHPIndirect.getValue())
                        .summerHCDirectMW(summerHCDirect.getValue())
                        .summerHCIndirectMW(summerHCIndirect.getValue())
                        .hurdlesCost(getBooleanCellValue(row, columnIndexMap.getOrDefault("hurdles_cost", 9)))
                        .type(overallType)
                        .build();
                result.add(entity);
            }
        } catch (IOException e) {
            log.error("Error reading Flowbased_nodes_links.xlsx - links sheet", e);
            throw BusinessException.builder()
                    .message("Error reading Flowbased_nodes_links.xlsx (links sheet): " + e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return result;
    }

    @Override
    public List<FlowbasedTypeDayEntity> buildFlowbasedTypeDayList(Path trajectoryFilePath) {
        List<FlowbasedTypeDayEntity> result = new ArrayList<>();
        Path csvPath = trajectoryFilePath.resolve("IdTypDays.csv");

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean headerSkipped = false;
            
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    try {
                        Integer idTypeDay = Integer.parseInt(parts[1].trim());
                        FlowbasedTypeDayEntity entity = FlowbasedTypeDayEntity.builder()
                                .clustering(parts[0].trim())
                                .idTypeDay(idTypeDay)
                                .classDay(parts[2].trim())
                                .build();
                        result.add(entity);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid id_type_day value, skipping row: {}", line);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading IdTypDays.csv", e);
            throw BusinessException.builder()
                    .message("Error reading IdTypDays.csv: " + e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return result;
    }

    private TrajectoryEntity createTrajectory(String trajectoryToUse, String checksum, LocalDateTime lastModificationContentDate, String horizon) {
        String createdBy = userService.getCurrentUserDetails() != null ? 
                userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;

        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(0L)
                .checksum(checksum)
                .type(TrajectoryType.FLOWBASED.name())
                .createdBy(createdBy)
                .creationDate(LocalDateTime.now())
                .lastModificationContentDate(lastModificationContentDate)
                .horizon(horizon)
                .version(1)
                .build();
    }

    private String getStringCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        return null;
    }

    protected IntegerCellValue getIntegerCellValueWithType(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return IntegerCellValue.builder().value(null).type(FlowbasedLinkCapacityType.DISABLED).build();

        if (cell.getCellType() == CellType.NUMERIC) {
            return IntegerCellValue.builder()
                    .value((int) cell.getNumericCellValue())
                    .type(FlowbasedLinkCapacityType.ENABLED)
                    .build();
        }

        if (cell.getCellType() == CellType.STRING) {
            String stringValue = cell.getStringCellValue().trim();
            if (stringValue.equalsIgnoreCase("infinite")) {
                return IntegerCellValue.builder()
                        .value(null)
                        .type(FlowbasedLinkCapacityType.INFINITE)
                        .build();
            }
            try {
                int intValue = Integer.parseInt(stringValue);
                return IntegerCellValue.builder()
                        .value(intValue)
                        .type(FlowbasedLinkCapacityType.ENABLED)
                        .build();
            } catch (NumberFormatException e) {
                throw BusinessException.builder()
                        .message("Invalid integer value: " + stringValue + ". Expected a numeric value or 'infinite'")
                        .build();
            }
        }

        throw BusinessException.builder()
                .message("Invalid cell type for integer value. Expected NUMERIC or STRING")
                .build();
    }

    protected Integer getIntegerCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }

        if (cell.getCellType() == CellType.STRING) {
            String stringValue = cell.getStringCellValue().trim();
            if (stringValue.equalsIgnoreCase("infinite")) {
                return null;
            }
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                throw BusinessException.builder()
                        .message("Invalid integer value: " + stringValue + ". Expected a numeric value or 'infinite'")
                        .build();
            }
        }

        throw BusinessException.builder()
                .message("Invalid cell type for integer value. Expected NUMERIC or STRING")
                .build();
    }

    private Boolean getBooleanCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return false;

        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        return false;
    }

    protected FlowbasedLinkCapacityType determineOverallType(
            IntegerCellValue winterHPDirect,
            IntegerCellValue winterHPIndirect,
            IntegerCellValue winterHCDirect,
            IntegerCellValue winterHCIndirect,
            IntegerCellValue summerHPDirect,
            IntegerCellValue summerHPIndirect,
            IntegerCellValue summerHCDirect,
            IntegerCellValue summerHCIndirect) {

        // Collect all types
        IntegerCellValue[] values = {
            winterHPDirect, winterHPIndirect, winterHCDirect, winterHCIndirect,
            summerHPDirect, summerHPIndirect, summerHCDirect, summerHCIndirect
        };

        // Count each type
        int infiniteCount = 0;
        int enabledCount = 0;
        int disabledCount = 0;

        for (IntegerCellValue cellValue : values) {
            if (cellValue.getType() == FlowbasedLinkCapacityType.INFINITE) {
                infiniteCount++;
            } else if (cellValue.getType() == FlowbasedLinkCapacityType.ENABLED) {
                enabledCount++;
            } else if (cellValue.getType() == FlowbasedLinkCapacityType.DISABLED) {
                disabledCount++;
            }
        }

        // Check if all values are INFINITE
        if (infiniteCount == 8) {
            return FlowbasedLinkCapacityType.INFINITE;
        }

        // Check if there's a mix of INFINITE and other types (not all DISABLED)
        if (infiniteCount > 0 && infiniteCount < 8) {
            throw BusinessException.builder()
                    .message("Invalid link capacity data: Cannot mix 'infinite' values with numeric or partially filled values. Either all values must be 'infinite' or all must be numeric.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check if all values are ENABLED (no DISABLED, no INFINITE)
        if (enabledCount == 8) {
            return FlowbasedLinkCapacityType.ENABLED;
        }

        // Otherwise DISABLED (contains at least one DISABLED, and no INFINITE)
        return FlowbasedLinkCapacityType.DISABLED;
    }

    private Map<String, Integer> buildColumnIndexMap(Row headerRow) {
        Map<String, Integer> columnIndexMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                columnIndexMap.put(cell.getStringCellValue(), i);
            }
        }
        return columnIndexMap;
    }
}

