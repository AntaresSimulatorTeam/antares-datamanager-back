package com.rte_france.antares.datamanager_back.service.dsr.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.DsrRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.DsrCapacityModulationEntity;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.dsr.DsrCapacityModulationFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl.UNKNOWN_USER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.Utils.buildTrajectory;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.isRowEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsrCapacityModulationFileProcessorServiceImpl implements DsrCapacityModulationFileProcessorService {
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final PathSecurityUtil pathSecurityUtil;
    private final TrajectoryRepository trajectoryRepository;
    private final UserService userService;
    private final DsrRepository dsrRepository;

    private static final String DSR_CAPACITY_MODULATION = "CM_";
    private static final String FILE_EXTENSION = ".xlsx";

    @Transactional
    @Override
    public TrajectoryEntity processDsrCapacityModulationFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        // Check trajectory file name prefix
        if (!startsWithIgnoreCase(trajectoryToUse, DSR_CAPACITY_MODULATION)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(DSR_CAPACITY_MODULATION))
                    .message("The trajectory file name must start with {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path trajectoryFilePath = getTrajectoryFilePath(trajectoryToUse);
        List<String> clusterKeys = getClusterKeys(studyId);

        var dsrCapacityModulationEntities = buildDsrCapacityModulationEntity(horizon, trajectoryFilePath, clusterKeys);

        TrajectoryEntity trajectoryEntity = buildDsrCapacityModulationTrajectory(trajectoryFilePath, horizon);
        dsrCapacityModulationEntities.forEach(entity -> entity.setTrajectory(trajectoryEntity));
        trajectoryEntity.setDsrCapacityModulationEntities(dsrCapacityModulationEntities);

        return trajectoryRepository.save(trajectoryEntity);
    }

    @Override
    public void validateDsrCapacityModulationCoherence(TrajectoryEntity trajectory, Integer studyId) throws IOException {
        // reconstruct filename for the nas (prefix)
        String fileName = trajectory.getFileName();
        if (!startsWithIgnoreCase(fileName, DSR_CAPACITY_MODULATION)) {
            fileName = DSR_CAPACITY_MODULATION + fileName;
        }

        Path trajectoryFilePath = getTrajectoryFilePath(fileName);
        buildDsrCapacityModulationEntity(trajectory.getHorizon(), trajectoryFilePath, getClusterKeys(studyId));
    }

    public List<DsrCapacityModulationEntity> buildDsrCapacityModulationEntity(String horizon, Path trajectoryFilePath, List<String> dsrClusters) throws IOException {
        String trajectoryFileName = trajectoryFilePath.getFileName().toString();
        List<DsrCapacityModulationEntity> results = new ArrayList<>();
        Set<String> dsrClusterNames;

        boolean onlyHeader = true;
        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = getRequiredSheet(workbook, horizon, trajectoryFilePath, "DSR Capacity Modulation");

            List<String> headers = getClusterName(sheet);
            Set<String> headerClusters = new HashSet<>(headers);

            // Compute the set of clusters that are required but missing in the file headers
            dsrClusterNames = dsrClusters.stream()
                    .filter(cluster -> !headerClusters.contains(cluster))
                    .collect(Collectors.toSet());

            if (!dsrClusterNames.isEmpty()) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryFileName, horizon))
                        .message("Missing Areas/Clusters " + String.join(", ", dsrClusterNames) + " in Capacity modulation file for trajectory {0} for horizon {1}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                onlyHeader = false;
            }
        }

        if (onlyHeader) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryFileName, horizon))
                    .message("No data in DSR Capacity Modulation trajectory {0} for horizon: {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        results.add(DsrCapacityModulationEntity.builder()
                .tsName(trajectoryFileName)
                .checksum(getFileChecksum(trajectoryFilePath.toString()))
                .build());
        return results;
    }

    public Path getTrajectoryFilePath(String trajectoryToUse) throws IOException {
        String baseName = trajectoryToUse;
        if (startsWithIgnoreCase(baseName, DSR_CAPACITY_MODULATION)) {
            baseName = baseName.substring(3);
        }

        String targetFileName = trajectoryToUse + FILE_EXTENSION;
        pathSecurityUtil.validatePathFromBaseDir(
                targetFileName,
                properties -> Path.of(properties.getNasDirectory())
                        .resolve(properties.getTrajectoryFilePath())
                        .resolve(properties.getDsrCapacityDirectory())
                        .toString()
        );

        //build the file path
        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getDsrCapacityDirectory())
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        Path upperCasePath = baseDirectory.resolve(DSR_CAPACITY_MODULATION + baseName + FILE_EXTENSION).normalize();
        Path lowerCasePath = baseDirectory.resolve(DSR_CAPACITY_MODULATION.toLowerCase(Locale.ROOT) + baseName + FILE_EXTENSION).normalize();

        //download the file
        Path trajectoryFilePath = baseDirectory.resolve(targetFileName).normalize();
        if (!upperCasePath.startsWith(baseDirectory) ||
                !lowerCasePath.startsWith(baseDirectory) ||
                !trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }

        if (Files.exists(trajectoryFilePath) && !Files.isSymbolicLink(trajectoryFilePath)) {
            return trajectoryFilePath;
        }
        if (Files.exists(upperCasePath) && !Files.isSymbolicLink(upperCasePath)) {
            return upperCasePath;
        }
        if (Files.exists(lowerCasePath) && !Files.isSymbolicLink(lowerCasePath)) {
            return lowerCasePath;
        }

        throw BusinessException.builder()
                .errorMessageArguments(List.of(targetFileName))
                .message("The trajectory file {0} was not found. It may have been moved or deleted.")
                .httpStatus(HttpStatus.NOT_FOUND)
                .build();
    }

    private List<String> getClusterKeys(Integer studyId) {
        List<DsrClusterEntity> dsrClusters = dsrRepository.findAllDsrClusterEntitiesByStudyId(studyId);
        return dsrClusters.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getArea() + "_" + c.getName()
                ))
                .values()
                .stream()
                .map(group -> {

                    // trajectoire spécifique = area != "OTHERS"
                    Optional<DsrClusterEntity> specific =
                            group.stream()
                                    .filter(c -> !"OTHERS".equals(c.getTrajectory().getArea()))
                                    .findFirst();

                    if (specific.isPresent()) {
                        return Boolean.TRUE.equals(specific.get().getModulation())
                                ? specific.get().getArea() + "_" + specific.get().getName()
                                : null;
                    }

                    // fallback OTHERS
                    return group.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getModulation()))
                            .findFirst()
                            .map(c -> c.getArea() + "_" + c.getName())
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public TrajectoryEntity buildDsrCapacityModulationTrajectory(Path trajectoryFilePath, String horizon) throws IOException {

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(trajectoryFilePath.getFileName().toString(), TrajectoryType.DSR_CAPACITY_MODULATION.name());
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(fileName, TrajectoryType.DSR_CAPACITY_MODULATION.name(), horizon, null, null);

        TrajectoryEntity trajectory;
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            // Same identifiers but different checksum -> version +1
            trajectory = buildTrajectory(trajectoryFilePath, existingOpt.get().getVersion(), horizon, createdBy, TrajectoryType.DSR_CAPACITY_MODULATION, null, null, null);
        } else {
            // No existing or not same file -> new trajectory with version 1
            trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.DSR_CAPACITY_MODULATION, null, null, null);
        }

        return trajectory;
    }

    private List<String> getClusterName(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(0);

        for (int i = 1; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);

            if (cell == null) break;

            String value = formatter.formatCellValue(cell).trim();
            if (value.isEmpty()) break;

            headers.add(value);
        }

        return headers;
    }
}
