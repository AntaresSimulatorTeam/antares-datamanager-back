package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.DsrRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.DsrCapacityModulationEntity;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.dsr.impl.DsrCapacityModulationFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DsrCapacityModulationFileProcessorServiceImplTest {

    private DsrCapacityModulationFileProcessorServiceImpl service;

    @Mock private DsrRepository dsrRepository;
    @Mock private TrajectoryRepository trajectoryRepository;
    @Mock private UserService userService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        AntaresDataManagerProperties properties = mock(AntaresDataManagerProperties.class);

        lenient().when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        lenient().when(properties.getTrajectoryFilePath()).thenReturn("INPUT");
        lenient().when(properties.getDsrCapacityDirectory()).thenReturn("capacity_modulation");

        service = spy(new DsrCapacityModulationFileProcessorServiceImpl(
                properties,
                new PathSecurityUtil(properties),
                trajectoryRepository,
                userService,
                dsrRepository
        ));

        lenient().when(trajectoryRepository.save(any())) .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // TEST 1 : Cas nominal
    // -------------------------------------------------------------------------
    @Test
    void shouldProcessCapacityModulationFileSuccessfully() throws IOException {
        List<String> clusters = List.of("DSR_industries");

        Path xlsx = tempDir.resolve("cm_capacity_test.xlsx");
        Files.createFile(xlsx);
        createWorkbookWithHeadersAndData(clusters, xlsx);

        DsrClusterEntity clusterEntity = new DsrClusterEntity();
        clusterEntity.setArea("FR");
        clusterEntity.setName("DSR_industries");
        // Le service lit désormais l'area depuis trajectory.getArea(), initialiser pour le test
        TrajectoryEntity clusterTrajectory = new TrajectoryEntity();
        clusterTrajectory.setArea("FR");
        clusterEntity.setTrajectory(clusterTrajectory);
        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of(clusterEntity));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        TrajectoryEntity trajectory = service.processDsrCapacityModulationFile(
                "cm_capacity_test.xlsx",
                "2029-2030",
                1
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getDsrCapacityModulationEntities()).hasSize(1);

        DsrCapacityModulationEntity entity =
                trajectory.getDsrCapacityModulationEntities().getFirst();

        assertThat(entity.getTrajectory()).isEqualTo(trajectory);
        assertThat(entity.getTsName()).isEqualTo("cm_capacity_test.xlsx");
    }

    // -------------------------------------------------------------------------
    // TEST 2 : Incrémentation de version
    // -------------------------------------------------------------------------
    @Test
    void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists() throws IOException {
        List<String> clustersInFile = List.of("DSR_industries");

        Path xlsx = tempDir.resolve("cm_capacity_test.xlsx");
        Files.createFile(xlsx);
        createWorkbookWithHeadersAndData(clustersInFile, xlsx);

        DsrClusterEntity clusterEntity2 = new DsrClusterEntity();
        clusterEntity2.setArea("FR");
        clusterEntity2.setName("DSR_industries");
        TrajectoryEntity clusterTrajectory2 = new TrajectoryEntity();
        clusterTrajectory2.setArea("FR");
        clusterEntity2.setTrajectory(clusterTrajectory2);
        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of(clusterEntity2));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        UserInfoDto user = new UserInfoDto();
        user.setNni("TESTNNI");
        when(userService.getCurrentUserDetails()).thenReturn(user);

        TrajectoryEntity existing = new TrajectoryEntity();
        existing.setType(TrajectoryType.DSR_CAPACITY_MODULATION.name());
        existing.setFileName("capacity_test");
        existing.setVersion(1);
        existing.setHorizon("2029-2030");
        existing.setChecksum("ABC123");

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        TrajectoryEntity trajectory = service.processDsrCapacityModulationFile(
                "cm_capacity_test",
                "2029-2030",
                1
        );

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getVersion()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // TEST 3 : Mauvais préfixe
    // -------------------------------------------------------------------------
    @Test
    void shouldThrowWhenFileNameDoesNotStartWithExpectedPrefix() {
        assertThatThrownBy(() ->
                service.processDsrCapacityModulationFile(
                        "wrong_prefix_file.xlsx",
                        "2030",
                        1
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("The trajectory file name must start with");
    }

    // -------------------------------------------------------------------------
    // TEST 4 : Clusters manquants
    // -------------------------------------------------------------------------
    @Test
    void shouldThrowWhenClustersMissing() throws IOException {
        List<String> clustersInFile = List.of("DSR_unknown");

        Path xlsx = tempDir.resolve("cm_capacity_test.xlsx");
        Files.createFile(xlsx);
        createWorkbookWithHeadersAndData(clustersInFile, xlsx);

        DsrClusterEntity clusterEntity3 = new DsrClusterEntity();
        clusterEntity3.setArea("FR");
        clusterEntity3.setName("DSR_industries");
        TrajectoryEntity clusterTrajectory3 = new TrajectoryEntity();
        clusterTrajectory3.setArea("FR");
        clusterEntity3.setModulation(TRUE);
        clusterEntity3.setTrajectory(clusterTrajectory3);
        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of(clusterEntity3));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        assertThatThrownBy(() ->
                service.processDsrCapacityModulationFile(
                        "cm_capacity_test.xlsx",
                        "2029-2030",
                        1
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing Areas/Clusters");
    }

    // -------------------------------------------------------------------------
    // TEST 5 : Fichier sans données
    // -------------------------------------------------------------------------
    @Test
    void shouldThrowWhenFileContainsOnlyHeader() throws IOException {
        List<String> clusters = List.of("DSR_industries");

        Path xlsx = tempDir.resolve("cm_capacity_test.xlsx");
        Files.createFile(xlsx);
        createWorkbookWithOnlyHeaders(clusters, xlsx);

        DsrClusterEntity clusterEntity4 = new DsrClusterEntity();
        clusterEntity4.setArea("FR");
        clusterEntity4.setName("DSR_industries");
        TrajectoryEntity clusterTrajectory4 = new TrajectoryEntity();
        clusterTrajectory4.setArea("FR");
        clusterEntity4.setTrajectory(clusterTrajectory4);
        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of(clusterEntity4));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        assertThatThrownBy(() ->
                service.processDsrCapacityModulationFile(
                        "cm_capacity_test.xlsx",
                        "2029-2030",
                        1
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No data in DSR Capacity Modulation trajectory");
    }

    @Test
    void validateDsrCapacityModulationCoherence_shouldReuseImportValidationRules() throws IOException {
        List<String> clusters = List.of("DSR_industries");

        Path xlsx = tempDir.resolve("cm_validation_test.xlsx");
        Files.createFile(xlsx);
        createWorkbookWithHeadersAndData(clusters, xlsx);

        DsrClusterEntity clusterEntity = new DsrClusterEntity();
        clusterEntity.setArea("FR");
        clusterEntity.setName("DSR_industries");
        TrajectoryEntity clusterTrajectory = new TrajectoryEntity();
        clusterTrajectory.setArea("FR");
        clusterEntity.setTrajectory(clusterTrajectory);
        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1)).thenReturn(List.of(clusterEntity));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        TrajectoryEntity trajectoryToValidate = new TrajectoryEntity();
        trajectoryToValidate.setFileName("cm_validation_test");
        trajectoryToValidate.setHorizon("2029-2030");
        service.validateDsrCapacityModulationCoherence(trajectoryToValidate, 1);

        verify(trajectoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // TEST 7 : Specific enabled should require specific even if OTHERS enabled
    // -------------------------------------------------------------------------
    @Test
    void shouldRequireSpecificWhenSpecificEnabledEvenIfOthersEnabled() throws IOException {
        // create xlsx that contains only OTHERS header (no FR header)
        Path xlsx = tempDir.resolve("cm_specific_true.xlsx");
        Files.createFile(xlsx);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("2029-2030");
            Row header = sh.createRow(0);
            header.createCell(0).setCellValue("date");
            header.createCell(1).setCellValue("area");
            header.createCell(2).setCellValue("OTHERS_DSR_tertiaire");

            Row data = sh.createRow(1);
            data.createCell(0).setCellValue("01/07/2028 00:00");
            data.createCell(1).setCellValue("OTHERS");
            data.createCell(2).setCellValue(1.0);

            try (OutputStream os = Files.newOutputStream(xlsx)) {
                wb.write(os);
            }
        }

        DsrClusterEntity fr = new DsrClusterEntity();
        fr.setArea("FR");
        fr.setName("DSR_tertiaire");
        fr.setModulation(true);
        TrajectoryEntity frTrajectory = new TrajectoryEntity();
        frTrajectory.setArea("FR");
        fr.setTrajectory(frTrajectory);

        DsrClusterEntity others = new DsrClusterEntity();
        others.setArea("OTHERS");
        others.setName("DSR_tertiaire");
        others.setModulation(true);
        TrajectoryEntity othersTrajectory = new TrajectoryEntity();
        othersTrajectory.setArea("OTHERS");
        others.setTrajectory(othersTrajectory);

        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(100))
                .thenReturn(List.of(fr, others));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        assertThatThrownBy(() -> service.processDsrCapacityModulationFile("cm_specific_true", "2029-2030", 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing Areas/Clusters");
    }

    // -------------------------------------------------------------------------
    // HELPERS EXCEL
    // -------------------------------------------------------------------------
    private void createWorkbookWithOnlyHeaders(List<String> clusters, Path file) throws IOException {
        String horizon = "2029-2030";
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(horizon);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("date");
        headerRow.createCell(1).setCellValue("area");

        for (int i = 0; i < clusters.size(); i++) {
            headerRow.createCell(i + 2).setCellValue("FR_" + clusters.get(i));
        }

        try (OutputStream os = Files.newOutputStream(file)) {
            workbook.write(os);
        }
        workbook.close();
    }

    private void createWorkbookWithHeadersAndData(List<String> clusters, Path file) throws IOException {
        String horizon = "2029-2030";
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(horizon);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("date");
        headerRow.createCell(1).setCellValue("area");

        for (int i = 0; i < clusters.size(); i++) {
            headerRow.createCell(i + 2).setCellValue("FR_" + clusters.get(i));
        }

        for (int r = 1; r <= 3; r++) {
            Row row = sheet.createRow(r);
            row.createCell(0).setCellValue("01/07/2028 0" + (r - 1) + ":00");
            row.createCell(1).setCellValue("FR");

            for (int c = 0; c < clusters.size(); c++) {
                row.createCell(c + 2).setCellValue(1.0);
            }
        }

        try (OutputStream os = Files.newOutputStream(file)) {
            workbook.write(os);
        }
        workbook.close();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "CM_exact_case,    CM_exact_case.xlsx,    false", // same case
            "cm_fallback_file, CM_fallback_file.xlsx, false", // uppercase
            "CM_fallback_file, cm_fallback_file.xlsx, false", // lowercase
            "CM_non_existent,  ,                      true"   // not found
    })
    void testGetTrajectoryFilePathScenarios(String input, String fileToCreate, boolean expect404) throws IOException {
        Path targetDir = tempDir.resolve("INPUT").resolve("capacity_modulation");
        Files.createDirectories(targetDir);
        if (fileToCreate != null) {
            Files.createFile(targetDir.resolve(fileToCreate));
        }

        if (expect404) {
            assertThatThrownBy(() -> service.getTrajectoryFilePath(input))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        } else {
            Path result = service.getTrajectoryFilePath(input);
            assertThat(result.getFileName()).hasToString(fileToCreate);
        }
    }

    @Test
    void testGetTrajectoryFilePathPathTraversalSecurity() {
        assertThatThrownBy(() -> service.getTrajectoryFilePath("../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }
}
