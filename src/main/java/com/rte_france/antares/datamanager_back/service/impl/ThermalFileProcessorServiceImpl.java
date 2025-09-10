package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterCapacityDto;
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
import static com.rte_france.antares.datamanager_back.util.Utils.buildTrajectory;

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
    public TrajectoryEntity processThermalCapacityFile(Path path, String horizon, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type, String area, String technology) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";
        return saveThermalTrajectory(buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_CAPACITY, area, technology), thermalClusterCapacityDto, type);
    }

    /**
     * Saves the thermal trajectory and associates it with the given thermal entities.
     *
     * @param trajectory                the trajectory entity to save
     * @param thermalClusterCapacityDto the list of thermal entities to associate with the trajectory
     * @param type                      the type of the trajectory
     * @return the saved trajectory entity
     */
    public TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type) {
        trajectory.setType(type.name());
        List<ThermalClusterCapacityEntity> thermalEntities = thermalClusterCapacityDto.getThermalClusterCapacities();
        trajectory.setWarningMessages(Set.of(thermalClusterCapacityDto.getWarningMessage()));
        thermalEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        if (!thermalEntities.isEmpty()) {
            trajectory.setThermalClusterCapacities(thermalEntities);

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
    public ThermalClusterCapacityDto buildThermalClusterCapacityValuesList(
            Path path, String horizon, boolean isCivilYear, String area, String technology, Integer studyId) {

        ThermalClusterCapacityDto thermalClusterCapacityDto = new ThermalClusterCapacityDto();
        log.info("Début du traitement du fichier THERMAL Installed Power : {}", path.getFileName());

        List<String> studyAreas = getStudyAreasForCurrentStudy(studyId);
        log.info("Areas liés à l'étude récupérées : {}", studyAreas);

        List<ThermalClusterCapacityEntity> thermalClusterCapacities = new ArrayList<>();
        boolean isSpecificAreaFound = false;
        Set<String> listOfOtherArea = new HashSet<>();

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            validateHorizonColumnsPresent(header, horizon, isCivilYear, path);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String rowArea = row.getCell(1).getStringCellValue().toUpperCase();
                // le cas FR ou (AT, BE,CH,DE,IT,LU,NL.......)
                if (!area.equals("OTHER")) {
                    if (rowArea.equals(area.toUpperCase())) {
                        isSpecificAreaFound = true;
                        processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, thermalClusterCapacities);
                    }
                }
                // le cas OTHER
                else {
                    listOfOtherArea.add(rowArea);
                    processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, thermalClusterCapacities);
                }
            }
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier : {}", e.getMessage());
            throw TechnicalException.builder().message("could not build thermal_capacity cluster  list : " + e.getMessage()).build();
        }
        //check if power and number have the same toUse value for each cluster/area
        checkPowerAndNumberWithSameToUse(thermalClusterCapacities);
        //check if at least one area of the AREA trajectory is present in THERMAL Installed
        WarningMessageEntity warningMessage = buildWarningMessage(path, area, studyId, isSpecificAreaFound, listOfOtherArea, studyAreas);

        log.info("Fin du traitement du fichier THERMAL Installed Power : {} ({} clusters trouvés)", path.getFileName(), thermalClusterCapacities.size());
        thermalClusterCapacityDto.setThermalClusterCapacities(thermalClusterCapacities);
        thermalClusterCapacityDto.setWarningMessage(warningMessage);
        return thermalClusterCapacityDto;
    }

    private WarningMessageEntity buildWarningMessage(Path path, String area, Integer studyId, boolean isSpecificAreaFound, Set<String> listOfOtherArea, List<String> studyAreas) {
        List<String> listMissingArea = checkForMissingArea(area, isSpecificAreaFound, listOfOtherArea, studyAreas, path);
        //save warning if missing areas
        WarningMessageEntity warningMessage = new WarningMessageEntity();

        if (!listMissingArea.isEmpty()) {
            String message = "The following areas are missing in the THERMAL Installed Power trajectory " + path.getFileName() + " : " + String.join(", ", listMissingArea);
            log.info(message);
            warningMessage = WarningMessageEntity.builder()
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
                    .build();
        } else {
            log.info("Toutes les areas sont présentes dans le fichier {}", path.getFileName());
        }
        return warningMessage;
    }

    private List<String> getStudyAreasForCurrentStudy(Integer studyId) {
        // À adapter selon votre contexte pour récupérer l'id de l'étude
        return areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
    }

    private void processThermalRow(Row row, Row header, String horizon, boolean isCivilYear, String technology,
                                   String rowArea, List<ThermalClusterCapacityEntity> result) {
        String techName = row.getCell(2).getStringCellValue();
        String clusterName = row.getCell(3).getStringCellValue();
        String categoryStr = row.getCell(4).getStringCellValue().toLowerCase();

        if (technology != null && !technology.isEmpty() && !techName.equalsIgnoreCase(technology)) return;

        for (int i = 5; i < header.getLastCellNum(); i++) {
            String monthYear = header.getCell(i).getStringCellValue();
            if (!isCellInHorizon(monthYear, horizon, isCivilYear)) continue;

            ThermalCategoryEnum category = categoryStr.equals(ThermalCategoryEnum.POWER.name().toLowerCase())
                    ? ThermalCategoryEnum.POWER
                    : ThermalCategoryEnum.NUMBER;

            double value = capacityValue(row, i);
            boolean toUse = row.getCell(0).getNumericCellValue() == 0;

            // Ajout des valeurs au checksum
            StringBuilder checksum = new StringBuilder();
            checksum.append(rowArea).append("|")
                    .append(techName).append("|")
                    .append(clusterName).append("|")
                    .append(category.name()).append("|")
                    .append(monthYear).append("|")
                    .append(value).append("|")
                    .append(toUse).append("\n");

            ThermalClusterCapacityEntity entity = ThermalClusterCapacityEntity.builder()
                    .toUse(toUse)
                    .area(rowArea)
                    .thermalClusterRef(findOrCreateThermalClusterRef(techName, clusterName))
                    .category(category)
                    .monthYear(monthYear)
                    .value(value)
                    .build();
            result.add(entity);
        }
    }

    private List<String> checkForMissingArea(String area, boolean isSpecificAreaFound, Set<String> listOfOtherArea, List<String> studyAreas, Path path) {
        if (!"OTHER".equals(area)) {
            if (!isSpecificAreaFound) {
                throw BusinessException.builder()
                        .message("No area of the AREA trajectory is present in THERMAL Installed Power trajectory " + path.getFileName())
                        .build();
            }
            return Collections.emptyList();
        }

        List<String> missingAreas = studyAreas.stream()
                .filter(studyArea -> !listOfOtherArea.contains(studyArea))
                .toList();

        if (missingAreas.size() == studyAreas.size()) {
            throw BusinessException.builder()
                    .message("No area of the AREA trajectory is present in THERMAL Installed Power trajectory " + path.getFileName())
                    .build();
        }

        return missingAreas;
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

    public static void checkPowerAndNumberWithSameToUse(List<ThermalClusterCapacityEntity> thermalClusterCapacities) {
        Map<String, List<ThermalClusterCapacityEntity>> grouped = thermalClusterCapacities.stream()
                .collect(Collectors.groupingBy(e -> e.getArea() + "/" + e.getThermalClusterRef().getName()));

        List<String> invalidGroups = new ArrayList<>();

        for (Map.Entry<String, List<ThermalClusterCapacityEntity>> entry : grouped.entrySet()) {
            Optional<ThermalClusterCapacityEntity> power = entry.getValue().stream()
                    .filter(e -> e.getCategory() == ThermalCategoryEnum.POWER)
                    .findFirst();
            Optional<ThermalClusterCapacityEntity> number = entry.getValue().stream()
                    .filter(e -> e.getCategory() == ThermalCategoryEnum.NUMBER)
                    .findFirst();

            if (power.isEmpty() || number.isEmpty() || !Objects.equals(power.get().getToUse(), number.get().getToUse())) {
                invalidGroups.add(entry.getKey());
            }
        }

        if (!invalidGroups.isEmpty()) {
            throw BusinessException.builder()
                    .message("Les couples area/cluster suivants sont invalides : " + String.join(", ", invalidGroups))
                    .build();
        }
    }
}
