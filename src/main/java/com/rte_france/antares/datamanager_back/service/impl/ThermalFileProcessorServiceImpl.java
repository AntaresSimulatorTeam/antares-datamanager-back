package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalFileProcessorServiceImpl implements ThermalFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalCostTypeRepository thermalCostTypeRepository;

    private final ThermalClusterRefRepository thermalClusterRefRepository;

    private final UserService userService;

    private List<ThermalClusterRef> cachedClusterRefs;

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param path the path to the file to process
     */
    public TrajectoryEntity processThermalCapacityFile(Path path, String horizon, List<ThermalClusterCapacityEntity> listThermalClusterCapacity, TrajectoryType type) throws IOException {
        var trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.THERMAL_CAPACITY.name()), horizon, type.name());
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";

        if (trajectoryEntity.isPresent() && checkTrajectoryVersion(path, trajectoryEntity.get())) {
            return saveThermalTrajectory(buildTrajectory(path, trajectoryEntity.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_CAPACITY), listThermalClusterCapacity, type);
        }
        return saveThermalTrajectory(buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_CAPACITY), listThermalClusterCapacity, type);
    }

    @Override
    public TrajectoryEntity processThermalParametersFile(Path path, String horizon, List<ThermalParameterEntity> thermalParameterEntityList, TrajectoryType type) throws IOException {
        return null;
    }

    @Override
    public TrajectoryEntity processThermalCostFile(Path path, String horizon, List<ThermalCostEntity> thermalCostEntityList, TrajectoryType type) throws IOException {
        return null;
    }


    /**
     * Saves the thermal trajectory and associates it with the given thermal entities.
     *
     * @param trajectory     the trajectory entity to save
     * @param thermalEntities the list of thermal entities to associate with the trajectory
     * @param type           the type of the trajectory
     * @return the saved trajectory entity
     */
    @SuppressWarnings("unchecked")
    public TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, List<? extends ThermalBaseEntity> thermalEntities, TrajectoryType type) {
        trajectory.setType(type.name());
        thermalEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        if (!thermalEntities.isEmpty()) {
            ThermalBaseEntity firstEntity = thermalEntities.get(0);
            if (firstEntity instanceof ThermalClusterCapacityEntity) {
                trajectory.setThermalClusterCapacities((List<ThermalClusterCapacityEntity>) thermalEntities);
            } else if (firstEntity instanceof ThermalParameterEntity) {
                trajectory.setThermalClusterParameters((List<ThermalParameterEntity>) thermalEntities);
            } else if (firstEntity instanceof ThermalCostEntity) {
                trajectory.setThermalCostEntities((List<ThermalCostEntity>) thermalEntities);
            } else {
                throw new IllegalArgumentException();
            }
        }

        return trajectoryRepository.save(trajectory);
    }

    /**
     * Builds a list of area configurations from the given file.
     *
     * @param path the path to the file to process
     * @return a list of area configurations
     */
    @Override
    public List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(Path path, String horizon, boolean isCivilYear, String area) throws IOException {
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String rowArea = row.getCell(1).getStringCellValue();
                if (!area.equals("OTHER") && !rowArea.equals(area)) continue;

                for (int i = 5; i < header.getLastCellNum(); i++) {
                    if (!isCellInHorizon(header.getCell(i).getStringCellValue(), horizon, isCivilYear)) continue;

                    String techName = row.getCell(2).getStringCellValue();
                    String clusterName = row.getCell(3).getStringCellValue();

                    ThermalClusterCapacityEntity entity = ThermalClusterCapacityEntity.builder()
                            .toUse(row.getCell(0).getNumericCellValue() == 0)
                            .area(rowArea)
                            .thermalClusterRef(findOrCreateThermalClusterRef(techName,clusterName))
                            .category(ThermalCategoryEnum.valueOf(
                                    row.getCell(4).getStringCellValue().equals(ThermalCategoryEnum.POWER.name().toLowerCase())
                                            ? ThermalCategoryEnum.POWER.name()
                                            : ThermalCategoryEnum.NUMBER.name()))
                            .monthYear(header.getCell(i).getStringCellValue())
                            .value(row.getCell(i).getNumericCellValue())
                            .build();
                    thermalClusterCapacities.add(entity);
                }
            }
        } catch (IOException e) {
            throw TechnicalException.builder().message("could not build thermal_capacity cluster  list : " + e.getMessage()).build();
        }
        return thermalClusterCapacities;
    }

    private boolean isCellInHorizon(String monthYear, String horizon, boolean isCivilYear) {
        // monthYear format: yyyy-MM
        String[] parts = monthYear.split("_");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int horizonYear = Integer.parseInt(horizon.split("-")[0]);

        if (isCivilYear) {
            // Année civile : janvier à décembre de l'année horizon
            return year == horizonYear;
        } else {
            // Année à cheval : juillet année horizon à juin année horizon+1
            if (year == horizonYear && month >= 7) return true;
            if (year == horizonYear + 1 && month <= 6) return true;
            return false;
        }
    }
    /**
     * Builds a list of thermal cost entities from the given file.
     *
     * @param path the path to the file to process
     * @return a list of thermal cost entities
     * @throws IOException if an I/O error occurs
     */
    @Override
    public List<ThermalCostEntity> buildThermalCosts(Path path) throws IOException {
        List<ThermalCostEntity> thermalCostEntities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

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
            throw  TechnicalException.builder().message("could not build thermal cost list : " + e.getMessage()).build();
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
     * @param path the path to the file to process
     * @return a list of area configurations
     */
    @ExecutionTime
    @Override
    public List<ThermalParameterEntity> buildThermalParameters(Path path) throws IOException {
        long start = System.currentTimeMillis();

        List<ThermalParameterEntity> thermalParameters = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path) ;
             Workbook workbook = WorkbookFactory.create(inputStream)) {
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
            throw TechnicalException.builder().message("could not build thermal_capacity parameters  list : " + e.getMessage()).build();
        }

        long executionTime = System.currentTimeMillis() - start;

        log.info("buildThermalParameters executed in {}ms", executionTime);
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

    private void loadAllThermalClusterRefs() {
        cachedClusterRefs = thermalClusterRefRepository.findAll();
    }

    private ThermalClusterRef findOrCreateThermalClusterRef(String name, String technology) {
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
        return cachedClusterRefs.stream()
                .filter(ref -> ref.getName().equalsIgnoreCase(name)
                        && ref.getThermalTechnology().getName().equalsIgnoreCase(technology))
                .findFirst()
                .orElseGet(() -> {
                    ThermalClusterRef ref = ThermalClusterRef.builder()
                            .name(name)
                            .namePemmdb("NA")
                            .thermalTechnology(ThermalTechnology.builder().name(technology).build())
                            .build();
                    ThermalClusterRef saved = thermalClusterRefRepository.save(ref);
                    cachedClusterRefs.add(saved);
                    return saved;
                });
    }
}
