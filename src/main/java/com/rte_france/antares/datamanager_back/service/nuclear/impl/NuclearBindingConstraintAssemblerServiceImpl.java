package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.NuclearBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearConstraintItemDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.NuclearModulationParameterRepository;
import com.rte_france.antares.datamanager_back.repository.model.NuclearModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearBindingConstraintAssemblerService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
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

    private final NuclearModulationParameterRepository nuclearModulationParameterRepository;
    private final NasFileService nasFileService;
    private final AntaresDataManagerProperties properties;
    private final PathSecurityUtil pathSecurityUtil;

    private record NuclearClusterNames(List<String> standard, List<String> peak, List<String> yNucModulation) {}

    private record TsArrowFiles(String hourly, String daily, String weekly) {}

    @Override
    public NuclearBindingConstraintGenerationDTO assembleBindingConstraints(TrajectoryEntity modulationTrajectory, List<String> frNuclearClusterNames) {
        Map<String, BigDecimal> coeffs = loadCoefficients(modulationTrajectory.getId());
        NuclearClusterNames clusterNames = buildClusterNames(frNuclearClusterNames);

        try {
            TsArrowFiles arrowFiles = convertTsFiles(modulationTrajectory.getFileName());
            int nbColumns = countTsColumns(modulationTrajectory.getFileName());
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

    private Map<String, BigDecimal> loadCoefficients(Integer trajectoryId) {
        return nuclearModulationParameterRepository.findByTrajectoryId(trajectoryId)
                .stream()
                .collect(Collectors.toMap(NuclearModulationParameterEntity::getType, NuclearModulationParameterEntity::getValue));
    }

    private NuclearClusterNames buildClusterNames(List<String> frNuclearClusterNames) {
        List<String> standard = new ArrayList<>();
        List<String> peak = new ArrayList<>();
        List<String> yNuc = new ArrayList<>();

        for (String name : frNuclearClusterNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("peak")) {
                peak.add("fr_" + lower);
            } else {
                standard.add("fr_" + lower);
                yNuc.add("y_nuc_modulation_" + lower);
            }
        }

        return new NuclearClusterNames(standard, peak, yNuc);
    }

    private TsArrowFiles convertTsFiles(String trajectoryName) throws IOException {
        String hourlyArrow = convertSingleTsFile(trajectoryName, TS_HOURLY);
        String dailyArrow = convertSingleTsFile(trajectoryName, TS_DAILY);
        String weeklyArrow = convertSingleTsFile(trajectoryName, TS_WEEKLY);
        return new TsArrowFiles(hourlyArrow, dailyArrow, weeklyArrow);
    }

    private String convertSingleTsFile(String trajectoryName, String tsType) throws IOException {
        Path tsPath = buildTsFilePath(trajectoryName, tsType);
        validateTsPath(trajectoryName, tsType);
        return nasFileService.readAndSaveMatrixToNas(tsPath, properties.getNuclearModulationTsOutputDirectory(), null, false);
    }

    private Path buildTsFilePath(String trajectoryName, String tsType) {
        return Path.of(properties.getNasDirectory())
                .resolve(properties.getTrajectoryFilePath())
                .resolve(properties.getNuclearModulationDirectory())
                .resolve(trajectoryName)
                .resolve(TS_MODULATION_SUBDIR)
                .resolve(trajectoryName + "_" + tsType + ".xlsx")
                .normalize();
    }

    private void validateTsPath(String trajectoryName, String tsType) {
        String relativePath = properties.getNuclearModulationDirectory()
                + "/" + trajectoryName
                + "/" + TS_MODULATION_SUBDIR
                + "/" + trajectoryName + "_" + tsType + ".xlsx";
        try {
            pathSecurityUtil.validatePathFromBaseDir(relativePath, AntaresDataManagerProperties::getTrajectoryFilePath);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(relativePath))
                    .message("Invalid nuclear TS modulation path: {0}")
                    .cause(e)
                    .build();
        }
    }

    /* TODO: this opens the file a second time. we could pass the column number the first time but it would need
        NasFileService refactors
     */
    private int countTsColumns(String trajectoryName) throws IOException {
        Path weeklyPath = buildTsFilePath(trajectoryName, TS_WEEKLY);
        try (InputStream is = Files.newInputStream(weeklyPath);
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            return headerRow != null ? headerRow.getLastCellNum() : 0;
        }
    }

    private List<NuclearConstraintItemDTO> buildConstraints(Map<String, BigDecimal> coeffs, TsArrowFiles arrowFiles) {
        return List.of(
                new NuclearConstraintItemDTO(CONSTRAINT_LIMIT, TS_HOURLY, coeffs.getOrDefault(COEFF_HOURLY, BigDecimal.ONE), true,  arrowFiles.hourly()),
                new NuclearConstraintItemDTO(CONSTRAINT_DAILY, TS_DAILY,  coeffs.getOrDefault(COEFF_DAILY,  BigDecimal.ONE), false, arrowFiles.daily()),
                new NuclearConstraintItemDTO(CONSTRAINT_WEEKLY, TS_WEEKLY, coeffs.getOrDefault(COEFF_WEEKLY, BigDecimal.ONE), false, arrowFiles.weekly())
        );
    }
}
