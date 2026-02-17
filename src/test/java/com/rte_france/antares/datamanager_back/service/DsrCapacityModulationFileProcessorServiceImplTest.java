package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.DsrRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.DsrCapacityModulationEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.dsr.impl.DsrCapacityModulationFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DsrCapacityModulationFileProcessorServiceImplTest {
    
    private DsrCapacityModulationFileProcessorServiceImpl service;

    @Mock private DsrRepository dsrRepository;
    @Mock private TrajectoryRepository trajectoryRepository;
    @Mock private UserService userService;
    @Mock private AreaRepository areaRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        AntaressDataManagerProperties properties = mock(AntaressDataManagerProperties.class);

        service = spy(new DsrCapacityModulationFileProcessorServiceImpl(
                properties,
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
        createWorkbookWithHeadersAndData(clusters, xlsx, "2029-2030");

        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of("FR_DSR_industries"));

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
        createWorkbookWithHeadersAndData(clustersInFile, xlsx, "2029-2030");

        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of("FR_DSR_industries"));

        doReturn(xlsx).when(service).getTrajectoryFilePath(anyString());

        UserInfoDto user = new UserInfoDto();
        user.setNni("TESTNNI");
        when(userService.getCurrentUserDetails()).thenReturn(user);

        TrajectoryEntity existing = new TrajectoryEntity();
        existing.setType(TrajectoryType.DSR_CAPACITY_MODULATION.name());
        existing.setFileName("cm_capacity_test");
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
        createWorkbookWithHeadersAndData(clustersInFile, xlsx, "2029-2030");

        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of("FR_DSR_industries"));

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
        createWorkbookWithOnlyHeaders(clusters, xlsx, "2029-2030");

        when(dsrRepository.findAllDsrClusterEntitiesByStudyId(1))
                .thenReturn(List.of("FR_DSR_industries"));

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

    // -------------------------------------------------------------------------
    // HELPERS EXCEL
    // -------------------------------------------------------------------------
    private Path createWorkbookWithOnlyHeaders(List<String> clusters, Path file, String horizon) throws IOException {
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
        return file;
    }

    private Path createWorkbookWithHeadersAndData(List<String> clusters, Path file, String horizon) throws IOException {
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
        return file;
    }
}
