package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalControlsServiceImpl implements ThermalControlService {

    private final StudyRepository studyRepository;
    private final TrajectoryRepository trajectoryRepository;
    public static final String SHEET_COSTS = "costs";
    public static final String SHEET_RATE = "rate";


    /**
     * Checks for missing clusters in the provided parameter clusters.
     *
     * @param studyId        The ID of the study.
     * @param horizon        The horizon for the trajectory.
     * @param paramClusters  The set of parameter clusters to check.
     * @param trajectoryType The type of the trajectory.
     * @param area           The area associated with the trajectory.
     */
    @Override
    public void checkMissingClusters(Integer studyId, String horizon, Set<String> paramClusters, TrajectoryType trajectoryType, String area) {
        Set<String> clustersWithoutParameters;

        if (area != null) {
            mergeExistingSpecificClusters(studyId, paramClusters, trajectoryType, area);
        }
        Set<String> installedPowerClusters = TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.equals(trajectoryType) ?
                getInstalledPowerClustersByStudyId(studyId, horizon) : getInstalledPowerClusterAreaByStudyId(studyId, horizon, area);

        if (!installedPowerClusters.isEmpty()) {
            clustersWithoutParameters = installedPowerClusters.stream()
                    .filter(cluster -> !paramClusters.contains(cluster))
                    .collect(Collectors.toSet());

            if (!clustersWithoutParameters.isEmpty()) {
                var paramType = trajectoryType.equals(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER) ? "Common" : "Specific";
                throw BusinessException.builder()
                        .message("Clusters : " + String.join(", ", clustersWithoutParameters) + " are not in " + paramType + " trajectory")
                        .build();
            }
        }
    }

    /**
     * Verifies that all clusters in the thermal installed power trajectory are present in the common parameter trajectory.
     *
     * @param studyId    The ID of the study.
     * @param horizon    The horizon for the trajectory.
     * @param capacities The list of thermal cluster capacities.
     */
    @Override
    public void verifyClustersInCommonParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities) {

        TrajectoryEntity thermalCommonParamTrajectory = trajectoryRepository
                .findByTypeAndStudyId(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name(), studyId).stream().findFirst().orElse(null);

        if (thermalCommonParamTrajectory != null) {
            Set<String> commonParamClusters = thermalCommonParamTrajectory.getThermalCommonParameters().stream()
                    .map(e -> e.getThermalClusterRef().getName())
                    .collect(Collectors.toSet());

            Set<String> installedPowerClusters = getInstalledPowerClustersByStudyId(studyId, horizon);
            capacities.forEach(e -> installedPowerClusters.add(e.getThermalClusterRef().getName()));

            List<String> missingClusters = installedPowerClusters.stream()
                    .filter(cluster -> !commonParamClusters.contains(cluster))
                    .toList();

            if (!missingClusters.isEmpty()) {
                throw BusinessException.builder()
                        .message("Clusters " + String.join(", ", missingClusters) +
                                " are not in Common trajectory " + thermalCommonParamTrajectory.getFileName())
                        .build();
            }
        }
    }

    /**
     * Verifies that all clusters in the thermal installed power trajectory are present in the specific parameter trajectory.
     *
     * @param studyId    The ID of the study.
     * @param horizon    The horizon for the trajectory.
     * @param capacities The list of thermal cluster capacities.
     */
    @Override
    public void verifyClustersInSpecificParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities) {
        List<TrajectoryEntity> specificParamTrajectories = trajectoryRepository
                .findByTypeAndStudyId(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name(), studyId);

        if (!specificParamTrajectories.isEmpty()) {
            // Récupère tous les clusters/areas présents dans les trajectoires spécifiques
            Set<String> specificParamAreaClusters = specificParamTrajectories.stream()
                    .map(TrajectoryEntity::getThermalSpecificParameters)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .map(e -> e.getThermalClusterRef().getName() + "/" + (e.getArea() != null ? e.getArea() : ""))
                    .collect(Collectors.toSet());

            // Récupère tous les clusters/areas de Installed Power existants + en cours d'import
            Set<String> installedPowerAreaClusters = getInstalledPowerClusterAreaByStudyId(studyId, horizon, OTHERS_AREA);
            capacities.forEach(e -> installedPowerAreaClusters.add(
                    e.getThermalClusterRef().getName() + "/" + (e.getArea() != null ? e.getArea() : "")));

            List<String> missingAreaClusters = installedPowerAreaClusters.stream()
                    .filter(ac -> !specificParamAreaClusters.contains(ac))
                    .toList();

            if (!missingAreaClusters.isEmpty()) {
                String trajectoryName = specificParamTrajectories.getFirst().getFileName();
                throw BusinessException.builder()
                        .message("Clusters " + String.join(", ", missingAreaClusters) +
                                " are not in Specific trajectory " + trajectoryName)
                        .build();
            }
        }
    }

    @Override
    public void verifyCostsTrajectory(String horizon, Path trajectoryFilePath, String trajectoryName, Integer studyId) throws IOException {

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet costsSheet = workbook.getSheet(SHEET_COSTS);
            Sheet rateSheet = workbook.getSheet(SHEET_RATE);

            if (costsSheet == null && rateSheet == null) {
                throw BusinessException.builder()
                        .message("Missing costs/rate data in trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            } else if (costsSheet == null) {
                throw BusinessException.builder()
                        .message("Missing costs data in trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            } else if (rateSheet == null) {
                throw BusinessException.builder()
                        .message("Missing rate data in trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Vérification de l'onglet costs
            Row headerCosts = costsSheet.getRow(0);
            Integer horizonColCosts = findHorizonColumnIndex(headerCosts, horizon);

            if (horizonColCosts == null) {
                throw BusinessException.builder()
                        .message("Horizon does not exist in THERMAL Costs trajectory {0} in costs tab")
                        .errorMessageArguments(List.of(horizon, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Vérifier qu'il y a au moins une donnée dans la colonne horizon (costs)
            boolean hasDataInHorizonColCosts = false;
            for (int r = 1; r <= costsSheet.getLastRowNum(); r++) {
                Row row = costsSheet.getRow(r);
                if (row != null) {
                    Object value = getCellValue(row, horizonColCosts);
                    if (value != null && (!(value instanceof String) || !((String) value).trim().isEmpty())) {
                        hasDataInHorizonColCosts = true;
                        break;
                    }
                }
            }

            if (!hasDataInHorizonColCosts) {
                throw BusinessException.builder()
                        .message("No data for horizon {0} in THERMAL Costs trajectory {1} in costs tab")
                        .errorMessageArguments(List.of(horizon, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            validateDataCells(costsSheet, trajectoryName, SHEET_COSTS, horizonColCosts);

            // Vérification de l'onglet rate
            Row headerRate = rateSheet.getRow(0);
            Integer horizonColRate = findHorizonColumnIndex(headerRate, horizon);

            if (horizonColRate == null) {
                throw BusinessException.builder()
                        .message("Horizon does not exist in THERMAL Costs trajectory {1} in rate tab")
                        .errorMessageArguments(List.of(horizon, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Vérifier qu'il y a au moins une donnée dans la colonne horizon (rate)
            boolean hasDataInHorizonColRate = false;
            for (int r = 1; r <= rateSheet.getLastRowNum(); r++) {
                Row row = rateSheet.getRow(r);
                if (row != null) {
                    Object value = getCellValue(row, horizonColRate);
                    if (value != null && (!(value instanceof String) || !((String) value).trim().isEmpty())) {
                        hasDataInHorizonColRate = true;
                        break;
                    }
                }
            }

            if (!hasDataInHorizonColRate) {
                throw BusinessException.builder()
                        .message("No data for horizon {0} in THERMAL Costs trajectory {1} in rate tab")
                        .errorMessageArguments(List.of(horizon, trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            validateDataCells(rateSheet, trajectoryName, SHEET_RATE, horizonColRate);

        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Cannot open trajectory file {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }


    private void mergeExistingSpecificClusters(Integer studyId, Set<String> paramClusters, TrajectoryType trajectoryType, String area) {
        if (trajectoryType.equals(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER)) {
            Set<String> existingSpecificsClustersAreaInBd;
            if (area.equals(OTHERS_AREA)) {
                existingSpecificsClustersAreaInBd = trajectoryRepository
                        .findByTypeAndStudyId(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name(), studyId)
                        .stream()
                        .map(TrajectoryEntity::getThermalSpecificParameters)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .map(e -> e.getThermalClusterRef().getName() + "/" + (e.getArea() != null ? e.getArea() : ""))
                        .collect(Collectors.toSet());
            } else {
                existingSpecificsClustersAreaInBd = trajectoryRepository
                        .findByTypeAndStudyId(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name(), studyId)
                        .stream()
                        .filter(e -> e.getArea().equals(OTHERS_AREA))
                        .map(TrajectoryEntity::getThermalSpecificParameters)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(e -> e.getArea().equals(area))
                        .map(e -> e.getThermalClusterRef().getName() + "/" + (e.getArea() != null ? e.getArea() : ""))
                        .collect(Collectors.toSet());
            }

            // Ensure mutable before modification
            if (!(paramClusters instanceof HashSet)) {
                paramClusters = new HashSet<>(paramClusters);
            }

            paramClusters.addAll(existingSpecificsClustersAreaInBd);
        }
    }

    public Set<String> getInstalledPowerClustersByStudyId(Integer studyId, String horizon) {
        return getInstalledPowerClusters(studyId, horizon, null);
    }

    public Set<String> getInstalledPowerClusterAreaByStudyId(Integer studyId, String horizon, String area) {
        return getInstalledPowerClusters(studyId, horizon, area);
    }

    @Override
    public void verifyThermalFuel(Integer studyId, String horizon, String trajectoryName, Set<String> listEconomicFuel, TrajectoryType trajectoryType) throws IOException {
        //horizon pattern yyyy-yyyy+1
        Pattern horizonPattern = Pattern.compile("^(\\d{4})-(\\d{4})$");
        Matcher horizonMatcher = horizonPattern.matcher(horizon);
        String buildHorizon = horizonMatcher.matches() ? horizon : Integer.parseInt(horizon) - 1 + "-" + horizon;

        Set<String> listFuelOfThermalCommonParam = getListTechnologyOfThermalCommonParam(studyId, buildHorizon);

        listFuelOfThermalCommonParam.forEach(commonFuel -> {
            if (!listEconomicFuel.isEmpty() && !listEconomicFuel.contains(commonFuel.toLowerCase())) {
                final String typeTrajectoryName = trajectoryType.equals(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER) ? "Cost" : "Economic";
                throw BusinessException.builder()
                        .message("Fuel {0} does not exist in "+ typeTrajectoryName+" Trajectory {1} for horizon {2}")
                        .errorMessageArguments(List.of(commonFuel, trajectoryName, horizon))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        });
    }

    private Set<String> getListTechnologyOfThermalCommonParam(Integer studyId, String buildHorizon) {

        StudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Study with id {0} not found")
                        .errorMessageArguments(List.of(studyId.toString()))
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .build());

        // Collect thermal cluster names from capacity trajectories
        Set<String> capacityClusters = study.getTrajectories().stream()
                .filter(t -> TrajectoryType.THERMAL_CAPACITY.name().equals(t.getType()) && buildHorizon.equals(t.getHorizon()))
                .flatMap(t -> t.getThermalClusterCapacities().stream())
                .map(ThermalClusterCapacityEntity::getThermalClusterRef)
                .map(ThermalClusterRef::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Collect technologies for matching common parameters
        return study.getTrajectories().stream()
                .filter(t -> TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name().equals(t.getType()) && buildHorizon.equals(t.getHorizon()))
                .flatMap(t -> t.getThermalCommonParameters().stream())
                .filter(p ->
                        capacityClusters.isEmpty() || capacityClusters.contains(p.getThermalClusterRef().getName().toLowerCase()))
                .map(p -> p.getFuel().toLowerCase())
                .collect(Collectors.toSet());
    }


    /**
     * Méthode générique utilisée par les deux précédentes.
     */
    private Set<String> getInstalledPowerClusters(Integer studyId, String horizon, String area) {
        List<TrajectoryEntity> installedTrajectories = trajectoryRepository
                .findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name());

        Stream<ThermalClusterCapacityEntity> capacityStream = installedTrajectories.stream()
                .map(TrajectoryEntity::getThermalClusterCapacities)
                .filter(Objects::nonNull)
                .flatMap(List::stream);

        if (area == null) {
            // Cas getInstalledPowerClustersByStudyId
            return capacityStream
                    .map(e -> e.getThermalClusterRef().getName())
                    .collect(Collectors.toSet());
        } else {
            // Cas getInstalledPowerClusterAreaByStudyId
            return capacityStream
                    .filter(e -> e.getArea().equals(area) || area.equals(OTHERS_AREA))
                    .map(e -> String.format("%s/%s",
                            e.getThermalClusterRef().getName(),
                            Optional.of(e.getArea()).orElse("")))
                    .collect(Collectors.toSet());
        }
    }

    public void validateDataCells(Sheet sheet, String trajectoryName, String tabName, Integer horizonCol) {
        Row header = sheet.getRow(0);
        if (header == null || horizonCol == null) return;

        int firstDataRow = 1;

        // Valider l'en-tête de la colonne horizon
        if (isCellBlank(header.getCell(horizonCol))) {
            String colLetter = org.apache.poi.ss.util.CellReference.convertNumToColString(horizonCol);
            throw BusinessException.builder()
                    .message("Null or empty header not allowed at column {0} in THERMAL Costs trajectory {1} in {2} tab")
                    .errorMessageArguments(List.of(colLetter, trajectoryName, tabName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Valider les cellules de la colonne horizon
        for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Cell cell = row.getCell(horizonCol);
            if (isCellBlank(cell)) continue; // Ignorer les cellules vides

            Object value = getCellValue(row, horizonCol);
            String columnName = getColumnName(header.getCell(horizonCol));

            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                throw BusinessException.builder()
                        .message("Null value not allowed for column {0} in THERMAL Costs trajectory {1} in {2} tab")
                        .errorMessageArguments(List.of(columnName, trajectoryName, tabName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (!isNumeric(value)) {
                throw BusinessException.builder()
                        .message("The value of power or number of horizon {0} in THERMAL Costs trajectory {1} in {2} tab must be numeric")
                        .errorMessageArguments(List.of(columnName, trajectoryName, tabName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }


    // Utility to check if a cell is null or blank
    private boolean isCellBlank(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return true;
        Object value = getCellValue(cell.getRow(), cell.getColumnIndex());
        return value instanceof String && ((String) value).trim().isEmpty();
    }

    // Utility to check if a value is numeric (Number or numeric string)
    private boolean isNumeric(Object value) {
        if (value instanceof Number) return true;
        if (value instanceof String) {
            try {
                Double.parseDouble(((String) value).trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }


    /**
     * Converts a cell value to String (handles STRING and NUMERIC types)
     */
    private String getColumnName(Cell cell) {
        if (cell == null) {
            return "UNKNOWN";
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((int) cell.getNumericCellValue());
        }
        return cell.getStringCellValue();
    }


}
