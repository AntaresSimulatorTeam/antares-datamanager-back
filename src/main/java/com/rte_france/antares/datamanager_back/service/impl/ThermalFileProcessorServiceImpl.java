package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.repository.model.WarningCode.THERMAL_INSTALLED_POWER_MISSING_AREAS;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalFileProcessorServiceImpl implements ThermalFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;

    private final AreaRepository areaRepository;

    private final ThermalClusterRefRepository thermalClusterRefRepository;

    private final UserService userService;

    private final ThermalTechnologyRepository thermalTechnologyRepository;

    private List<ThermalClusterRef> cachedClusterRefs;

    private final WarningRepository warningMessageRepository;

    private final StudyRepository studyRepository;


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
     * @param trajectory      the trajectory entity to save
     * @param thermalEntities the list of thermal entities to associate with the trajectory
     * @param type            the type of the trajectory
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
    public List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(
            Path path, String horizon, boolean isCivilYear, String area, String technology, Integer studyId) throws IOException {

        log.info("Début du traitement du fichier THERMAL Installed Power : {}", path.getFileName());

        List<String> studyAreas = getStudyAreasForCurrentStudy(studyId);
        log.info("Areas liés à l'étude récupérés : {}", studyAreas);

        List<ThermalClusterCapacityEntity> thermalClusterCapacities = new ArrayList<>();
        boolean areaFound = false;
        Set<String> foundAreas = new HashSet<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            validateHorizonColumnsPresent(header, horizon, isCivilYear, path);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String rowArea = row.getCell(1).getStringCellValue().toUpperCase();

                if (!area.equals("OTHER")) {
                    if (rowArea.equals(area.toUpperCase())) {
                        areaFound = true;
                        processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, thermalClusterCapacities);
                    }
                } else {
                    foundAreas.add(rowArea);
                    processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, thermalClusterCapacities);
                }
            }
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier : {}", e.getMessage());
            throw TechnicalException.builder().message("could not build thermal_capacity cluster  list : " + e.getMessage()).build();
        }

        List<String> listMissingArea = checkForMissingArea(area, areaFound, foundAreas, studyAreas, path);
        //save warning if missing areas
        if (!listMissingArea.isEmpty()) {
            String message = "The following areas are missing in the THERMAL Installed Power trajectory " + path.getFileName() + " : " + String.join(", ", listMissingArea);
            log.info(message);
            warningMessageRepository.save(WarningMessageEntity.builder()
                    .warningContent(message)
                    .warningLevel(WarningLevel.WARNING_LEVEL)
                    .warningCode(THERMAL_INSTALLED_POWER_MISSING_AREAS)
                    .study(studyRepository.findById(studyId)
                            .orElseThrow(() -> BusinessException.builder()
                                    .message("Study not found with id: " + studyId)
                                    .httpStatus(HttpStatus.NOT_FOUND)
                                    .build()))
                    .creationDate(LocalDateTime.now())
                    .createdBy(userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER")
                    .isAck(false)
                    .build());
        } else {
            log.info("Toutes les areas sont présentes dans le fichier {}", path.getFileName());
        }

        log.info("Fin du traitement du fichier THERMAL Installed Power : {} ({} clusters trouvés)", path.getFileName(), thermalClusterCapacities.size());
        return thermalClusterCapacities;
    }

    private List<String> getStudyAreasForCurrentStudy(Integer studyId) {
        // À adapter selon votre contexte pour récupérer l'id de l'étude
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .collect(Collectors.toList());
    }

    private void processThermalRow(Row row, Row header, String horizon, boolean isCivilYear, String technology,
                                   String rowArea, List<ThermalClusterCapacityEntity> result) {
        for (int i = 5; i < header.getLastCellNum(); i++) {
            if (!isCellInHorizon(header.getCell(i).getStringCellValue(), horizon, isCivilYear)) continue;

            String techName = row.getCell(2).getStringCellValue();
            if (technology != null && !technology.isEmpty() && !techName.equalsIgnoreCase(technology)) continue;
            String clusterName = row.getCell(3).getStringCellValue();

            ThermalClusterCapacityEntity entity = ThermalClusterCapacityEntity.builder()
                    .toUse(row.getCell(0).getNumericCellValue() == 0)
                    .area(rowArea)
                    .thermalClusterRef(findOrCreateThermalClusterRef(techName, clusterName))
                    .category(ThermalCategoryEnum.valueOf(
                            row.getCell(4).getStringCellValue().equals(ThermalCategoryEnum.POWER.name().toLowerCase())
                                    ? ThermalCategoryEnum.POWER.name()
                                    : ThermalCategoryEnum.NUMBER.name()))
                    .monthYear(header.getCell(i).getStringCellValue())
                    .value(capacityValue(row, i))
                    .build();
            result.add(entity);
        }
    }

    private List<String> checkForMissingArea(String area, boolean areaFound, Set<String> foundAreas, List<String> studyAreas, Path path) {
        if (!area.equals("OTHER") && !areaFound) {
            log.info("Aucune area '{}' trouvée dans le fichier {}", area, path.getFileName());
            throw BusinessException.builder()
                    .message("No area of the AREA trajectory is present in THERMAL Installed Power trajectory " + path.getFileName())
                    .build();
        }
        if (area.equals("OTHER")) {
            List<String> missingAreas = studyAreas.stream()
                    .filter(studyArea -> !foundAreas.contains(studyArea))
                    .collect(Collectors.toList());

            boolean atLeastOnePresent = studyAreas.stream().anyMatch(foundAreas::contains);

            if (!atLeastOnePresent) {
                log.info("Aucune area de l'étude n'est présente dans le fichier {}", path.getFileName());
                throw BusinessException.builder()
                        .message("No area of the AREA trajectory is present in THERMAL Installed Power trajectory " + path.getFileName())
                        .build();
            }

            if (!missingAreas.isEmpty()) {
                return missingAreas;
            } else {
                log.info("Toutes les areas de l'étude sont présentes dans le fichier {}", path.getFileName());
            }
        }
        return List.of();
    }

    private static double capacityValue(Row row, int i) {
        Cell cell = row.getCell(i);
        if (cell == null) {
            throw BusinessException.builder()
                    .message("La cellule de capacité est vide à la colonne " + i)
                    .build();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue());
            } catch (NumberFormatException e) {
                throw BusinessException.builder()
                        .message("The value of power or number of horizon {0} THERMAL Installed Power trajectory {1} must be numeric")
                        .errorMessageArguments(List.of(String.valueOf(i), cell.getStringCellValue()))
                        .build();
            }
        } else {
            throw BusinessException.builder()
                    .message("Type de cellule non supporté pour la capacité à la colonne " + i + " : " + cell.getCellType())
                    .build();
        }
    }

    /**
     * Validates that the horizon columns are present in the header row.
     *
     * @param header      the header row of the sheet
     * @param horizon     the horizon to validate
     * @param isCivilYear whether the horizon is a civil year
     * @param path        the path of the file being processed
     */
    private void validateHorizonColumnsPresent(Row header, String horizon, boolean isCivilYear, Path path) {
        log.info("Vérification de la présence des colonnes pour l'horizon : {}", horizon);
        List<String> expectedColumns = getExpectedColumns(horizon, isCivilYear);
        // Vérifie la présence de chaque colonne attendue via isCellInHorizon
        List<String> actualColumns = new ArrayList<>();
        for (int i = 5; i < header.getLastCellNum(); i++) {
            String colName = header.getCell(i).getStringCellValue();
            if (isCellInHorizon(colName, horizon, isCivilYear)) {
                actualColumns.add(colName);
            }
        }
        for (String col : expectedColumns) {
            if (!actualColumns.contains(col)) {
                throw BusinessException.builder()
                        .message("The columns representing the horizon  {0} are missing in THERMAL Installed Power trajectory {1}")
                        .errorMessageArguments(List.of(horizon, path.getFileName().toString()))
                        .build();
            }
        }
    }

    private static List<String> getExpectedColumns(String horizon, boolean isCivilYear) {
        List<String> expectedColumns = new ArrayList<>();
        int horizonYear = Integer.parseInt(horizon.split("-")[0]);
        // Génère la liste des colonnes attendues selon le mode
        if (isCivilYear) {
            for (int m = 1; m <= 12; m++) {
                String col = String.format("%04d_%02d", horizonYear, m);
                expectedColumns.add(col);
            }
        } else {
            for (int m = 7; m <= 12; m++) {
                String col = String.format("%04d_%02d", horizonYear, m);
                expectedColumns.add(col);
            }
            for (int m = 1; m <= 6; m++) {
                String col = String.format("%04d_%02d", horizonYear + 1, m);
                expectedColumns.add(col);
            }
        }
        return expectedColumns;
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
