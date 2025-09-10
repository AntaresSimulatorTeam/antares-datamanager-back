package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalFileProcessorServiceImpl implements ThermalFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalClusterRefRepository thermalClusterRefRepository;

    private final UserService userService;

    private final ThermalTechnologyRepository thermalTechnologyRepository;

    private List<ThermalClusterRef> cachedClusterRefs;

    @Override
    public List<ThermalCommonParameterEntity> buildThermalCommonParameterValuesList(Path path, String horizon, boolean isCivilYear) throws IOException {
        List<ThermalCommonParameterEntity> thermalParameters = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findHorizonSheet(workbook, horizon);
            if (sheet == null) {
                throw TechnicalException.builder().message("could not build thermal_common_parameter list : missing suitable sheet for horizon '" + horizon + "'").build();
            }
            for (Row row : sheet) {
                if (row.getRowNum() > 4) {
                    var technology = castString(getCellValue(row, 4));
                    var clusterName = castString(getCellValue(row, 1));
                    var clusterPemmdb= castString(getCellValue(row, 0));
                    ThermalCommonParameterEntity param = ThermalCommonParameterEntity.builder()
                            .thermalClusterRef(findOrCreateThermalClusterRef(technology, clusterName,clusterPemmdb))
                            .category(castDouble(getCellValue(row, 2)))
                            .fuel(castString(getCellValue(row, 3)))
                            .efficiencyRange(castString(getCellValue(row, 5)))
                            .efficiencyDefault(castDouble(getCellValue(row, 6)))
                            .co2(castDouble(getCellValue(row, 7)))
                            .omCost(castDouble(getCellValue(row, 8)))
                            .minUpTime(castDouble(getCellValue(row, 9)))
                            .minDownTime(castDouble(getCellValue(row, 10)))
                            .startUpFuel(castDouble(getCellValue(row, 11)))
                            .startUpFixCost(castDouble(getCellValue(row, 12)))
                            .startUpFuelColdStart(castDouble(getCellValue(row, 13)))
                            .startUpFixCostColdStart(castDouble(getCellValue(row, 14)))
                            .startUpFuelHotStart(castDouble(getCellValue(row, 15)))
                            .startUpFixCostHotStart(castDouble(getCellValue(row, 16)))
                            .transitionHotWarm(castDouble(getCellValue(row, 17)))
                            .transitionHotCold(castDouble(getCellValue(row, 18)))
                            .shutdownTime(castDouble(getCellValue(row, 19)))
                            .foRateDefault(castDouble(getCellValue(row, 20)))
                            .foDurationDefault(castDouble(getCellValue(row, 21)))
                            .poDurationDefault(castDouble(getCellValue(row, 22)))
                            .poWinterDefault(castDouble(getCellValue(row, 23)))
                            .minStableGenerationDefault(castDouble(getCellValue(row, 24)))
                            .rampUp(castDouble(getCellValue(row, 25)))
                            .rampDown(castDouble(getCellValue(row, 26)))
                            .fixedGenerationReduction(castDouble(getCellValue(row, 27)))
                            .build();
                    thermalParameters.add(param);
                }
            }
            return thermalParameters;
        } catch (IOException e) {
            throw TechnicalException.builder().message("could not build thermal_common_parameter list : " + e.getMessage()).build();
        }
    }

    @Override
    public TrajectoryEntity processThermalCommonParameterFile(Path path, String horizon, List<ThermalCommonParameterEntity> list, TrajectoryType type) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";
        // Find existing trajectory for same file name/horizon/type
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.THERMAL_COMMON_PARAMETERS.name()),
                horizon,
                TrajectoryType.THERMAL_COMMON_PARAMETERS.name()
        );

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(path, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(path, existingOpt.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_COMMON_PARAMETERS, null, null);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_COMMON_PARAMETERS, null, null);
        }
        return saveThermalTrajectory(trajectory, list, TrajectoryType.THERMAL_COMMON_PARAMETERS);
    }

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param path the path to the file to process
     */
    public TrajectoryEntity processThermalCapacityFile(Path path, String horizon, List<ThermalClusterCapacityEntity> listThermalClusterCapacity, TrajectoryType type, String area, String technology) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";
        return saveThermalTrajectory(buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_CAPACITY, area, technology), listThermalClusterCapacity, type);
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
                // ensure provided type matches entity class
                if (type != TrajectoryType.THERMAL_CAPACITY) {
                    throw new IllegalArgumentException("Entity list type does not match trajectory type");
                }
                trajectory.setThermalClusterCapacities((List<ThermalClusterCapacityEntity>) thermalEntities);
            } else if (firstEntity instanceof ThermalCommonParameterEntity) {
                if (type != TrajectoryType.THERMAL_COMMON_PARAMETERS) {
                    throw new IllegalArgumentException("Entity list type does not match trajectory type");
                }
                trajectory.setThermalClusterParameters((List<ThermalCommonParameterEntity>) thermalEntities);
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
    public List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(Path path, String horizon, boolean isCivilYear, String area, String technology) throws IOException {
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String rowArea = row.getCell(1).getStringCellValue();
                if (!area.equals("OTHER") && !rowArea.equals(area.toUpperCase())) continue;

                for (int i = 5; i < header.getLastCellNum(); i++) {
                    if (!isCellInHorizon(header.getCell(i).getStringCellValue(), horizon, isCivilYear)) continue;

                    String techName = row.getCell(2).getStringCellValue();
                    if (technology != null && !technology.isEmpty() && !techName.equalsIgnoreCase(technology)) continue;
                    String clusterName = row.getCell(3).getStringCellValue();

                    ThermalClusterCapacityEntity entity = ThermalClusterCapacityEntity.builder()
                            .toUse(row.getCell(0).getNumericCellValue() == 1)
                            .area(rowArea)
                            .thermalClusterRef(findOrCreateThermalClusterRef(techName,clusterName))
                            .category(ThermalCategoryEnum.valueOf(
                                    row.getCell(4).getStringCellValue().equals(ThermalCategoryEnum.POWER.name().toLowerCase())
                                            ? ThermalCategoryEnum.POWER.name()
                                            : ThermalCategoryEnum.NUMBER.name()))
                            .monthYear(header.getCell(i).getStringCellValue())
                            .value(row.getCell(i).getCellType() == CellType.STRING
                                    ? Double.parseDouble(row.getCell(i).getStringCellValue())
                                    : row.getCell(i).getNumericCellValue())
                            .build();
                    thermalClusterCapacities.add(entity);
                }
            }
        } catch (IOException e) {
            throw TechnicalException.builder().message("could not build thermal_capacity cluster  list : " + e.getMessage()).build();
        }
        return thermalClusterCapacities;
    }




    private static String castString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double castDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isCellInHorizon(String monthYear, String horizon, boolean isCivilYear) {
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

    private void loadAllThermalClusterRefs() {
        cachedClusterRefs = thermalClusterRefRepository.findAll();
    }

    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name) {
        // Backward-compatible delegate: no PEMMDB provided
        return findOrCreateThermalClusterRef(technology, name, null);
    }

    /**
     * Finds an existing ThermalClusterRef by technology and name, or creates a new one if not found.
     * If a `namePemmdb` value is provided, the method may update an existing entry if its `namePemmdb`
     * field is blank or set to "NA".
     *
     * The method first checks the cached `ThermalClusterRef` instances. If not present or not matching
     * the search parameters, it attempts to find an associated `ThermalTechnology`. If the technology
     * does not exist, it creates a new one and associates it with the created ThermalClusterRef.
     *
     * @param technology the name of the thermal technology; a default value of "UNKNOWN" is used if null or blank
     * @param name the name of the thermal cluster; defaults to an empty string if null
     * @param namePemmdb an optional value to be associated with the ThermalClusterRef; if null or blank, "NA" is used
     * @return the existing or newly created ThermalClusterRef instance with the specified properties
     */
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
        String safeTech = (technology == null || technology.isBlank()) ? "UNKNOWN" : technology;
        String safeName = (name == null) ? "" : name;
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
        Optional<ThermalClusterRef> existingOpt = cachedClusterRefs.stream()
                .filter(ref -> ref.getName() != null && ref.getName().equalsIgnoreCase(safeName)
                        && ref.getThermalTechnology() != null
                        && ref.getThermalTechnology().getName() != null
                        && ref.getThermalTechnology().getName().equalsIgnoreCase(safeTech))
                .findFirst();

        if (existingOpt.isPresent()) {
            ThermalClusterRef existing = existingOpt.get();
            // If PEMMDB value is provided and existing is null or equals to "NA", update it instead of creating a new row
            if (namePemmdb != null && !namePemmdb.isBlank()) {
                String current = existing.getNamePemmdb();
                if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                    existing.setNamePemmdb(namePemmdb);
                    ThermalClusterRef saved = thermalClusterRefRepository.save(existing);
                    // also update the cached list instance (already same reference), but ensure it's consistent
                    return saved;
                }
            }
            return existing;
        }

        Optional<ThermalTechnology> savedThermalTechnology = thermalTechnologyRepository.findThermalTechnologyByName(safeTech);
        ThermalTechnology thermalTechnology = savedThermalTechnology.orElseGet(() -> {
            ThermalTechnology newTech = ThermalTechnology.builder()
                    .name(safeTech)
                    .build();
            return thermalTechnologyRepository.save(newTech);
        });
        ThermalClusterRef.ThermalClusterRefBuilder refBuilder = ThermalClusterRef.builder()
                .name(safeName)
                .thermalTechnology(thermalTechnology);
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            refBuilder.namePemmdb(namePemmdb);
        } else {
            refBuilder.namePemmdb("NA");
        }
        ThermalClusterRef ref = refBuilder.build();
        ThermalClusterRef saved = thermalClusterRefRepository.save(ref);
        cachedClusterRefs.add(saved);
        return saved;
    }
}
