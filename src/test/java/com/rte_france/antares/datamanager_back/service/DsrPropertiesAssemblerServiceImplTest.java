package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
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
import org.springframework.http.HttpStatus;
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
    private AntaressDataManagerProperties antaressDataManagerProperties;

    @Mock
    private NasFileService nasFileService;

    @InjectMocks
    private DsrPropertiesAssemblerServiceImpl dsrPropertiesAssemblerService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(dsrPropertiesAssemblerService, "antaressDataManagerProperties", antaressDataManagerProperties);
        ReflectionTestUtils.setField(dsrPropertiesAssemblerService, "nasFileService", nasFileService);

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaressDataManagerProperties.getDsrCapacityDirectory()).thenReturn("dsr_capacity");
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

        // Create file for modulation series
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "some_ts";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        Files.createFile(tsPath);

        // Mocking TS name finding in study trajectories
        DsrCapacityModulationEntity modulationEntity = DsrCapacityModulationEntity.builder().tsName(tsName).build();
        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(modulationEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(dsrTrajectory, modulationTrajectory))
                .build();

        when(antaressDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output");
        when(nasFileService.saveMatrixToNas(eq(tsPath), anyString())).thenReturn("saved_ts");

        // When
        Map<String, DsrGenerationDTO> result = dsrPropertiesAssemblerService.assembleDsrProperties(study);

        // Then
        assertNotNull(result.get("FR_ClusterModTrue").getDsrTsList());
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
    void createMatrixDsrTsFiles_ShouldNotDuplicateWhenSameNameAndChecksum() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "duplicate.txt";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        Files.createFile(tsPath);

        DsrCapacityModulationEntity mod1 = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .checksum("checksum123")
                .build();
        DsrCapacityModulationEntity mod2 = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .checksum("checksum123")
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(mod1, mod2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory))
                .build();

        when(antaressDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output");
        when(nasFileService.saveMatrixToNas(eq(tsPath), anyString())).thenReturn("unique_saved_filename.txt");

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertEquals(1, result.size());
        assertEquals("unique_saved_filename.txt", result.get(0));
        verify(nasFileService, times(1)).saveMatrixToNas(any(), anyString());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldDuplicateWhenSameNameButDifferentChecksum() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "duplicate.txt";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        Files.createFile(tsPath);

        DsrCapacityModulationEntity mod1 = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .checksum("checksum1")
                .build();
        DsrCapacityModulationEntity mod2 = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .checksum("checksum2")
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(mod1, mod2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory))
                .build();

        when(antaressDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output");
        when(nasFileService.saveMatrixToNas(eq(tsPath), anyString()))
                .thenReturn("saved1.txt")
                .thenReturn("saved2.txt");

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains("saved1.txt"));
        assertTrue(result.contains("saved2.txt"));
        verify(nasFileService, times(2)).saveMatrixToNas(any(), anyString());
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
    void createMatrixDsrTsFiles_ShouldSaveFilesWhenTheyExist() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "dsr_ts_1.txt";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        Files.createFile(tsPath);

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(modulation))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory))
                .build();

        when(antaressDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output/dsr_arrow");
        when(nasFileService.saveMatrixToNas(eq(tsPath), anyString())).thenReturn("saved_ts_1.txt");

        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study);

        // Then
        assertEquals(1, result.size());
        assertEquals("saved_ts_1.txt", result.get(0));
        verify(nasFileService).saveMatrixToNas(eq(tsPath), eq("output/dsr_arrow"));
    }

    @Test
    void createMatrixDsrTsFiles_ShouldThrowBusinessExceptionWhenFileNotFound() {
        // Given
        String tsName = "missing.txt";
        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(modulation))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory))
                .build();

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study));
        assertTrue(ex.getMessage().contains("Required DSR capacity modulation series file not found"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void createMatrixDsrTsFiles_ShouldThrowTechnicalExceptionOnIOException() throws IOException {
        // Given
        Path dsrCapacityDir = tempDir.resolve("trajectories").resolve("dsr_capacity");
        Files.createDirectories(dsrCapacityDir);
        String tsName = "dsr_ts_1.txt";
        Path tsPath = dsrCapacityDir.resolve(tsName);
        Files.createFile(tsPath);

        DsrCapacityModulationEntity modulation = DsrCapacityModulationEntity.builder()
                .tsName(tsName)
                .build();

        TrajectoryEntity modulationTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .dsrCapacityModulations(List.of(modulation))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(modulationTrajectory))
                .build();

        when(antaressDataManagerProperties.getDsrModulationTsOutputDirectory()).thenReturn("output/dsr_arrow");
        when(nasFileService.saveMatrixToNas(any(), anyString())).thenThrow(new IOException("NAS error"));

        // When & Then
        TechnicalException ex = assertThrows(TechnicalException.class, () -> dsrPropertiesAssemblerService.createMatrixDsrTsFiles(study));
        assertTrue(ex.getMessage().contains("Failed to save DSR arrow modulation file"));
    }

    @Test
    void createMatrixDsrTsFiles_ClusterVariant_ShouldReturnEmptyList() {
        // When
        List<String> result = dsrPropertiesAssemblerService.createMatrixDsrTsFiles(new DsrClusterEntity(), "2030");

        // Then
        assertTrue(result.isEmpty());
    }
}
