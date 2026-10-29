package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedAreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.service.flowbased.FlowbasedFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.AREA;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedFileProcessorServiceImpl implements FlowbasedFileProcessorService {

    private static final Pattern HVDC_NODE_PATTERN = Pattern.compile("z_([^_\\s]+)_([^_\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final String FLOWBASED_NODES_LINKS_FILE = "Flowbased_nodes_links.xlsx";
    private final TrajectoryRepository trajectoryRepository;
    private final AreaRepository areaRepository;
    private final UserService userService;
    private final FlowbasedConstraintsRestrictionsFileProcessorService restrictionsFileProcessorService;
    private final PathSecurityUtil pathSecurityUtil;

    private static final String[] REQUIRED_FILES = {
            FLOWBASED_NODES_LINKS_FILE,
            "IdTypDays.csv",
            "second_member.txt",
            "weight.txt",
            "ts.txt",
            "areas_necessary.xlsx",
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
                    .errorMessageArguments(List.of(String.join(", ", missingFiles), trajectoryFilePath.getFileName().toString()))
                    .message("Required files are missing: {0} in Flowbased trajectory {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @Transactional
    @Override
    public TrajectoryEntity processFlowbasedFiles(Path trajectoryFilePath, String trajectoryToUse, Integer studyId, String horizon) {
        validateRequiredFiles(trajectoryFilePath);

        if (studyId == null) {
            throw BusinessException.builder()
                    .message("studyId is required to import a Flowbased trajectory")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        List<FlowbasedAreaEntity> areas = buildAreasNecessaryList(trajectoryFilePath);
        validateAreasExistInStudy(areas, studyId);
        List<FlowbasedVirtualNodesEntity> virtualNodes = buildFlowbasedVirtualNodesList(trajectoryFilePath);
        validateVirtualNodesHvdcLinks(virtualNodes, studyId);
        List<FlowbasedLinkCapacityEntity> linkCapacities = buildFlowbasedLinkCapacityList(trajectoryFilePath);
        List<FlowbasedTypeDayEntity> typeDays = buildFlowbasedTypeDayList(trajectoryFilePath);
        var restrictions = restrictionsFileProcessorService.readRestrictions(trajectoryFilePath, horizon, trajectoryToUse);

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
                        .message("File already processed with same content: " + trajectoryToUse)
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
        areas.forEach(area -> area.setTrajectory(savedTrajectory));
        restrictions.constraints().forEach(constraint -> constraint.setTrajectory(savedTrajectory));
        restrictions.links().forEach(link -> link.setTrajectory(savedTrajectory));

        savedTrajectory.setFlowbasedVirtualNodes(virtualNodes);
        savedTrajectory.setFlowbasedLinkCapacities(linkCapacities);
        savedTrajectory.setFlowbasedTypeDays(typeDays);
        savedTrajectory.setFlowbasedAreas(areas);
        savedTrajectory.setFlowbasedConstraintsRestrictions(restrictions.constraints());
        savedTrajectory.setFlowbasedLinkLists(restrictions.links());

        return trajectoryRepository.save(savedTrajectory);
    }

    @Override
    public List<FlowbasedAreaEntity> buildAreasNecessaryList(Path trajectoryFilePath) {
        Path excelPath = pathSecurityUtil.resolveSafePath(trajectoryFilePath, "areas_necessary.xlsx");
        DataFormatter formatter = new DataFormatter();
        List<FlowbasedAreaEntity> areas = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalidAreasFile();
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null || !"areas".equals(formatter.formatCellValue(header.getCell(0)).trim())
                    || hasAdditionalNonEmptyCells(header, formatter)) {
                throw invalidAreasFile();
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String area = formatter.formatCellValue(row.getCell(0)).trim();
                if (area.isEmpty()) {
                    if (hasAdditionalNonEmptyCells(row, formatter)) {
                        throw invalidAreasFile();
                    }
                    continue;
                }
                if (hasAdditionalNonEmptyCells(row, formatter)) {
                    throw invalidAreasFile();
                }
                areas.add(FlowbasedAreaEntity.builder().area(area).build());
            }
        } catch (IOException | org.apache.poi.EmptyFileException
                 | org.apache.poi.UnsupportedFileFormatException | org.apache.poi.ooxml.POIXMLException e) {
            log.error("Error reading areas_necessary.xlsx", e);
            throw BusinessException.builder()
                    .message("Error reading areas_necessary.xlsx: " + e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return areas;
    }

    private boolean hasAdditionalNonEmptyCells(Row row, DataFormatter formatter) {
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            if (cellIndex != 0 && !formatter.formatCellValue(row.getCell(cellIndex)).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private BusinessException invalidAreasFile() {
        return BusinessException.builder()
                .message("Invalid areas_necessary.xlsx format: expected one column with header 'areas'")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    @Override
    public void validateAreasExistInStudy(List<FlowbasedAreaEntity> areas, Integer studyId) {
        Set<String> studyAreas = areaRepository.findAllByStudyId(studyId, AREA.name()).stream()
                .map(AreaEntity::getName)
                .filter(Objects::nonNull)
                .map(area -> area.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> missingAreas = areas.stream()
                .map(FlowbasedAreaEntity::getArea)
                .filter(area -> !studyAreas.contains(area.toUpperCase(Locale.ROOT)))
                .distinct()
                .toList();
        if (!missingAreas.isEmpty()) {
            throw BusinessException.builder()
                    .message("Mandatory Areas {0} in Flowbased trajectory are missing in AREA trajectory")
                    .errorMessageArguments(List.of(String.join(", ", missingAreas)))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @Override
    public List<FlowbasedVirtualNodesEntity> buildFlowbasedVirtualNodesList(Path trajectoryFilePath) {
        List<FlowbasedVirtualNodesEntity> result = new ArrayList<>();
        Path excelPath = trajectoryFilePath.resolve(FLOWBASED_NODES_LINKS_FILE);

        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("nodes");
            if (sheet == null) {
                throw new IOException("Sheet 'nodes' not found in "+ FLOWBASED_NODES_LINKS_FILE);
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
    public void validateVirtualNodesHvdcLinks(List<FlowbasedVirtualNodesEntity> virtualNodes, Integer studyId) {
        validateVirtualNodesHvdcLinks(virtualNodes, () ->
                trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId).stream()
                        .flatMap(trajectory -> trajectory.getLinkEntities().stream())
                        .toList());
    }

    @Override
    public void validateVirtualNodesHvdcLinks(List<FlowbasedVirtualNodesEntity> virtualNodes, List<LinkEntity> links) {
        validateVirtualNodesHvdcLinks(virtualNodes, () -> links);
    }

    private void validateVirtualNodesHvdcLinks(List<FlowbasedVirtualNodesEntity> virtualNodes, Supplier<List<LinkEntity>> links) {
        Map<String, List<String>> nodePairs = new LinkedHashMap<>();
        for (FlowbasedVirtualNodesEntity node : virtualNodes) {
            var matcher = HVDC_NODE_PATTERN.matcher(node.getName());
            if (matcher.matches()) {
                nodePairs.put(node.getName(), countryPair(matcher.group(1), matcher.group(2)));
            }
        }
        if (nodePairs.isEmpty()) {
            return;
        }

        Set<List<String>> hvdcPairs = links.get().stream()
                .filter(link -> isNonZeroCapacity(link.getHvdcMwDirect()) && isNonZeroCapacity(link.getHvdcMwIndirect()))
                .map(LinkEntity::getName)
                .filter(Objects::nonNull)
                .map(name -> name.split("-", -1))
                .filter(parts -> parts.length == 2)
                .map(parts -> countryPair(parts[0], parts[1]))
                .collect(Collectors.toSet());

        List<String> invalidNodes = nodePairs.entrySet().stream()
                .filter(entry -> !hvdcPairs.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!invalidNodes.isEmpty()) {
            throw BusinessException.builder()
                    .message("Virtual nodes {0} in Flowbased trajectory require a matching link in the LINK trajectory associated with the study, with both hvdc_mw_direct and hvdc_mw_indirect non-null and different from zero")
                    .errorMessageArguments(List.of(String.join(", ", invalidNodes)))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private boolean isNonZeroCapacity(Double capacity) {
        return capacity != null && capacity != 0;
    }

    private List<String> countryPair(String country1, String country2) {
        return java.util.stream.Stream.of(country1, country2)
                .map(country -> country.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    @Override
    public List<FlowbasedLinkCapacityEntity> buildFlowbasedLinkCapacityList(Path trajectoryFilePath) {
        List<FlowbasedLinkCapacityEntity> result = new ArrayList<>();
        Path excelPath = trajectoryFilePath.resolve(FLOWBASED_NODES_LINKS_FILE);

        try (InputStream inputStream = Files.newInputStream(excelPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("links");
            if (sheet == null) {
                throw new IOException("Sheet 'links' not found in Flowbased_nodes_links.xlsx");
            }

            // Get header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Header row not found in 'links' sheet of Flowbased_nodes_links.xlsx");
            }
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
