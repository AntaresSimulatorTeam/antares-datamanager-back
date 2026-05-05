package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.rte_france.antares.datamanager_back.util.Utils.checkMissingColumns;
import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.isRowEmpty;


@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageConstraintsFileProcessorServiceImpl implements StStorageConstraintsFileProcessorService {

    public static final String PARAMETERS_SHEET_NAME = "parameters";
    public static final String HOURS_SHEET_NAME = "hours";
    @Override
    public List<StConstraintsParameterEntity> processConstraintsParametersAnHoursFile(Path additionalConstraintsPath, String areaParam,
                                                                                      List<String> studyAreas, String technology, String clusterName,
                                                                                      String trajectoryFileName, Integer trajectoryId) throws IOException {
        List<StConstraintsParameterEntity> parameters = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(additionalConstraintsPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {


                Sheet parametersSheet = workbook.getSheet(PARAMETERS_SHEET_NAME);
                Sheet hoursSheet = workbook.getSheet(HOURS_SHEET_NAME);

                if(parametersSheet ==null || hoursSheet == null){
                    throw BusinessException.builder()
                            .message("Missing parameters or hours data in STS {0} Additional Constraints file")
                            .errorMessageArguments(List.of(technology))
                            .build();
                }

                validateSheetNotEmpty(parametersSheet, PARAMETERS_SHEET_NAME, technology);
                validateSheetNotEmpty(hoursSheet, HOURS_SHEET_NAME, technology);

                processParameters(parametersSheet, parameters, areaParam, studyAreas, technology);
                processHours(hoursSheet, parameters, areaParam, studyAreas, technology);

                return parameters;
        }

    }

    private void processHours(Sheet hoursSheet, List<StConstraintsParameterEntity> parameters, String areaParam, List<String> studyAreas, String technology) {
        if (hoursSheet == null) return;

        String [] expectedColumns = {"name", "zone", "cluster", "occurrence", "start", "end"};
        checkMissingColumns(hoursSheet,
                expectedColumns,
                StsTsFile.ADDITIONAL_CONSTRAINTS.fileName(),
                TrajectoryType.STS,
                HOURS_SHEET_NAME,
                technology);
        Predicate<String> zoneFilter = buildZoneFilter(areaParam, studyAreas);
        Map<String, StConstraintsParameterEntity> parameterIndex = indexParameters(parameters);

        for (Row row : hoursSheet) {
            if (row.getRowNum() == 0) continue; // skip header
            if (isRowEmpty(row)) break;
            processHoursRow(row, zoneFilter, parameterIndex, technology);
        }
    }

    private void processHoursRow(
            Row row,
            Predicate<String> zoneFilter,
            Map<String, StConstraintsParameterEntity> parameterIndex,
            String technology
    ) {
        Result result = getResult(row, zoneFilter, technology, HOURS_SHEET_NAME
        );
        if (result == null) {
            return;
        }

        StConstraintsParameterEntity param = resolveIndexedParameter(parameterIndex, result);
        StConstraintsHoursEntity hour = getStConstraintsHoursEntity(row, param);
        param.getHours().add(hour);
    }

    /**
     * Resolves an indexed parameter from the provided parameter index map
     * using the specified result details.
     * Throws a {@link BusinessException} if the parameter cannot be resolved.
     *
     * @param parameterIndex     a map containing parameter keys mapped to {@code StConstraintsParameterEntity} instances
     * @param result             the result object containing the attributes necessary for parameter resolution
     * @return the resolved {@code StConstraintsParameterEntity} associated with the given index
     * @throws BusinessException if no corresponding parameter is found for the resolved key
     */
    private StConstraintsParameterEntity resolveIndexedParameter(
            Map<String, StConstraintsParameterEntity> parameterIndex,
            Result result
    ) {
        return Optional.ofNullable(parameterIndex.get(buildParameterKey(result.name(), result.zone(), result.cluster())))
                .orElseThrow(() -> BusinessException.builder()
                        .message("Parameter: name={0}, zone={1}, cluster{2}, not found in hours sheet")
                        .errorMessageArguments(List.of(result.name(), result.zone(), result.cluster()))
                        .build());
    }

    private @Nullable Result getResult(Row row, Predicate<String> zoneFilter, String technology, String sheetDisplayName) {
        String name = getStringCellValue(row, 0);
        String zone = getStringCellValue(row, 1);
        String cluster = getStringCellValue(row, 2);

        if (!zoneFilter.test(zone)) {
            return null;
        }

        if (isBlank(name) || isBlank(zone) || isBlank(cluster)) {
            throw BusinessException.builder()
                    .message("Values name, zone and cluster must not be empty in {0} tab STS {1} Additional Constraint")
                    .errorMessageArguments(List.of(sheetDisplayName, technology))
                    .build();

        }
        return new Result(name, zone, cluster);
    }

    private record Result(String name, String zone, String cluster) {
    }

    private void validateSheetNotEmpty(Sheet sheet, String sheetDisplayName, String technology) {
        boolean hasData = false;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            if (!isRowEmpty(row)) {
                hasData = true;
                break;
            }
        }
        if (!hasData) {
            throw BusinessException.builder()
                    .message("No data found in {0} tab in STS {1} Additional Constraint file")
                    .errorMessageArguments(List.of(sheetDisplayName, technology))
                    .build();
        }
    }

    private StConstraintsHoursEntity getStConstraintsHoursEntity(Row row, StConstraintsParameterEntity param) {
        StConstraintsHoursEntity hour = new StConstraintsHoursEntity();
        hour.setOccurrence(getIntegerCellValue(row, 3, param, "occurrence"));

        hour.setStartHour(getIntegerCellValue(row, 4, param, "start"));

        hour.setEndHour(getIntegerCellValue(row, 5, param, "end"));

        hour.setParameter(param);
        return hour;
    }

    private void processParameters(Sheet parametersSheet, List<StConstraintsParameterEntity> parameters, String areaParam, List<String> studyAreas, String technology) {
        String [] expectedColumns = {"name", "zone", "cluster", "variable", "operator", "enabled"};
        checkMissingColumns(parametersSheet,
                expectedColumns,
                StsTsFile.ADDITIONAL_CONSTRAINTS.fileName(),
                TrajectoryType.STS,
                PARAMETERS_SHEET_NAME,
                technology);
        Predicate<String> zoneFilter = buildZoneFilter(areaParam, studyAreas);

        for (Row row : parametersSheet) {
            if (row.getRowNum() == 0) continue; // skip header
            if (isRowEmpty(row)) break;

            processParametersRow(row, zoneFilter, parameters, technology);
        }

    }

    private void processParametersRow(Row row, Predicate<String> zoneFilter, List<StConstraintsParameterEntity> parameters, String technology) {
        Result result = getResult(row, zoneFilter, technology, PARAMETERS_SHEET_NAME);
        if (result == null) {
            return;
        }

        StConstraintsParameterEntity param = new StConstraintsParameterEntity();
        param.setHours(new ArrayList<>());
        param.setName(result.name());
        param.setZone(result.zone());
        param.setCluster(result.cluster());
        param.setVariable(getStringCellValue(row, 3));
        param.setOperator(getStringCellValue(row, 4));
        param.setEnabled(getBooleanCellValue(row));


        if (parameters.stream().anyMatch(p -> p.getCluster().equalsIgnoreCase(param.getCluster()) && p.getName().equalsIgnoreCase(param.getName()))) {
            throw BusinessException.builder()
                    .message("Selected area/cluster {0}/{1} not present in the 'zone' column of {2} tab in STS {3} Additional Constraint file")
                    .errorMessageArguments(List.of(result.zone(), result.cluster(), PARAMETERS_SHEET_NAME, technology))
                    .build();
        }

        parameters.add(param);
    }

    private String getStringCellValue(Row row, int index) {
        return Optional.ofNullable(getCellValue(row, index))
                .map(Object::toString)
                .map(String::trim)
                .orElse(null);
    }

    private boolean getBooleanCellValue(Row row) {
        Cell cell = row.getCell(5);
        if (cell == null) return false;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> Boolean.parseBoolean(cell.getStringCellValue().trim());
            case NUMERIC -> cell.getNumericCellValue() != 0;
            default -> false;
        };
    }

    private int getIntegerCellValue(Row row, int index, StConstraintsParameterEntity param, String fieldName) {
        Object value = getCellValue(row, index);
        if (value == null) {
            throw BusinessException.builder()
                    .message("Values for node {0} / cluster {1} are not numeric in STS Additional Constraint {2}")
                    .errorMessageArguments(List.of(param.getZone(), param.getCluster(), fieldName))
                    .build();
        }

        if (value instanceof Double d) return d.intValue();

        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw BusinessException.builder()
                    .message("Values for node {0} / cluster {1} are not numeric in STS Additional Constraint {2}")
                    .errorMessageArguments(List.of(param.getZone(), param.getCluster(), fieldName))
                    .build();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, StConstraintsParameterEntity> indexParameters(List<StConstraintsParameterEntity> parameters) {
        Map<String, StConstraintsParameterEntity> index = new LinkedHashMap<>();
        for (StConstraintsParameterEntity parameter : parameters) {
            // first match
            index.putIfAbsent(buildParameterKey(parameter.getName(), parameter.getZone(), parameter.getCluster()), parameter);
        }
        return index;
    }

    private String buildParameterKey(String name, String zone, String cluster) {
        return name + "|" + zone + "|" + cluster;
    }

    private Predicate<String> buildZoneFilter(String areaParam, List<String> studyAreas) {
        if ("OTHERS".equalsIgnoreCase(areaParam)) {
            return zone -> studyAreas != null &&
                    studyAreas.stream().anyMatch(a -> a.equalsIgnoreCase(zone));
        }
        return zone -> areaParam != null && areaParam.equalsIgnoreCase(zone);
    }
}

