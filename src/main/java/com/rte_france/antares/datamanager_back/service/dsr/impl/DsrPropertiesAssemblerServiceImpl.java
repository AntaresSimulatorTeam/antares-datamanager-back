package com.rte_france.antares.datamanager_back.service.dsr.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.mapper.DsrMapper;
import com.rte_france.antares.datamanager_back.repository.model.DsrCapacityModulationEntity;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.util.ColumnSplitWriter;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsrPropertiesAssemblerServiceImpl implements DsrGenerationAssemblerService {


    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;

@Override
public Map<String, DsrGenerationDTO> assembleDsrProperties(StudyEntity studyEntity) {

    // Compute modulation files only once
    List<String> modulationFiles = createMatrixDsrTsFiles(studyEntity);

    return studyEntity.getTrajectories().stream()
            .filter(Objects::nonNull)
            .filter(t -> TrajectoryType.DSR.name().equals(t.getType()))
            .map(TrajectoryEntity::getDsrClusterEntities)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .filter(dsr ->
                    Boolean.TRUE.equals(dsr.getToUse()) &&
                            dsr.getCapacity() != null &&
                            dsr.getCapacity().compareTo(BigDecimal.ZERO) != 0
            )
            .collect(Collectors.toMap(
                    this::buildDsrKey,
                    dsr -> {
                        DsrGenerationDTO dto = DsrMapper.mapToDsrGenerationDTO(dsr);
                        if (Boolean.TRUE.equals(dsr.getModulation())) {
                            String clusterKey = this.buildDsrKey(dsr);
                            String nameKey = dsr.getName();
                            List<String> clusterModulationFiles = getClusterModulationFiles(modulationFiles, clusterKey, nameKey);
                            dto.setDsrTsList(clusterModulationFiles);
                        }
                        return dto;
                    },
                    (existing, replacement) -> existing
            ));
}

    private static @NonNull List<String> getClusterModulationFiles(List<String> modulationFiles, String clusterKey, String nameKey) {
        String clusterSuffix = "_" + clusterKey + ".";
        String nameSuffix = (nameKey != null) ? "_" + nameKey + "." : null;

        return modulationFiles.stream()
                .filter(fileName -> {
                    String lowerFileName = fileName.toLowerCase();
                    return lowerFileName.contains(clusterSuffix.toLowerCase()) ||
                            (nameSuffix != null && lowerFileName.contains(nameSuffix.toLowerCase()));
                })
                .toList();
    }


    /**
     * Builds key from uppercase area and name
     */
    private String buildDsrKey(DsrClusterEntity dsr) {
        String area = dsr.getArea() != null ? dsr.getArea().toUpperCase() : "";
        String name = dsr.getName() != null ? dsr.getName() : "";
        return area + "_" + name;
    }
    @Override
    public List<String> createMatrixDsrTsFiles(StudyEntity studyEntity) {
        List<Path> splitFiles = createSplitCmDsrFiles(studyEntity);
        try {
            return splitFiles.stream()
                    .map(path -> {
                        try {
                            String outputDir = antaresDataManagerProperties.getDsrModulationTsOutputDirectory();
                            return nasFileService.saveMatrixToNas(path, outputDir);
                        } catch (IOException e) {
                            throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
                        }
                    })
                    .toList();
        } finally {
            // Delete temporary files
            for (Path path : splitFiles) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete temporary file: {}", path, e);
                }
            }
        }
    }

    private List<Path> createSplitCmDsrFiles(StudyEntity study) {
        List<Path> cmDsrModulationTsFiles = getDsrModulationTsFiles(study.getTrajectories());

        Set<String> dsrClusters = study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.DSR.name().equals(t.getType()))
                .map(TrajectoryEntity::getDsrClusterEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .flatMap(dsr -> Stream.of(
                        this.buildDsrKey(dsr),
                        dsr.getName()
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (dsrClusters.isEmpty()) {
            return Collections.emptyList();
        }

        String horizon = study.getHorizon();

        return cmDsrModulationTsFiles.stream()
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(path -> {
                    try {
                        List<Path> parts = splitDsrCmFiles(path, dsrClusters, horizon);
                        return parts.stream();
                    } catch (IOException e) {
                        throw TechnicalException.builder()
                                .message("Error while splitting DSR CM file: " + path.getFileName())
                                .cause(e)
                                .build();
                    }
                })
                .toList();
    }

    private List<Path> getDsrModulationTsFiles(Collection<TrajectoryEntity> trajectories) {
        Path dsrCapacityDir = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getDsrCapacityDirectory());

        return trajectories.stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.DSR_CAPACITY_MODULATION.name().equals(t.getType()))
                .map(TrajectoryEntity::getDsrCapacityModulationEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(e -> e.getTsName() != null)
                .map(DsrCapacityModulationEntity::getTsName)
                .map(dsrCapacityDir::resolve)
                .filter(Files::exists)
                .toList();
    }

    private List<Path> splitDsrCmFiles(Path file, Set<String> dsrClusters, String horizon) throws IOException {
        Set<String> allowedClusters = dsrClusters.stream()
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());

        List<Path> generatedFiles = new ArrayList<>();
        String fileName = file.getFileName().toString().toLowerCase();

        TimeSeriesMatrix matrix;
        var localReader = (this.timeSeriesReader != null)
                ? this.timeSeriesReader
                : new TimeSeriesReader();
        if (fileName.endsWith(".xlsx")) {
            try {
                matrix = localReader.readFromXlsx(file, horizon);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        } else if (fileName.endsWith(".csv") || fileName.endsWith(".txt")) {
            throw BusinessException.builder()
                    .message("Generation error capacity modulation files should have .xlsx extension .csv or .txt not supported")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } else {
            return generatedFiles;
        }

        if (matrix.columns().isEmpty()) {
            throw TechnicalException.builder()
                    .message("The matrix of the capacity modulation file is empty: " + file.getFileName())
                    .build();
        }

        Path tmpDir = Files.createTempDirectory("dsr_param_modulation_split_",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------"))
        );
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }
        String baseName = getBaseName(file);

        for (var column : matrix.columns()) {
            String clusterName = column.name() != null ? column.name().trim() : "";
            if (clusterName.isEmpty()) continue;

            // Use a common helper with flexible suffix matching for DSR
            Optional<BufferedWriter> bwOpt = ColumnSplitWriter
                    .openWriterIfAllowed(clusterName, baseName, tmpDir, allowedClusters, generatedFiles, true, true);
            if (bwOpt.isEmpty()) continue;

            try (BufferedWriter bw = bwOpt.get()) {
                // Write a header (column name) so that NasFileService (using readFromTxt) correctly identifies it as a header
                bw.write(clusterName);
                bw.newLine();
                for (double value : column.values()) {
                    bw.write(String.valueOf(value));
                    bw.newLine();
                }
            }
        }

        return generatedFiles;
    }


    private String getBaseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

}