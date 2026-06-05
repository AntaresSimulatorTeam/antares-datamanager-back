package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalSpecificFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.CastCellUtil.*;
import static com.rte_france.antares.datamanager_back.util.Utils.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalSpecificFileProcessorServiceImpl implements ThermalSpecificFileProcessorService {

    private static final String VALUES_FOR_NODE_MESSAGE_PREFIX = "Values for node ";
    private static final String CLUSTER_MESSAGE_SEPARATOR = " / cluster ";

    private final AreaRepository areaRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final ThermalControlService thermalControlService;
    private final ThermalClusterRefServiceImpl thermalClusterRef;
    private final ThermalSpecificParametersRepository thermalSpecificParametersRepository;

    /**
     * Builds a list of ThermalSpecificParametersEntity objects based on the provided trajectory file
     * and associated parameters such as trajectory name, horizon, area, and study ID.
     * This method processes the thermal specific parameter trajectory file, validates data,
     * and checks for business logic constraints.
     *
     * @param trajectoryName     the name of the thermal specific parameter trajectory
     * @param trajectoryFilePath the file path of the thermal specific parameter trajectory
     * @param horizon            the horizon identifier to locate the specific sheet in the file
     * @param area               the selected area for which the data is being validated and processed
     * @param studyId            the identifier of the study, used for validating associated areas
     * @return a list of ThermalSpecificParametersEntity objects based on the valid rows of the trajectory file
     * @throws BusinessException  if there are validation or business logic errors
     * @throws TechnicalException if there are issues processing the file
     */
    @Override
    public List<ThermalSpecificParametersEntity> buildThermalSpecificParameterValueList(String trajectoryName, Path trajectoryFilePath, String horizon, String area, Integer studyId) {
        List<ThermalSpecificParametersEntity> specificParams = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSpecificSheet(workbook, horizon, trajectoryName);
            Row header = resolveSpecificHeader(sheet);
            validateHeaderColumns(header, trajectoryName);

            Set<String> studyAreasSet = new HashSet<>(getStudyAreasForCurrentStudy(studyId));
            boolean isOthers = OTHERS_AREA.equalsIgnoreCase(area);
            String selectedAreaUpper = normalizeArea(area);

            for (Row row : sheet) {
                if (row.getRowNum() <= 2) continue;

                String rowArea = castString(getCellValue(row, 0));
                String rowAreaUpper = normalizeArea(rowArea);
                if (shouldSkipSpecificRow(rowAreaUpper, isOthers, selectedAreaUpper, studyAreasSet)) continue;

                String clusterName = castString(getCellValue(row, 2));

                checkNumericColumns(row, rowArea, clusterName, trajectoryName);
                processThermalSpecificRow(row, header, specificParams, trajectoryName);
            }

            if (specificParams.isEmpty()) {
                throw BusinessException.builder().message("None of the areas of trajectory AREA are present in THERMAL Specific Param trajectory " + trajectoryName).build();
            }
            Set<String> specificClusters = buildSpecificClusters(specificParams);

            thermalControlService.checkMissingClusters(studyId, horizon, specificClusters, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, area);

            return specificParams;
        } catch (IOException e) {
            throw TechnicalException.builder().message("Error processing file: " + e.getMessage()).build();
        }
    }

    private Sheet resolveSpecificSheet(Workbook workbook, String horizon, String trajectoryName) {
        Sheet sheet = findHorizonSheet(workbook, horizon);
        if (sheet != null) {
            return sheet;
        }
        throw BusinessException.builder().message("Horizon " + horizon + " does not exist in the THERMAL Specific Param trajectory " + trajectoryName).build();
    }

    private Row resolveSpecificHeader(Sheet sheet) {
        Row header = sheet.getRow(2);
        if (header == null || header.getLastCellNum() <= 0) {
            return sheet.getRow(0);
        }
        return header;
    }

    private boolean shouldSkipSpecificRow(String rowAreaUpper, boolean isOthers, String selectedAreaUpper, Set<String> studyAreasSet) {
        if (rowAreaUpper == null || rowAreaUpper.isBlank()) {
            return true;
        }
        if (!isOthers && !rowAreaUpper.equals(selectedAreaUpper)) {
            return true;
        }
        return !studyAreasSet.contains(rowAreaUpper);
    }

    private String normalizeArea(String area) {
        return area == null ? null : area.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> buildSpecificClusters(List<ThermalSpecificParametersEntity> specificParams) {
        return specificParams.stream().filter(Objects::nonNull).map(e -> e.getThermalClusterRef().getName() + "/" + (e.getArea() != null ? e.getArea().toUpperCase() : "")).collect(Collectors.toSet());
    }

    @Override
    public TrajectoryEntity saveThermalSpecificTrajectory(TrajectoryEntity trajectory, List<ThermalSpecificParametersEntity> thermalSpecificParameters, TrajectoryType type) {
        trajectory.setType(type.name());
        if (thermalSpecificParameters != null && !thermalSpecificParameters.isEmpty()) {
            thermalSpecificParameters.forEach(p -> p.setTrajectory(trajectory));
            trajectory.setThermalSpecificParameters(thermalSpecificParameters);
        }
        return trajectoryRepository.save(trajectory);


    }

    @Override
    public Set<String> getListClusterByAreaForSpecificParam(String horizon, Integer studyId, boolean mr) {
        List<ThermalSpecificParametersEntity> params = thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(studyId, horizon);

        return params.stream().filter(p -> Objects.equals(mr ? p.getMrSpecific() : p.getCmSpecific(), 1)).map(p -> (p.getArea() + "_" + p.getThermalClusterRef().getName()).toLowerCase()).collect(Collectors.toSet());
    }

    @Override
    public boolean isParamModulationRequired(String horizon, Integer studyId) {
        Set<ThermalSpecificParametersEntity> clustersWithCmOrMr = thermalSpecificParametersRepository.findPreferredEntitiesByStudyIdAndHorizon(studyId, horizon).stream().filter(p -> Objects.equals(p.getMrSpecific(), 1) || Objects.equals(p.getCmSpecific(), 1)).collect(Collectors.toSet());
        return !clustersWithCmOrMr.isEmpty();
    }

    private List<String> getStudyAreasForCurrentStudy(Integer studyId) {

        List<AreaEntity> areas = areaRepository.findAllByStudyId(studyId);
        if (areas == null) {
            return Collections.emptyList();
        }
        return areas.stream().map(a -> a.getName().toUpperCase()).toList();
    }

    private void processThermalSpecificRow(Row row, Row header, List<ThermalSpecificParametersEntity> result, String trajectoryName) {
        String clusterName = castString(getCellValue(row, 2));
        String clusterPemmdb = castString(getCellValue(row, 1));
        String areaName = castString(getCellValue(row, 0));
        log.info("Processing THERMAL Specific Param row for area: {}, cluster: {}", areaName, clusterName);

        ThermalSpecificParametersEntity entity = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(thermalClusterRef.findOrCreateThermalClusterRef(null, clusterName, clusterPemmdb))
                .node(areaName)
                .minStableGeneration(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 3), getHeaderText(header, 3), row.getRowNum())))
                .spinning(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 4), getHeaderText(header, 4), row.getRowNum())))
                .efficiency(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 5), getHeaderText(header, 5), row.getRowNum())))
                .foRate(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 6), getHeaderText(header, 6), row.getRowNum())))
                .foDuration(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 7), getHeaderText(header, 7), row.getRowNum())))
                .poDuration(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 8), getHeaderText(header, 8), row.getRowNum())))
                .poWinter(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 9), getHeaderText(header, 9), row.getRowNum())))
                .marginalCost(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 10), getHeaderText(header, 10), row.getRowNum())))
                .marketBid(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 11), getHeaderText(header, 11), row.getRowNum())))
                .mrSpecific(castInt(getCellValue(row, 12)))
                .cmSpecific(castInt(getCellValue(row, 13)))
                .npoMaxWinter(castInt(getCellValue(row, 14)))
                .npoMaxSummer(castInt(getCellValue(row, 15)))
                .nbUnit(castInt(getCellValue(row, 16)))
                .f1(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 17), getHeaderText(header, 17), row.getRowNum())))
                .f2(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 18), getHeaderText(header, 18), row.getRowNum())))
                .f3(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 19), getHeaderText(header, 19), row.getRowNum())))
                .f4(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 20), getHeaderText(header, 20), row.getRowNum())))
                .f5(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 21), getHeaderText(header, 21), row.getRowNum())))
                .f6(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 22), getHeaderText(header, 22), row.getRowNum())))
                .f7(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 23), getHeaderText(header, 23), row.getRowNum())))
                .f8(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 24), getHeaderText(header, 24), row.getRowNum())))
                .f9(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 25), getHeaderText(header, 25), row.getRowNum())))
                .f10(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 26), getHeaderText(header, 26), row.getRowNum())))
                .f11(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 27), getHeaderText(header, 27), row.getRowNum())))
                .f12(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 28), getHeaderText(header, 28), row.getRowNum())))
                .p1(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 29), getHeaderText(header, 29), row.getRowNum())))
                .p2(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 30), getHeaderText(header, 30), row.getRowNum())))
                .p3(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 31), getHeaderText(header, 31), row.getRowNum())))
                .p4(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 32), getHeaderText(header, 32), row.getRowNum())))
                .p5(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 33), getHeaderText(header, 33), row.getRowNum())))
                .p6(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 34), getHeaderText(header, 34), row.getRowNum())))
                .p7(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 35), getHeaderText(header, 35), row.getRowNum())))
                .p8(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 36), getHeaderText(header, 36), row.getRowNum())))
                .p9(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 37), getHeaderText(header, 37), row.getRowNum())))
                .p10(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 38), getHeaderText(header, 38), row.getRowNum())))
                .p11(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 39), getHeaderText(header, 39), row.getRowNum())))
                .p12(specificParamValueMustBePositive(areaName, clusterName, trajectoryName, castDouble(getCellValue(row, 40), getHeaderText(header, 40), row.getRowNum())))
                .area(areaName)
                .build();
        result.add(entity);
    }

    private Double specificParamValueMustBePositive(String areaName, String clusterName, String trajectoryName, Double value) {
        if (value != null && Double.compare(value, 0.0) < 0) {
            throw BusinessException.builder().message(VALUES_FOR_NODE_MESSAGE_PREFIX + areaName + CLUSTER_MESSAGE_SEPARATOR + clusterName + " must be positive in THERMAL Specific Param trajectory " + trajectoryName).build();
        }
        return value;
    }

    private void validateHeaderColumns(Row header, String trajectoryName) {
        List<String> expected = buildExpectedHeaderNames();
        List<Set<String>> aliases = buildHeaderAliases(expected);
        List<String> missingNames = findMissingExpectedHeaders(header, expected, aliases);

        if (!missingNames.isEmpty()) {
            throw BusinessException.builder().message("Missing columns " + String.join(", ", missingNames) + " in THERMAL Specific Param trajectory " + trajectoryName).build();
        }
    }

    private List<String> buildExpectedHeaderNames() {
        List<String> expected = new ArrayList<>(List.of("node", "cluster_PEMMDB", "cluster", "min_stable_generation", "spinning", "efficiency", "FO_rate", "FO_duration", "PO_duration", "PO_winter", "marginal_cost", "market_bid", "MR_specific", "CM_specific", "NPO_max_winter", "NPO_max_summer", "nb_unit"));
        for (int i = 1; i <= 12; i++) {
            expected.add("F" + i);
        }
        for (int i = 1; i <= 12; i++) {
            expected.add("P" + i);
        }
        return expected;
    }

    private List<Set<String>> buildHeaderAliases(List<String> expected) {
        List<Set<String>> aliases = new ArrayList<>();
        for (String s : expected) {
            Set<String> values = new HashSet<>();
            values.add(normalized(s));
            aliases.add(values);
        }
        aliases.get(2).add(normalized("cluster_name"));
        return aliases;
    }

    private List<String> findMissingExpectedHeaders(Row header, List<String> expected, List<Set<String>> aliases) {
        if (header == null) {
            return new ArrayList<>(expected);
        }

        Set<String> present = collectNormalizedPresentHeaders(header);
        List<String> missingNames = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            if (!containsAnyAlias(present, aliases.get(i))) {
                missingNames.add(expected.get(i));
            }
        }
        return missingNames;
    }

    private Set<String> collectNormalizedPresentHeaders(Row header) {
        Set<String> present = new HashSet<>();
        short last = header.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String actual = getHeaderLabel(header, i);
            if (actual != null && !actual.isBlank()) {
                present.add(normalized(actual));
            }
        }
        return present;
    }

    private boolean containsAnyAlias(Set<String> present, Set<String> aliases) {
        for (String alias : aliases) {
            if (present.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private String getHeaderLabel(Row header, int index) {
        if (header == null) return null;
        Object v = getCellValue(header, index);
        String s = castString(v);
        return s == null ? null : s.trim();
    }

    private String getHeaderText(Row header, int index) {
        String s = getHeaderLabel(header, index);
        if (s == null || s.isBlank()) return toExcelColumn(index);
        return s;
    }

    private String normalized(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void checkNumericColumns(Row row, String areaName, String clusterName, String trajectoryName) {
        for (int i = 3; i <= 40; i++) {
            Object v = getCellValue(row, i);
            if (v == null) continue; // allow blanks
            if (!(v instanceof Number)) {
                throw BusinessException.builder().message(VALUES_FOR_NODE_MESSAGE_PREFIX + areaName + CLUSTER_MESSAGE_SEPARATOR + clusterName + " are not numeric in THERMAL Specific Param trajectory " + trajectoryName).build();
            }
            double d = ((Number) v).doubleValue();
            if (d < 0) {
                throw BusinessException.builder().message(VALUES_FOR_NODE_MESSAGE_PREFIX + areaName + CLUSTER_MESSAGE_SEPARATOR + clusterName + " must be positive in THERMAL Specific Param trajectory " + trajectoryName).build();
            }
        }
    }

    private static String toExcelColumn(int index0Based) {
        int n = index0Based + 1; // convert to 1-based
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }
}
