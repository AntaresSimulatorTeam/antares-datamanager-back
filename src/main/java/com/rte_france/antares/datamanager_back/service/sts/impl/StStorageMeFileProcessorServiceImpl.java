package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StStorageMeFileProcessorServiceImpl implements StStorageMeFileProcessorService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    private static final Integer SERIES_INDEX_ME = 10;
    private static final String EXCEL_EXTENSION = ".xlsx";
    private static final int NODE_MAX_LENGTH = 60;
    private static final int NAME_MAX_LENGTH = 40;
    private static final int GROUP_MAX_LENGTH = 20;

    @Transactional
    @Override
    public TrajectoryEntity processStStorageMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {

        Path trajectoryFilePath = findTrajectoryFileCaseInsensitive(trajectoryToUse);

        List<StStorageEntity> stStorageEntityList = buildStStorageMeLines(horizon.split("-")[1], trajectoryFilePath, studyId);
        return saveTrajectoryImport(stStorageEntityList, trajectoryFilePath, horizon);
    }

    private TrajectoryEntity saveTrajectoryImport(List<StStorageEntity> stStorageEntityList, Path trajectoryFilePath,
                                                   String horizon) throws IOException {
        if (stStorageEntityList.isEmpty()) {
            throw createValidationError("No ST Storage data found in the file for horizon: {0}", List.of(horizon));
        }

        boolean isSeriesTrue = stStorageEntityList.stream()
                .anyMatch(entity -> Boolean.TRUE.equals(entity.getSeries()));

        TrajectoryEntity trajectoryEntity = buildStStorageMeTrajectory(trajectoryFilePath, horizon, isSeriesTrue);

        stStorageEntityList.forEach(stStorageEntity -> stStorageEntity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setStStorageEntities(stStorageEntityList);
        return trajectoryRepository.save(trajectoryEntity);
    }

    private List<StStorageEntity> buildStStorageMeLines(String horizonYear, Path trajectoryFilePath, Integer studyId) throws IOException {
        List<StStorageEntity> stStorageEntityList = new ArrayList<>();
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet(horizonYear);
            if (sheet == null) {
                throw createValidationError("Missing horizon {0} in ST_STORAGE_ME Clusters trajectory {1}", List.of(horizonYear, trajectoryFileName));
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmptyMe(row)) continue;

                String node = row.getCell(0).getStringCellValue();
                String name = row.getCell(1).getStringCellValue();
                String group = row.getCell(2).getStringCellValue();

                // Validations
                validateMeNodeField(node, trajectoryFileName);
                validateMeFieldLength(node, NODE_MAX_LENGTH, "Node", trajectoryFileName);
                validateMeFieldLength(name, NAME_MAX_LENGTH, "Name", trajectoryFileName);
                validateMeFieldLength(group, GROUP_MAX_LENGTH, "Group", trajectoryFileName);

                validateNumericRangeMe(row, trajectoryFileName);
                validateMeInitialLevelRange(row, trajectoryFileName);
                validateBooleanRangeMe(row, trajectoryFileName);

                StStorageEntity stStorageEntity = mapRowToEntityMe(row, trajectoryFilePath, node, name, group);
                stStorageEntityList.add(stStorageEntity);
            }
        }

        return stStorageEntityList;
    }

    private StStorageEntity mapRowToEntityMe(Row row, Path trajectoryFilePath, String node, String name, String group) throws IOException {
        StStorageEntity stStorageEntity = new StStorageEntity();

        Boolean hasSeriesMe = getBooleanCell(row, SERIES_INDEX_ME);
        if (hasSeriesMe) {
            Path seriesPath = buildStsOptionalFilesPathMe(trajectoryFilePath);
            List<String> missingFiles = isOptionalStsFileMissingMe(seriesPath);
            if (!missingFiles.isEmpty()) {
                throw createValidationError("Missing file(s) {0} in ST_STORAGE ME Series trajectory {1}",
                        List.of(String.join(", ", missingFiles), name));
            }
            stStorageEntity.setTsPath(seriesPath.toString());
        }

        stStorageEntity.setArea(node);
        stStorageEntity.setName(name);
        stStorageEntity.setGroupe(group);
        stStorageEntity.setInjection(BigDecimal.valueOf(row.getCell(3).getNumericCellValue()));
        stStorageEntity.setWithdrawal(BigDecimal.valueOf(row.getCell(4).getNumericCellValue()));
        stStorageEntity.setStorage(BigDecimal.valueOf(row.getCell(5).getNumericCellValue()));
        stStorageEntity.setEfficiencyInjection(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setEfficiencyWithdrawal(BigDecimal.valueOf(row.getCell(6).getNumericCellValue()));
        stStorageEntity.setInitialLevel(BigDecimal.valueOf(row.getCell(7).getNumericCellValue()));
        stStorageEntity.setInitialLevelOptim(getBooleanCell(row, 8));
        stStorageEntity.setEnabled(getBooleanCell(row, 9));
        stStorageEntity.setSeries(hasSeriesMe);
        stStorageEntity.setConstraintsFlag(getBooleanCell(row, 11));
        return stStorageEntity;
    }

    private Path buildStsOptionalFilesPathMe(Path trajectoryFilePath) {
        String trajectoryFileName = trajectoryFilePath.getFileName().toString().split("\\.")[0];
        Path seriesRoot = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsMeSeriesDirectory());
        return seriesRoot.resolve(trajectoryFileName);
    }

    private List<String> isOptionalStsFileMissingMe(Path optionalFilesPath) {
        List<String> missingFiles = new ArrayList<>();

            String[] requiredFiles = {"lower_curve.xlsx", "Pmax_injection.xlsx", "Pmax_soutirage.xlsx", "upper_curve.xlsx"};
            for (String fileName : requiredFiles) {
                if (!Files.exists(optionalFilesPath.resolve(fileName))) {
                    missingFiles.add(fileName);
                }
        }

        return missingFiles;
    }


    private void validateMeNodeField(String node, String trajectoryFileName) {
        if (node == null || node.isEmpty()) {
            throw createValidationError("Node column must be filled in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
        }
    }

    private void validateMeFieldLength(String fieldValue, int maxLength, String fieldName, String trajectoryFileName) {
        if (fieldValue != null && fieldValue.length() > maxLength) {
            throw createValidationError("{0} cannot exceed {1} characters in ST_STORAGE_ME Clusters trajectory {2}",
                    List.of(fieldName, String.valueOf(maxLength), trajectoryFileName));
        }
    }

    private void validateNumericRangeMe(Row row, String trajectoryFileName) {
        for (int idx = 3; idx <= 7; idx++) {
            Cell numericCell = row.getCell(idx);
            if (!isNumericCell(numericCell)) {
                throw createValidationError("Columns Injection [MW], Withdrawal [MW], Storage [MWh], Efficiency and initial_level must be numeric in ST_STORAGE_ME Clusters trajectory {0}",
                        List.of(trajectoryFileName));
            }
        }
    }

    private void validateMeInitialLevelRange(Row row, String trajectoryFileName) {
        double initialLevel = row.getCell(7).getNumericCellValue();
        if (initialLevel < 0 || initialLevel > 1) {
            throw createValidationError("initial_level must be between 0 and 1 in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
        }
    }

    private void validateBooleanRangeMe(Row row, String trajectoryFileName) {
        for (int idx = 8; idx <= 11; idx++) {
            Cell cell = row.getCell(idx);
            if (!isBooleanCell(cell)) {
                throw createValidationError("Columns initial_level_optim and Series must be boolean in ST_STORAGE_ME Clusters trajectory {0}", List.of(trajectoryFileName));
            }
        }
    }

    private boolean isRowEmptyMe(Row row) {
        for (int c = 0; c <= 9; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }


    private Boolean getBooleanCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return false;
        if (cell.getCellType() == CellType.BOOLEAN || cell.getCellType() == CellType.FORMULA) return cell.getBooleanCellValue();
        String s = cell.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return false;
        return "true".equals(s) || "1".equals(s);
    }

    public Path findTrajectoryFileCaseInsensitive(String trajectoryFileName) throws IOException {
        Path root = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsMeDirectory());

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new NoSuchFileException("STS_ME root not found: " + root);
        }

        try (Stream<Path> s = Files.list(root)) {
            Optional<Path> file = s.filter(Files::isRegularFile).filter(p -> {
                String fileName = p.getFileName().toString();
                return fileName.equalsIgnoreCase(trajectoryFileName) ||
                        fileName.equalsIgnoreCase(trajectoryFileName + EXCEL_EXTENSION);
            }).findFirst();

            if (file.isPresent()) {
                return file.get();
            }
        }

        throw new NoSuchFileException("Trajectory file not found: " + trajectoryFileName + " in " + root);
    }

    private TrajectoryEntity buildStStorageMeTrajectory(Path trajectoryFilePath, String horizon, Boolean hasSeries) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String trajectoryName = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.STS_ME.name());

        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.STS_ME.name());

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon.split("-")[1],
                    createdBy, TrajectoryType.STS_ME, null, null, hasSeries);
        } else {
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon.split("-")[1], createdBy,
                    TrajectoryType.STS_ME, null, null, hasSeries);
        }

        return trajectory;
    }

    private BusinessException createValidationError(String message, List<String> args) {
        return BusinessException.builder()
                .message(message)
                .errorMessageArguments(args)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }
}
