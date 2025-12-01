package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterCapacityDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicCostAndRateService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
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
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.repository.model.WarningCode.THERMAL_INSTALLED_POWER_MISSING_AREAS;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castDouble;
import static com.rte_france.antares.datamanager_back.util.CastCellUtil.castString;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalFileProcessorServiceImpl implements ThermalFileProcessorService {

    public static final String UNKNOWN_USER = "UNKNOWN__USER";
    private final TrajectoryRepository trajectoryRepository;

    private final AreaRepository areaRepository;

    private final UserService userService;

    private final StudyRepository studyRepository;

    private final ThermalControlService thermalControlService;

    private final ThermalEconomicCostAndRateService thermalEconomicCostAndRateService;

    private final ThermalClusterRefService thermalClusterRefService;

    private static final String YEAR_MONTH_PATTERN = "%04d_%02d";

    public static final List<String> REQUIRED_COMMON_PARAM_HEADER_COLUMNS = List.of(
            "cluster_PEMMDB", "cluster_BP", "Category", "Fuel", "Type", "efficiency_range", "efficiency_default",
            "CO2", "OM_cost", "min_up_time", "min_down_time", "start_up_fuel", "start_up_fix_cost",
            "start_up_fuel_cold_start", "start_up_fix_cost_cold_start", "start_up_fuel_hot_start", "start_up_fix_cost_hot_start",
            "transition_hot_warm", "transition_hot_cold", "shutdown_time", "startup_time", "FO_rate_default",
            "FO_duration_default", "PO_duration_default", "PO_winter_default", "min_stable_generation_default",
            "ramp_up", "ramp_down", "fixed_generation_reduction");


    /**
     * Processes a thermal common parameter file and saves the corresponding trajectory.
     *
     * @param path    The path to the thermal common parameter file.
     * @param horizon The horizon for the trajectory.
     * @param list    The list of thermal common parameter entities.
     * @param type    The type of trajectory.
     * @return The saved trajectory entity.
     * @throws IOException If an error occurs while processing the file.
     */
    @Override
    public TrajectoryEntity processThermalCommonParameterFile(Path path, String horizon, List<ThermalCommonParameterEntity> list, TrajectoryType type) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
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
        return saveThermalCommonTrajectory(trajectory, list, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    /**
     * Processes a thermal capacity file and saves the corresponding trajectory.
     *
     * @param path                      The path to the thermal capacity file.
     * @param horizon                   The horizon for the trajectory.
     * @param thermalClusterCapacityDto The DTO containing thermal cluster capacity data.
     * @param type                      The type of trajectory.
     * @param area                      The area associated with the trajectory.
     * @param technology                The technology associated with the trajectory.
     * @return The saved trajectory entity.
     * @throws IOException If an error occurs while processing the file.
     */
    @Override
    public TrajectoryEntity processThermalCapacityFile(Path path, String horizon, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type, String area, String technology) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        return saveThermalCapacitiesTrajectory(buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_CAPACITY, area, technology), thermalClusterCapacityDto, type);
    }

    @Override
    public TrajectoryEntity processThermalEconomicCostsAndRatesFile(Path path, String horizon, List<ThermalCostTypeEntity> thermalEconomicCosts, List<ThermalCostsRateEntity> thermalEconomicRates, TrajectoryType type) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;

        String trajectoryTypeName = TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.name();
        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryTypeName);
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(fileName, horizon, trajectoryTypeName);

        TrajectoryEntity trajectory;
        String checksum = calculateThermalCostTrajectoryChecksum(thermalEconomicCosts, thermalEconomicRates);

        if (existingTrajectoryOpt.isPresent() && existingTrajectoryOpt.get().getChecksum() != null) {
            if (existingTrajectoryOpt.get().getChecksum().equals(checksum)) {
                throwAlreadyProcessedFileException(path);
            }
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(path, existingTrajectoryOpt.get().getVersion(), horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, null, null);
        } else {
            // No existing or different file -> new trajectory with version 1
            trajectory = buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, null, null);
        }
        trajectory.setChecksum(checksum);
        return thermalEconomicCostAndRateService.saveThermalEconomicCostAndRateTrajectory(trajectory, thermalEconomicCosts, thermalEconomicRates, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER);
    }


    /**
     * Saves a thermal capacities trajectory.
     *
     * @param trajectory                The trajectory entity to save.
     * @param thermalClusterCapacityDto The DTO containing thermal cluster capacity data.
     * @param type                      The type of trajectory.
     * @return The saved trajectory entity.
     */
    @Override
    public TrajectoryEntity saveThermalCapacitiesTrajectory(TrajectoryEntity trajectory, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type) {
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
     * Saves a thermal common parameter trajectory.
     *
     * @param trajectory                       The trajectory entity to save.
     * @param thermalCommonParameterEntityList The list of thermal common parameter entities.
     * @param type                             The type of trajectory.
     * @return The saved trajectory entity.
     */
    @Override
    public TrajectoryEntity saveThermalCommonTrajectory(TrajectoryEntity trajectory, List<ThermalCommonParameterEntity> thermalCommonParameterEntityList, TrajectoryType type) {
        trajectory.setType(type.name());
        thermalCommonParameterEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        trajectory.setThermalCommonParameters(thermalCommonParameterEntityList);
        return trajectoryRepository.save(trajectory);
    }

    /**
     * Builds a ThermalClusterCapacityDto object by processing a thermal installed power file.
     *
     * @param path        The path to the thermal installed power file.
     * @param horizon     The horizon for the trajectory.
     * @param isCivilYear Indicates if the horizon is a civil year.
     * @param area        The area associated with the trajectory.
     * @param technology  The technology associated with the trajectory.
     * @param studyId     The ID of the study.
     * @return A ThermalClusterCapacityDto object containing the processed data.
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
                if (rowArea.isEmpty()) continue;
                String trajectoryName = path.getFileName().toString();

                if (!area.equals(OTHERS_AREA)) {
                    if (rowArea.equals(area.toUpperCase())) {
                        isSpecificAreaFound = true;
                        processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, capacities, checksumBuilder, trajectoryName);
                    }
                } else {
                    otherAreas.add(rowArea);
                    processThermalRow(row, header, horizon, isCivilYear, technology, rowArea, capacities, checksumBuilder, trajectoryName);
                }
            }
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier : {}", e.getMessage());
            throw TechnicalException.builder()
                    .message("could not build thermal_capacity cluster  list : " + e.getMessage())
                    .build();
        }
        verifyThermalCapacityTechnologie(path, horizon, studyId, capacities);

        List<String> studyAreas = getStudyAreasForCurrentStudy(studyId);
        List<ThermalClusterCapacityEntity> filteredCapacities = capacities.stream()
                .filter(capacity -> studyAreas.contains(capacity.getArea()))
                .toList();

        String checksum = calculateChecksum(checksumBuilder.toString());
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(path, horizon, area, technology);

        handleChecksumAndVersion(dto, existingTrajectory, checksum, path);

        checkPowerAndNumberWithSameToUse(filteredCapacities, path.getFileName().toString());

        thermalControlService.verifyClustersInCommonParamTrajectory(studyId, horizon, filteredCapacities);
        thermalControlService.verifyClustersInSpecificParamTrajectory(studyId, horizon, filteredCapacities);

        if (area.equals(OTHERS_AREA)) {
            log.info("Areas liés à l'étude récupérées : {}", studyAreas);
            warningMessage = buildWarningMessage(path, area, studyId, isSpecificAreaFound, otherAreas, studyAreas);
        }
        log.info("Fin du traitement du fichier THERMAL Installed Power : {} ({} clusters trouvés)", path.getFileName(), filteredCapacities.size());
        dto.setThermalClusterCapacities(filteredCapacities);
        dto.setWarningMessage(warningMessage);

        return dto;
    }

    private void verifyThermalCapacityTechnologie(Path path, String horizon, Integer studyId, List<ThermalClusterCapacityEntity> capacities) {
        Set<String> existingTechnologies = capacities.stream()
                .map(capacity -> capacity.getThermalClusterRef().getThermalTechnology().getName().toLowerCase())
                .collect(Collectors.toSet());
        thermalControlService.verifyThermalCapacityTechnology(studyId, horizon, path.getFileName().toString(), getTechnologiesFromCostsAndCo2(studyId), existingTechnologies);
    }

    public Set<String> getTechnologiesFromCostsAndCo2(Integer studyId) {
        List<TrajectoryEntity> trajectories = trajectoryRepository.findByTypeAndStudyId(null, studyId);

        return trajectories.stream()
                .filter(trajectory -> trajectory.getType().equals(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.name())
                        || trajectory.getType().equals(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name()))
                .flatMap(t -> Stream.concat(
                        Optional.ofNullable(t.getThermalCosts()).stream().flatMap(List::stream)
                                .map(cost -> cost.getThermalType().getFuel()),
                        Optional.ofNullable(t.getThermalEconomicCo2s()).stream().flatMap(List::stream)
                                .map(ThermalEconomicCo2Entity::getFuel)
                ))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * Builds a list of ThermalCommonParameterEntity objects by processing a thermal common parameter file.
     *
     * @param path    The path to the thermal common parameter file.
     * @param horizon The horizon for the trajectory.
     * @param studyId The ID of the study.
     * @return A list of ThermalCommonParameterEntity objects.
     */
    @Override
    public List<ThermalCommonParameterEntity> buildThermalCommonParameterValuesList(Path path, String horizon, Integer studyId) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = findHorizonSheetOrThrow(workbook, horizon);
            Row header = sheet.getRow(4);
            validateCommonParamHeaderColumns(header, path);

            Set<String> commonParamClusters = new HashSet<>();
            List<ThermalCommonParameterEntity> thermalParameters = parseThermalCommonParameterRows(sheet, header, commonParamClusters);

            if (thermalParameters.isEmpty()) {
                throw BusinessException.builder()
                        .message("No data found from line 6 in Common Param trajectory")
                        .build();
            }

            thermalControlService.checkMissingClusters(studyId, horizon, commonParamClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null);

            return thermalParameters;
        } catch (IOException e) {
            throwTechnicalException(e);
            return Collections.emptyList(); // unreachable, mais pour le compilateur
        }
    }

    private List<ThermalCommonParameterEntity> parseThermalCommonParameterRows(Sheet sheet, Row header, Set<String> commonParamClusters) {
        List<ThermalCommonParameterEntity> thermalParameters = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() <= 4) continue;
            String clusterName = castString(getCellValue(row, 1));
            if (clusterName == null || clusterName.isEmpty()) continue;
            String clusterPemmdb = castString(getCellValue(row, 0));
            commonParamClusters.add(clusterName);
            ThermalCommonParameterEntity param = buildThermalCommonParameterEntity(row, clusterName, clusterPemmdb, header);
            thermalParameters.add(param);
        }
        return thermalParameters;
    }


    private void throwTechnicalException(IOException e) {
        throw TechnicalException.builder()
                .message("Error processing file: " + e.getMessage())
                .build();
    }


    private ThermalCommonParameterEntity buildThermalCommonParameterEntity(Row row, String clusterName, String clusterPemmdb, Row header) {
        String technology = castString(getCellValue(row, 3));
        return ThermalCommonParameterEntity.builder()
                .thermalClusterRef(thermalClusterRefService.findOrCreateThermalClusterRef(technology, clusterName, clusterPemmdb))
                .category(castDouble(getCellValue(row, 2), header.getCell(2).getStringCellValue(), row.getRowNum()))
                .fuel(technology)
                .type(castString(getCellValue(row, 4)))
                .efficiencyRange(castString(getCellValue(row, 5)))
                .efficiencyDefault(castDouble(getCellValue(row, 6), header.getCell(6).getStringCellValue(), row.getRowNum()))
                .co2(castDouble(getCellValue(row, 7), header.getCell(7).getStringCellValue(), row.getRowNum()))
                .omCost(castDouble(getCellValue(row, 8), header.getCell(8).getStringCellValue(), row.getRowNum()))
                .minUpTime(castDouble(getCellValue(row, 9), header.getCell(9).getStringCellValue(), row.getRowNum()))
                .minDownTime(castDouble(getCellValue(row, 10), header.getCell(10).getStringCellValue(), row.getRowNum()))
                .startUpFuel(castDouble(getCellValue(row, 11), header.getCell(11).getStringCellValue(), row.getRowNum()))
                .startUpFixCost(castDouble(getCellValue(row, 12), header.getCell(12).getStringCellValue(), row.getRowNum()))
                .startUpFuelColdStart(castDouble(getCellValue(row, 13), header.getCell(13).getStringCellValue(), row.getRowNum()))
                .startUpFixCostColdStart(castDouble(getCellValue(row, 14), header.getCell(14).getStringCellValue(), row.getRowNum()))
                .startUpFuelHotStart(castDouble(getCellValue(row, 15), header.getCell(15).getStringCellValue(), row.getRowNum()))
                .startUpFixCostHotStart(castDouble(getCellValue(row, 16), header.getCell(16).getStringCellValue(), row.getRowNum()))
                .transitionHotWarm(castDouble(getCellValue(row, 17), header.getCell(17).getStringCellValue(), row.getRowNum()))
                .transitionHotCold(castDouble(getCellValue(row, 18), header.getCell(18).getStringCellValue(), row.getRowNum()))
                .shutdownTime(castDouble(getCellValue(row, 19), header.getCell(19).getStringCellValue(), row.getRowNum()))
                .startupTime(castDouble(getCellValue(row, 20), header.getCell(20).getStringCellValue(), row.getRowNum()))
                .foRateDefault(castDouble(getCellValue(row, 21), header.getCell(21).getStringCellValue(), row.getRowNum()))
                .foDurationDefault(castDouble(getCellValue(row, 22), header.getCell(22).getStringCellValue(), row.getRowNum()))
                .poDurationDefault(castDouble(getCellValue(row, 23), header.getCell(23).getStringCellValue(), row.getRowNum()))
                .poWinterDefault(castDouble(getCellValue(row, 24), header.getCell(24).getStringCellValue(), row.getRowNum()))
                .minStableGenerationDefault(castDouble(getCellValue(row, 25), header.getCell(25).getStringCellValue(), row.getRowNum()))
                .rampUp(castDouble(getCellValue(row, 26), header.getCell(26).getStringCellValue(), row.getRowNum()))
                .rampDown(castDouble(getCellValue(row, 27), header.getCell(27).getStringCellValue(), row.getRowNum()))
                .fixedGenerationReduction(castDouble(getCellValue(row, 28), header.getCell(28).getStringCellValue(), row.getRowNum()))
                .build();
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
                throwAlreadyProcessedFileException(path);
            } else {
                dto.setChecksum(checksum);
                dto.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else if (existingTrajectory.isEmpty()) {
            dto.setChecksum(checksum);
            dto.setVersion(1);
        }
    }

    public static void throwAlreadyProcessedFileException(Path path) {
        log.info("Le contenu du fichier {} n'a pas changé par rapport à la dernière version enregistrée.", path.getFileName());
        throw BusinessException.builder()
                .message("File already processed with same content {0}")
                .errorMessageArguments(List.of(path.getFileName().toString()))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }


    public WarningMessageEntity buildWarningMessage(Path path, String area, Integer studyId, boolean isSpecificAreaFound, Set<String> listOfOtherArea, List<String> studyAreas) {
        List<String> listMissingArea = checkForMissingArea(area, isSpecificAreaFound, listOfOtherArea, studyAreas, path);
        //save warning if missing areas
        WarningMessageEntity warningMessage = null;

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
                    .createdBy(userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER)
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
                                   String rowArea, List<ThermalClusterCapacityEntity> result, StringBuilder checksum, String trajectoryName) {
        String techName = row.getCell(2).getStringCellValue();
        String clusterName = row.getCell(3).getStringCellValue();
        String categoryStr = row.getCell(4).getStringCellValue().toLowerCase();

        if (technology != null && !technology.isEmpty() && !techName.equalsIgnoreCase(technology)) return;

        for (int i = 5; i < header.getLastCellNum(); i++) {
            String monthYear = header.getCell(i).getStringCellValue();
            boolean toUse = row.getCell(0).getNumericCellValue() == 1;
            if (!isCellInHorizon(monthYear, horizon, isCivilYear) || !toUse) continue;

            ThermalCategoryEnum category = categoryStr.equals(ThermalCategoryEnum.POWER.name().toLowerCase())
                    ? ThermalCategoryEnum.POWER
                    : ThermalCategoryEnum.NUMBER;

            double value = capacityValue(row, i, horizon, trajectoryName);

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
                    .thermalClusterRef(thermalClusterRefService.findOrCreateThermalClusterRef(techName, clusterName))
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

    private static double capacityValue(Row row, int i, String horizon, String trajectoryFileName) {
        Cell cell = row.getCell(i);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw BusinessException.builder()
                    .message("Null value not allowed for column " + i + " in THERMAL Installed Power trajectory " + trajectoryFileName)
                    .build();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else {
            try {
                return Double.parseDouble(cell.getStringCellValue());
            } catch (NumberFormatException e) {
                throw BusinessException.builder()
                        .message("The value of power or number of horizon {0} in THERMAL Installed Power trajectory  {1} must be numeric")
                        .errorMessageArguments(List.of(horizon, trajectoryFileName))
                        .build();
            }
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


    public static void checkPowerAndNumberWithSameToUse(List<ThermalClusterCapacityEntity> thermalClusterCapacities, String fileName) {
        Map<String, List<ThermalClusterCapacityEntity>> grouped = thermalClusterCapacities.stream()
                .collect(Collectors.groupingBy(e -> e.getArea() + "/" + e.getThermalClusterRef().getName()));

        List<String> missingCategoryGroups = new ArrayList<>();
        List<String> invalidToUseGroups = new ArrayList<>();
        List<String> invalidTypes = new ArrayList<>();

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
            } else if (!Objects.equals(power.get().getThermalClusterRef().getThermalTechnology(), number.get().getThermalClusterRef().getThermalTechnology())) {
                invalidTypes.add(entry.getKey());
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

        if (!invalidTypes.isEmpty()) {
            throw BusinessException.builder()
                    .message("Area/Cluster {0} must have same type value for power AND number category in THERMAL Installed Power trajectory {1}")
                    .errorMessageArguments(List.of(String.join(", ", invalidTypes), fileName))
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

    private void validateCommonParamHeaderColumns(Row header, Path path) {
        for (int i = 0; i < REQUIRED_COMMON_PARAM_HEADER_COLUMNS.size(); i++) {
            Cell cell = header.getCell(i);
            String cellValue = cell != null ? cell.getStringCellValue() : null;
            if (cellValue == null || !cellValue.equalsIgnoreCase(REQUIRED_COMMON_PARAM_HEADER_COLUMNS.get(i))) {
                throw BusinessException.builder()
                        .message("The expected column '" + REQUIRED_COMMON_PARAM_HEADER_COLUMNS.get(i) + "' is missing or misplaced in the Common Parameters file " + path.getFileName())
                        .build();
            }
        }
    }


}
