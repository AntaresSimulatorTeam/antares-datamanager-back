package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@AllArgsConstructor
public class ThermalEconomicServiceImpl implements ThermalEconomicService {

    public static final String SHEET_CO2 = "CO2_emissions";
    public static final String SHEET_ENR = "ener_content";

    private final UserService userService;
    private final TrajectoryRepository trajectoryRepository;
    private final ThermalControlService thermalControlService;

    @Override
    public List<ThermalEconomicCo2Entity> buildThermalEconomicCo2ParameterValuesList(String trajectoryFileName, String horizon, Integer studyId, Sheet co2Sheet) throws IOException {
        List<ThermalEconomicCo2Entity> thermalEconomicCo2EntityList = parseCo2Sheet(co2Sheet, horizon, trajectoryFileName);
        Set<String> listTechnology = thermalEconomicCo2EntityList.stream().map(ThermalEconomicCo2Entity::getFuel).map(String::toLowerCase).collect(Collectors.toSet());
        thermalControlService.verifyThermalFuel(studyId, horizon, trajectoryFileName, listTechnology, TrajectoryType.THERMAL_ECONOMIC_PARAMETER);
        return thermalEconomicCo2EntityList;

    }

    @Override
    public List<ThermalEconomicEnerContentEntity> buildThermalEconomicEnerContentParameterValuesList(String trajectoryFileName, String horizon, Integer studyId, Sheet enerSheet) throws IOException {
        return parseEnerSheet(enerSheet, trajectoryFileName, horizon);
    }

    @Override
    public TrajectoryEntity processThermalEconomicParameterFile(Path trajectoryFilePath, String horizon, List<ThermalEconomicCo2Entity> thermalEconomicCo2Entities, List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContentEntities, TrajectoryType trajectoryType) throws IOException {
        String trajectoryTypeName = trajectoryType != null ? trajectoryType.name() : TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name();
        String trajectoryFileName = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), trajectoryTypeName);
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        // Find existing trajectory for same file name/horizon/type
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryFileName, horizon, trajectoryTypeName);

        TrajectoryEntity trajectory;
        String checksum = calculateThermalEconomicChecksum(thermalEconomicCo2Entities, thermalEconomicEnerContentEntities);

        if (existingTrajectoryOpt.isPresent() && existingTrajectoryOpt.get().getChecksum() != null) {
            if (existingTrajectoryOpt.get().getChecksum().equals(checksum)) {
                throwAlreadyProcessedFileException(trajectoryFilePath);
            }
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingTrajectoryOpt.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_PARAMETER, null, null, null, false);
        } else {
            // No existing or different file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_PARAMETER, null, null, null, false);
        }
        trajectory.setChecksum(checksum);
        trajectory.setType(trajectoryTypeName);
        thermalEconomicCo2Entities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        thermalEconomicEnerContentEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        trajectory.setThermalEconomicCo2s(thermalEconomicCo2Entities);
        trajectory.setThermalEconomicEnerContents(thermalEconomicEnerContentEntities);
        return trajectoryRepository.save(trajectory);

    }

    // java
    private List<ThermalEconomicCo2Entity> parseCo2Sheet(Sheet sheet, String horizon, String trajectoryFileName) {
        List<ThermalEconomicCo2Entity> list = new ArrayList<>();
        if (sheet == null) return list;

        boolean foundAnyDataRow = false;
        boolean foundAnyMatchingHorizon = false;
        Integer horizonYear = parseInteger(horizon.split("-")[1]);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header

            String fuel = getCellString(row, 0);
            String country = getCellString(row, 1);
            String yearStr = getCellString(row, 2);
            String co2Str = getCellString(row, 3);
            String unitCo2 = getCellString(row, 4);
            String comment = getCellString(row, 5);

            // ignorer les lignes entièrement vides
            if (isCo2RowEmpty(fuel, country, yearStr, co2Str, unitCo2, comment)) {
                continue;
            }

            foundAnyDataRow = true;

            Integer year = parseInteger(yearStr);

            // filtrage horizon : si l'année est renseignée et différente -> ligne à ignorer
            if (!matchesHorizon(year, horizonYear)) {
                continue;
            }

            // si on arrive ici, la ligne est considérée comme correspondant à l'horizon (year == null ou égal)
            foundAnyMatchingHorizon = true;

            BigDecimal co2 = parseBigDecimal(co2Str);
            if (co2 == null) {
                throw BusinessException.builder()
                        .message("The value of CO2_EmissionFuel of horizon {0} in THERMAL Economic trajectory {1} in CO2_emissions  tab must be numeric")
                        .errorMessageArguments(List.of(horizon, trajectoryFileName))
                        .build();
            }

            list.add(buildCo2Entity(fuel, country, year, co2, unitCo2, comment));
        }

        if (!foundAnyDataRow) {
            throw BusinessException.builder()
                    .message("No data in THERMAL Economic trajectory {0} in CO2_emissions tab")
                    .errorMessageArguments(List.of(trajectoryFileName))
                    .build();
        }

        if (!foundAnyMatchingHorizon) {
            throw BusinessException.builder()
                    .message("Horizon does not exist in THERMAL Economic trajectory {0} in CO2_emissions tab")
                    .errorMessageArguments(List.of(trajectoryFileName))
                    .build();
        }

        return list;
    }

    private boolean isCo2RowEmpty(String fuel, String country, String yearStr, String co2Str, String unitCo2, String comment) {
        return fuel.isEmpty() && country.isEmpty() && yearStr.isBlank() && co2Str.isBlank() && unitCo2.isEmpty() && comment.isEmpty();
    }

    private boolean matchesHorizon(Integer year, Integer horizonYear) {
        return year == null || horizonYear == null || year.equals(horizonYear);
    }

    private ThermalEconomicCo2Entity buildCo2Entity(String fuel, String country, Integer year, BigDecimal co2, String unitCo2, String comment) {
        ThermalEconomicCo2Entity entity = new ThermalEconomicCo2Entity();
        entity.setFuel(fuel);
        entity.setCountry(country);
        if (year != null) {
            entity.setYear(year);
        }
        entity.setCo2EmissionFuel(co2);
        entity.setUnitCo2(unitCo2);
        entity.setComment(comment);
        return entity;
    }



    // java
    private List<ThermalEconomicEnerContentEntity> parseEnerSheet(Sheet sheet, String trajectoryFileName, String horizon) {
        List<ThermalEconomicEnerContentEntity> list = new ArrayList<>();
        if (sheet == null) return list;

        boolean onlyHeader = true;

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String valueStr = getCellString(row, 0);
            String unit = getCellString(row, 1);
            String comment = getCellString(row, 2);

            // ignorer les lignes entièrement vides
            if (valueStr.isEmpty() && unit.isEmpty() && comment.isEmpty()) {
                continue;
            }

            // une ligne contient des données -> tenter de parser la valeur
            BigDecimal value = parseBigDecimal(valueStr);
            if (value == null) {
                throw BusinessException.builder()
                        .message("The value of value of horizon {0} in THERMAL Economic trajectory {1} in ener_content  tab must be numeric")
                        .errorMessageArguments(List.of(horizon, trajectoryFileName))
                        .build();
            }

            ThermalEconomicEnerContentEntity e = new ThermalEconomicEnerContentEntity();
            e.setValue(value);
            e.setUnit(unit);
            e.setComment(comment);
            list.add(e);
            onlyHeader = false;
        }

        if (onlyHeader) {
            throw BusinessException.builder()
                    .message("No data in THERMAL Economic trajectory {0} in ener_content tab")
                    .errorMessageArguments(List.of(trajectoryFileName))
                    .build();
        }

        return list;
    }


    private String getCellString(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) return "";
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue().trim();
        if (c.getCellType() == CellType.NUMERIC) {
            double d = c.getNumericCellValue();
            if (Math.floor(d) == d) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        return c.toString().trim();
    }

    private Integer parseInteger(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            String normalized = s.replace(",", ".");
            if (normalized.isBlank()) return null;
            return new BigDecimal(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private String calculateThermalEconomicChecksum(List<ThermalEconomicCo2Entity> thermalEconomicCo2Entities, List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContentEntities) {
        StringBuilder sb = new StringBuilder();

        if (thermalEconomicCo2Entities != null) {
            for (ThermalEconomicCo2Entity entity : thermalEconomicCo2Entities) {
                if (entity != null) {
                    sb.append(entity.getFuel()).append(entity.getCountry()).append(entity.getYear()).append(entity.getCo2EmissionFuel()).append(entity.getUnitCo2()).append(entity.getComment()).append("|");
                }
            }
        }

        if (thermalEconomicEnerContentEntities != null) {
            for (ThermalEconomicEnerContentEntity entity : thermalEconomicEnerContentEntities) {
                if (entity != null) {
                    sb.append(entity.getValue()).append(entity.getUnit()).append(entity.getComment()).append("|");
                }
            }
        }

        return Integer.toHexString(sb.toString().hashCode());
    }
}
