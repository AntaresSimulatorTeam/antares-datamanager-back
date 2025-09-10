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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name()),
                horizon,
                TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name()
        );

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(path, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(path, existingOpt.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null, null);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null, null);
        }
        return saveThermalTrajectory(trajectory, list, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    private final StudyRepository studyRepository;

    private static final String YEAR_MONTH_PATTERN = "%04d_%02d";


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
        trajectory.setVersion(thermalClusterCapacityDto.getVersion());
        trajectory.setChecksum(thermalClusterCapacityDto.getChecksum());
        List<ThermalClusterCapacityEntity> thermalEntities = thermalClusterCapacityDto.getThermalClusterCapacities();
        thermalEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        if (!thermalEntities.isEmpty()) {
            trajectory.setThermalClusterCapacities(thermalEntities);

        }
        if (thermalClusterCapacityDto.getWarningMessage() != null) {
            thermalClusterCapacityDto.getWarningMessage().setTrajectory(trajectory);
            trajectory.setWarningMessages(Set.of(thermalClusterCapacityDto.getWarningMessage()));
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
        WarningMessageEntity warningMessage = null;
        ThermalClusterCapacityDto dto = new ThermalClusterCapacityDto();
        log.info("Début du traitement du fichier THERMAL Installed Power : {}", path.getFileName());

        List<ThermalClusterCapacityEntity> capacities = new ArrayList<>();
        Set<String> otherAreas = new HashSet<>();
        StringBuilder checksumBuilder = new StringBuilder();
        boolean isSpecificAreaFound = false;

        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            validateHeaderColumns(header, path);
            validateHorizonColumnsPresent(header, horizon, isCivilYear, path);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String rowArea = row.getCell(1).getStringCellValue().toUpperCase();

                if (!area.equals(OTHERS_AREA)) {
                    if (rowArea.equals(area.toUpperCase())) {
                        isSpecificAreaFound = true;
                        processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, capacities, checksumBuilder);
                    }
                } else {
                    otherAreas.add(rowArea);
                    processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, capacities, checksumBuilder);
                }
            }
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier : {}", e.getMessage());
            throw TechnicalException.builder()
                    .message("could not build thermal_capacity cluster  list : " + e.getMessage())
                    .build();
        }

        String checksum = calculateChecksum(checksumBuilder.toString());
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(path, horizon, area, technology);

        handleChecksumAndVersion(dto, existingTrajectory, checksum, path);

        checkPowerAndNumberWithSameToUse(capacities, path.getFileName().toString());

        if (area.equals(OTHERS_AREA)) {
            List<String> studyAreas = getStudyAreasForCurrentStudy(studyId);
            log.info("Areas liés à l'étude récupérées : {}", studyAreas);
            warningMessage = buildWarningMessage(path, area, studyId, isSpecificAreaFound, otherAreas, studyAreas);
        }
        log.info("Fin du traitement du fichier THERMAL Installed Power : {} ({} clusters trouvés)", path.getFileName(), capacities.size());
        dto.setThermalClusterCapacities(capacities);
        dto.setWarningMessage(warningMessage);

        return dto;
    }

    private Optional<TrajectoryEntity> findExistingTrajectory(Path path, String horizon, String area, String technology) {
        return trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.THERMAL_CAPACITY.name()),
                TrajectoryType.THERMAL_CAPACITY.name(),
                horizon,
                area,
                technology);
    }

    public void handleChecksumAndVersion(ThermalClusterCapacityDto dto, Optional<TrajectoryEntity> existingTrajectory, String checksum, Path path) {
        if (existingTrajectory.isPresent() && existingTrajectory.get().getChecksum() != null) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                log.info("Le contenu du fichier {} n'a pas changé par rapport à la dernière version enregistrée.", path.getFileName());
                throw BusinessException.builder()
                        .message("File already processed with same content {0}")
                        .errorMessageArguments(List.of(path.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            } else {
                dto.setChecksum(checksum);
                dto.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else if (existingTrajectory.isEmpty()) {
            dto.setChecksum(checksum);
            dto.setVersion(1);
        }
    }


    public WarningMessageEntity buildWarningMessage(Path path, String area, Integer studyId, boolean isSpecificAreaFound, Set<String> listOfOtherArea, List<String> studyAreas) {
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
                                   String rowArea, List<ThermalClusterCapacityEntity> result, StringBuilder checksum) {
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

            double value = capacityValue(row, i, horizon);
            boolean toUse = row.getCell(0).getNumericCellValue() == 0;

            // Ajout des valeurs au checksum
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
        if (!OTHERS_AREA.equals(area)) {
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

    private static double capacityValue(Row row, int i, String horizon) {
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
                        .message("The value of power or number of horizon {0} in THERMAL Installed Power trajectory must be numeric, found {1} instead")
                        .errorMessageArguments(List.of(horizon, cell.getStringCellValue()))
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

    private void validateHeaderColumns(Row header, Path path) {
        List<String> requiredColumns = List.of("ToUse", "Area", "Type", "Cluster", "Category");
        for (int i = 0; i < requiredColumns.size(); i++) {
            String cellValue = header.getCell(i).getStringCellValue();
            if (!cellValue.equalsIgnoreCase(requiredColumns.get(i))) {
                throw BusinessException.builder()
                        .message("The expected column '" + requiredColumns.get(i) + "' is missing or misplaced in the file " + path.getFileName())
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
                String col = String.format(YEAR_MONTH_PATTERN, horizonYear, m);
                expectedColumns.add(col);
            }
        } else {
            for (int m = 7; m <= 12; m++) {
                String col = String.format(YEAR_MONTH_PATTERN, horizonYear, m);
                expectedColumns.add(col);
            }
            for (int m = 1; m <= 6; m++) {
                String col = String.format(YEAR_MONTH_PATTERN, horizonYear + 1, m);
                expectedColumns.add(col);
            }
        }
        return expectedColumns;
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

    public static void checkPowerAndNumberWithSameToUse(List<ThermalClusterCapacityEntity> thermalClusterCapacities, String fileName) {
        Map<String, List<ThermalClusterCapacityEntity>> grouped = thermalClusterCapacities.stream()
                .collect(Collectors.groupingBy(e -> e.getArea() + "/" + e.getThermalClusterRef().getName()));

        List<String> missingCategoryGroups = new ArrayList<>();
        List<String> invalidToUseGroups = new ArrayList<>();

        for (Map.Entry<String, List<ThermalClusterCapacityEntity>> entry : grouped.entrySet()) {
            Optional<ThermalClusterCapacityEntity> power = entry.getValue().stream()
                    .filter(e -> e.getCategory() == ThermalCategoryEnum.POWER)
                    .findFirst();
            Optional<ThermalClusterCapacityEntity> number = entry.getValue().stream()
                    .filter(e -> e.getCategory() == ThermalCategoryEnum.NUMBER)
                    .findFirst();

            if (power.isEmpty() || number.isEmpty()) {
                missingCategoryGroups.add(entry.getKey());
            } else if (!Objects.equals(power.get().getToUse(), number.get().getToUse())) {
                invalidToUseGroups.add(entry.getKey());
            }
        }

        if (!missingCategoryGroups.isEmpty()) {
            throw BusinessException.builder()
                    .message("Area/Cluster {0} must have power AND number category in THERMAL Installed Power trajectory {1}")
                    .errorMessageArguments(List.of(String.join(", ", missingCategoryGroups), fileName))
                    .build();
        }

        if (!invalidToUseGroups.isEmpty()) {
            throw BusinessException.builder()
                    .message("Area/Cluster {0} must have same to_use value for power AND number category in THERMAL Installed Power trajectory {1}")
                    .errorMessageArguments(List.of(String.join(", ", invalidToUseGroups), fileName))
                    .build();
        }
    }


    // Méthode utilitaire pour calculer le checksum SHA-256
    private String calculateChecksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur lors du calcul du checksum", e);
        }
    }
}
