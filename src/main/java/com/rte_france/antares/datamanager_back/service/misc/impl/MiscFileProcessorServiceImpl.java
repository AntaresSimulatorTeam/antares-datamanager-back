package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.MiscFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
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

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiscFileProcessorServiceImpl implements MiscFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final MiscClusterCapacityRepository miscClusterCapacityRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;

    private final TrajectoryServiceImpl trajectoryService;

    private static final String INSTALLED_MISC_PREFIX = "installedMisc_";
    protected static final String[] REQUIRED_CLUSTER_COLUMNS = {
            "ToUse", "Area", "Group", "Cluster", "Category"};

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
            // TODO définir quelle année prendre quand isCivilYear est à true
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
            while (rows.hasNext()) {
                processMiscCapacityRow(areaParam, rows, yearColIndex, entities, checksumBuilder, fileAreas, trajectoryToUse, invalidCombos);
            }

            validateTrajectoryAreasPresence(studyAreas, fileAreas, TrajectoryType.MISC_CAPACITY, trajectoryToUse);

            // The selected area must be present in the file's 'node' column, except when area equals OTHERS
            validateSelectedAreaPresence(areaParam, fileAreas, TrajectoryType.MISC_CAPACITY, trajectoryToUse);

            if (entities.isEmpty()) {
                throw BusinessException.builder().message("No area found in Misc trajectory " + filePath.getFileName()).httpStatus(HttpStatus.BAD_REQUEST).build();
            }

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
        // for each group search ts file (ex: load_factor_waste_2030-2031 )  from
        // physical file system in  directory trajectoryFilePath/group/group
        // ou load_factor is the prefix of ts and group the group and horizon the horizon
        if (listAreasByGroup.isEmpty()) {
            log.warn("No group found for study id {} and area {} in misc cluster capacity table, at least one group is expected to check load factor file(s)", studyId, area);

        } else {
            for (Map.Entry<GroupClusterKey, List<String>> entry : listAreasByGroup.entrySet()) {
                GroupClusterKey groupCluster = entry.getKey();
                List<String> areas = entry.getValue().stream().map(String::toLowerCase).collect(Collectors.toList());
                verifyTsFile(horizon, trajectoryFilePath, groupCluster, areas, studyId);
            }
        }
        TrajectoryEntity trajectory = buildLoadFactorMiscTrajectory(trajectoryFilePath, horizon, area);
        return trajectoryRepository.save(trajectory);

    }

    private TrajectoryEntity buildLoadFactorMiscTrajectory(Path trajectoryFilePath, String horizon, String area) throws Exception {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String checksum = calculateDirectoryChecksum(trajectoryFilePath);
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.MISC_LOAD.name()))// file name without extension
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

    private void verifyTsFile(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey, List<String> areas, Integer studyId) throws Exception {

        Path tsFilePath = getLoadFactorByGroupPath(horizon, trajectoryFilePath, groupClusterKey);

        List<String> mergedHeader = mergedAllHeadersOfAllLoadFactorMiscTrajectories(horizon, trajectoryFilePath, groupClusterKey, studyId);
        Set<String> missingAreas = new HashSet<>(areas);
        mergedHeader.forEach(missingAreas::remove);
        if (!missingAreas.isEmpty()) {
            throw BusinessException.builder()
                    .message("Load factor file {0} is missing areas {1} for group {2}")
                    .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), missingAreas.toString(), groupClusterKey.groupe))
                    .build();
        } else {
            log.info("Load factor file {} for group {} contains all expected areas (merged headers)", tsFilePath.getFileName(), groupClusterKey.groupe);
        }
    }

    private List<String> mergedAllHeadersOfAllLoadFactorMiscTrajectories(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey, Integer studyId) throws IOException {
        // Read header of the current file
        List<String> headerAreas = readHeaderAreas(horizon, trajectoryFilePath, groupClusterKey);

        // Merge headers from existing misc load trajectories for this study/horizon (read from DB)
        Set<String> mergedHeaderSet = new LinkedHashSet<>(headerAreas);

        List<TrajectoryEntity> existingTrajectories = trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name());
        Set<Path> processedPaths = new HashSet<>();
        for (TrajectoryEntity traj : existingTrajectories) {
            try {
                Path existingTrajectoryFilePath = trajectoryService.buildTrajectoryPath(traj.getFileName(), TrajectoryType.MISC_LOAD);
                Path existingPath = getLoadFactorByGroupPath(horizon, existingTrajectoryFilePath, groupClusterKey);
                if (processedPaths.contains(existingPath)) continue;
                processedPaths.add(existingPath);
                if (Files.exists(existingPath)) {
                    List<String> otherHeader = readHeaderAreas(horizon, existingTrajectoryFilePath, groupClusterKey);
                    mergedHeaderSet.addAll(otherHeader);
                }
            } catch (Exception e) {
                log.warn("Could not read header for trajectory {}: {}", traj.getFileName(), e.getMessage());
            }
        }

        return new ArrayList<>(mergedHeaderSet);
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
                            .message("Load factor file {0} for group {1} is empty")
                            .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                            .build();
                }
            }

        } else {
            throw BusinessException.builder()
                    .message("Load factor file not found for group {0}: expected at {1}")
                    .errorMessageArguments(List.of(tsFilePath.getFileName().toString(), groupClusterKey.groupe))
                    .build();
        }
    }

    private static Path getLoadFactorByGroupPath(String horizon, Path trajectoryFilePath, GroupClusterKey groupClusterKey) {
        return trajectoryFilePath
                .resolve(groupClusterKey.groupe)
                .resolve(groupClusterKey.cluster)// for biomass group the file is in small biomass subfolder
                .resolve("load_factor_" + groupClusterKey.cluster + "_" + horizon + ".csv");

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

    private void processMiscCapacityRow(String areaParam, Iterator<Row> rows, int yearColIndex, List<MiscClusterCapacityEntity> entities, StringBuilder checksumBuilder, List<String> fileAreas, String trajectoryToUse, Set<String> invalidCombos) {
        Row row = rows.next();
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // toUse: ExcelCommonValidator peut extraire 1/0 comme boolean; si absent on considère false
        Boolean toUse = ExcelCommonValidator.getBooleanCellValue(row.getCell(0)).orElse(null);
        if (Boolean.FALSE.equals(toUse)) {
            //skip rows with ToUse = false or empty, they are not relevant for trajectory and can contain invalid data that we don't want to process
            return;
        }

        String area = Optional.ofNullable(getCellValue(row, 1)).map(Object::toString).orElse(null);
        fileAreas.add(area);

        String group = Optional.ofNullable(getCellValue(row, 2)).map(Object::toString).orElse(null);
        String cluster = Optional.ofNullable(getCellValue(row, 3)).map(Object::toString).orElse(null);
        String category = Optional.ofNullable(getCellValue(row, 4)).map(Object::toString).orElse(null);

        if (toUse == null || area == null || group == null || cluster == null || category == null) {
            throw BusinessException.builder()
                    .message("ToUse, Area, Group, Cluster and Category values can't be empty in Misc trajectory " + trajectoryToUse)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Filtre par area param (si areaParam différent de OTHERS on garde uniquement la même area)
        if (areaParam != null && !OTHERS_AREA.equalsIgnoreCase(areaParam)) {
            if (!areaParam.equalsIgnoreCase(Objects.toString(area, ""))) {
                return;
            }
        }

        String combo = "%s/%s/%s".formatted(
                Objects.toString(area, ""),
                Objects.toString(group, ""),
                Objects.toString(cluster, "")
        );

        // Récupérer la valeur numérique de la colonne correspondant à l'horizon de manière robuste
        Object cellVal = getCellValue(row, yearColIndex);
        Number numeric = null;
        if (cellVal instanceof Number) numeric = (Number) cellVal;
        else if (cellVal instanceof String) {
            try {
                numeric = Double.parseDouble((String) cellVal);
            } catch (NumberFormatException ignored) {
                invalidCombos.add(combo);
            }
        }
        if (numeric == null) {
            invalidCombos.add(combo);
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
        entities.add(entity);
        checksumBuilder.append(area).append("|")
                .append(group).append("|")
                .append(cluster).append("|")
                .append(category).append("|")
                .append(capacityByYear).append("|")
                .append(toUse).append("\n");
    }

    private Optional<TrajectoryEntity> findExistingTrajectory(Path path, String horizon, String area, TrajectoryType trajectoryType) {
        return trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), trajectoryType.name()),
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
