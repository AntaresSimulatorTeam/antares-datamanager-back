package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicCostService;
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

import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castDouble;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castString;
import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalEconomicCostServiceImpl implements ThermalEconomicCostService {

    private final TrajectoryRepository trajectoryRepository;
    private final ThermalCostTypeRepository thermalCostTypeRepository;

    public static final String MANDATORY_SHEET_COSTS = "costs";
    public static final String MANDATORY_SHEET_RATE = "rate";

    @Override
    public List<ThermalCostTypeEntity> buildThermalEconomicCostValueList(String trajectoryName, Path trajectoryFilePath, String horizon, Integer studyId) {
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(MANDATORY_SHEET_COSTS);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw BusinessException.builder()
                        .message("Header row is missing in 'costs' sheet for trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            Integer horizonCol = findHorizonColumnIndex(header, horizon);
            if (horizonCol == null) {
                throw BusinessException.builder()
                        .message("Horizon does not exist in THERMAL Costs trajectory {0} in costs tab ")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            List<ThermalCostTypeEntity> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String fuel = castString(getCellValue(row, 1));
                if (fuel == null || fuel.isBlank()) continue;

                ThermalCostTypeEntity type = buildThermalEconomicCostType(row, header);

                Double costValue = castDouble(getCellValue(row, horizonCol), String.valueOf(header.getCell(horizonCol).getNumericCellValue()), horizonCol);
                Double yearValue = parseYear(horizon);
                if (costValue != null) {
                    ThermalCostEntity cost = ThermalCostEntity.builder()
                            .cost(costValue)
                            .year(yearValue)
                            .build();
                    type.setThermalCostEntities(List.of(cost));
                } else {
                    type.setThermalCostEntities(Collections.emptyList());
                }
                result.add(type);
            }

            return result;
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Could not read thermal economic costs file: {0}")
                    .errorMessageArguments(List.of(trajectoryFilePath.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @Override
    public TrajectoryEntity saveThermalEconomicCostTrajectory(TrajectoryEntity trajectory, List<ThermalCostTypeEntity> thermalCostTypeEntities, TrajectoryType type) {
        trajectory.setType(type.name());

        List<ThermalCostEntity> toPersistCosts = new ArrayList<>();
        for (ThermalCostTypeEntity inputType : thermalCostTypeEntities) {
            if (inputType == null) continue;
            String fuel = inputType.getFuel();
            String country = inputType.getCountry();

            ThermalCostTypeEntity typeEntity = thermalCostTypeRepository
                    .findThermalCostTypeEntityByFuelAndCountry(fuel, country)
                    .orElseGet(() -> thermalCostTypeRepository.save(
                            ThermalCostTypeEntity.builder()
                                    .fuel(fuel)
                                    .country(country)
                                    .comment(inputType.getComment())
                                    .unit(inputType.getUnit())
                                    .modulation(inputType.getModulation())
                                    .ratioNcvHcv(inputType.getRatioNcvHcv())
                                    .build()
                    ));

            List<ThermalCostEntity> costs = inputType.getThermalCostEntities();
            if (costs != null) {
                for (ThermalCostEntity c : costs) {
                    if (c == null) continue;
                    c.setThermalType(typeEntity);
                    c.setTrajectory(trajectory);
                    toPersistCosts.add(c);
                }
            }
        }

        if (!toPersistCosts.isEmpty()) {
            trajectory.setThermalCosts(toPersistCosts);
        }
        return trajectoryRepository.save(trajectory);
    }

    private ThermalCostTypeEntity buildThermalEconomicCostType(Row row, Row header) {
        return ThermalCostTypeEntity.builder()
                .country(castString(getCellValue(row, 0)))
                .fuel(castString(getCellValue(row, 1)))
                .comment(castString(getCellValue(row, 2)))
                .unit(castString(getCellValue(row, 3)))
                .modulation(castString(getCellValue(row, 4)))
                .ratioNcvHcv(castDouble(getCellValue(row, 5), header.getCell(5).getStringCellValue(), row.getRowNum()))
                .build();
    }

    private static Double parseYear(String horizon) {
        try {
            return horizon == null ? null : Double.valueOf(horizon.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Finds the column index whose header matches the horizon string exactly.
     */
    private Integer findHorizonColumnIndex(Row header, String horizon) {
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String headerValue = getHeaderValue(cell);
                if (headerValue != null && headerValue.equals(horizon)) {
                    return i;
                }
            }
        }
        return null;
    }


    private String getHeaderValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double numValue = cell.getNumericCellValue();

                if (numValue == Math.floor(numValue)) {
                    yield String.valueOf((int) numValue);
                } else {
                    yield String.valueOf(numValue);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }


}









