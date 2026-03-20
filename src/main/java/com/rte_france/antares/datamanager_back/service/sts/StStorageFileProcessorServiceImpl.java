package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageFileProcessorServiceImpl implements StStorageFileProcessorService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;


    @Transactional
    @Override
    public TrajectoryEntity processStStorageFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String areaParam, String technology) throws IOException {
        final String stsTrajectoryPrefix = "cluster_" + technology.toLowerCase() + "_";
        if (!trajectoryToUse.toLowerCase().startsWith(stsTrajectoryPrefix)) {
            throw BusinessException.builder().message(" {0} Trajectory name must start with : {1} ").errorMessageArguments(List.of(trajectoryToUse, stsTrajectoryPrefix)).build();
        }

        Path trajectoryFilePath = findTrajectoryFileCaseInsensitive(trajectoryToUse, technology);

        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream().map(a -> a.getName().toUpperCase()).toList();

        List<StStorageEntity> stStorageEntityList = buildStStorageLines(horizon.split("-")[1], trajectoryFilePath, areaParam, technology, studyAreas);
        if (stStorageEntityList.isEmpty()) {
            throw BusinessException.builder().message("No ST Storage data found in the file for horizon: " + horizon).build();
        }

        boolean isSeriesTrue = stStorageEntityList.stream()
                .anyMatch(entity -> Boolean.TRUE.equals(entity.getSeries()));

        TrajectoryEntity trajectoryEntity = buildStStorageTrajectory(trajectoryFilePath, horizon, areaParam, technology, isSeriesTrue);

        stStorageEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        return trajectoryRepository.save(trajectoryEntity);
    }

    public Path findTrajectoryFileCaseInsensitive(String trajectoryFileName, String technology) throws IOException {
        Path root = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(antaresDataManagerProperties.getTrajectoryFilePath()).resolve(antaresDataManagerProperties.getStsDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS root not found: " + root);
        }

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("clusters");

        try (Stream<Path> s = Files.list(techDir)) {
            java.util.Optional<Path> file = s.filter(Files::isRegularFile).filter(p -> {
                String fn = p.getFileName().toString();
                return fn.equalsIgnoreCase(trajectoryFileName + ".xlsx") || fn.equalsIgnoreCase(trajectoryFileName + ".xls");
            }).findFirst();

            if (file.isPresent()) {
                return file.get();
            } else {
                throw new NoSuchFileException("Trajectory file not found in " + techDir.toString() + " for '" + trajectoryFileName + "'");
            }
        }
    }


    private TrajectoryEntity buildStStorageTrajectory(Path trajectoryFilePath, String horizon, String areaParam, String technology, Boolean hasSeries) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name(), null), TrajectoryType.STS.name(), horizon, areaParam, technology);

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon.split("-")[1], createdBy, TrajectoryType.STS, areaParam, technology, hasSeries);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon.split("-")[1], createdBy, TrajectoryType.STS, areaParam, technology, hasSeries);
        }

        return trajectory;
    }

    private List<StStorageEntity> buildStStorageLines(String horizon, Path trajectoryFilePath, String areaParam, String technology, List<String> studyAreas) throws IOException {
        List<StStorageEntity> results = new ArrayList<>();
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryFilePath);

            String[] expectedColumns = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
            checkMissingColumns(sheet, expectedColumns, trajectoryFileName, TrajectoryType.STS.name());

            boolean foundStudyArea = false;

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) { // skip header
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                String rowArea = getStringCellValue(row, 0);
                String clusterName = getStringCellValue(row, 1);
                String groupName = getStringCellValue(row, 2);
                // Zone must be present
                if (rowArea == null || rowArea.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, String.valueOf(r)))
                            .message("Area is missing in STS trajectory " + trajectoryFileName + " for row: " + r)
                            .build();
                }

                if (!shouldIncludeRow(rowArea, areaParam) || !studyAreas.contains(rowArea.toUpperCase())) {
                    continue;
                }
                foundStudyArea = true;

                // Cluster name is mandatory
                if (clusterName == null || clusterName.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, rowArea, horizon))
                            .message("No valid cluster name found in the trajectory {0} for area {1} and horizon {2}")
                            .build();
                }

                // Group name is mandatory
                if (groupName == null || groupName.isEmpty()) {
                    throw BusinessException.builder()
                            .errorMessageArguments(List.of(trajectoryFileName, rowArea, horizon))
                            .message("No valid cluster group found in the trajectory {0} for area {1} and horizon {2}")
                            .build();
                }


                validateNumericRange(row, 3, 8, rowArea, clusterName, trajectoryFileName);

                StStorageEntity entity = mapRowToEntity(row, trajectoryFilePath, technology, rowArea, clusterName, trajectoryFileName);
                results.add(entity);
            }

            // The selected area must be present in the file's 'node' column, except when area equals OTHERS
            validateSelectedAreaPresence(areaParam, results, trajectoryFileName);

            if (!foundStudyArea) {
                throw BusinessException.builder()
                        .message("None of the areas of trajectory AREA are present in STS trajectory " + trajectoryFileName)
                        .build();
            }
        }
        return results;
    }

    private Sheet getRequiredSheet(Workbook workbook, String horizon, Path trajectoryFilePath) {
        Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheet(horizon) : null;
        if (sheet == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(horizon, trajectoryFilePath.getFileName().toString()))
                    .message("Horizon {0} does not exist in the STS trajectory {1}")
                    .build();
        }
        return sheet;
    }

    private boolean shouldIncludeRow(String rowArea, String areaParam) {
        return rowArea.equalsIgnoreCase(areaParam) || areaParam.equals(OTHERS_AREA);
    }

    private String getStringCellValue(Row row, int idx) {
        Cell cell = row.getCell(idx);
        return cell == null ? null : cell.getStringCellValue();
    }

    private void validateNumericRange(Row row, int fromIdx, int toIdx, String rowArea, String clusterName, String trajectoryFileName) {
        for (int idx = fromIdx; idx <= toIdx; idx++) {
            Cell numericCell = row.getCell(idx);
            if (!isNumericCell(numericCell)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(rowArea, clusterName, trajectoryFileName))
                        .message("Values for node {0} / cluster  {1} are not numeric in STS trajectory {2}")
                        .build();
            }
        }
    }

    private StStorageEntity mapRowToEntity(Row row, Path trajectoryFilePath, String technology, String rowArea, String clusterName, String trajectoryFileName) throws IOException {
        StStorageEntity stStorageEntity = new StStorageEntity();

        // Series/TS files handling
        Boolean series = getBooleanCell(row, 11);
        if (Boolean.TRUE.equals(series)) {
            Path stsTs = buildStsTimeSeriesPath(trajectoryFilePath, rowArea, technology, clusterName);
            if (isTsFileMissing(stsTs)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName))
                        .message("Can not import : Missing TS for trajectory {0}")
                        .build();
            }
            stStorageEntity.setTsPath(stsTs.toString());
        }

        stStorageEntity.setArea(rowArea);
        stStorageEntity.setName(clusterName);
        stStorageEntity.setGroupe(getStringCellValue(row, 2));
        stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
        stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
        stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
        stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setEfficiencyWithdrawal(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
        stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(8).getNumericCellValue()));
        stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 9));
        stStorageEntity.setEnabled(getBooleanCell(row, 10));
        stStorageEntity.setSeries(series);
        stStorageEntity.setConstraintsFlag(getBooleanCell(row, 12));
        return stStorageEntity;
    }

    private void validateSelectedAreaPresence(String areaParam, List<StStorageEntity> results, String trajectoryFileName) {
        Set<String> fileAreas = results.stream()
                .map(StStorageEntity::getArea)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        if (!areaParam.isBlank() && !OTHERS_AREA.equals(areaParam) && !fileAreas.contains(areaParam.toUpperCase())) {
            throw BusinessException.builder()
                    .message("Selected area " + areaParam + " is not present in the 'node' column of STS trajectory " + trajectoryFileName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    public Path buildStsTimeSeriesPath(Path trajectoryFilePath, String areaParam, String technology, String clusterName) throws IOException {
        // \\\'STS\\<techno>\\series\\<trajectoire>\\<nom du cluster>\\<area>\\*

        Path root = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsDirectory());

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("series");

        Path trajectoryDir = findChildDirectoryIgnoreCase(techDir, getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name(), null));
        Path clusterSeriesDir = findChildDirectoryIgnoreCase(trajectoryDir, clusterName);

        return clusterSeriesDir.resolve(areaParam).normalize();
    }

    private static boolean isTsFileMissing(Path stsTs) {
            if (!Files.exists(stsTs) || !Files.isDirectory(stsTs)) {
                log.warn("ST Storage series directory not found: {}", stsTs);
                return true;
            }
            File[] files = stsTs.toFile().listFiles();
            if (files == null || files.length == 0) {
                log.warn("Unable to list files in ST Storage series directory: {}", stsTs);
                return true;
            }

            String[] required = {"inflows.xlsx", "lower_curve.xlsx", "Pmax_injection.xlsx", "Pmax_soutirage.xlsx", "upper_curve.xlsx"};
            boolean hasAll = true;
            for (String req : required) {
                boolean found = Arrays.stream(files).anyMatch(f -> f.getName().equalsIgnoreCase(req));
                if (!found) {
                    hasAll = false;
                    break;
                }
            }
            if (!hasAll) {
                log.warn("ST Storage series directory missing required files: {}", stsTs);
                return true;
            }
        return false;
    }


    private boolean isRowEmpty(Row row) {
        for (int c = 0; c <= 12; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private Boolean getBooleanCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.BOOLEAN || cell.getCellType() == CellType.FORMULA) return cell.getBooleanCellValue();
        String s = cell.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s);
    }

}
