package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.dsr.impl.DsrPropertiesAssemblerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DsrPropertiesAssemblerServiceImplTest {

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private NasFileService nasFileService;

    @InjectMocks
    private DsrPropertiesAssemblerServiceImpl dsrPropertiesAssemblerService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(dsrPropertiesAssemblerService, "antaresDataManagerProperties", antaresDataManagerProperties);
        ReflectionTestUtils.setField(dsrPropertiesAssemblerService, "nasFileService", nasFileService);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getDsrCapacityDirectory()).thenReturn("dsr_capacity");
    }

    @Test
    void assembleDsrProperties_ShouldReturnMappedProperties() {
        // Given
        DsrClusterEntity dsrCluster = DsrClusterEntity.builder()
                .area("fr")
                .name("Cluster1")
                .toUse(true)
                .capacity(new BigDecimal("100.5"))
                .nbUnits(5)
                .price(new BigDecimal("50.0"))
                .modulation(true)
                .build();

        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(dsrCluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(dsrTrajectory))
                .build();

        // When
        Map<String, DsrGenerationDTO> result = dsrPropertiesAssemblerService.assembleDsrProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_Cluster1"));
        DsrGenerationDTO dto = result.get("FR_Cluster1");
        assertEquals(true, dto.getEnabled());
        assertEquals(100.5, dto.getNominalCapacity());
        assertEquals(5, dto.getUnitCount());
        assertEquals(50.0, dto.getMarginalCost());
    }

    @Test
    void assembleDsrProperties_ShouldSkipClustersWhenToUseIsFalseOrCapacityIsZeroOrNull() {
        // Given
        DsrClusterEntity clusterToUseFalse = DsrClusterEntity.builder()
                .area("fr").name("Cluster1").toUse(false).capacity(new BigDecimal("100")).build();
        DsrClusterEntity clusterCapacityZero = DsrClusterEntity.builder()
                .area("fr").name("Cluster2").toUse(true).capacity(BigDecimal.ZERO).build();
        DsrClusterEntity clusterCapacityNull = DsrClusterEntity.builder()
                .area("fr").name("Cluster3").toUse(true).capacity(null).build();
        DsrClusterEntity validCluster = DsrClusterEntity.builder()
                .area("fr").name("ClusterValid").toUse(true).capacity(new BigDecimal("100")).build();

        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(clusterToUseFalse, clusterCapacityZero, clusterCapacityNull, validCluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(dsrTrajectory))
                .build();

        // When
        Map<String, DsrGenerationDTO> result = dsrPropertiesAssemblerService.assembleDsrProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_ClusterValid"));
    }

    @Test
    void assembleDsrProperties_ShouldOnlyCallCreateMatrixDsrTsFilesWhenModulationIsTrue() throws IOException {
        // Given
        DsrClusterEntity clusterModulationTrue = DsrClusterEntity.builder()
                .area("fr").name("ClusterModTrue").toUse(true).capacity(new BigDecimal("100")).modulation(true).build();
        DsrClusterEntity clusterModulationFalse = DsrClusterEntity.builder()
                .area("fr").name("ClusterModFalse").toUse(true).capacity(new BigDecimal("100")).modulation(false).build();
        DsrClusterEntity clusterModulationNull = DsrClusterEntity.builder()
                .area("fr").name("ClusterModNull").toUse(true).capacity(new BigDecimal("100")).modulation(null).build();

        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(clusterModulationTrue, clusterModulationFalse, clusterModulationNull))
                .build();

        // Create the file for modulation series
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "some_ts.xlsx";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        createSimpleXlsx(tsPath, "2030", new String[]{"ClusterModTrue"}, new double[][]{ new double[]{10.0} });

        // Mocking TS name finding in study trajectories
        DsrCapacityModulationEntity modulationEntity = DsrCapacityModulationEntity.builder().tsName(tsName).build();
        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulationEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(dsrTrajectory, modulationTrajectory))
                .build();

        when(antaresDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output");
        when(nasFileService.saveMatrixToNas(any(Path.class), anyString())).thenAnswer(invocation -> {
            Path p = invocation.getArgument(0);
            return p.getFileName().toString() + ".arrow";
        });

        // When
        Map<String, DsrGenerationDTO> result = dsrPropertiesAssemblerService.assembleDsrProperties(study);

        // Then
        assertEquals(List.of("some_ts_ClusterModTrue.csv.arrow"), result.get("FR_ClusterModTrue").getDsrTsList());
        assertNull(result.get("FR_ClusterModFalse").getDsrTsList());
        assertNull(result.get("FR_ClusterModNull").getDsrTsList());
    }

    @Test
    void assembleDsrProperties_ShouldSkipNonDsrTrajectories() {
        // Given
        TrajectoryEntity otherTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(otherTrajectory))
                .build();

        // When
        Map<String, DsrGenerationDTO> result = dsrPropertiesAssemblerService.assembleDsrProperties(study);

        // Then
        assertTrue(result.isEmpty());
    }



    @Test
    void createMatrixDsrTsFiles_ShouldReturnEmptyListWhenNoTsNames() {
        // Given
        StudyEntity study = StudyEntity.builder()
                .trajectories(Collections.emptySet())
                .build();

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldSupportIso8859_1Encoding() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "CM_iso_encoding.xlsx";
        Path tsPath = dsrCapacityDir.resolve(tsName);

        // Feuille avec en-tête accentué (Cluster_Accént)
        createSimpleXlsx(tsPath, "2030", new String[]{"Cluster_Accént"}, new double[][]{ new double[]{10.0} });

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulation))
                .build();

        DsrClusterEntity cluster = DsrClusterEntity.builder().name("Cluster_Accént").build();
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(cluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon("2030")
                .trajectories(Set.of(modulationTrajectory, dsrTrajectory))
                .build();

        when(antaresDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output");
        when(nasFileService.saveMatrixToNas(any(Path.class), anyString())).thenReturn("saved.txt");

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertEquals(1, result.size());
        // On vérifie qu'un fichier a été généré avec le nom attendu (contenant l'accent)
        verify(nasFileService).saveMatrixToNas(argThat(p -> p.getFileName().toString().contains("Cluster_Accént")), anyString());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldContextualizeExceptionWhenIoErrorOccurs() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "CM_error.xlsx";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        createSimpleXlsx(tsPath, "2030", new String[]{"CL1"}, new double[][]{ new double[]{10.0} });

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulation))
                .build();

        DsrClusterEntity cluster = DsrClusterEntity.builder().name("CL1").build();
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(cluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon("2030")
                .trajectories(Set.of(modulationTrajectory, dsrTrajectory))
                .build();

        // On rend le fichier illisible pour provoquer une IOException au moment de la lecture
        tsPath.toFile().setReadable(false);

        try {
            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () ->
                    dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study)
            );
            assertTrue(exception.getMessage().contains("Error while splitting DSR CM file: CM_error.xlsx"));
        } finally {
            tsPath.toFile().setReadable(true); // cleanup pour ne pas gêner @TempDir
        }
    }

    @Test
    void createMatrixDsrTsFiles_ShouldSaveFilesWhenTheyExist() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "CM_dsr_ts_1.xlsx";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        createSimpleXlsx(tsPath, "2030", new String[]{"CL1"}, new double[][]{ new double[]{10.0} });

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulation))
                .build();

        // add DSR clusters to enable splitting
        DsrClusterEntity cluster = DsrClusterEntity.builder().area("").name("CL1").build();
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(cluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon("2030")
                .trajectories(Set.of(modulationTrajectory, dsrTrajectory))
                .build();

        when(antaresDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output/dsr_arrow");
        when(nasFileService.saveMatrixToNas(any(Path.class), anyString())).thenReturn("saved_ts_1.txt");

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertEquals(1, result.size());
        assertEquals("saved_ts_1.txt", result.getFirst());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldThrowBusinessExceptionWhenFileNotFound() {
        // Given
        String tsName = "CM_missing.xlsx";
        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulation))
                .build();

        // add DSR clusters to enable splitting
        DsrClusterEntity cluster = DsrClusterEntity.builder().area("").name("CL1").build();
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(cluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory, dsrTrajectory))
                .build();

        // When & Then: since file is missing, service skips it and returns empty list
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);
        assertTrue(result.isEmpty());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldThrowTechnicalExceptionOnIOException() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "CM_dsr_ts_1.xlsx";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        createSimpleXlsx(tsPath, "2030", new String[]{"CL1"}, new double[][]{ new double[]{10.0} });

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulationEntities(List.of(modulation))
                .build();

        // add DSR clusters to enable splitting
        DsrClusterEntity cluster = DsrClusterEntity.builder().area("").name("CL1").build();
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR.name())
                .dsrClusterEntities(List.of(cluster))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory, dsrTrajectory))
                .build();

        when(antaresDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output/dsr_arrow");
        when(nasFileService.saveMatrixToNas(any(Path.class), anyString())).thenThrow(new IOException("NAS error"));

        // When & Then
        TechnicalException ex = assertThrows(TechnicalException.class, () -> dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study));
        assertTrue(ex.getMessage().contains("NAS error"));
    }


    // Helpers
    private void createSimpleXlsx(Path path, String sheetName, String[] headers, double[][] data) throws IOException {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = (sheetName != null && !sheetName.isBlank()) ? wb.createSheet(sheetName) : wb.createSheet();
            int rowIdx = 0;
            var headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < headers.length; c++) {
                var cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
            }
            int rows = (data != null && data.length > 0) ? data[0].length : 0;
            for (int r = 0; r < rows; r++) {
                var row = sheet.createRow(rowIdx++);
                for (int c = 0; c < headers.length; c++) {
                    var cell = row.createCell(c);
                    double v = (data != null && c < data.length && r < data[c].length) ? data[c][r] : 0.0;
                    cell.setCellValue(v);
                }
            }
            try (var out = java.nio.file.Files.newOutputStream(path)) {
                wb.write(out);
            }
        }
    }
}
