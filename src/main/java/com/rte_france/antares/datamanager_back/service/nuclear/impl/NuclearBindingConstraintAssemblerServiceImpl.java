package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.NuclearBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearConstraintItemDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearTalonBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.NuclearModulationParameterRepository;
import com.rte_france.antares.datamanager_back.repository.model.NuclearModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearBindingConstraintAssemblerService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearClusterNames;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearFilePrefixes;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NuclearBindingConstraintAssemblerServiceImpl implements NuclearBindingConstraintAssemblerService {

    private static final String COEFF_HOURLY = "nucFR_modul_hourly";
    private static final String COEFF_DAILY = "nucFR_modul_daily";
    private static final String COEFF_WEEKLY = "nucFR_modul_weekly";
    private static final String TS_MODULATION_SUBDIR = "TS_modulation";
    private static final String TS_HOURLY = "hourly";
    private static final String TS_DAILY = "daily";
    private static final String TS_WEEKLY = "weekly";
    private static final String CONSTRAINT_LIMIT = "nuc_modulation_limit";
    private static final String CONSTRAINT_DAILY = "nuc_modulation_daily";
    private static final String CONSTRAINT_WEEKLY = "nuc_modulation_weekly";
    private static final String CONSTRAINT_GROUP_NAME = "scenarised";
    private static final String XLSX_SUFFIX = ".xlsx";
    private static final List<String> TALON_PREFIX_CANDIDATES = List.of(
            NuclearFilePrefixes.TALON_FILE_PREFIX,
            NuclearFilePrefixes.TALON_FILE_PREFIX.toLowerCase(Locale.ROOT)
    );

    private final NuclearModulationParameterRepository nuclearModulationParameterRepository;
    private final NasFileService nasFileService;
    private final AntaresDataManagerProperties properties;
    private final PathSecurityUtil pathSecurityUtil;

    private record ClusterNameGroups(List<String> standard, List<String> peak, List<String> yNucModulation) {}

    private record TsArrowFiles(String hourly, String daily, String weekly) {}

    @Override
    public NuclearBindingConstraintGenerationDTO assembleModulationBindingConstraints(StudyEntity studyEntity, TrajectoryEntity modulationTrajectory, List<String> frNuclearClusterNames) {
        Map<String, BigDecimal> coeffs = loadCoefficients(modulationTrajectory.getId());
        ClusterNameGroups clusterNames = buildClusterNames(frNuclearClusterNames);
        String horizonYear = extractHorizonYear(studyEntity.getHorizon());

        try {
            TsArrowFiles arrowFiles = convertTsFiles(modulationTrajectory.getFileName(), horizonYear);
            int nbColumns = countModulationTsColumns(modulationTrajectory.getFileName(), horizonYear);
            String group = CONSTRAINT_GROUP_NAME + nbColumns;

            List<NuclearConstraintItemDTO> constraints = buildConstraints(coeffs, arrowFiles);
            return new NuclearBindingConstraintGenerationDTO(
                    group,
                    nbColumns,
                    clusterNames.standard(),
                    clusterNames.peak(),
                    clusterNames.yNucModulation(),
                    constraints
            );
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(modulationTrajectory.getFileName()))
                    .message("Failed to assemble nuclear binding constraints for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    @Override
    public NuclearTalonBindingConstraintGenerationDTO assembleTalonBindingConstraint(StudyEntity studyEntity, TrajectoryEntity talonTrajectory, List<String> frNuclearClusterNames) {
        List<String> standardClusters = buildStandardClusterNames(frNuclearClusterNames);
        String horizonYear = extractHorizonYear(studyEntity.getHorizon());

        try {
            Path relativePath = buildTalonRelativePath(talonTrajectory.getFileName());
            Path talonPath = resolveValidatedNasPath(relativePath, "Invalid nuclear talon path: {0}");

            int nbColumns = nasFileService.countXlsxColumns(talonPath, horizonYear);
            String arrowFile = nasFileService.readAndSaveMatrixToNas(talonPath, properties.getNuclearTalonTsOutputDirectory(), horizonYear, false);
            String group = CONSTRAINT_GROUP_NAME + nbColumns;

            return new NuclearTalonBindingConstraintGenerationDTO(group, nbColumns, standardClusters, arrowFile);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(talonTrajectory.getFileName()))
                    .message("Failed to assemble nuclear talon binding constraint for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    private Map<String, BigDecimal> loadCoefficients(Integer trajectoryId) {
        return nuclearModulationParameterRepository.findByTrajectoryId(trajectoryId)
                .stream()
                .collect(Collectors.toMap(
                        NuclearModulationParameterEntity::getType,
                        NuclearModulationParameterEntity::getValue,
                        (a, b) -> {
                            throw TechnicalException.builder()
                                    .message("Duplicate nuclear modulation coefficient found for trajectory " + trajectoryId)
                                    .build();
                        }));
    }

    private ClusterNameGroups buildClusterNames(List<String> frNuclearClusterNames) {
        List<String> standard = new ArrayList<>();
        List<String> peak = new ArrayList<>();
        List<String> yNuc = new ArrayList<>();

        for (String name : frNuclearClusterNames) {
            String lower = NuclearClusterNames.normalize(name);
            if (NuclearClusterNames.isPeak(name)) {
                peak.add("fr_" + lower);
            } else {
                standard.add("fr_" + lower);
                yNuc.add("y_nuc_modulation_" + lower);
            }
        }

        return new ClusterNameGroups(standard, peak, yNuc);
    }

    private List<String> buildStandardClusterNames(List<String> frNuclearClusterNames) {
        return frNuclearClusterNames.stream()
                .filter(name -> !NuclearClusterNames.isPeak(name))
                .map(name -> "fr_" + NuclearClusterNames.normalize(name))
                .distinct()
                .toList();
    }

    private TsArrowFiles convertTsFiles(String trajectoryName, String horizonYear) throws IOException {
        String hourlyArrow = convertSingleTsFile(trajectoryName, TS_HOURLY, horizonYear);
        String dailyArrow = convertSingleTsFile(trajectoryName, TS_DAILY, horizonYear);
        String weeklyArrow = convertSingleTsFile(trajectoryName, TS_WEEKLY, horizonYear);
        return new TsArrowFiles(hourlyArrow, dailyArrow, weeklyArrow);
    }

    private String convertSingleTsFile(String trajectoryName, String tsType, String horizonYear) throws IOException {
        Path relativePath = buildTsRelativePath(trajectoryName, tsType);
        Path tsPath = resolveValidatedNasPath(relativePath, "Invalid nuclear TS modulation path: {0}");
        return nasFileService.readAndSaveMatrixToNas(tsPath, properties.getNuclearModulationTsOutputDirectory(), horizonYear, false);
    }

    private Path buildTsRelativePath(String trajectoryName, String tsType) {
        return Path.of(properties.getNuclearModulationDirectory())
                .resolve(trajectoryName)
                .resolve(TS_MODULATION_SUBDIR)
                .resolve(trajectoryName + "_" + tsType + XLSX_SUFFIX);
    }

    private int countModulationTsColumns(String trajectoryName, String horizonYear) throws IOException {
        Path weeklyRelativePath = buildTsRelativePath(trajectoryName, TS_WEEKLY);
        Path weeklyPath = resolveValidatedNasPath(weeklyRelativePath, "Invalid nuclear TS modulation path: {0}");
        return nasFileService.countXlsxColumns(weeklyPath, horizonYear);
    }

    private String extractHorizonYear(String horizon) {
        return horizon != null && horizon.contains("-") ? horizon.split("-")[1] : horizon;
    }

    private List<NuclearConstraintItemDTO> buildConstraints(Map<String, BigDecimal> coeffs, TsArrowFiles arrowFiles) {
        return List.of(
                new NuclearConstraintItemDTO(CONSTRAINT_LIMIT, TS_HOURLY, coeffs.getOrDefault(COEFF_HOURLY, BigDecimal.ONE), true,  arrowFiles.hourly()),
                new NuclearConstraintItemDTO(CONSTRAINT_DAILY, TS_DAILY,  coeffs.getOrDefault(COEFF_DAILY,  BigDecimal.ONE), false, arrowFiles.daily()),
                new NuclearConstraintItemDTO(CONSTRAINT_WEEKLY, TS_WEEKLY, coeffs.getOrDefault(COEFF_WEEKLY, BigDecimal.ONE), false, arrowFiles.weekly())
        );
    }

    private Path buildTalonRelativePath(String storedFileName) throws IOException {
        Path talonDirectory = Path.of(properties.getNuclearTalonDirectory());
        for (String prefix : TALON_PREFIX_CANDIDATES) {
            Path candidate = talonDirectory.resolve(buildTalonFileName(prefix, storedFileName));
            if (Files.exists(resolveValidatedNasPath(candidate, "Invalid nuclear talon path: {0}"))) {
                return candidate;
            }
        }
        throw new IOException("Nuclear talon file not found for: " + storedFileName);
    }

    private String buildTalonFileName(String prefix, String storedFileName) {
        String prefixed = prefix + storedFileName;
        return prefixed.toLowerCase(Locale.ROOT).endsWith(XLSX_SUFFIX) ? prefixed : prefixed + XLSX_SUFFIX;
    }

    private Path resolveNasPath(Path relativePath) {
        return pathSecurityUtil.resolveSafePath(
                p -> Path.of(p.getNasDirectory(), p.getTrajectoryFilePath()),
                relativePath.toString()
        );
    }

    private Path resolveValidatedNasPath(Path relativePath, String invalidPathMessage) {
        try {
            return resolveNasPath(relativePath);
        } catch (BusinessException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(relativePath.toString()))
                    .message(invalidPathMessage)
                    .cause(e)
                    .build();
        }
    }
}