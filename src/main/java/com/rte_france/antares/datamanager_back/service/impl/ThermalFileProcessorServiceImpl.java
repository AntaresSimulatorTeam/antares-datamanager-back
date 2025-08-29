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
                trajectory.setThermalClusterCapacities((List<ThermalClusterCapacityEntity>) thermalEntities);
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
                            .toUse(row.getCell(0).getNumericCellValue() == 0)
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
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
        return cachedClusterRefs.stream()
                .filter(ref -> ref.getName().equalsIgnoreCase(name)
                        && ref.getThermalTechnology().getName().equalsIgnoreCase(technology))
                .findFirst()
                .orElseGet(() -> {
                    Optional<ThermalTechnology> savedThermalTechnology = thermalTechnologyRepository.findThermalTechnologyByName(technology);
                    ThermalTechnology thermalTechnology = savedThermalTechnology.orElseGet(() -> {
                        ThermalTechnology newTech = ThermalTechnology.builder()
                                .name(technology)
                                .build();
                        return thermalTechnologyRepository.save(newTech);
                    });
                    ThermalClusterRef ref = ThermalClusterRef.builder()
                            .name(name)
                            .namePemmdb("NA")
                            .thermalTechnology(thermalTechnology)
                            .build();
                    ThermalClusterRef saved = thermalClusterRefRepository.save(ref);
                    cachedClusterRefs.add(saved);
                    return saved;
                });
    }
}
