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

    private final StudyRepository studyRepository;

    private static final String YEAR_MONTH_PATTERN = "%04d_%02d";

    public static final List<String> REQUIRED_COMMON_PARAM_HEADER_COLUMNS = List.of(
            "cluster_PEMMDB", "cluster_BP", "Category", "Fuel", "Type", "efficiency_range", "efficiency_default",
            "CO2", "OM_cost", "min_up_time", "min_down_time", "start_up_fuel", "start_up_fix_cost",
            "start_up_fuel_cold_start", "start_up_fix_cost_cold_start", "start_up_fuel_hot_start", "start_up_fix_cost_hot_start",
            "transition_hot_warm", "transition_hot_cold", "shutdown_time", "startup_time", "FO_rate_default",
            "FO_duration_default", "PO_duration_default", "PO_winter_default", "min_stable_generation_default",
            "ramp_up", "ramp_down", "fixed_generation_reduction");


    @Override
    public List<ThermalCommonParameterEntity> buildThermalCommonParameterValuesList(Path path, String horizon, Integer studyId) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = findHorizonSheetOrThrow(workbook, horizon, path);
            Row header = sheet.getRow(4);
            validateCommonParamHeaderColumns(header, path);

            Set<String> commonParamClusters = new HashSet<>();
            List<String> clustersWithoutParameters = new ArrayList<>();
            List<ThermalCommonParameterEntity> thermalParameters = parseThermalCommonParameterRows(sheet, header, commonParamClusters);

            if (thermalParameters.isEmpty()) {
                throw BusinessException.builder()
                        .message("No data found from line 6 in Common Param trajectory")
                        .build();            }

            checkMissingClusters(studyId, horizon, commonParamClusters, clustersWithoutParameters, path);

            return thermalParameters;
        } catch (IOException e) {
            throwTechnicalException(e);
            return Collections.emptyList(); // unreachable, mais pour le compilateur
        }
    }

    private Sheet findHorizonSheetOrThrow(Workbook workbook, String horizon, Path path) {
        Sheet sheet = findHorizonSheet(workbook, horizon);
        if (sheet == null) {
            throw TechnicalException.builder()
                    .message("Missing suitable sheet for horizon '" + horizon + "'")
                    .build();
        }
        return sheet;
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

    private void checkMissingClusters(Integer studyId, String horizon, Set<String> commonParamClusters, List<String> clustersWithoutParameters, Path path) {
        Set<String> installedPowerClusters = getInstalledPowerClustersByStudyId(studyId, horizon);
        if (!installedPowerClusters.isEmpty()) {
            installedPowerClusters.stream()
                    .filter(cluster -> !commonParamClusters.contains(cluster))
                    .forEach(clustersWithoutParameters::add);

            if (!clustersWithoutParameters.isEmpty()) {
                throw BusinessException.builder()
                        .message("The following clusters are missing in the Common Parameters trajectory: " + String.join(", ", clustersWithoutParameters))
                        .build();
            }
        }
    }

    private void throwTechnicalException(IOException e) {
        throw TechnicalException.builder()
                .message("Error processing file: " + e.getMessage())
                .build();
    }


    @Override
    public List<ThermalSpecificParametersEntity> buildThermalSpecificParameterValueList(Path trajectoryFilePath, String horizon, Integer studyId) {
        List<ThermalSpecificParametersEntity> result = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = findHorizonSheet(workbook, horizon);
            if (sheet == null) {
                throw TechnicalException.builder()
                        .message("Missing suitable sheet for horizon '" + horizon + "'")
                        .build();
            }

           // Set<String> installedPowerClusters = getInstalledPowerClustersByStudyId(studyId, horizon);
            Set<String> specificParamClusters = new HashSet<>();
          //  List<String> clustersWithoutParameters = new ArrayList<>();

            Row header = sheet.getRow(0);
            for (Row row : sheet) {
                if (row.getRowNum() <= 2) continue; // skip headers/metadata lines

                String clusterName = castString(getCellValue(row, 4));
                String clusterPemmdb = castString(getCellValue(row, 3));
                if ((clusterName == null || clusterName.isBlank()) && (clusterPemmdb == null || clusterPemmdb.isBlank())) {
                    continue;
                }
                specificParamClusters.add(clusterName);

                ThermalSpecificParametersEntity entity = ThermalSpecificParametersEntity.builder()
                        .thermalClusterRef(findOrCreateThermalClusterRef(null, clusterName, clusterPemmdb))
                        .node(castString(getCellValue(row, 0)))
                        .nodeEntsoe(castString(getCellValue(row, 1)))
                        .comment(castString(getCellValue(row, 2)))
                        .minStableGeneration(castDouble(getCellValue(row, 5), header.getCell(5).getStringCellValue()))
                        .spinning(castDouble(getCellValue(row, 6), header.getCell(6).getStringCellValue()))
                        .efficiency(castDouble(getCellValue(row, 7), header.getCell(7).getStringCellValue()))
                        .foRate(castDouble(getCellValue(row, 8), header.getCell(8).getStringCellValue()))
                        .foDuration(castDouble(getCellValue(row, 9), header.getCell(9).getStringCellValue()))
                        .poDuration(castDouble(getCellValue(row, 10), header.getCell(10).getStringCellValue()))
                        .poWinter(castDouble(getCellValue(row, 11), header.getCell(11).getStringCellValue()))
                        .marginalCost(castDouble(getCellValue(row, 12), header.getCell(12).getStringCellValue()))
                        .marketBid(castDouble(getCellValue(row, 13), header.getCell(13).getStringCellValue()))
                        .mrSpecific(castInt(getCellValue(row, 14)))
                        .cmSpecific(castInt(getCellValue(row, 15)))
                        .npoMaxWinther(castInt
                                (getCellValue(row, 16)))
                        .npoMaxSummer(castInt
                                (getCellValue(row, 17)))
                        .nbUnit(castInt
                                (getCellValue(row, 18)))
                        .poWinterRate(castDouble(getCellValue(row, 19), header.getCell(19).getStringCellValue()))
                        .f1(castDouble(getCellValue(row, 20), header.getCell(20).getStringCellValue()))
                        .f2(castDouble(getCellValue(row, 21), header.getCell(21).getStringCellValue()))
                        .f3(castDouble(getCellValue(row, 22), header.getCell(22).getStringCellValue()))
                        .f4(castDouble(getCellValue(row, 23), header.getCell(23).getStringCellValue()))
                        .f5(castDouble(getCellValue(row, 24), header.getCell(24).getStringCellValue()))
                        .f6(castDouble(getCellValue(row, 25), header.getCell(25).getStringCellValue()))
                        .f7(castDouble(getCellValue(row, 26), header.getCell(26).getStringCellValue()))
                        .f8(castDouble(getCellValue(row, 27), header.getCell(27).getStringCellValue()))
                        .f9(castDouble(getCellValue(row, 28), header.getCell(28).getStringCellValue()))
                        .f10(castDouble(getCellValue(row, 29), header.getCell(29).getStringCellValue()))
                        .f11(castDouble(getCellValue(row, 30), header.getCell(30).getStringCellValue()))
                        .f12(castDouble(getCellValue(row, 31), header.getCell(31).getStringCellValue()))
                        .p1(castDouble(getCellValue(row, 32), header.getCell(32).getStringCellValue()))
                        .p2(castDouble(getCellValue(row, 33), header.getCell(33).getStringCellValue()))
                        .p3(castDouble(getCellValue(row, 34), header.getCell(34).getStringCellValue()))
                        .p4(castDouble(getCellValue(row, 35), header.getCell(35).getStringCellValue()))
                        .p5(castDouble(getCellValue(row, 36), header.getCell(36).getStringCellValue()))
                        .p6(castDouble(getCellValue(row, 37), header.getCell(37).getStringCellValue()))
                        .p7(castDouble(getCellValue(row, 38), header.getCell(38).getStringCellValue()))
                        .p8(castDouble(getCellValue(row, 39), header.getCell(39).getStringCellValue()))
                        .p9(castDouble(getCellValue(row, 40), header.getCell(40).getStringCellValue()))
                        .p10(castDouble(getCellValue(row, 41), header.getCell(41).getStringCellValue()))
                        .p11(castDouble(getCellValue(row, 42), header.getCell(42).getStringCellValue()))
                        .p12(castDouble(getCellValue(row, 43), header.getCell(43).getStringCellValue()))
                        .build();
                result.add(entity);
            }

            if (result.isEmpty()) {
                throw BusinessException.builder()
                        .message("No data found from line 6 in Specific Param file")
                        .build();
            }

            //installedPowerClusters.stream()
              //      .filter(cluster -> !specificParamClusters.contains(cluster))
               //     .forEach(clustersWithoutParameters::add);

           // if (!clustersWithoutParameters.isEmpty()) {
             //   throw BusinessException.builder()
               //         .message("Missing clusters: " + String.join(", ", clustersWithoutParameters))
                 //       .build();
           // }

            return result;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error processing file: " + e.getMessage())
                    .build();
        }
    }


    // Java
    private Integer castInt(Object cellValue) {
        if (cellValue == null) return null;
        else if (cellValue instanceof Number n) {
            return n.intValue();
        }
        return null; // couvrir les autres types
    }




    private ThermalCommonParameterEntity buildThermalCommonParameterEntity(Row row, String clusterName, String clusterPemmdb, Row header) {
        return ThermalCommonParameterEntity.builder()
                .thermalClusterRef(findOrCreateThermalClusterRef(null, clusterName, clusterPemmdb))
                .category(castDouble(getCellValue(row, 2), header.getCell(2).getStringCellValue()))
                .fuel(castString(getCellValue(row, 3)))
                .type(castString(getCellValue(row, 4)))
                .efficiencyRange(castString(getCellValue(row, 5)))
                .efficiencyDefault(castDouble(getCellValue(row, 6), header.getCell(6).getStringCellValue()))
                .co2(castDouble(getCellValue(row, 7), header.getCell(7).getStringCellValue()))
                .omCost(castDouble(getCellValue(row, 8), header.getCell(8).getStringCellValue()))
                .minUpTime(castDouble(getCellValue(row, 9), header.getCell(9).getStringCellValue()))
                .minDownTime(castDouble(getCellValue(row, 10), header.getCell(10).getStringCellValue()))
                .startUpFuel(castDouble(getCellValue(row, 11), header.getCell(11).getStringCellValue()))
                .startUpFixCost(castDouble(getCellValue(row, 12), header.getCell(12).getStringCellValue()))
                .startUpFuelColdStart(castDouble(getCellValue(row, 13), header.getCell(13).getStringCellValue()))
                .startUpFixCostColdStart(castDouble(getCellValue(row, 14), header.getCell(14).getStringCellValue()))
                .startUpFuelHotStart(castDouble(getCellValue(row, 15), header.getCell(15).getStringCellValue()))
                .startUpFixCostHotStart(castDouble(getCellValue(row, 16), header.getCell(16).getStringCellValue()))
                .transitionHotWarm(castDouble(getCellValue(row, 17), header.getCell(17).getStringCellValue()))
                .transitionHotCold(castDouble(getCellValue(row, 18), header.getCell(18).getStringCellValue()))
                .shutdownTime(castDouble(getCellValue(row, 19), header.getCell(19).getStringCellValue()))
                .startupTime(castDouble(getCellValue(row, 20), header.getCell(20).getStringCellValue()))
                .foRateDefault(castDouble(getCellValue(row, 21), header.getCell(21).getStringCellValue()))
                .foDurationDefault(castDouble(getCellValue(row, 22), header.getCell(22).getStringCellValue()))
                .poDurationDefault(castDouble(getCellValue(row, 23), header.getCell(23).getStringCellValue()))
                .poWinterDefault(castDouble(getCellValue(row, 24), header.getCell(24).getStringCellValue()))
                .minStableGenerationDefault(castDouble(getCellValue(row, 25), header.getCell(25).getStringCellValue()))
                .rampUp(castDouble(getCellValue(row, 26), header.getCell(26).getStringCellValue()))
                .rampDown(castDouble(getCellValue(row, 27), header.getCell(27).getStringCellValue()))
                .fixedGenerationReduction(castDouble(getCellValue(row, 28), header.getCell(28).getStringCellValue()))
                .build();
    }


    public Set<String> getInstalledPowerClustersByStudyId(Integer studyId, String horizon) {
        List<TrajectoryEntity> installedTrajectories = trajectoryRepository
                .findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name());

        return installedTrajectories.stream()
                .map(TrajectoryEntity::getThermalClusterCapacities)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(e -> e.getThermalClusterRef().getName())
                .collect(Collectors.toSet());
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
        return saveThermalCommonTrajectory(trajectory, list, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    /**
     * Processes the given file.
     * If a trajectory with the same file name exists, it updates the trajectory.
     * Otherwise, it creates a new trajectory.
     *
     * @param path the path to the file to process
     */
    public TrajectoryEntity processThermalCapacityFile(Path path, String horizon, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type, String area, String technology) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";
        return saveThermalCapacitiesTrajectory(buildTrajectory(path, 0, horizon, createdBy, TrajectoryType.THERMAL_CAPACITY, area, technology), thermalClusterCapacityDto, type);
    }

    /**
     * Saves the thermal trajectory and associates it with the given thermal entities.
     *
     * @param trajectory                the trajectory entity to save
     * @param thermalClusterCapacityDto the list of thermal entities to associate with the trajectory
     * @param type                      the type of the trajectory
     * @return the saved trajectory entity
     */
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

    @Override
    public TrajectoryEntity saveThermalCommonTrajectory(TrajectoryEntity trajectory, List<ThermalCommonParameterEntity> thermalCommonParameterEntityList, TrajectoryType type) {
        trajectory.setType(type.name());
        thermalCommonParameterEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        trajectory.setThermalClusterParameters(thermalCommonParameterEntityList);
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
                        .message("The value of power or number of horizon {0} in THERMAL Installed Power trajectory must be numeric")
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

    private static Double castDouble(Object o, String columnName) {
        if (o == null) return null;
        try {
            java.math.BigDecimal bd;
            if (o instanceof Number n) {
                bd = java.math.BigDecimal.valueOf(n.doubleValue());
            } else {
                bd = new java.math.BigDecimal(String.valueOf(o));
            }
            bd = bd.setScale(2, java.math.RoundingMode.HALF_UP);
            return bd.doubleValue();
        } catch (NumberFormatException e) {
            throw BusinessException.builder()
                    .message("The value '" + o + "' in column '" + columnName + "' is not numeric")
                    .build();
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

        List<ThermalClusterRef> list = thermalClusterRefRepository.findAll();
        cachedClusterRefs = new ArrayList<>(list);
    }

    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name) {
        // Backward-compatible delegate: no PEMMDB provided
        return findOrCreateThermalClusterRef(technology, name, null);
    }

    /**
     * Finds an existing ThermalClusterRef by technology and name, or creates a new one if not found.
     * If a `namePemmdb` value is provided, the method may update an existing entry if its `namePemmdb`
     * field is blank or set to "NA".
     * <p>
     * The method first checks the cached `ThermalClusterRef` instances. If not present or not matching
     * the search parameters, it attempts to find an associated `ThermalTechnology`. If the technology
     * does not exist, it creates a new one and associates it with the created ThermalClusterRef.
     *
     * @param technology the name of the thermal technology; a default value of "UNKNOWN" is used if null or blank
     * @param name       the name of the thermal cluster; defaults to an empty string if null
     * @param namePemmdb an optional value to be associated with the ThermalClusterRef; if null or blank, "NA" is used
     * @return the existing or newly created ThermalClusterRef instance with the specified properties
     */
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        ensureClusterRefsLoaded();

        Optional<ThermalClusterRef> existingOpt = findCachedClusterRef(technology, name);

        if (existingOpt.isPresent()) {
            return updatePemmdbIfNeeded(existingOpt.get(), namePemmdb);
        }

        ThermalTechnology thermalTechnology =  technology != null ? findOrCreateTechnology(technology) : null;
        ThermalClusterRef newRef = buildClusterRef(name, thermalTechnology, namePemmdb);
        ThermalClusterRef saved = thermalClusterRefRepository.save(newRef);
        cachedClusterRefs.add(saved);
        return saved;
    }

    private void ensureClusterRefsLoaded() {
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
    }

    private Optional<ThermalClusterRef> findCachedClusterRef(String technology, String name) {
        return cachedClusterRefs.stream()
                .filter(ref -> ref.getName() != null && ref.getName().equalsIgnoreCase(name)
                        && (technology == null || technology.isBlank()
                        || (ref.getThermalTechnology() != null
                        && ref.getThermalTechnology().getName() != null
                        && ref.getThermalTechnology().getName().equalsIgnoreCase(technology))))
                .findFirst();
    }


    private ThermalClusterRef updatePemmdbIfNeeded(ThermalClusterRef ref, String namePemmdb) {
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            String current = ref.getNamePemmdb();
            if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                ref.setNamePemmdb(namePemmdb);
                return thermalClusterRefRepository.save(ref);
            }
        }
        return ref;
    }

    private ThermalTechnology findOrCreateTechnology(String technology) {
        return thermalTechnologyRepository.findThermalTechnologyByName(technology)
                .orElseGet(() -> thermalTechnologyRepository.save(
                        ThermalTechnology.builder().name(technology).build()));
    }

    private ThermalClusterRef buildClusterRef(String name, ThermalTechnology technology, String namePemmdb) {
        return ThermalClusterRef.builder()
                .name(name)
                .thermalTechnology(technology)
                .namePemmdb((namePemmdb != null && !namePemmdb.isBlank()) ? namePemmdb : "NA")
                .build();
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
