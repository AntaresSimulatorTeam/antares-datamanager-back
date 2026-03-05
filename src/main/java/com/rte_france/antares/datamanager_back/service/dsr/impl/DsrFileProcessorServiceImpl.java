package com.rte_france.antares.datamanager_back.service.dsr.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.dsr.DsrFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castInt;
import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.isRowEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsrFileProcessorServiceImpl implements DsrFileProcessorService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;

    private static final String DSR_PREFIX = "cluster_DSR_";
    protected static final String[] REQUIRED_CLUSTER_COLUMNS = {
            "toUse", "Area", "Name", "Capacity", "Reliability", "nb_hour_per_day", "max_hour_per_day",
            "price", "nb_units", "FO_rate", "FO_duration", "Modulation"};


    @Transactional
    @Override
    public TrajectoryEntity processDsrClusterFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area) throws IOException {
        // Check trajectory file name prefix
        boolean prefixMatch = startsWithIgnoreCase(trajectoryToUse, DSR_PREFIX);
        if (!prefixMatch) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(DSR_PREFIX))
                    .message("The trajectory file name must start with {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path trajectoryFilePath = getTrajectoryFilePath(trajectoryToUse);
        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream().map(a -> a.getName().toUpperCase()).toList();
        var dsrClusterEntities = buildDsrClusterEntities(horizon.split("-")[1], trajectoryFilePath, area, studyAreas);

        boolean isSeriesTrue = dsrClusterEntities.stream()
                .anyMatch(entity -> Boolean.TRUE.equals(entity.getModulation()));

        TrajectoryEntity trajectoryEntity = buildDsrClusterTrajectory(trajectoryFilePath, horizon, area, isSeriesTrue);

        dsrClusterEntities.forEach(dsrClusterEntity -> dsrClusterEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setDsrClusterEntities(dsrClusterEntities);

        return trajectoryRepository.save(trajectoryEntity);
    }

    public Path getTrajectoryFilePath(String trajectoryToUse) throws IOException {
        //build the file path
        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getDsrDirectory())
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        //download the file
        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse + ".xlsx").normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }
        return trajectoryFilePath;
    }

    public static boolean startsWithIgnoreCase(String name, String prefix) {
        if (name == null) {
            return false;
        }
        return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }
    

    private String getStringCellValue(Row row, int idx) {
        Cell cell = row.getCell(idx);
        return cell == null ? null : cell.getStringCellValue();
    }

    private boolean shouldIncludeRow(String rowArea, String areaParam) {
        return rowArea.equalsIgnoreCase(areaParam) || areaParam.equals(OTHERS_AREA);
    }

    private void validateRow(String rowArea, String trajectoryFileName, int r) {
        if (rowArea == null || rowArea.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryFileName, String.valueOf(r)))
                    .message("Area is missing in DSR trajectory {0} for row: {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateClusterNameLength(String clusterName, String trajectoryFileName, String rowArea, String horizon) {
        if (clusterName != null && clusterName.length() > 40) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(clusterName, trajectoryFileName, rowArea, horizon))
                    .message("Value {0} too long in DSR Cluster trajectory {1} for area {2} and horizon {3}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateNumericRange(Row header, Row row, int[] indexes, String rowArea, String clusterName, String trajectoryFileName) {
        List<String> wrongColumns = new ArrayList<>();

        for (int idx : indexes) {
            Cell cell = row.getCell(idx);

            if (!isNumericCell(cell)) {
                Cell headerCell = header.getCell(idx);
                String headerName = headerCell != null
                        ? headerCell.getStringCellValue()
                        : null;
                if(headerName!= null) wrongColumns.add(headerName);
            }
        }

        if (!wrongColumns.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(rowArea, clusterName, trajectoryFileName))
                    .message("Values "+ String.join(", ", wrongColumns) + " for node {0} / cluster {1} must be numeric in DSR Cluster trajectory {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private boolean isInteger(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double num = cell.getNumericCellValue();
                yield num % 1 == 0;
            }
            default -> false;
        };
    }

    private void validateIntegerRange(Row header, Row row, int[] indexes, String rowArea, String clusterName, String trajectoryFileName) {
        List<String> wrongColumns = new ArrayList<>();

        for (int idx : indexes) {
            Cell cell = row.getCell(idx);

            if (!isInteger(cell)) {
                Cell headerCell = header.getCell(idx);
                String headerName = headerCell != null
                        ? headerCell.getStringCellValue()
                        : null;
                if(headerName!= null) wrongColumns.add(headerName);
            }
        }
        if (!wrongColumns.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(rowArea, clusterName, trajectoryFileName))
                    .message("Values " + String.join(", ", wrongColumns) + " for node {0} / cluster {1} must be integer in DSR Cluster trajectory {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private Boolean getBooleanCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();

            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case BOOLEAN:
                        return cell.getBooleanCellValue();
                    case NUMERIC:
                        return cell.getNumericCellValue() == 1.0;
                    case STRING:
                        return parseBooleanString(cell.getStringCellValue());
                    default:
                        return null;
                }

            case STRING:
                return parseBooleanString(cell.getStringCellValue());

            case NUMERIC:
                return cell.getNumericCellValue() == 1.0;

            default:
                return null;
        }
    }

    private Boolean parseBooleanString(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        if (s.equals("1")) return true;
        if (s.equals("0")) return false;

        return null;
    }

    private void validateBooleanValue(Row row, int idx, String rowArea, String clusterName, String trajectoryFileName) {
        if (getBooleanCell(row, idx) == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(rowArea, clusterName, trajectoryFileName))
                    .message("Modulation for node {0} / cluster {1} are not boolean in DSR trajectory {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private List<DsrClusterEntity> buildDsrClusterEntities(String horizon, Path trajectoryFilePath, String areaParam, List<String> studyAreas) throws IOException {
        List<DsrClusterEntity> results = new ArrayList<>();
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryFilePath, "DSR Cluster");
            Row header = sheet.getRow(0);
            checkMissingColumns(sheet, REQUIRED_CLUSTER_COLUMNS, trajectoryFileName, TrajectoryType.DSR.name());

            List<String> fileAreas = new ArrayList<>();
            boolean onlyHeader = true;

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);

                onlyHeader = false;
                if (row == null || isRowEmpty(row)) continue;

                String rowArea = getStringCellValue(row, 1);

                // Zone must be present
                validateRow(rowArea, trajectoryFileName, r);

                fileAreas.add(rowArea);

                if (!shouldIncludeRow(rowArea, areaParam) || !studyAreas.contains(rowArea.toUpperCase())) {
                    continue;
                }

                Object value = getCellValue(row, 0);
                Integer intValue = (value != null) ? castInt(value) : null;

                if (intValue == null || intValue == 0) {
                    continue;
                }

                String clusterName = getStringCellValue(row, 2);

                // Cluster name should not exceed 40 characters
                validateClusterNameLength(clusterName, trajectoryFileName, rowArea, horizon);

                int[] numericColumnIndexes = {3, 4, 7, 9};
                validateNumericRange(header, row, numericColumnIndexes, rowArea, clusterName, trajectoryFileName);

                int[] integerColumnIndexes = {5, 6, 8, 10};
                validateIntegerRange(header, row, integerColumnIndexes, rowArea, clusterName, trajectoryFileName);

                validateBooleanValue(row, 11, rowArea, clusterName, trajectoryFileName);

                DsrClusterEntity entity = mapRowToEntity(row, rowArea, clusterName);
                results.add(entity);
            }

            if (onlyHeader) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName, horizon))
                        .message("No data in DSR Cluster trajectory {0} for horizon: {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            
            validateTrajectoryAreasPresence(studyAreas, fileAreas, TrajectoryType.DSR, trajectoryFileName);

            // The selected area must be present in the file's 'node' column, except when area equals OTHERS
            validateSelectedAreaPresence(areaParam, fileAreas, TrajectoryType.DSR, trajectoryFileName);
        }
        return results;
    }

    private TrajectoryEntity buildDsrClusterTrajectory(Path trajectoryFilePath, String horizon, String areaParam, Boolean hasSeries) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.DSR.name());
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(fileName, TrajectoryType.DSR.name(), horizon, areaParam, null);

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon.split("-")[1], createdBy, TrajectoryType.DSR, areaParam, null, hasSeries);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon.split("-")[1], createdBy, TrajectoryType.DSR, areaParam, null, hasSeries);
        }

        return trajectory;
    }

    private DsrClusterEntity mapRowToEntity(Row row, String rowArea, String clusterName) {
        DsrClusterEntity dsrClusterEntity = new DsrClusterEntity();

        dsrClusterEntity.setArea(rowArea);
        dsrClusterEntity.setName(clusterName);
        dsrClusterEntity.setToUse(getBooleanCell(row, 0));
        dsrClusterEntity.setCapacity(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
        dsrClusterEntity.setReliability(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
        dsrClusterEntity.setNbHourPerDay((int) row.getCell(5).getNumericCellValue());
        dsrClusterEntity.setMaxHourPerDay((int) row.getCell(6).getNumericCellValue());
        dsrClusterEntity.setPrice(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
        dsrClusterEntity.setNbUnits((int) row.getCell(8).getNumericCellValue());
        dsrClusterEntity.setFoRate(BigDecimal.valueOf(row.getCell(9).getNumericCellValue()));
        dsrClusterEntity.setFoDuration((int) row.getCell(10).getNumericCellValue());
        dsrClusterEntity.setModulation(getBooleanCell(row, 11));
        return dsrClusterEntity;
    }
}
