package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.CategoryEnum;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.MiscFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiscFileProcessorServiceImpl implements MiscFileProcessorService {
    @Getter
    @RequiredArgsConstructor
    private static class MiscRowProcessingContext {
        private final List<String> studyAreas;
        private final String areaParam;
        private final int yearColIndex;
        private final String trajectoryToUse;
    }

    @Getter
    @RequiredArgsConstructor
    private static class MiscRowProcessingResult {
        private final List<MiscClusterCapacityEntity> entities;
        private final StringBuilder checksumBuilder;
        private final List<String> fileAreas;
        private final Set<String> invalidCombos;
    }

    private final TrajectoryRepository trajectoryRepository;
    private final MiscClusterCapacityRepository miscClusterCapacityRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;

    private final TrajectoryServiceImpl trajectoryService;

    private static final String INSTALLED_MISC_PREFIX = "installedMisc_";
    protected static final String[] REQUIRED_CLUSTER_COLUMNS = {"ToUse", "Area", "Group", "Cluster", "Category"};

    // Group constants
    private static final String GROUP_BIOMASS = "biomass";
    private static final String GROUP_BIOGAS = "biogas";
    private static final String GROUP_GEOTHERMAL = "geothermal";
    private static final String GROUP_OTHER = "other";
    private static final String GROUP_WASTE = "waste";
    private static final String GROUP_WAVE = "wave";
    private static final String GROUP_HYDROKINETIC = "hydrokinetic";

    // Cluster constants
    private static final String CLUSTER_BIOMASS = "Small biomass";
    private static final String CLUSTER_BIOGAS = "biogas";
    private static final String CLUSTER_GEOTHERMAL = "geothermal";
    private static final String CLUSTER_OTHER = "other";
    private static final String CLUSTER_WASTE = "waste";
    private static final String CLUSTER_WAVE = "wave";
    private static final String CLUSTER_HYDROKINETIC = "hydrokinetic";

    private static final Set<String> VALID_GROUPS = Set.of(
            GROUP_BIOMASS, GROUP_BIOGAS, GROUP_GEOTHERMAL, GROUP_OTHER,
            GROUP_WASTE, GROUP_WAVE, GROUP_HYDROKINETIC
    );

    // Error message constants
    private static final String ERROR_LOAD_FACTOR_NOT_FOUND = "Load factor file not found for group {0}: expected at {1}";
    private static final String ERROR_LOAD_FACTOR_MISSING_AREAS = "Load factor file {0} is missing areas {1} for group {2}";
    private static final String ERROR_LOAD_FACTOR_EMPTY = "Load factor file {0} for group {1} is empty";


    @Transactional
    @Override
    public TrajectoryEntity processInstalledMiscFile(String trajectoryToUse, String horizon, Integer studyId, String areaParam, boolean isCivilYear) throws IOException {
        // prefix check
        if (!startsWithIgnoreCase(trajectoryToUse, INSTALLED_MISC_PREFIX)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(INSTALLED_MISC_PREFIX))
                    .message("The trajectory file name must start with {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream().map(a -> a.getName().toUpperCase()).toList();

        Path filePath = trajectoryService.getTrajectoryFilePath(TrajectoryType.MISC_CAPACITY, trajectoryToUse, null);

        // parse installedMisc sheet
        try (InputStream is = Files.newInputStream(filePath); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = getRequiredSheet(workbook, "InstalledMisc", filePath, TrajectoryType.MISC_CAPACITY.name());

            Row header = sheet.getRow(0);
            if (header == null) {
                throw BusinessException.builder()
                        .message("Missing header in InstalledMisc file for trajectory " + filePath.getFileName())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Expected cols: ToUse, Area, Group, Cluster, Category, <years...>
            int lastCol = header.getLastCellNum();
            if (lastCol < 6) {
                throw BusinessException.builder().message("InstalledMisc header is invalid").httpStatus(HttpStatus.BAD_REQUEST).build();
            }

            checkMissingColumns(sheet, REQUIRED_CLUSTER_COLUMNS, trajectoryToUse, TrajectoryType.MISC_CAPACITY.name());

            // Détecter l'index de la colonne correspondant à l'horizon hors de la boucle de lignes
            int yearColIndex = -1;
            String horizonYear = horizon.split("-")[1];

            yearColIndex = getYearColIndex(lastCol, header, horizonYear, yearColIndex);
            if (yearColIndex == -1) {
                throw BusinessException.builder()
                        .message("Horizon '" + horizon + "' does not exist in the Misc trajectory " + filePath.getFileName())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            List<MiscClusterCapacityEntity> entities = new ArrayList<>();
            Iterator<Row> rows = sheet.rowIterator();
            rows.next(); // skip header
            StringBuilder checksumBuilder = new StringBuilder();

            List<String> fileAreas = new ArrayList<>();
            Set<String> invalidCombos = new LinkedHashSet<>();
            boolean allRowsEmpty = true;
            MiscRowProcessingContext context = new MiscRowProcessingContext(studyAreas, areaParam, yearColIndex, trajectoryToUse);

            MiscRowProcessingResult result = new MiscRowProcessingResult(entities, checksumBuilder, fileAreas, invalidCombos);

            while (rows.hasNext()) {
                Row row = rows.next();

                if (!ExcelCommonValidator.isRowEmpty(row)) {
                    allRowsEmpty = false;
                    processMiscCapacityRow(context, result, row);
                }
            }

            if (allRowsEmpty) {
                throw BusinessException.builder().message("No area found in Misc trajectory " + filePath.getFileName()).httpStatus(HttpStatus.BAD_REQUEST).build();
            }

            // The selected area must be present in the file's 'node' column, except when area equals OTHERS
            validateSelectedAreaPresence(areaParam, fileAreas, TrajectoryType.MISC_CAPACITY, trajectoryToUse);

            validateTrajectoryAreasPresence(studyAreas, fileAreas, TrajectoryType.MISC_CAPACITY, trajectoryToUse);

            if (!invalidCombos.isEmpty()) {
                String combos = String.join(", ", invalidCombos);

                throw BusinessException.builder()
                        .message("Values for node/group/cluster %s are not numeric in Misc trajectory %s"
                                .formatted(combos, trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            trajectoryService.controlesMiscOnImportInstalledPower(studyId, entities, areaParam);
            TrajectoryEntity trajectory = buildMiscTrajectory(horizon, areaParam, checksumBuilder, filePath, entities);

            // save trajectory
            return trajectoryRepository.save(trajectory);
        }
    }

    @Override
    public TrajectoryEntity processLoadFactorMiscFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws Exception {
        Path trajectoryFilePath = trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD);

        Map<GroupClusterKey, List<String>> listAreasByGroup = getAreasByGroupClusterByStudyId(studyId, area);

        if (listAreasByGroup.isEmpty()) {
            verifyLoadFactorTsFilesWithoutInstalledPower(horizon, studyId, area, trajectoryFilePath);

        } else {
            verifyLoadFactorTsFilesWithInstalledPower(horizon, studyId, listAreasByGroup, trajectoryFilePath, area);
        }
        TrajectoryEntity trajectory = buildLoadFactorMiscTrajectory(trajectoryFilePath, horizon, area);
        return trajectoryRepository.save(trajectory);

    }

    private void verifyLoadFactorTsFilesWithInstalledPower(String horizon, Integer studyId, Map<GroupClusterKey, List<String>> listAreasByGroup, Path trajectoryFilePath, String area) throws Exception {
        for (Map.Entry<GroupClusterKey, List<String>> entry : listAreasByGroup.entrySet()) {
            GroupClusterKey groupCluster = entry.getKey();
            List<String> areas = entry.getValue().stream().map(String::toLowerCase).collect(Collectors.toList());
            verifyTsFile(horizon, trajectoryFilePath, groupCluster, areas, studyId, area);
        }
    }

    private static void verifyLoadFactorTsFilesWithoutInstalledPower(String horizon, Integer studyId, String area, Path trajectoryFilePath) {
        log.warn("No group found for study id {} and area {} in misc cluster capacity table, at least one group is expected to check load factor file(s)", studyId, area);
        //check that all files exist
        List<GroupClusterKey> groupClusterKeyList = List.of(
                new GroupClusterKey("biomass", ""),
                new GroupClusterKey("biogas", ""),
                new GroupClusterKey("geothermal", ""),
                new GroupClusterKey("other", ""),
                new GroupClusterKey("waste", ""),
                new GroupClusterKey("wave", ""),
                new GroupClusterKey("hydrokinetic", "")
        );
        groupClusterKeyList.forEach(groupClusterKey -> {
            Path tsFilePath = getLoadFactorByGroupPath(horizon, trajectoryFilePath, groupClusterKey);
            if (!Files.exists(tsFilePath)) {
                throw BusinessException.builder()
                        .message("Load factor file not found for group {0}: expected at {1}")
                        .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                        .build();
            } else {
                try {
                    List<String> headerAreas = readHeaderAreas(horizon, trajectoryFilePath, groupClusterKey);
                    if (area != null && !OTHERS_AREA.equalsIgnoreCase(area)) {
                        if (!headerAreas.contains(area.toLowerCase())) {
                            throw BusinessException.builder()
                                    .message("Load factor file {0} is missing area {1} for group {2}")
                                    .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), area, groupClusterKey.groupe))
                                    .build();
                        }
                    }
                    log.info("Load factor file {} for group {} found at expected location {}", tsFilePath.getFileName(), groupClusterKey.groupe, tsFilePath);
                } catch (TechnicalException | IOException technicalException) {
                    throw TechnicalException.builder()
                            .message("Error while reading load factor file for group {0} at {1}: {2}")
                            .errorMessageArguments(List.of(groupClusterKey.groupe, tsFilePath.getFileName().toString(), technicalException.getMessage()))
                            .build();
                }
            }
        });
    }

    private TrajectoryEntity buildLoadFactorMiscTrajectory(Path trajectoryFilePath, String horizon, String area) throws Exception {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String checksum = calculateDirectoryChecksum(trajectoryFilePath);
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.MISC_LOAD.name(), null))// file name without extension
                .fileSize(Files.size(trajectoryFilePath))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .checksum(checksum)
                .lastModificationContentDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(Files.getLastModifiedTime(trajectoryFilePath).toMillis()), ZoneId.systemDefault()))
                .horizon(civilToChevalHorizon(horizon))
                .area(area)
                .technology(null)
                .type(TrajectoryType.MISC_LOAD.name())
                .hasTimeSeries(true)
                .build();
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(trajectoryFilePath, horizon, area, TrajectoryType.MISC_LOAD);
        if (existingTrajectory.isPresent()) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                // use Utils since method moved
                throwAlreadyProcessedFileException(trajectoryFilePath);
            } else {
                trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
                trajectory.setChecksum(checksum);
            }
        } else {
            trajectory.setVersion(1);
            trajectory.setChecksum(checksum);
        }

        return trajectory;
    }

    private void verifyTsFile(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey, List<String> expectedAreas, Integer studyId, String selectedArea) throws Exception {

        Path tsFilePath = getLoadFactorByGroupPath(horizon, trajectoryFilePath, groupClusterKey);

        // 1. Lire header du fichier courant
        List<String> currentHeader = readHeaderAreas(horizon, trajectoryFilePath, groupClusterKey);

        if (verifySpecificAreatTsFile(groupClusterKey, expectedAreas, selectedArea, currentHeader, tsFilePath)) return;

        // 3. Cas OTHERS → merge des zones
        Set<String> mergedAreas = new LinkedHashSet<>(currentHeader);

        mergeSpecificTrajectoryWithActualOther(horizon, groupClusterKey, studyId, tsFilePath, mergedAreas);

        // 4. Vérification finale : toutes les zones attendues sont présentes
        Set<String> missingAreas = new HashSet<>(expectedAreas.stream().map(String::toLowerCase).toList());

        mergedAreas.forEach(missingAreas::remove);

        if (!missingAreas.isEmpty()) {
            throw BusinessException.builder()
                    .message("Load factor file {0} is missing areas {1} for group {2}")
                    .errorMessageArguments(List.of(
                            tsFilePath.getFileName().toString(),
                            missingAreas.toString(),
                            groupClusterKey.groupe))
                    .message(ERROR_LOAD_FACTOR_MISSING_AREAS)
                    .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), missingAreas.toString(), groupClusterKey.groupe))
                    .build();
        }

        log.info("Load factor file {} for group {} contains all expected areas",
                tsFilePath.getFileName(), groupClusterKey.groupe);
    }

    private void mergeSpecificTrajectoryWithActualOther(String horizon, GroupClusterKey groupClusterKey, Integer studyId, Path tsFilePath, Set<String> mergedAreas) {
        List<TrajectoryEntity> existingTrajectories = trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name());

        for (TrajectoryEntity traj : existingTrajectories) {

            if (OTHERS_AREA.equalsIgnoreCase(traj.getArea())) continue;

            try {
                Path existingTrajectoryPath = trajectoryService.buildTrajectoryPath(traj.getFileName(), TrajectoryType.MISC_LOAD);

                Path existingTsPath = getLoadFactorByGroupPath(horizon, existingTrajectoryPath, groupClusterKey);

                if (!Files.exists(existingTsPath)) {
                    throw BusinessException.builder()
                            .message("Load factor file not found for group {0}: expected at {1}")
                            .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                            .build();
                }
                List<String> header = readHeaderAreas(horizon, existingTrajectoryPath, groupClusterKey);

                if (header.contains(traj.getArea().toLowerCase())) {
                    mergedAreas.add(traj.getArea().toLowerCase());
                }
            } catch (Exception e) {
                log.warn("Skipping trajectory {}: {}", traj.getFileName(), e.getMessage());
            }
        }
    }

    private static boolean verifySpecificAreatTsFile(GroupClusterKey groupClusterKey, List<String> expectedAreas, String selectedArea, List<String> currentHeader, Path tsFilePath) {
        if (!OTHERS_AREA.equalsIgnoreCase(selectedArea)) {

            String area = selectedArea.toLowerCase();

            // ✅ Vérif 2 : présence dans le fichier
            if (!currentHeader.contains(area)) {
                throw BusinessException.builder()
                        .message("Load factor file {0} is missing area {1} for group {2}")
                        .errorMessageArguments(List.of(
                                tsFilePath.getFileName().toString(),
                                selectedArea,
                                groupClusterKey.groupe))
                        .build();
            }
            // ✅ Vérif 1 : cohérence IP
            if (!expectedAreas.contains(area)) {
                throw BusinessException.builder()
                        .message("Area {0} is not expected for group {1}")
                        .errorMessageArguments(List.of(
                                selectedArea,
                                groupClusterKey.groupe))
                        .build();
            }

            log.info("Load factor file {} for group {} contains expected area {}",
                    tsFilePath.getFileName(), groupClusterKey.groupe, selectedArea);

            return true;
        }
        return false;
    }

    public static List<String> readHeaderAreas(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey) throws IOException {
        Path tsFilePath = getLoadFactorByGroupPath(horizon, trajectoryFilePath, groupClusterKey);
        if (Files.exists(tsFilePath)) {
            try (Scanner scanner = new Scanner(tsFilePath)) {
                if (scanner.hasNextLine()) {
                    String headerLine = scanner.nextLine();
                    return Arrays.stream(headerLine.split(";"))
                            .map(s -> s.strip().replace("\"", "").toLowerCase())
                            .toList();
                } else {
                    throw BusinessException.builder()
                            .message(ERROR_LOAD_FACTOR_EMPTY)
                            .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                            .build();
                }
            }

        } else {
            throw BusinessException.builder()
                    .message(ERROR_LOAD_FACTOR_NOT_FOUND)
                    .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                    .build();
        }
    }

    public static Path getLoadFactorByGroupPath(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey) {
        Path groupPath = trajectoryFilePath.resolve(groupClusterKey.groupe);

        String cluster;

        if (groupClusterKey.cluster != null && !groupClusterKey.cluster.isEmpty()) {
            cluster = groupClusterKey.cluster;
        } else {
            try (Stream<Path> paths = Files.list(groupPath)) {
                cluster = paths
                        .filter(Files::isDirectory)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No cluster directory found under " + groupPath))
                        .getFileName()
                        .toString();
            } catch (IOException e) {
                throw new RuntimeException("Error reading directory " + groupPath, e);
            }
        }

        return groupPath
                .resolve(cluster)
                .resolve("load_factor_" + cluster + "_" + horizon + ".csv");
    }

    public record GroupClusterKey(String groupe, String cluster) {
    }

    public Map<GroupClusterKey, List<String>> getAreasByGroupClusterByStudyId(Integer studyId, String area) {

        return miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area)
                .stream()
                .collect(Collectors.groupingBy(
                        entity -> new GroupClusterKey(
                                entity.getGroupe(),
                                entity.getCluster()
                        ),
                        Collectors.mapping(
                                GroupAreaMiscCapacity::getArea,
                                Collectors.toList()
                        )
                ));
    }

    public Map<GroupClusterKey, List<String>> getAreasByGroupClusterByTrajectoryId(Integer trajectoryId) {
        return miscClusterCapacityRepository.findByTrajectoryId(trajectoryId)
                .stream()
                .collect(Collectors.groupingBy(
                        entity -> new GroupClusterKey(
                                entity.getGroupe(),
                                entity.getCluster()
                        ),
                        Collectors.mapping(
                                GroupAreaMiscCapacity::getArea,
                                Collectors.toList()
                        )
                ));
    }

    private static int getYearColIndex(int lastCol, Row header, String horizonYear, int yearColIndex) {
        for (int c = 5; c < lastCol; c++) {
            Integer headerVal = (int) header.getCell(c).getNumericCellValue();
            if (horizonYear.equals(String.valueOf(headerVal).trim())) {
                yearColIndex = c;
                break;
            }
        }
        return yearColIndex;
    }

    private TrajectoryEntity buildMiscTrajectory(String horizon, String areaParam, StringBuilder checksumBuilder, Path filePath, List<MiscClusterCapacityEntity> entities) throws IOException {
        String checksum = calculateChecksum(checksumBuilder.toString());
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(filePath, horizon, areaParam, TrajectoryType.MISC_CAPACITY);
        TrajectoryEntity trajectory = buildInstalledMiscTrajectory(filePath, horizon, areaParam);

        if (existingTrajectory.isPresent() && existingTrajectory.get().getChecksum() != null) {
            if (existingTrajectory.get().getChecksum().equals(checksum)) {
                // use Utils since method moved
                throwAlreadyProcessedFileException(filePath);
            } else {
                trajectory.setChecksum(checksum);
                trajectory.setVersion(existingTrajectory.get().getVersion() + 1);
            }
        } else if (existingTrajectory.isEmpty()) {
            trajectory.setChecksum(checksum);
            trajectory.setVersion(1);
        }

        entities.forEach(e -> e.setTrajectory(trajectory));
        trajectory.setMiscClusterCapacityEntities(entities);
        return trajectory;
    }

    private void processMiscCapacityRow(MiscRowProcessingContext context, MiscRowProcessingResult result, Row row) {

        if (ExcelCommonValidator.isRowEmpty(row)) return;

        Boolean toUse = ExcelCommonValidator.getBooleanCellValue(row.getCell(0)).orElse(null);
        if (Boolean.FALSE.equals(toUse)) {
            return;
        }

        String area = Optional.ofNullable(getCellValue(row, 1)).map(Object::toString).orElse(null);

        if (context.getAreaParam() != null
                && !OTHERS_AREA.equalsIgnoreCase(context.getAreaParam())
                && !context.getAreaParam().equalsIgnoreCase(Objects.toString(area, ""))) {
            return;
        }

        if (OTHERS_AREA.equalsIgnoreCase(context.getAreaParam())
                && !context.getStudyAreas().contains(Objects.toString(area, "").toUpperCase())) {
            return;
        }

        result.getFileAreas().add(area);

        String group = Optional.ofNullable(getCellValue(row, 2)).map(Object::toString).orElse(null);

        String cluster = Optional.ofNullable(getCellValue(row, 3)).map(Object::toString).orElse(null);
        String category = CategoryEnum.POWER.name().toLowerCase(); // default category since only power is currently expected in Misc, can be extended later if needed

        if (toUse == null || area == null || group == null || cluster == null) {
            throw BusinessException.builder()
                    .message("ToUse, Area, Group, Cluster values can't be empty in Misc trajectory {0}")
                    .errorMessageArguments(List.of(context.getTrajectoryToUse()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check if group is a valid group
        if (!VALID_GROUPS.contains(group.toLowerCase().trim())) {
            throw BusinessException.builder()
                    .message("Group '{0}' is not a valid group. Valid groups are: {1} in Misc trajectory {2}")
                    .errorMessageArguments(List.of(group, String.join(", ", VALID_GROUPS), context.getTrajectoryToUse()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String combo = "%s/%s/%s".formatted(
                Objects.toString(area, ""),
                Objects.toString(group, ""),
                Objects.toString(cluster, "")
        );

        Object cellVal = getCellValue(row, context.getYearColIndex());
        Number numeric = null;

        if (cellVal instanceof Number) {
            numeric = (Number) cellVal;
        } else if (cellVal instanceof String) {
            try {
                numeric = Double.parseDouble((String) cellVal);
            } catch (NumberFormatException ignored) {
                result.getInvalidCombos().add(combo);
            }
        }

        if (numeric == null) {
            result.getInvalidCombos().add(combo);
            return;
        }

        BigDecimal capacityByYear = BigDecimal.valueOf(numeric.doubleValue());

        MiscClusterCapacityEntity entity = MiscClusterCapacityEntity.builder()
                .toUse(toUse)
                .area(area)
                .groupe(group)
                .cluster(cluster)
                .category(category)
                .capacityByYear(capacityByYear)
                .build();

        result.getEntities().add(entity);

        result.getChecksumBuilder()
                .append(area).append("|")
                .append(group).append("|")
                .append(cluster).append("|")
                .append(category).append("|")
                .append(capacityByYear).append("|")
                .append(toUse).append("\n");
    }

    private Optional<TrajectoryEntity> findExistingTrajectory(Path path, String horizon, String area, TrajectoryType trajectoryType) {
        return trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name(), null),
                trajectoryType.name(),
                horizon,
                area,
                null);
    }

    private TrajectoryEntity buildInstalledMiscTrajectory(Path trajectoryFilePath, String horizon, String area) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        return buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.MISC_CAPACITY, area, null, null);
    }
}
