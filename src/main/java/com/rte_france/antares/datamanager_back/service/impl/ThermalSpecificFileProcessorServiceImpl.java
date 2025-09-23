package com.rte_france.antares.datamanager_back.service.impl;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.ThermalSpecificFileProcessorService;
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

import static com.rte_france.antares.datamanager_back.util.CastCellUtil.*;
import static com.rte_france.antares.datamanager_back.util.Utils.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalSpecificFileProcessorServiceImpl implements ThermalSpecificFileProcessorService {

    private final ThermalFileProcessorServiceImpl thermalFileProcessorService;
    private final AreaRepository areaRepository;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    /**
     * Builds a list of ThermalSpecificParametersEntity objects based on the provided trajectory file
     * and associated parameters such as trajectory name, horizon, area, and study ID.
     * This method processes the thermal specific parameter trajectory file, validates data,
     * and checks for business logic constraints.
     *
     * @param trajectoryName the name of the thermal specific parameter trajectory
     * @param trajectoryFilePath the file path of the thermal specific parameter trajectory
     * @param horizon the horizon identifier to locate the specific sheet in the file
     * @param area the selected area for which the data is being validated and processed
     * @param studyId the identifier of the study, used for validating associated areas
     * @return a list of ThermalSpecificParametersEntity objects based on the valid rows of the trajectory file
     * @throws BusinessException if there are validation or business logic errors
     * @throws TechnicalException if there are issues processing the file
     */
    @Override
    public List<ThermalSpecificParametersEntity> buildThermalSpecificParameterValueList(String trajectoryName, Path trajectoryFilePath, String horizon, String area, Integer studyId) {
        List<ThermalSpecificParametersEntity> specificParams = new ArrayList<>();
        Set<String> otherAreas = new HashSet<>();
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findHorizonSheet(workbook, horizon);
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("Horizon " + horizon + " does not exist in the THERMAL Specific Param trajectory " + trajectoryName)
                        .build();
            }

            Row header = sheet.getRow(2);
            if (header == null || header.getLastCellNum() <= 0) {
                header = sheet.getRow(0); // fallback: some files place header at the first row
            }
            validateHeaderColumns(header, trajectoryName);


            for (Row row : sheet) {
                if (row.getRowNum() <= 2) continue; // skip headers/metadata lines (data from line 4)

                String rowArea = castString(getCellValue(row, 0));
                rowArea = rowArea == null ? null : rowArea.trim();
                if (rowArea == null || rowArea.isBlank()) {
                    continue; // ignore empty lines
                }
                String rowAreaUpper = rowArea.toUpperCase();

                otherAreas.add(rowAreaUpper);

                String clusterName = castString(getCellValue(row, 4));
                // Validate cluster name: must be present and known
                if (clusterName == null || clusterName.isBlank() || !thermalFileProcessorService.clusterExistsByName(clusterName)) {
                    // Keep the original message format used by existing tests (cluster name may be blank)
                    throw BusinessException.builder()
                            .message("Cluster " + (clusterName == null ? "" : clusterName) + " does not exist in THERMAL Specific Param trajectory " + trajectoryName)
                            .build();
                }

                checkNumericColumns(row, header, rowArea, clusterName, trajectoryName);
                processThermalSpecificRow(row, header, specificParams);

            }

            if (specificParams.isEmpty()) {
                throw BusinessException.builder()
                        .message("No area found in THERMAL Specific Param trajectory " + trajectoryName)
                        .build();
            }

            // Business rule: If none of the areas from the study's AREA trajectory are present in the file,
            // raise a BusinessException for the THERMAL Specific Param trajectory, regardless of selected area.
            List<String> studyAreas = getStudyAreasForCurrentStudy(studyId);
            if (studyAreas != null && !studyAreas.isEmpty()) {
                boolean anyPresent = otherAreas.stream().anyMatch(studyAreas::contains);
                if (!anyPresent) {
                    throw BusinessException.builder()
                            .message("None of the areas of trajectory AREA are present in THERMAL Specific Param trajectory " + trajectoryName)
                            .build();
                }
            }


            return specificParams;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error processing file: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Processes a specific thermal file and generates or updates a trajectory entity based on the given inputs.
     *
     * @param trajectoryFilePath the path of the thermal trajectory file to process
     * @param horizon the horizon identifier to associate with the trajectory
     * @param params the list of thermal-specific parameters to attach to the trajectory
     * @param trajectoryType the type of trajectory to be processed
     * @return the newly created or updated trajectory entity
     * @throws TechnicalException if an error occurs while processing the thermal file
     */
    @Override
    public TrajectoryEntity processSpecificThermalFile(Path trajectoryFilePath, String horizon, List<ThermalSpecificParametersEntity> params, TrajectoryType trajectoryType) {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";

        try {

            Optional<TrajectoryEntity> existingOpt = findExistingSpecificTrajectory(trajectoryFilePath, horizon);

            TrajectoryEntity trajectory;
            if (existingOpt.isPresent()) {
                if (checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {

                    trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon, createdBy, trajectoryType, null, null);
                } else {

                    trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, trajectoryType, null, null);
                }
            } else {

                trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, trajectoryType, null, null);
            }

            // Attach parameters to the trajectory
            if (params != null) {
                params.forEach(p -> p.setTrajectory(trajectory));
                trajectory.setThermalSpecificParameters(params);
            }

            return saveThermalSpecificTrajectory(trajectory, params, trajectoryType);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error building trajectory: " + e.getMessage())
                    .build();
        }
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

    // Local lookup for THERMAL specific parameter trajectories (moved from ThermalFileProcessorServiceImpl)
    private Optional<TrajectoryEntity> findExistingSpecificTrajectory(Path path, String horizon) {
        return trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name()),
                horizon,
                TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name()
        );
    }

    private List<String> getStudyAreasForCurrentStudy(Integer studyId) {

        List<com.rte_france.antares.datamanager_back.repository.model.AreaEntity> areas = areaRepository.findAllByStudyId(studyId);
        if (areas == null) {
            return Collections.emptyList();
        }
        return areas.stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }

    private void processThermalSpecificRow(Row row, Row header, List<ThermalSpecificParametersEntity> result) {

        String clusterName = castString(getCellValue(row, 4));
        String clusterPemmdb = castString(getCellValue(row, 3));

        ThermalSpecificParametersEntity entity = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(thermalFileProcessorService.findOrCreateThermalClusterRef(null, clusterName, clusterPemmdb))
                .node(castString(getCellValue(row, 0)))
                .nodeEntsoe(castString(getCellValue(row, 1)))
                .comment(castString(getCellValue(row, 2)))
                .minStableGeneration(castDouble(getCellValue(row, 5), getHeaderText(header, 5)))
                .spinning(castDouble(getCellValue(row, 6), getHeaderText(header, 6)))
                .efficiency(castDouble(getCellValue(row, 7), getHeaderText(header, 7)))
                .foRate(castDouble(getCellValue(row, 8), getHeaderText(header, 8)))
                .foDuration(castDouble(getCellValue(row, 9), getHeaderText(header, 9)))
                .poDuration(castDouble(getCellValue(row, 10), getHeaderText(header, 10)))
                .poWinter(castDouble(getCellValue(row, 11), getHeaderText(header, 11)))
                .marginalCost(castDouble(getCellValue(row, 12), getHeaderText(header, 12)))
                .marketBid(castDouble(getCellValue(row, 13), getHeaderText(header, 13)))
                .mrSpecific(castInt(getCellValue(row, 14)))
                .cmSpecific(castInt(getCellValue(row, 15)))
                .npoMaxWinther(castInt
                        (getCellValue(row, 16)))
                .npoMaxSummer(castInt
                        (getCellValue(row, 17)))
                .nbUnit(castInt
                        (getCellValue(row, 18)))
                .poWinterRate(castDouble(getCellValue(row, 19), getHeaderText(header, 19)))
                .f1(castDouble(getCellValue(row, 20), getHeaderText(header, 20)))
                .f2(castDouble(getCellValue(row, 21), getHeaderText(header, 21)))
                .f3(castDouble(getCellValue(row, 22), getHeaderText(header, 22)))
                .f4(castDouble(getCellValue(row, 23), getHeaderText(header, 23)))
                .f5(castDouble(getCellValue(row, 24), getHeaderText(header, 24)))
                .f6(castDouble(getCellValue(row, 25), getHeaderText(header, 25)))
                .f7(castDouble(getCellValue(row, 26), getHeaderText(header, 26)))
                .f8(castDouble(getCellValue(row, 27), getHeaderText(header, 27)))
                .f9(castDouble(getCellValue(row, 28), getHeaderText(header, 28)))
                .f10(castDouble(getCellValue(row, 29), getHeaderText(header, 29)))
                .f11(castDouble(getCellValue(row, 30), getHeaderText(header, 30)))
                .f12(castDouble(getCellValue(row, 31), getHeaderText(header, 31)))
                .p1(castDouble(getCellValue(row, 32), getHeaderText(header, 32)))
                .p2(castDouble(getCellValue(row, 33), getHeaderText(header, 33)))
                .p3(castDouble(getCellValue(row, 34), getHeaderText(header, 34)))
                .p4(castDouble(getCellValue(row, 35), getHeaderText(header, 35)))
                .p5(castDouble(getCellValue(row, 36), getHeaderText(header, 36)))
                .p6(castDouble(getCellValue(row, 37), getHeaderText(header, 37)))
                .p7(castDouble(getCellValue(row, 38), getHeaderText(header, 38)))
                .p8(castDouble(getCellValue(row, 39), getHeaderText(header, 39)))
                .p9(castDouble(getCellValue(row, 40), getHeaderText(header, 40)))
                .p10(castDouble(getCellValue(row, 41), getHeaderText(header, 41)))
                .p11(castDouble(getCellValue(row, 42), getHeaderText(header, 42)))
                .p12(castDouble(getCellValue(row, 43), getHeaderText(header, 43)))
                .build();
        result.add(entity);
    }

    private void validateHeaderColumns(Row header, String trajectoryName) {
        // Primary expected names (used for error message)
        List<String> expected = new ArrayList<>(List.of(
                "node",
                "node_ENTSOE",
                "comments",
                "cluster_PEMMDB",
                "cluster",
                "min_stable_generation",
                "spinning",
                "efficiency",
                "FO_rate",
                "FO_duration",
                "PO_duration",
                "PO_winter",
                "marginal_cost",
                "market_bid",
                "MR_specific",
                "CM_specific",
                "NPO_max_winter",
                "NPO_max_summer",
                "nb_unit",
                "PO_winter_rate"
        ));
        for (int i = 1; i <= 12; i++) expected.add("F" + i);
        for (int i = 1; i <= 12; i++) expected.add("P" + i);

        // Allowed aliases per index (normalized)
        List<Set<String>> aliases = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) aliases.add(new HashSet<>());
        // Fill aliases with each own expected normalized value
        for (int i = 0; i < expected.size(); i++) aliases.get(i).add(normalized(expected.get(i)));
        // Additional accepted variants
        aliases.get(2).add(normalized("comment")); // comments/comment
        aliases.get(4).add(normalized("cluster_name")); // cluster/cluster_name

        List<String> missingNames = new ArrayList<>();
        if (header == null) {
            missingNames.addAll(expected);
        } else {
            // Collect all present header labels (normalized) regardless of index
            Set<String> present = new HashSet<>();
            short last = header.getLastCellNum();
            for (int i = 0; i < last; i++) {
                String actual = getHeaderLabel(header, i);
                if (actual == null || actual.isBlank()) continue;
                present.add(normalized(actual));
            }
            // For each expected column, check if any alias is present
            for (int i = 0; i < expected.size(); i++) {
                boolean found = false;
                for (String alias : aliases.get(i)) {
                    if (present.contains(alias)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missingNames.add(expected.get(i));
                }
            }
        }

        if (!missingNames.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing columns " + String.join(", ", missingNames) + " THERMAL Specific Param trajectory " + trajectoryName)
                    .build();
        }
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

    private void checkNumericColumns(Row row, Row header, String areaName, String clusterName, String trajectoryName) {
        for (int i = 5; i <= 43; i++) { // Columns F to AR
            Object v = getCellValue(row, i);
            if (v == null) continue; // allow blanks
            if (!(v instanceof Number)) {
                throw BusinessException.builder()
                        .message("Values for node " + areaName + " / cluster " + clusterName + " are not numeric in THERMAL Specific Param trajectory " + trajectoryName)
                        .build();
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
