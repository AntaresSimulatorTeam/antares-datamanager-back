package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicCostAndRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castDouble;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castString;
import static com.rte_france.antares.datamanager_back.util.Utils.findHorizonColumnIndex;
import static com.rte_france.antares.datamanager_back.util.Utils.getCellValue;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalEconomicCostAndRateServiceImpl implements ThermalEconomicCostAndRateService {

    private final TrajectoryRepository trajectoryRepository;
    private final ThermalCostTypeRepository thermalCostTypeRepository;

    public static final String SHEET_COSTS = "costs";
    public static final String SHEET_RATE = "rate";

    @Override
    public List<ThermalCostTypeEntity> buildThermalEconomicCostValueList(String trajectoryName, Path trajectoryFilePath, String horizon, Integer studyId) {
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(SHEET_COSTS);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw BusinessException.builder()
                        .message("Header row is missing in 'costs' sheet for trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            Integer horizonCol = findHorizonColumnIndex(header, horizon);
            Set<String> distinctTechnologyList = trajectoryRepository
                    .findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, Integer.parseInt(horizon)-1 +"-"+ horizon, TrajectoryType.THERMAL_CAPACITY.name())
                    .stream()
                    .flatMap(trajectory -> trajectory.getThermalClusterCapacities().stream())
                    .map(capacity -> capacity.getThermalClusterRef().getThermalTechnology().getName().toLowerCase())
                    .collect(Collectors.toSet());
            List<ThermalCostTypeEntity> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String fuel = castString(getCellValue(row, 1));
                if (fuel == null || fuel.isBlank()) continue;
                if(!distinctTechnologyList.isEmpty() && !distinctTechnologyList.contains(fuel.toLowerCase())){
                    throw BusinessException.builder()
                            .message("Technology {0} in THERMAL Costs trajectory {1} in costs tab does not exist in installed power trajectory for horizon {2}")
                            .errorMessageArguments(List.of(fuel, trajectoryName, horizon))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                ThermalCostTypeEntity type = buildThermalEconomicCostType(row, header);

                Double costValue = castDouble(getCellValue(row, horizonCol), String.valueOf(header.getCell(horizonCol).getNumericCellValue()), horizonCol);
                Integer yearValue = parseYear(horizon);
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
    public List<ThermalCostsRateEntity> buildThermalEconomicRateValueList(String trajectoryName, Path trajectoryFilePath, String horizon, Integer studyId) {
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet rateSheet = workbook.getSheet(SHEET_RATE);
            Row header = rateSheet.getRow(0);

            Integer horizonCol = findHorizonColumnIndex(header, horizon);

            List<ThermalCostsRateEntity> result = new ArrayList<>();
            for (int r = 1; r <= rateSheet.getLastRowNum(); r++) {
                Row row = rateSheet.getRow(r);
                if (row == null) continue;
                String rateType = castString(getCellValue(row, 0));
                if (rateType == null || rateType.isBlank()) continue;


                Double rateValue = castDouble(getCellValue(row, horizonCol), String.valueOf(header.getCell(horizonCol).getNumericCellValue()), horizonCol);
                ThermalCostsRateEntity rate = ThermalCostsRateEntity.builder()
                        .rateType(rateType)
                        .value(BigDecimal.valueOf(rateValue))
                        .year(parseYear(horizon))
                        .build();

                result.add(rate);
            }

            return result;

        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Could not read thermal economic rate file: {0}")
                    .errorMessageArguments(List.of(trajectoryFilePath.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

    }


    @Override
    @Transactional
    public TrajectoryEntity saveThermalEconomicCostAndRateTrajectory(TrajectoryEntity trajectory,
                                                                     List<ThermalCostTypeEntity> thermalCostTypeEntities,
                                                                     List<ThermalCostsRateEntity> thermalRateEntities,
                                                                     TrajectoryType type) {
        trajectory.setType(type.name());
        List<ThermalCostEntity> toPersistCosts = new ArrayList<>();

        if (thermalCostTypeEntities != null) {
            for (ThermalCostTypeEntity inputType : thermalCostTypeEntities) {
                if (inputType == null) continue;


                ThermalCostTypeEntity typeEntity = thermalCostTypeRepository.save(
                        ThermalCostTypeEntity.builder()
                                .fuel(inputType.getFuel())
                                .country(inputType.getCountry())
                                .comment(inputType.getComment())
                                .unit(inputType.getUnit())
                                .modulation(inputType.getModulation())
                                .ratioNcvHcv(inputType.getRatioNcvHcv())
                                .build()
                );


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
        }

        if (!toPersistCosts.isEmpty()) {
            trajectory.setThermalCosts(toPersistCosts);
        }

        if (thermalRateEntities != null && !thermalRateEntities.isEmpty()) {
            for (ThermalCostsRateEntity rate : thermalRateEntities) {
                if (rate == null) continue;
                rate.setTrajectory(trajectory);
            }
            trajectory.setThermalCostsRates(thermalRateEntities);
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

    private static Integer parseYear(String horizon) {
        try {
            return horizon == null ? null : Integer.valueOf(horizon.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}









