package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.Utils.buildTrajectory;

@Slf4j
@Service
@AllArgsConstructor
public class ThermalEconomicServiceImpl implements ThermalEconomicService {

    private static final String SHEET_CO2 = "CO2_emissions";
    private static final String SHEET_ENR = "ener_content";

    private final UserService userService;
    private final TrajectoryRepository trajectoryRepository;

    @Override
    public List<ThermalEconomicCo2Entity> buildThermalEconomicCo2ParameterValuesList(Path trajectoryFilePath, String horizon, Integer studyId) throws IOException {

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findHorizonSheet(workbook, SHEET_CO2);
          return  parseCo2Sheet(sheet, horizon);
        }
    }

    @Override
    public List<ThermalEconomicEnerContentEntity> buildThermalEconomicEnerContentParameterValuesList(Path trajectoryFilePath, String horizon, Integer studyId) throws IOException {
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findHorizonSheet(workbook, SHEET_ENR);
            return  parseEnerSheet(sheet);
        }
    }

    @Override
    public TrajectoryEntity processThermalEconomicParameterFile(Path trajectoryFilePath, String horizon, List<ThermalEconomicCo2Entity> thermalEconomicCo2Entities, List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContentEntities, TrajectoryType trajectoryType) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        // Find existing trajectory for same file name/horizon/type
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name()),
                horizon,
                TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name()
        );

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_PARAMETER, null, null);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_PARAMETER, null, null);
        }
        trajectory.setType(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name());
        thermalEconomicCo2Entities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        thermalEconomicEnerContentEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        trajectory.setThermalEconomicCo2s(thermalEconomicCo2Entities);
        trajectory.setThermalEconomicEnerContents(thermalEconomicEnerContentEntities);
        return trajectoryRepository.save(trajectory);

    }


    private List<ThermalEconomicCo2Entity> parseCo2Sheet(Sheet sheet, String horizon) {
        List<ThermalEconomicCo2Entity> list = new ArrayList<>();
        if (sheet == null) return list;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Integer year = parseInteger(getCellString(row, 2));
            Integer horizonYear = parseInteger(horizon.split("-")[1]);
            if( year !=null && !year.equals(horizonYear)) continue;
            String fuel = getCellString(row, 0);
            String country = getCellString(row, 1);
            BigDecimal co2 = parseBigDecimal(getCellString(row, 3));
            String unitCo2 = getCellString(row, 4);
            String comment = getCellString(row, 5);

            if (fuel.isEmpty() && country.isEmpty() && year == null && co2 == null) continue;

            ThermalEconomicCo2Entity e = new ThermalEconomicCo2Entity();
            e.setFuel(fuel);
            e.setCountry(country);
            if (year != null) e.setYear(year);
            e.setCo2EmissionFuel(co2 != null ? co2 : BigDecimal.ZERO);
            e.setUnitCo2(unitCo2);
            e.setComment(comment);
            list.add(e);
        }
        return list;
    }

    private List<ThermalEconomicEnerContentEntity> parseEnerSheet(Sheet sheet) {
        List<ThermalEconomicEnerContentEntity> list = new ArrayList<>();
        if (sheet == null) return list;

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            BigDecimal value = parseBigDecimal(getCellString(row, 0));
            String unit = getCellString(row, 1);
            String comment = getCellString(row, 2);

            if (value == null) continue;

            ThermalEconomicEnerContentEntity e = new ThermalEconomicEnerContentEntity();
            e.setValue(value);
            e.setUnit(unit);
            e.setComment(comment);
            list.add(e);
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
            String normalized = s.replace(",", ".").replaceAll("[^0-9.\\-]", "");
            if (normalized.isBlank()) return null;
            return new BigDecimal(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

}
