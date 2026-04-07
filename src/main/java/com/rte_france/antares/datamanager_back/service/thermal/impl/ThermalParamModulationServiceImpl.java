package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalSpecificFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.ColumnSplitWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.*;
import static com.rte_france.antares.datamanager_back.util.excel_file_validators.ExcelCommonValidator.checkNumericDataCMorMR;


@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalParamModulationServiceImpl implements ThermalParamModulationService {


    public static final String UNKNOWN_USER = "UNKNOWN__USER";

    private final TrajectoryRepository trajectoryRepository;

    private final UserService userService;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final ThermalSpecificFileProcessorService thermalSpecificFileProcessorService;

    private final NasFileService nasFileService;

    /**
     * Saves a thermal modulation parameter trajectory.
     *
     * @param trajectory                         The trajectory entity to save.
     * @param thermalModulationParameterEntities The list of thermal modulation parameter entities.
     * @param type                               The type of trajectory.
     * @return The saved trajectory entity.
     */
    @Override
    public TrajectoryEntity saveThermalParamModulationTrajectory(TrajectoryEntity trajectory, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type) {
        trajectory.setType(type.name());
        thermalModulationParameterEntities.forEach(thermalEntity -> thermalEntity.setTrajectory(trajectory));
        trajectory.setThermalModulationParameters(thermalModulationParameterEntities);
        return trajectoryRepository.save(trajectory);
    }

    /**
     * Processes a thermal modulation parameter file and saves the corresponding trajectory.
     *
     * @param path                               The path to the thermal modulation parameter file.
     * @param horizon                            The horizon for the trajectory.
     * @param thermalModulationParameterEntities The list of thermal modulation parameter entities.
     * @param type                               The type of trajectory.
     * @return The saved trajectory entity.
     * @throws IOException If an error occurs while processing the file.
     */
    @Override
    public TrajectoryEntity processThermalModulationParameterFile(Path path, String horizon, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type) throws IOException {
        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : UNKNOWN_USER;
        // Find existing trajectory for the same file name/horizon/type
        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(path.getFileName().toString(), horizon, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name());

        TrajectoryEntity trajectory;

        int version = existingOpt.isPresent() && checkParamModulationTrajectoryVersion(thermalModulationParameterEntities, existingOpt.get()) ? existingOpt.get().getVersion() : 0;

        trajectory = buildTrajectory(path, version, horizon, createdBy, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER, null, null, null, false);

        return saveThermalParamModulationTrajectory(trajectory, thermalModulationParameterEntities, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);
    }

    public List<String> createMatrixParamModulationTsFiles(StudyEntity study) {
        return createSplitCmAndMrParamFiles(study).stream()
                .map(path -> {
                    try {
                        String outputDir = antaresDataManagerProperties.getParamModulationOutputDirectory();
                        return nasFileService.saveMatrixToNas(path, outputDir);
                    } catch (IOException e) {
                        throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
                    }
                })
                .toList();
    }


    public void processThermalModulationSingleFile(
            String trajectoryToUse,
            String horizon,
            Integer studyId,
            Path trajectoryFilePath,
            String fileName,
            List<ThermalModulationParameterEntity> thermalModulationParameters,
            Path file,
            String fileType // "CM" or "MR"
    ) throws IOException {
        Path normalizedPath = buildParamModulationFilePath(trajectoryFilePath);
        checkNumericDataCMorMR(normalizedPath, trajectoryToUse, fileType);
        // Verify existing clusters depending on type
        verifyExistingSpecificClustersOfParamModulation(horizon, studyId, normalizedPath, trajectoryToUse, fileType);

        thermalModulationParameters.add(
                ThermalModulationParameterEntity.builder()
                        .tsName(fileName)
                        .checksum(getFileChecksum(file.toString()))
                        .build()
        );
    }

    private Path buildParamModulationFilePath(Path trajectoryFilePath) {
        Path allowedBaseDir = Paths.get(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .normalize();
        Path normalizedPath = trajectoryFilePath.normalize();

        if (!normalizedPath.startsWith(allowedBaseDir)) {
            throw new SecurityException("Trying to access a file outside the allowed base directory: " + allowedBaseDir);
        }
        return normalizedPath;
    }

    public void verifyExistingSpecificClustersOfParamModulation(String horizon, Integer studyId, Path modulationFile, String trajectoryName, String fileType) throws IOException {

        List<String> clustersInFile = extractClustersFromCsvHeader(modulationFile);

        boolean isMrFile = fileType.equals("MR");
        Set<String> listClusterByAreaForSpecificParam = thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam(horizon, studyId, isMrFile);
        if(listClusterByAreaForSpecificParam.isEmpty()) {
            log.warn("No specific clusters found for horizon {} and studyId {}. Skipping verification.", horizon, studyId);
            return;
        }

        Set<String> missingClusters = listClusterByAreaForSpecificParam.stream()
                .filter(cluster -> !clustersInFile.contains(cluster))
                .collect(Collectors.toSet());

        if (!missingClusters.isEmpty()) {
            String fileTypeLabel = isMrFile ? "Must Run" : "Cost Modulation";
            throw BusinessException.builder()
                    .message("Missing Areas/Cluster {0} in " + fileTypeLabel + " file for trajectory {1} in horizon {2}")
                    .errorMessageArguments(List.of(String.join(", ", missingClusters), trajectoryName, horizon))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }


    public List<String> extractClustersFromCsvHeader(Path normalized) throws IOException {
        try (var reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header != null) {
                String delimiter = header.contains(";") ? ";" : ",";
                String[] columns = header.split(delimiter);
                return Arrays.stream(columns)
                        .skip(2)
                        .map(String::toLowerCase)// Ignore DATE_HEURE et heure
                        .toList();
            }
        }
        return List.of();
    }

    public List<Path> createSplitCmAndMrParamFiles(StudyEntity study) {
        List<Path> cmAndMrParamModulationTsFiles = getParamModulationTsFiles(study.getTrajectories());

        return cmAndMrParamModulationTsFiles.stream()
                .filter(Objects::nonNull)
                .flatMap(path -> {
                            List<Path> parts;
                            Set<String> listSpecificParamClusters = new HashSet<>();
                            String fileName = path.getFileName().toString();

                            if (fileName.startsWith("CM")) {
                                listSpecificParamClusters = thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam(study.getHorizon(), study.getId(), false);
                            } else if (fileName.startsWith("MR")) {
                                listSpecificParamClusters = thermalSpecificFileProcessorService.getListClusterByAreaForSpecificParam(study.getHorizon(), study.getId(), true);

                            }
                            // Skip if no specific clusters found
                            if (listSpecificParamClusters == null || listSpecificParamClusters.isEmpty()) {
                                return Stream.empty();
                            }
                            try {
                                parts = splitCmAndMrParamFiles(path, listSpecificParamClusters);
                            } catch (IOException e) {
                                throw TechnicalException.builder().message(e.getMessage()).cause(e).build();
                            }
                            return parts == null ? Stream.<Path>empty() : parts.stream();
                        }
                )
                .toList();
    }


    public List<Path> getParamModulationTsFiles(Collection<TrajectoryEntity> trajectories) {
        return trajectories.stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER
                        .equals(TrajectoryType.valueOf(t.getType())))
                .flatMap(t -> Stream.ofNullable(t.getThermalModulationParameters())
                        .flatMap(List::stream))
                .flatMap(param -> Stream.ofNullable(param.getTsName()))
                .map(this::resolveTrajectoryFile)
                .filter(Objects::nonNull)
                .toList();
    }

    public Path resolveTrajectoryFile(String tsName) {
        String trajectoryName = betweenFirstAndLastUnderscore(tsName);
        try {
            Path path = buildTrajectoryPath(
                    trajectoryName,
                    TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER
            );
            return findFile(path, tsName).orElse(null);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Error while processing trajectory file: " + trajectoryName)
                    .cause(e)
                    .build();
        }
    }

    public List<Path> splitCmAndMrParamFiles(Path file, Set<String> listSpecificParamClusters) throws IOException {
        Objects.requireNonNull(file);

        // normaliser la liste des clusters spécifiques (trim + toLowerCase)
        Set<String> allowedClusters = (listSpecificParamClusters == null) ? Collections.emptySet()
                : listSpecificParamClusters.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());

        List<Path> generatedFiles = new ArrayList<>();
        Path tmpDir = Files.createTempDirectory("thermal_param_modulation_split_",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------"))
        );
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }

        if (!Files.exists(file)) {
            return generatedFiles;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String header = reader.readLine();

            if (header == null) {
                return generatedFiles;
            }

            String delimiter = header.contains(";") ? ";" : ",";
            String[] columns = header.split(delimiter);

            if (columns.length <= 2) {
                return generatedFiles;
            }

            Map<Integer, BufferedWriter> writers = null;

            try {
                writers = createWriters(columns, file, tmpDir, generatedFiles, allowedClusters);
                processFileLines(reader, columns, writers, delimiter);
            } finally {
                if (writers != null) closeAll(writers);
            }
        }

        return generatedFiles;
    }

    public Map<Integer, BufferedWriter> createWriters(String[] columns,
                                                       Path file,
                                                       Path targetDir,
                                                       List<Path> generatedFiles,
                                                       Set<String> listSpecificParamClusters) throws IOException {

        Map<Integer, BufferedWriter> writers = new HashMap<>();
        String baseName = getBaseName(file);

        for (int i = 2; i < columns.length; i++) {
            final int index = i;
            String areaCluster = columns[i].trim();
            if (areaCluster.isEmpty()) continue;

            //Helper to handle lowercase check and writer creation (exact match)
            ColumnSplitWriter
                    .openWriterIfAllowed(areaCluster, baseName, targetDir, listSpecificParamClusters, generatedFiles, false)
                    .ifPresent(bw -> writers.put(index, bw));
        }

        return writers;
    }


    public void processFileLines(BufferedReader reader, String[] columns, Map<Integer, BufferedWriter> writers, String delimiter) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;

            String[] fields = line.split(delimiter);
            if (fields.length < 2) continue;

            for (int i = 2; i < columns.length; i++) {
                BufferedWriter bw = writers.get(i);
                if (bw == null) continue;

                String value = (i < fields.length) ? fields[i].trim() : "";
                bw.write(value);
                bw.newLine();
            }
        }
    }

    public void closeAll(Map<Integer, BufferedWriter> writers) {
        writers.values().forEach(bw -> {
            try {
                bw.close();
            } catch (IOException ignored) {
            }
        });
    }

    public String getBaseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    public static String betweenFirstAndLastUnderscore(String filename) {
        Objects.requireNonNull(filename);
        String name = Paths.get(filename).getFileName().toString();
        int first = name.indexOf('_');
        int last = name.lastIndexOf('_');
        if (first < 0 || last < 0 || first == last) {
            return "";
        }
        return name.substring(first + 1, last);
    }

    public Path buildTrajectoryPath(String trajectoryToUse, TrajectoryType type) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = "";
        if (TrajectoryType.LOAD.equals(type)) {
            directoryByType = antaresDataManagerProperties.getLoadDirectory();
        } else if (TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.equals(type)) {
            directoryByType = antaresDataManagerProperties.getThermalModulationParameterDirectory();
        }

        if (nasDir == null || trajFilePath == null || directoryByType == null) {
            throw BusinessException.builder()
                    .message("Antares path configuration is incomplete")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }


        Path baseDirectory = Path.of(nasDir)
                .resolve(trajFilePath)
                .resolve(directoryByType)
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        //download the file
        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse).normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }

        return trajectoryFilePath;
    }
}
