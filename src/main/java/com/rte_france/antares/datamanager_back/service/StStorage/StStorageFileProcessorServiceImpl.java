package com.rte_france.antares.datamanager_back.service.StStorage;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageFileProcessorServiceImpl implements StStorageFileProcessorService {

    private final AntaressDataManagerProperties antaressDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    @Transactional
    @Override
    public TrajectoryEntity processStStorageFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String areaParam, String technology) throws IOException {
        final String horizonYear = horizon.split("-")[1];
        final String stsTrajectoryPrefix = "cluster_" + technology.toLowerCase() + "_";
        if (!trajectoryToUse.toLowerCase().startsWith(stsTrajectoryPrefix)) {
            throw BusinessException.builder().message(" {0} Trajectory name must start with : {1} ")
                    .errorMessageArguments(List.of(trajectoryToUse, stsTrajectoryPrefix))
                    .build();
        }

        Path trajectoryFilePath = findTrajectoryFileCaseInsensitive(trajectoryToUse, technology);

        List<StStorageEntity> stStorageEntityList = buildStStorageLines(horizonYear, trajectoryFilePath, areaParam, technology);
        if (stStorageEntityList.isEmpty()) {
            throw BusinessException.builder()
                    .message("No ST Storage data found in the file for horizon: " + horizonYear)
                    .build();
        }

        TrajectoryEntity trajectoryEntity = buildStStorageTrajectory(trajectoryFilePath, horizonYear, areaParam, technology);

        stStorageEntityList.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        trajectoryEntity.setHorizon(horizon);
        return trajectoryRepository.save(trajectoryEntity);
    }

    private Path findTrajectoryFileCaseInsensitive(String trajectoryFileName, String technology) throws IOException {
        Path root = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaressDataManagerProperties.getStsDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS root not found: " + root);
        }

        Path techDir = findChildDirectoryIgnoreCase(root, technology).resolve("clusters");

        try (java.util.stream.Stream<Path> s = Files.list(techDir)) {
            java.util.Optional<Path> file = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        String target = trajectoryFileName;
                        return fn.equalsIgnoreCase(target) || fn.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
                    })
                    .findFirst();

            if (file.isPresent()) {
                return file.get();
            } else {
                throw new NoSuchFileException("Trajectory file not found in " + techDir.toString() +
                        " for '" + trajectoryFileName + "'");
            }
        }
    }




    private TrajectoryEntity buildStStorageTrajectory(Path trajectoryFilePath, String horizon, String areaParam, String technology) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()),
                TrajectoryType.STS.name(), horizon, areaParam, technology);

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon, createdBy, TrajectoryType.STS, areaParam, technology);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.STS, areaParam, technology);
        }

        return trajectory;
    }

    private List<StStorageEntity> buildStStorageLines(String horizon, Path trajectoryFilePath, String areaParam, String technology) throws IOException {
        List<StStorageEntity> results = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheet(horizon) : null;
            if (sheet == null) {
                throw BusinessException.builder()
                        .message("No sheet found in file")
                        .build();
            }

            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue;
                } // skip header
                if (isRowEmpty(row)) continue;

                StStorageEntity stStorageEntity = new StStorageEntity();
                String rowArea = row.getCell( 0).getStringCellValue();
                String clusterName = row.getCell( 1).getStringCellValue();

                if (rowArea == null || rowArea.isEmpty() || Objects.requireNonNull(clusterName).isEmpty()) continue;

                if (!rowArea.equals(areaParam) && !areaParam.equals(OTHERS_AREA)) {
                    continue;
                }

                Boolean series = getBooleanCell(row, 11);
                Path stsTs = buildStsTimeSeriesPath(trajectoryFilePath, rowArea, technology, clusterName);

                if (isTsFileMissing(series, stsTs)) continue;

                stStorageEntity.setArea(rowArea);
                stStorageEntity.setName(clusterName);
                stStorageEntity.setGroupe(row.getCell( 2).getStringCellValue());
                stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
                stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
                stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
                stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
                stStorageEntity.setEfficiencyWithdrawal((int)(row.getCell(7).getNumericCellValue()));
                stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(8).getNumericCellValue()));
                stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 9));
                stStorageEntity.setEnabled(getBooleanCell(row, 10));
                stStorageEntity.setSeries(series);
                stStorageEntity.setConstraintsFlag(getBooleanCell(row, 12));

                results.add(stStorageEntity);
            }
        }
        return results;
    }

    private Path buildStsTimeSeriesPath(Path trajectoryFilePath, String areaParam, String technology, String clusterName) {
        // \\STS\<techno>\series\<trajectoire>\<nom du cluster>\<area>\
        return Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaressDataManagerProperties.getStsDirectory())
                .resolve(technology) //get technology from path
                .resolve("series")
                .resolve(getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS.name()))
                .resolve(clusterName)
                .resolve(areaParam)
                .normalize();
    }

    private static boolean isTsFileMissing(Boolean series, Path stsTs) {
        if (Boolean.TRUE.equals(series)) {
            if (!Files.exists(stsTs) || !Files.isDirectory(stsTs)) {
                log.warn("ST Storage series directory not found: {}", stsTs);
                return true;
            }
            File[] files = stsTs.toFile().listFiles();
            if (files == null || files.length == 0) {
                log.warn("Unable to list files in ST Storage series directory: {}", stsTs);
                return true;
            }

            String[] required = {
                    "inflows.xlsx",
                    "lower_curve.xlsx",
                    "Pmax_injection.xlsx",
                    "Pmax_soutirage.xlsx",
                    "upper_curve.xlsx"
            };
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


}
