package com.rte_france.antares.datamanager_back.service.StStorage;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageFileProcessorServiceImpl implements StStorageFileProcessorService {

    private final AntaressDataManagerProperties antaressDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final AreaRepository areaRepository;
    private final StudyRepository studyRepository;


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
        
        WarningMessageEntity warningMessageEntity = buildWarningMessageIfAreaStudyIsMissing(studyId, areaParam, stStorageEntityList, studyAreas, trajectoryFilePath, trajectoryEntity);

        stStorageEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        if (warningMessageEntity != null) {
            trajectoryEntity.setWarningMessages(Set.of(warningMessageEntity));
        }
        return trajectoryRepository.save(trajectoryEntity);
    }

    private WarningMessageEntity buildWarningMessageIfAreaStudyIsMissing(Integer studyId, String areaParam, List<StStorageEntity> stStorageEntityList, List<String> studyAreas, Path trajectoryFilePath, TrajectoryEntity trajectoryEntity) {
        // si OTHERS_AREA : lister les areas de l'étude absentes et créer un warning (pas d'exception)
        if (areaParam.equals(OTHERS_AREA)) {
            List<String> stsAreas = stStorageEntityList.stream().map(StStorageEntity::getArea).map(String::toUpperCase).distinct().toList();
            String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";

            List<String> missingAreas = studyAreas.stream().filter(sa -> !stsAreas.contains(sa)).toList();
            if (!missingAreas.isEmpty()) {
                String message = " Area(s) " + missingAreas + " in AREA trajectory is not present in STS trajectory " + trajectoryFilePath.getFileName().toString();
                return WarningMessageEntity.builder().warningContent(message).warningLevel(WarningLevel.WARNING_LEVEL).warningCode(WarningCode.STS_MISSING_AREAS).study(studyRepository.findById(studyId).orElseThrow(() -> BusinessException.builder().message("Study not found with id: " + studyId).httpStatus(HttpStatus.NOT_FOUND).build())).creationDate(LocalDateTime.now()).createdBy(createdBy).isAck(false).trajectory(trajectoryEntity).build();
            }
        }
        return null;
    }

    public Path findTrajectoryFileCaseInsensitive(String trajectoryFileName, String technology) throws IOException {
        Path root = Path.of(antaressDataManagerProperties.getNasDirectory()).resolve(antaressDataManagerProperties.getTrajectoryFilePath()).resolve(antaressDataManagerProperties.getStsDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS root not found: " + root);
        }

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("clusters");

        try (java.util.stream.Stream<Path> s = Files.list(techDir)) {
            java.util.Optional<Path> file = s.filter(Files::isRegularFile).filter(p -> {
                String fn = p.getFileName().toString();
                String target = trajectoryFileName;
                return fn.equalsIgnoreCase(target) || fn.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
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
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()), TrajectoryType.STS.name(), horizon, areaParam, technology);

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
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheet(horizon) : null;
            if (sheet == null) {
                throw BusinessException.builder().errorMessageArguments(List.of(horizon, trajectoryFilePath.getFileName().toString())).message("Horizon {0} does not exist in the STS trajectory {1}").build();
            }

                String[] expectedColumns = {"Area", "Name", "Group", "Injection", "Withdrawal", "Storage", "Efficiency_injection", "Efficiency_withdrawal", "Initial_level", "Initial_level_optim", "Enabled", "Series", "Constraints"};
                checkMissingColumns(sheet, expectedColumns, trajectoryFileName);
                boolean firstRow = true;
                boolean foundStudyArea = false;

                for (Row row : sheet) {
                    if (firstRow) {
                        firstRow = false;
                        continue;
                    } // skip header
                    if (isRowEmpty(row)) continue;

                    StStorageEntity stStorageEntity = new StStorageEntity();
                    String rowArea = row.getCell(0).getStringCellValue();
                    String clusterName = row.getCell(1).getStringCellValue();

                    if (rowArea == null || rowArea.isEmpty() || Objects.requireNonNull(clusterName).isEmpty()) continue;

                    if (!rowArea.equalsIgnoreCase(areaParam) && !areaParam.equals(OTHERS_AREA)) {
                        continue;
                    }

                    // marquer si cette ligne correspond à une area de l'étude
                    if (studyAreas.contains(rowArea.toUpperCase())) {
                        foundStudyArea = true;
                    }


                    Boolean series = getBooleanCell(row, 11);
                    if(Boolean.TRUE.equals(series)) {
                        Path stsTs = buildStsTimeSeriesPath(trajectoryFilePath, rowArea.toUpperCase(), technology, clusterName);

                        if (isTsFileMissing(stsTs)) {
                            throw BusinessException.builder()
                                    .errorMessageArguments(List.of(trajectoryFileName))
                                    .message("Can not import : Missing TS for trajectory {0}")
                                    .build();
                        }
                        stStorageEntity.setTsPath(stsTs.toString());
                    }
                    for (int idx = 3; idx <= 8; idx++) {
                        Cell numericCell = row.getCell(idx);
                        if (!isNumericCell(numericCell)) {
                            throw BusinessException.builder().errorMessageArguments(List.of(rowArea, clusterName, trajectoryFileName)).message("Values for node {0} / cluster  {1} are not numeric in STS trajectory {2}").build();
                        }
                    }
                    stStorageEntity.setArea(rowArea);
                    stStorageEntity.setName(clusterName);
                    stStorageEntity.setGroupe(row.getCell(2).getStringCellValue());
                    stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
                    stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
                    stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
                    stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
                    stStorageEntity.setEfficiencyWithdrawal((int) (row.getCell(7).getNumericCellValue()));
                    stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(8).getNumericCellValue()));
                    stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 9));
                    stStorageEntity.setEnabled(getBooleanCell(row, 10));
                    stStorageEntity.setSeries(series);
                    stStorageEntity.setConstraintsFlag(getBooleanCell(row, 12));

                    results.add(stStorageEntity);
                }
                // The selected area must be present in the file's 'node' column, except when area equals OTHERS
                Set<String> fileAreas = results.stream().map(StStorageEntity::getArea).map(String::toUpperCase).collect(Collectors.toSet());
                if (!areaParam.isBlank() && !OTHERS_AREA.equals(areaParam) && !fileAreas.contains(areaParam.toUpperCase())) {
                    throw BusinessException.builder()
                            .message("Selected area " + areaParam + " is not present in the 'node' column of STS trajectory " + trajectoryFileName)
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                if (!foundStudyArea) {
                    throw BusinessException.builder()
                            .message("None of the areas of trajectory AREA are present in STS trajectory " + trajectoryFileName)
                            .build();
                }


        }
        return results;
    }


    private static void checkMissingColumns(Sheet sheet, String[] expectedColumns, String trajectoryName) {
        Row headerRow = sheet.getRow(0);
        List<String> missingColumns = new ArrayList<>();
        if (headerRow == null) {
            missingColumns.addAll(Arrays.asList(expectedColumns));
        } else {
            int lastCell = headerRow.getLastCellNum() < 0 ? 0 : headerRow.getLastCellNum();
            Set<String> headerNames = new HashSet<>();
            for (int i = 0; i < lastCell; i++) {
                Cell c = headerRow.getCell(i);
                if (c != null) {
                    String val = c.toString().trim().toLowerCase(Locale.ROOT);
                    if (!val.isEmpty()) headerNames.add(val);
                }
            }
            for (String expected : expectedColumns) {
                String norm = expected.trim().toLowerCase(Locale.ROOT);
                if (!headerNames.contains(norm)) {
                    missingColumns.add(expected);
                }
            }
        }
        if (!missingColumns.isEmpty()) {
            String missingList = String.join(", ", missingColumns);
            throw BusinessException.builder().message("Missing columns " + missingList + " STS trajectory " + trajectoryName).build();
        }
    }


    private Path buildStsTimeSeriesPath(Path trajectoryFilePath, String areaParam, String technology, String clusterName) throws IOException {
        // \\STS\<techno>\series\<trajectoire>\<nom du cluster>\<area>\*

        Path root = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaressDataManagerProperties.getStsDirectory());

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("series");

        Path trajectoryDir = findChildDirectoryIgnoreCase(techDir, getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()));
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
        if (cell.getCellType() == CellType.BOOLEAN) return cell.getBooleanCellValue();
        String s = cell.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s);
    }


    private boolean isNumericCell(Cell cell) {
        if (cell == null) return false;
        CellType t = cell.getCellType();
        if (t == CellType.NUMERIC) return true;
        if (t == CellType.FORMULA) {
            CellType resType = cell.getCachedFormulaResultType();
            return resType == CellType.NUMERIC;
        }
        if (t == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return false;
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

}
