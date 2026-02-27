package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.InstalledMiscFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator;
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
import java.nio.file.Path;
import java.util.*;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstalledMiscFileProcessorServiceImpl implements InstalledMiscFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;

    private final TrajectoryServiceImpl trajectoryService;

    private static final String INSTALLED_MISC_PREFIX = "installedMisc_";

    @Transactional
    @Override
    public TrajectoryEntity processInstalledMiscFile(String trajectoryToUse, String horizon, Integer studyId, String areaParam) throws IOException {
        // prefix check
        if (!startsWithIgnoreCase(trajectoryToUse, INSTALLED_MISC_PREFIX)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(INSTALLED_MISC_PREFIX))
                    .message("The trajectory file name must start with {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

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

            // Détecter l'index de la colonne correspondant à l'horizon hors de la boucle de lignes
            int yearColIndex = -1;
            String horizonYear = horizon.split("-")[1];

            yearColIndex = getYearColIndex(lastCol, header, horizonYear, yearColIndex);
            if (yearColIndex == -1) {
                throw BusinessException.builder()
                        .message("Horizon column '" + horizon + "' not found in InstalledMisc header for trajectory " + filePath.getFileName())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            List<MiscClusterCapacityEntity> entities = new ArrayList<>();
            Iterator<Row> rows = sheet.rowIterator();
            rows.next(); // skip header
            StringBuilder checksumBuilder = new StringBuilder();

            while (rows.hasNext()) {
                processMiscCapacityRow(areaParam, rows, yearColIndex, entities, checksumBuilder);
            }

            if (entities.isEmpty()) {
                throw BusinessException.builder().message("No data in InstalledMisc trajectory " + filePath.getFileName()).httpStatus(HttpStatus.BAD_REQUEST).build();
            }

            TrajectoryEntity trajectory = buildMiscTrajectory(horizon, areaParam, checksumBuilder, filePath, entities);

            // save trajectory
            return trajectoryRepository.save(trajectory);
        }
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
        Optional<TrajectoryEntity> existingTrajectory = findExistingTrajectory(filePath, horizon, areaParam, null);
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

    private void processMiscCapacityRow(String areaParam, Iterator<Row> rows, int yearColIndex, List<MiscClusterCapacityEntity> entities, StringBuilder checksumBuilder) {
        Row row = rows.next();
        if (ExcelCommonValidator.isRowEmpty(row)) return;

        // toUse: ExcelCommonValidator peut extraire 1/0 comme boolean; si absent on considère false
        boolean toUse = ExcelCommonValidator.getBooleanCellValue(row.getCell(0)).orElse(false);

        String area = Optional.ofNullable(getCellValue(row,1)).map(Object::toString).orElse(null);

        // Filtre par area param (si areaParam différent de OTHERS on garde uniquement la même area)
        if (areaParam != null && !OTHERS_AREA.equalsIgnoreCase(areaParam)) {
            if (!areaParam.equalsIgnoreCase(Optional.ofNullable(area).orElse(""))) {
                return;
            }
        }

        String group = Optional.ofNullable(getCellValue(row,2)).map(Object::toString).orElse(null);
        String cluster = Optional.ofNullable(getCellValue(row,3)).map(Object::toString).orElse(null);
        String category = Optional.ofNullable(getCellValue(row,4)).map(Object::toString).orElse(null);

        // Récupérer la valeur numérique de la colonne correspondant à l'horizon de manière robuste
        Object cellVal = getCellValue(row, yearColIndex);
        Number numeric = null;
        if (cellVal instanceof Number) numeric = (Number) cellVal;
        else if (cellVal instanceof String) {
            try { numeric = Double.parseDouble((String) cellVal); } catch (NumberFormatException ignored) {}
        }
        if (numeric == null) return;
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

    private Optional<TrajectoryEntity> findExistingTrajectory(Path path, String horizon, String area, String technology) {
        return trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.MISC_CAPACITY.name()),
                TrajectoryType.MISC_CAPACITY.name(),
                horizon,
                area,
                technology);
    }

    private TrajectoryEntity buildInstalledMiscTrajectory(Path trajectoryFilePath, String horizon, String area) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        return buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.MISC_CAPACITY, area, null, null);
    }
}
