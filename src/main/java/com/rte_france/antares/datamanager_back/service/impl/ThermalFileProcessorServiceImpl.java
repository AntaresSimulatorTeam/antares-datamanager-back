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
            Sheet sheet = workbook.getSheetAt(workbook.getActiveSheetIndex());// case one sheet = one year
            if (isSheetNameYearNumber(sheet)) {
                for (Row row : sheet) {
                    if (row.getRowNum() > 6) {
                        ThermalParameterEntity thermalParameter = ThermalParameterEntity.builder()
                                .node((String) getCellValue(row, 0))
                                .nodeEntsoe((String) getCellValue(row, 1))
                                .comments((String) getCellValue(row, 2))
                                .category((String) getCellValue(row, 3))
                                .fuel((String) getCellValue(row, 4))
                                .techno((String) getCellValue(row, 5))
                                .clusterPemmdb((String) getCellValue(row, 6))
                                .cluster((String) getCellValue(row, 7))
                                .enabled(eliminateCharacters((String) getCellValue(row, 8)))
                                .efficiencyRange((String) getCellValue(row, 9))
                                .efficiencyDefault((Double) getCellValue(row, 10))
                                .co2((Double) getCellValue(row, 11))
                                .omCost((Double) getCellValue(row, 12))
                                .minUpTime((Double) getCellValue(row, 13))
                                .minDownTime((Double) getCellValue(row, 14))
                                .startUpFuel((Double) getCellValue(row, 15))
                                .startUpFixCost((Double) getCellValue(row, 16))
                                .startUpFuelColdStart((Double) getCellValue(row, 17))
                                .startUpFixCostColdStart((Double) getCellValue(row, 18))
                                .startUpFuelHotStart((Double) getCellValue(row, 19))
                                .startUpFixCostHotStart((Double) getCellValue(row, 20))
                                .transitionHotWarm((Double) getCellValue(row, 21))
                                .transitionHotCold((Double) getCellValue(row, 22))
                                .shutdownTime((Double) getCellValue(row, 23))
                                .foRateDefault((Double) getCellValue(row, 24))
                                .foDurationDefault((Double) getCellValue(row, 25))
                                .poDurationDefault((Double) getCellValue(row, 26))
                                .poWinterDefault((Double) getCellValue(row, 27))
                                .minStableGenerationDefault((Double) getCellValue(row, 28))
                                .rampUp((Double) getCellValue(row, 29))
                                .rampDown((Double) getCellValue(row, 30))
                                .fixedGenerationReduction((Double) getCellValue(row, 31))
                                .minStableGeneration((Double) getCellValue(row, 32))
                                .spinning((Double) getCellValue(row, 33))
                                .efficiency((Double) getCellValue(row, 34))
                                .foRate((Double) getCellValue(row, 35))
                                .foDuration((Double) getCellValue(row, 36))
                                .poDuration((Double) getCellValue(row, 37))
                                .poWinter((Double) getCellValue(row, 38))
                                .f1((Double) getCellValue(row, 39))
                                .f2((Double) getCellValue(row, 40))
                                .f3((Double) getCellValue(row, 41))
                                .f4((Double) getCellValue(row, 42))
                                .f5((Double) getCellValue(row, 43))
                                .f6((Double) getCellValue(row, 44))
                                .f7((Double) getCellValue(row, 45))
                                .f8((Double) getCellValue(row, 46))
                                .f9((Double) getCellValue(row, 47))
                                .f10((Double) getCellValue(row, 48))
                                .f11((Double) getCellValue(row, 49))
                                .f12((Double) getCellValue(row, 50))
                                .p1((Double) getCellValue(row, 51))
                                .p2((Double) getCellValue(row, 52))
                                .p3((Double) getCellValue(row, 53))
                                .p4((Double) getCellValue(row, 54))
                                .p5((Double) getCellValue(row, 55))
                                .p6((Double) getCellValue(row, 56))
                                .p7((Double) getCellValue(row, 57))
                                .p8((Double) getCellValue(row, 58))
                                .p9((Double) getCellValue(row, 59))
                                .p10((Double) getCellValue(row, 60))
                                .p11((Double) getCellValue(row, 61))
                                .p12((Double) getCellValue(row, 62))
                                .spread((Double) getCellValue(row, 63))
                                .marginalCost((Double) getCellValue(row, 64))
                                .marketBid((Double) getCellValue(row, 65))
                                .fixedCost((Double) getCellValue(row, 66))
                                .offsetVariableCost((Double) getCellValue(row, 67))
                                .npoMaxWinter((Double) getCellValue(row, 68))
                                .npoMaxSummer((Double) getCellValue(row, 69))
                                .nbUnits((Double) getCellValue(row, 70))
                                .mrSpecific((Double) getCellValue(row, 71))
                                .m1((Double) getCellValue(row, 72))
                                .m2((Double) getCellValue(row, 73))
                                .m3((Double) getCellValue(row, 74))
                                .m4((Double) getCellValue(row, 75))
                                .m5((Double) getCellValue(row, 76))
                                .m6((Double) getCellValue(row, 77))
                                .m7((Double) getCellValue(row, 78))
                                .m8((Double) getCellValue(row, 79))
                                .m9((Double) getCellValue(row, 80))
                                .m10((Double) getCellValue(row, 81))
                                .m11((Double) getCellValue(row, 82))
                                .m12((Double) getCellValue(row, 83))
                                .cmSpecific((Double) getCellValue(row, 84))
                                .c1((Double) getCellValue(row, 85))
                                .c2((Double) getCellValue(row, 86))
                                .c3((Double) getCellValue(row, 87))
                                .c4((Double) getCellValue(row, 88))
                                .c5((Double) getCellValue(row, 89))
                                .c6((Double) getCellValue(row, 90))
                                .c7((Double) getCellValue(row, 91))
                                .c8((Double) getCellValue(row, 92))
                                .c9((Double) getCellValue(row, 93))
                                .c10((Double) getCellValue(row, 94))
                                .c11((Double) getCellValue(row, 95))
                                .c12((Double) getCellValue(row, 96))
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
}
