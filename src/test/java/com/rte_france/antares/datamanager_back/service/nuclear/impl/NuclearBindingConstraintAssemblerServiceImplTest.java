package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.NuclearBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearConstraintItemDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearTalonBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.NuclearModulationParameterRepository;
import com.rte_france.antares.datamanager_back.repository.model.NuclearModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NuclearBindingConstraintAssemblerServiceImplTest {

    @Mock private NuclearModulationParameterRepository nuclearModulationParameterRepository;
    @Mock private NasFileService nasFileService;
    @Mock private AntaresDataManagerProperties properties;
    @Mock private PathSecurityUtil pathSecurityUtil;

    @InjectMocks
    private NuclearBindingConstraintAssemblerServiceImpl assembler;

    private Path tempDir;

    @BeforeEach
    void setUpNas() throws IOException {
        tempDir = Files.createTempDirectory("nuc_bc_test_");
        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("INPUT");
    }

    @AfterEach
    void tearDownNas() throws IOException {
        deleteRecursively(tempDir);
    }

    @Nested
    class ModulationBindingConstraints {

        private static final String TRAJ_NAME = "default_nuc";
        private static final int NB_COLUMNS = 200;

        @BeforeEach
        void setUp() throws IOException {
            when(properties.getNuclearModulationDirectory()).thenReturn("specific_nuclear/Modulation");
            when(properties.getNuclearModulationTsOutputDirectory()).thenReturn("output/nuclear_modulation_arrow");

            Path tsDir = tempDir
                    .resolve("INPUT")
                    .resolve("specific_nuclear/Modulation")
                    .resolve(TRAJ_NAME)
                    .resolve("TS_modulation");
            Files.createDirectories(tsDir);
            createWeeklyXlsx(tsDir.resolve(TRAJ_NAME + "_weekly.xlsx"), NB_COLUMNS);

            when(nasFileService.readAndSaveMatrixToNas(any(Path.class), anyString(), any(), anyBoolean()))
                    .thenReturn("arrow_hourly.arrow", "arrow_daily.arrow", "arrow_weekly.arrow");
            when(nasFileService.countXlsxColumns(any(Path.class))).thenReturn(NB_COLUMNS);
        }

        @Test
        void assembleModulationBindingConstraints_shouldReturnEmptyClusterListsWhenNoFrNuclearClusters() {
            TrajectoryEntity modulationTraj = modulationTrajectory();
            mockCoefficients(modulationTraj.getId());

            NuclearBindingConstraintGenerationDTO result = assembler.assembleModulationBindingConstraints(modulationTraj, List.of());

            assertThat(result.frStandardClusters()).isEmpty();
            assertThat(result.frPeakClusters()).isEmpty();
            assertThat(result.yNucModulationClusters()).isEmpty();
        }

        @Test
        void assembleModulationBindingConstraints_happyPath_shouldBuildCorrectDto() {
            TrajectoryEntity modulationTraj = modulationTrajectory();
            mockCoefficients(modulationTraj.getId());

            List<String> frNuclearClusterNames = List.of("Nuclear_cp0", "Nuclear_epr", "Nuclear_peak1");
            NuclearBindingConstraintGenerationDTO dto = assembler.assembleModulationBindingConstraints(modulationTraj, frNuclearClusterNames);

            assertThat(dto.group()).isEqualTo("scenarised" + NB_COLUMNS);
            assertThat(dto.nbTsColumns()).isEqualTo(NB_COLUMNS);

            assertThat(dto.frStandardClusters()).containsExactlyInAnyOrder("fr_nuclear_cp0", "fr_nuclear_epr");
            assertThat(dto.frPeakClusters()).containsExactly("fr_nuclear_peak1");
            assertThat(dto.yNucModulationClusters()).containsExactlyInAnyOrder(
                    "y_nuc_modulation_nuclear_cp0", "y_nuc_modulation_nuclear_epr");

            assertThat(dto.constraints()).hasSize(3);
            NuclearConstraintItemDTO limit = findConstraint(dto, "nuc_modulation_limit");
            assertThat(limit.type()).isEqualTo("hourly");
            assertThat(limit.includesPeak()).isTrue();
            assertThat(limit.coeff()).isEqualByComparingTo(new BigDecimal("1.00"));

            NuclearConstraintItemDTO daily = findConstraint(dto, "nuc_modulation_daily");
            assertThat(daily.type()).isEqualTo("daily");
            assertThat(daily.includesPeak()).isFalse();
            assertThat(daily.coeff()).isEqualByComparingTo(new BigDecimal("0.97"));

            NuclearConstraintItemDTO weekly = findConstraint(dto, "nuc_modulation_weekly");
            assertThat(weekly.type()).isEqualTo("weekly");
            assertThat(weekly.includesPeak()).isFalse();
            assertThat(weekly.coeff()).isEqualByComparingTo(new BigDecimal("0.93"));
        }

        @Test
        void assembleModulationBindingConstraints_shouldThrowTechnicalExceptionWhenDuplicateCoefficientTypeExists() {
            TrajectoryEntity modulationTraj = modulationTrajectory();
            when(nuclearModulationParameterRepository.findByTrajectoryId(modulationTraj.getId())).thenReturn(List.of(
                    modParam("nucFR_modul_hourly", new BigDecimal("1.00")),
                    modParam("nucFR_modul_hourly", new BigDecimal("0.50"))
            ));

            assertThatThrownBy(() -> assembler.assembleModulationBindingConstraints(modulationTraj, List.of()))
                    .isInstanceOf(TechnicalException.class);
        }

        private TrajectoryEntity modulationTrajectory() {
            TrajectoryEntity traj = new TrajectoryEntity();
            traj.setId(42);
            traj.setType("NUCLEAR_FR_MODULATION");
            traj.setFileName(TRAJ_NAME);
            traj.setNuclearModulationParameterEntities(List.of());
            return traj;
        }

        private void mockCoefficients(Integer trajectoryId) {
            when(nuclearModulationParameterRepository.findByTrajectoryId(trajectoryId)).thenReturn(List.of(
                    modParam("nucFR_modul_hourly", new BigDecimal("1.00")),
                    modParam("nucFR_modul_daily",  new BigDecimal("0.97")),
                    modParam("nucFR_modul_weekly", new BigDecimal("0.93"))
            ));
        }

        private NuclearModulationParameterEntity modParam(String type, BigDecimal value) {
            NuclearModulationParameterEntity e = new NuclearModulationParameterEntity();
            e.setType(type);
            e.setValue(value);
            return e;
        }

        private NuclearConstraintItemDTO findConstraint(NuclearBindingConstraintGenerationDTO dto, String name) {
            return dto.constraints().stream()
                    .filter(c -> c.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Constraint not found: " + name));
        }

        private void createWeeklyXlsx(Path filePath, int nbColumns) throws IOException {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet();
                Row header = sheet.createRow(0);
                for (int i = 0; i < nbColumns; i++) {
                    header.createCell(i).setCellValue("col" + i);
                }
                try (OutputStream out = Files.newOutputStream(filePath)) {
                    wb.write(out);
                }
            }
        }
    }

    @Nested
    class TalonBindingConstraint {

        private static final String STORED_FILE_NAME = "default_talon";
        private static final int NB_COLUMNS = 200;

        @BeforeEach
        void setUp() throws IOException {
            when(properties.getNuclearTalonDirectory()).thenReturn("specific_nuclear/Talon_nuc");
            when(properties.getNuclearTalonTsOutputDirectory()).thenReturn("output/nuclear_talon_arrow");

            Path talonDir = tempDir.resolve("INPUT").resolve("specific_nuclear/Talon_nuc");
            Files.createDirectories(talonDir);
            createTalonXlsx(talonDir.resolve("TALON_NUC_" + STORED_FILE_NAME + ".xlsx"), NB_COLUMNS);

            when(nasFileService.readAndSaveMatrixToNas(any(Path.class), anyString(), any(), anyBoolean()))
                    .thenReturn("talon_arrow.arrow");
            when(nasFileService.countXlsxColumns(any(Path.class))).thenReturn(NB_COLUMNS);
        }

        @Test
        void assembleTalonBindingConstraint_shouldReturnEmptyStandardClustersWhenNoFrNuclearClusters() {
            NuclearTalonBindingConstraintGenerationDTO result = assembler.assembleTalonBindingConstraint(talonTrajectory(STORED_FILE_NAME), List.of());

            assertThat(result.frStandardClusters()).isEmpty();
        }

        @Test
        void assembleTalonBindingConstraint_happyPath_shouldBuildCorrectDto() {
            List<String> frNuclearClusterNames = List.of("Nuclear_cp0_cp1_cp2", "Nuclear_epr", "Nuclear_peak1");

            NuclearTalonBindingConstraintGenerationDTO dto = assembler.assembleTalonBindingConstraint(talonTrajectory(STORED_FILE_NAME), frNuclearClusterNames);

            assertThat(dto.group()).isEqualTo("scenarised" + NB_COLUMNS);
            assertThat(dto.nbTsColumns()).isEqualTo(NB_COLUMNS);
            assertThat(dto.frStandardClusters()).containsExactlyInAnyOrder("fr_nuclear_cp0_cp1_cp2", "fr_nuclear_epr");
            assertThat(dto.series()).isEqualTo("talon_arrow.arrow");
        }

        @Test
        void assembleTalonBindingConstraint_shouldExcludeAllPeakClusters() {
            List<String> frNuclearClusterNames = List.of("Nuclear_peak1", "Nuclear_peak2");

            NuclearTalonBindingConstraintGenerationDTO dto = assembler.assembleTalonBindingConstraint(talonTrajectory(STORED_FILE_NAME), frNuclearClusterNames);

            assertThat(dto.frStandardClusters()).isEmpty();
        }

        @Test
        void assembleTalonBindingConstraint_shouldThrowTechnicalExceptionWhenFileUnreadable() throws IOException {
            when(nasFileService.countXlsxColumns(any(Path.class))).thenThrow(new IOException("boom"));
            TrajectoryEntity trajectory = talonTrajectory(STORED_FILE_NAME);

            assertThatThrownBy(() -> assembler.assembleTalonBindingConstraint(trajectory, List.of()))
                    .isInstanceOf(TechnicalException.class);
        }

        @Test
        void assembleTalonBindingConstraint_shouldThrowTechnicalExceptionWhenPathValidationFails() throws IOException {
            doThrow(new IOException("invalid path"))
                    .when(pathSecurityUtil).validatePathFromBaseDir(anyString(), any());
            TrajectoryEntity trajectory = talonTrajectory(STORED_FILE_NAME);

            assertThatThrownBy(() -> assembler.assembleTalonBindingConstraint(trajectory, List.of()))
                    .isInstanceOf(TechnicalException.class);
        }

        @Test
        void assembleTalonBindingConstraint_shouldFindFileWithUppercasePrefixCandidate() throws IOException {
            assembler.assembleTalonBindingConstraint(talonTrajectory(STORED_FILE_NAME), List.of());

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            verify(nasFileService).countXlsxColumns(pathCaptor.capture());

            assertThat(pathCaptor.getValue().toString())
                    .endsWith(Path.of("specific_nuclear", "Talon_nuc", "TALON_NUC_" + STORED_FILE_NAME + ".xlsx").toString());
        }

        @Test
        void assembleTalonBindingConstraint_shouldFindFileWithLowercasePrefixCandidate() throws IOException {
            String storedName = "case_test_talon";
            Path talonDir = tempDir.resolve("INPUT").resolve("specific_nuclear/Talon_nuc");
            createTalonXlsx(talonDir.resolve("talon_nuc_" + storedName + ".xlsx"), NB_COLUMNS);

            assembler.assembleTalonBindingConstraint(talonTrajectory(storedName), List.of());

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            verify(nasFileService, atLeastOnce()).countXlsxColumns(pathCaptor.capture());

            assertThat(pathCaptor.getValue().toString()).endsWith("talon_nuc_" + storedName + ".xlsx");
        }

        @Test
        void assembleTalonBindingConstraint_shouldThrowTechnicalExceptionWhenNeitherPrefixCandidateExists() {
            TrajectoryEntity trajectory = talonTrajectory("no_such_talon_file");

            assertThatThrownBy(() -> assembler.assembleTalonBindingConstraint(trajectory, List.of()))
                    .isInstanceOf(TechnicalException.class);
        }

        @Test
        void assembleTalonBindingConstraint_shouldAvoidDoubleXlsxExtensionWhenStoredNameAlreadyHasIt() throws IOException {
            String storedNameWithExtension = STORED_FILE_NAME + ".xlsx";
            Path talonDir = tempDir.resolve("INPUT").resolve("specific_nuclear/Talon_nuc");
            createTalonXlsx(talonDir.resolve("TALON_NUC_" + storedNameWithExtension), NB_COLUMNS);

            assembler.assembleTalonBindingConstraint(talonTrajectory(storedNameWithExtension), List.of());

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            verify(nasFileService, atLeastOnce()).countXlsxColumns(pathCaptor.capture());

            String resolved = pathCaptor.getValue().toString();
            assertThat(resolved)
                    .endsWith("TALON_NUC_" + storedNameWithExtension)
                    .doesNotContain(".xlsx.xlsx");
        }

        private TrajectoryEntity talonTrajectory(String fileName) {
            TrajectoryEntity traj = new TrajectoryEntity();
            traj.setId(7);
            traj.setType("NUCLEAR_FR_TALON");
            traj.setFileName(fileName);
            return traj;
        }

        private void createTalonXlsx(Path filePath, int nbColumns) throws IOException {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet();
                Row header = sheet.createRow(0);
                for (int i = 0; i < nbColumns; i++) {
                    header.createCell(i).setCellValue("col" + i);
                }
                try (OutputStream out = Files.newOutputStream(filePath)) {
                    wb.write(out);
                }
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}