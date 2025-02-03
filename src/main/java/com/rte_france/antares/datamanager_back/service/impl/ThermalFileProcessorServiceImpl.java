package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalFileProcessorServiceImpl implements ThermalFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalCostTypeRepository thermalCostTypeRepository;


    @Transactional
    public TrajectoryEntity processThermalCapacityFile(File file, String horizon) throws IOException {
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(getFileNameWithoutExtension(file.getName()));
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(file, trajectoryEntity.get())) {
            return saveThermalCapacitiesTrajectory(buildTrajectory(file, trajectoryEntity.get().getVersion(),horizon), buildThermalClusterCapacityValuesList(file));
        }
        return saveThermalCapacitiesTrajectory(buildTrajectory(file, 0, horizon), buildThermalClusterCapacityValuesList(file));
    }

    @Override
    public TrajectoryEntity processThermalParameterFile(File file, String horizon) throws IOException {
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(getFileNameWithoutExtension(file.getName()));
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(file, trajectoryEntity.get())) {
            return saveThermalParametersTrajectory(buildTrajectory(file, trajectoryEntity.get().getVersion(),horizon), buildThermalParameters(file));
        }
        return saveThermalParametersTrajectory(buildTrajectory(file, 0, horizon), buildThermalParameters(file));
    }

    @Override
    public TrajectoryEntity processThermalCostFile(File file,String horizon) throws IOException {
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc(getFileNameWithoutExtension(file.getName()));
        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(file, trajectoryEntity.get())) {
            return saveThermalCostTrajectory(buildTrajectory(file, trajectoryEntity.get().getVersion(), horizon), buildThermalCosts(file));
        }
        return saveThermalCostTrajectory(buildTrajectory(file, 0,horizon), buildThermalCosts(file));
    }

    @Override
    public TrajectoryEntity saveThermalCapacitiesTrajectory(TrajectoryEntity trajectory, List<ThermalClusterCapacityEntity> thermalClusterCapacities) {
        trajectory.setType(TrajectoryType.THERMAL_CAPACITY.name());
        thermalClusterCapacities.forEach(thermalClusterCapacityEntity -> thermalClusterCapacityEntity.setTrajectory(trajectory));
        trajectory.setThermalClusterCapacities(thermalClusterCapacities);
        return trajectoryRepository.save(trajectory);
    }

    @ExecutionTime
    @Override
    public TrajectoryEntity saveThermalParametersTrajectory(TrajectoryEntity trajectory, List<ThermalParameterEntity> thermalParameterEntities) {
        trajectory.setType(TrajectoryType.THERMAL_PARAMETER.name());
        thermalParameterEntities.forEach(thermalClusterCapacityEntity -> thermalClusterCapacityEntity.setTrajectory(trajectory));
        trajectory.setThermalClusterParameters(thermalParameterEntities);
        return trajectoryRepository.save(trajectory);
    }

    @Override
    public TrajectoryEntity saveThermalCostTrajectory(TrajectoryEntity trajectory, List<ThermalCostEntity> thermalCostEntities) {
        trajectory.setType(TrajectoryType.THERMAL_COST.name());
        thermalCostEntities.forEach(thermalCostEntity -> thermalCostEntity.setTrajectory(trajectory));
        trajectory.setThermalCostEntities(thermalCostEntities);
        return trajectoryRepository.save(trajectory);
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param file the file to process
     * @return a list of area configurations
     */
    private List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(File file) throws IOException {
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);

            for (Row row : sheet) {

                for (int i = 5; i < header.getLastCellNum(); i++) {
                    if (row.getRowNum() != 0) {

                        ThermalClusterCapacityEntity thermalClusterCapacityEntity = ThermalClusterCapacityEntity.builder()
                                .toUse(row.getCell(0).getNumericCellValue() == 0)
                                .scenario(row.getCell(1).getStringCellValue())
                                .defaultScenario(row.getCell(2).getNumericCellValue() == 0)
                                .name(row.getCell(3).getStringCellValue())
                                .category(ThermalCategoryEnum.valueOf(row.getCell(4).getStringCellValue().equals(ThermalCategoryEnum.POWER.name().toLowerCase()) ? ThermalCategoryEnum.POWER.name() : ThermalCategoryEnum.NUMBER.name()))
                                .monthYear(header.getCell(i).getStringCellValue())
                                .value(row.getCell(i).getNumericCellValue())
                                .build();
                        thermalClusterCapacities.add(thermalClusterCapacityEntity);
                    }

                }
            }
        } catch (IOException e) {
            throw new IOException("could not build thermal_capacity cluster  list : " + e.getMessage());
        }
        return thermalClusterCapacities;
    }

    public List<ThermalCostEntity> buildThermalCosts(File file) throws IOException {
        List<ThermalCostEntity> thermalCostEntities = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            Row header = sheet.getRow(0);

            for (Row row : sheet) {
                if (row.getRowNum() != 0) {
                ThermalCostTypeEntity thermalCostTypeEntity = findOrCreateThermalCostTypeEntities(row);
                for (int i = 7; i < header.getLastCellNum(); i++) {
                        ThermalCostEntity thermalCostEntity = new ThermalCostEntity(
                                (Double) getCellValue(row, i),
                                header.getCell(i).getNumericCellValue(),
                                thermalCostTypeEntity);
                        thermalCostEntities.add(thermalCostEntity);
                    }

                }
            }
        } catch (IOException e) {
            throw new IOException("could not build thermal cost list : " + e.getMessage());
        }
        return thermalCostEntities;
    }

    private ThermalCostTypeEntity findOrCreateThermalCostTypeEntities(Row row) {
        return thermalCostTypeRepository.findThermalCostTypeEntityByFuelAndCountry(row.getCell(0).getStringCellValue(), row.getCell(1).getStringCellValue())
                .orElseGet(() -> {
                    ThermalCostTypeEntity thermalCostTypeEntity = ThermalCostTypeEntity.builder()
                            .country((String) getCellValue(row, 0))
                            .fuel((String) getCellValue(row, 1))
                            .scenario((String) getCellValue(row, 2))
                            .comment((String) getCellValue(row, 3))
                            .unit((String) getCellValue(row, 4))
                            .modulation((String) getCellValue(row, 5))
                            .ratioNcvHcv((Double) getCellValue(row, 6))
                            .build();
                    return thermalCostTypeRepository.save(thermalCostTypeEntity);

                });
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param file the file to process
     * @return a list of area configurations
     */
    @ExecutionTime
    public List<ThermalParameterEntity> buildThermalParameters(File file) throws IOException {
        long start = System.currentTimeMillis();

        List<ThermalParameterEntity> thermalParameters = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            var sheetWithYear = getSheetWithYear(workbook);
            if (sheetWithYear != null) {
                for (Row row : sheetWithYear) {
                    if (row.getRowNum() > 4) {
                        ThermalParameterEntity thermalParameter = ThermalParameterEntity.builder()
                                .node((String) getCellValue(row, 0))
                                .nodeEntsoe((String) getCellValue(row, 1))
                                .category((Double) getCellValue(row, 2))
                                .fuel((String) getCellValue(row, 3))
                                .type((String) getCellValue(row, 4))
                                .efficiencyRange((String) getCellValue(row, 5))
                                .efficiencyDefault((Double) getCellValue(row, 6))
                                .co2((Double) getCellValue(row, 7))
                                .omCost((Double) getCellValue(row, 8))
                                .minUpTime((Double) getCellValue(row, 9))
                                .minDownTime((Double) getCellValue(row, 10))
                                .startUpFuel((Double) getCellValue(row, 11))
                                .startUpFixCost((Double) getCellValue(row, 12))
                                .startUpFuelColdStart((Double) getCellValue(row, 13))
                                .startUpFixCostColdStart((Double) getCellValue(row, 14))
                                .startUpFuelHotStart((Double) getCellValue(row, 15))
                                .startUpFixCostHotStart((Double) getCellValue(row, 16))
                                .transitionHotWarm((Double) getCellValue(row, 17))
                                .transitionHotCold((Double) getCellValue(row, 18))
                                .shutdownTime((Double) getCellValue(row, 19))
                                .foRateDefault((Double) getCellValue(row, 20))
                                .foDurationDefault((Double) getCellValue(row, 21))
                                .poDurationDefault((Double) getCellValue(row, 22))
                                .poWinterDefault((Double) getCellValue(row, 23))
                                .minStableGenerationDefault((Double) getCellValue(row, 24))
                                .rampUp((Double) getCellValue(row, 25))
                                .rampDown((Double) getCellValue(row, 26))
                                .fixedGenerationReduction((Double) getCellValue(row, 27))
                                .efficiency((Double) getCellValue(row, 28))
                                .build();
                        thermalParameters.add(thermalParameter);
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("could not build thermal_capacity parameters  list : " + e.getMessage());
        }

        long executionTime = System.currentTimeMillis() - start;

        log.info("buildThermalParameters executed in " + executionTime + "ms");
        return thermalParameters;
    }

    private static Sheet getSheetWithYear(Workbook workbook) {
        for (var i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet currentSheet = workbook.getSheetAt(i);
            if (isSheetNameYearNumber(currentSheet)) {
                return currentSheet;
            }
        }
        return null;
    }
}
