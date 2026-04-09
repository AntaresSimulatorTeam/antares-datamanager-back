package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.res.impl.ResGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResGenerationAssemblerServiceImplTest {

    @TempDir
    Path tempDir;

    private AntaresDataManagerProperties properties;
    private NasFileService nasFileService;
    private ResGenerationAssemblerServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AntaresDataManagerProperties();
        properties.nasDirectory = tempDir.toString();
        properties.trajectoryFilePath = "INPUT";
        properties.resLoadDirectory = "RES/load factor";
        properties.outputLoadDirectory = "output";

        nasFileService = mock(NasFileService.class);
        service = new ResGenerationAssemblerServiceImpl(nasFileService, properties, new PathSecurityUtil(properties));
    }

    @Test
    void assembleResProperties_nonFr_shouldReturnSingleSeriesPerGroup() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.2\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("DE")
                .groupe("wind onshore")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(3150))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        assertTrue(result.containsKey("DE"));
        Map<String, Object> groups = result.get("DE");
        assertTrue(groups.containsKey("wind_onshore"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) groups.get("wind_onshore");
        @SuppressWarnings("unchecked")
        Map<String, Object> clusterProperties = (Map<String, Object>) cluster.get("properties");
        assertEquals("wind_onshore", clusterProperties.get("group"));
        assertEquals(3150.0, clusterProperties.get("capacity"));

        @SuppressWarnings("unchecked")
        List<String> series = (List<String>) cluster.get("series");
        assertEquals(1, series.size());
    }

    @Test
    void assembleResProperties_fr_shouldReturnOptionCFrAggregation() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("FR")
                .groupe("wind offshore")
                .cluster("global")
                .capacityByYear(BigDecimal.valueOf(18500))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        ResZonalDistributionEntity zonal = ResZonalDistributionEntity.builder()
                .area("FR")
                .groupe("wind offshore")
                .pecdZone("FR01")
                .capacityByYear(BigDecimal.valueOf(60))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(zonal))
                .build();

        ResTechnologyDistributionEntity tech = ResTechnologyDistributionEntity.builder()
                .area("FR")
                .groupe("wind offshore")
                .pecdZone("FR01")
                .pecdTechnology("tech_a")
                .capacityByYear(100)
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(tech))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        Map<String, Object> frClusters = result.get("FR");
        assertNotNull(frClusters);

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) frClusters.get("wind_offshore");
        @SuppressWarnings("unchecked")
        Map<String, Object> clusterProperties = (Map<String, Object>) cluster.get("properties");
        assertEquals("wind_offshore", clusterProperties.get("group"));

        @SuppressWarnings("unchecked")
        List<String> series = (List<String>) cluster.get("series");
        assertTrue(series.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Object> frAggregation = (Map<String, Object>) cluster.get("fr_aggregation");
        assertNotNull(frAggregation);
        assertTrue(frAggregation.containsKey("zone_weights"));
        assertTrue(frAggregation.containsKey("tech_weights_by_zone"));
        assertTrue(frAggregation.containsKey("series_by_zone_and_tech"));
    }

    @Test
    void assembleResProperties_fr_withInvalidZone_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("FR")
                .groupe("wind offshore")
                .cluster("global")
                .capacityByYear(BigDecimal.valueOf(18500))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        ResZonalDistributionEntity zonal = ResZonalDistributionEntity.builder()
                .area("FR")
                .groupe("wind offshore")
                .pecdZone("XX01")
                .capacityByYear(BigDecimal.valueOf(60))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(zonal))
                .build();

        ResTechnologyDistributionEntity tech = ResTechnologyDistributionEntity.builder()
                .area("FR")
                .groupe("wind offshore")
                .pecdZone("FR01")
                .pecdTechnology("tech_a")
                .capacityByYear(100)
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(tech))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
    }

    @Test
    void assembleResProperties_shouldReturnEmpty_whenNoResCapacityTrajectory() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        study.setTrajectories(new LinkedHashSet<>(List.of(
                TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build()
        )));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        assertTrue(result.isEmpty());
    }

    @Test
    void assembleResProperties_withUnsupportedGroup_shouldThrowBusinessException() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("DE")
                .groupe("wind")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(1000))
                .build();

        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resCapacity)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Unsupported RES group"));
    }

    @Test
    void assembleResProperties_nonFr_withMultipleMatchingSeries_shouldThrow() throws IOException {
        Path csv1 = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Path csv2 = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("beta")
                .resolve("wind_DE_onshore_beta_2030-2031.csv");
        Files.createDirectories(csv1.getParent());
        Files.createDirectories(csv2.getParent());
        Files.writeString(csv1, "v\n0.2\n");
        Files.writeString(csv2, "v\n0.3\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("DE")
                .groupe("wind onshore")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(3150))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("exactly one arrow"));
    }

    @Test
    void assembleResProperties_fr_withoutDistribution_shouldThrow() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("FR")
                .groupe("wind offshore")
                .cluster("global")
                .capacityByYear(BigDecimal.valueOf(18500))
                .build();

        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resCapacity)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Missing FR aggregation data"));
    }

    @Test
    void assembleResProperties_shouldWrapIOExceptionFromArrowGeneration() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.2\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenThrow(new IOException("disk full"));

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("DE")
                .groupe("wind onshore")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(3150))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        TechnicalException exception = assertThrows(TechnicalException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Could not generate RES arrow file"));
    }

    @Test
    void assembleResProperties_shouldReturnEmpty_whenTrajectoriesIsNull() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        study.setTrajectories(null);

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        assertTrue(result.isEmpty());
    }

    @Test
    void assembleResProperties_shouldThrowWhenResLoadPathIsInvalid() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("missing_folder").build();
        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("DE")
                .groupe("wind onshore")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(3150))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Invalid RES load trajectory path"));
    }

    @Test
    void assembleResProperties_fr_withZeroZoneSum_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.ZERO)
                        .build()))
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(ResTechnologyDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .pecdTechnology("tech_a")
                        .capacityByYear(100)
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("zone weights sum must be strictly positive"));
    }

    @Test
    void assembleResProperties_fr_withMissingTechnologyForZonalZone_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR02_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.valueOf(50))
                        .build()))
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(ResTechnologyDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR02")
                        .pecdTechnology("tech_a")
                        .capacityByYear(100)
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Missing FR technology mapping for zone FR01"));
    }

    @Test
    void assembleResProperties_fr_withNullZonalWeight_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(null)
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Missing RES zonal weight"));
    }

    @Test
    void assembleResProperties_shouldIgnoreLockFileDuringArrowGeneration() throws IOException {
        Path lockFile = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve(".~lock.wind_DE_onshore_alpha_2030-2031.csv");
        Path csv = lockFile.getParent().resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Files.createDirectories(lockFile.getParent());
        Files.writeString(lockFile, "lock");
        Files.writeString(csv, "v\n0.2\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("DE")
                        .groupe("wind onshore")
                        .cluster("1")
                        .capacityByYear(BigDecimal.valueOf(3150))
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        assertDoesNotThrow(() -> service.assembleResProperties(study));
        verify(nasFileService, times(1)).saveMatrixToNas(any(Path.class), eq("output"));
    }

    @Test
    void assembleResProperties_shouldIgnoreSupportedExtensionFileWithoutGroupPrefix() throws IOException {
        Path notes = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve("metadata_notes.txt");
        Path validSeries = notes.getParent().resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Files.createDirectories(notes.getParent());
        Files.writeString(notes, "metadata");
        Files.writeString(validSeries, "v\n0.2\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("DE")
                        .groupe("wind onshore")
                        .cluster("1")
                        .capacityByYear(BigDecimal.valueOf(3150))
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        assertDoesNotThrow(() -> service.assembleResProperties(study));
        verify(nasFileService, times(1)).saveMatrixToNas(any(Path.class), eq("output"));
    }

    @Test
    void assembleResProperties_fr_withMissingSeriesForOneTechnology_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.valueOf(100))
                        .build()))
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(
                        ResTechnologyDistributionEntity.builder()
                                .area("FR")
                                .groupe("wind offshore")
                                .pecdZone("FR01")
                                .pecdTechnology("tech_a")
                                .capacityByYear(60)
                                .build(),
                        ResTechnologyDistributionEntity.builder()
                                .area("FR")
                                .groupe("wind offshore")
                                .pecdZone("FR01")
                                .pecdTechnology("tech_b")
                                .capacityByYear(40)
                                .build()
                ))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("series resolution must return exactly one arrow"));
    }

    @Test
    void assembleResProperties_fr_withZeroTechnologyWeightsSum_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.valueOf(100))
                        .build()))
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(ResTechnologyDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .pecdTechnology("tech_a")
                        .capacityByYear(0)
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("technology weights sum must be strictly positive"));
    }

    @Test
    void assembleResProperties_fr_withNegativeZonalWeight_shouldThrow() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_a_2030-2031.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, "v\n0.4\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                        .toUse(true)
                        .area("FR")
                        .groupe("wind offshore")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(18500))
                        .build()))
                .build();
        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("wind offshore")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.valueOf(-10))
                        .build()))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("Negative RES zonal weight is forbidden"));
    }

    @Test
    void assembleResProperties_shouldWrapIOExceptionWhenListingResLoadFiles() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));

        Path trajectoryRoot = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref");
        Files.createDirectories(trajectoryRoot);

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(trajectoryRoot);
        try {
            Files.setPosixFilePermissions(trajectoryRoot, PosixFilePermissions.fromString("---------"));

            StudyEntity study = StudyEntity.builder().id(1).name("S").build();
            TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
            TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                    .type("RES_CAPACITY")
                    .resClusterCapacityEntities(List.of(ResClusterCapacityEntity.builder()
                            .toUse(true)
                            .area("DE")
                            .groupe("wind onshore")
                            .cluster("1")
                            .capacityByYear(BigDecimal.valueOf(3150))
                            .build()))
                    .build();
            study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

            TechnicalException exception = assertThrows(TechnicalException.class, () -> service.assembleResProperties(study));
            assertTrue(exception.getMessage().contains("Could not list RES load trajectory files"));
        } finally {
            Files.setPosixFilePermissions(trajectoryRoot, originalPermissions);
        }
    }

    @Test
    void validateFrContract_withAmbiguousPayload_shouldThrow() throws Exception {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                invokePrivate(
                        "validateFrContract",
                        new Class[]{List.class, Map.class, String.class},
                        List.of("series.arrow"),
                        Map.of("zone_weights", Map.of("FR01", 1.0)),
                        "wind_offshore"
                )
        );
        assertTrue(exception.getMessage().contains("use either series or fr_aggregation, not both"));
    }

    @Test
    void validateFrAggregation_withTechnologyKeysMismatch_shouldThrow() throws Exception {
        Map<String, Double> zoneWeights = new LinkedHashMap<>();
        zoneWeights.put("FR01", 1.0);

        Map<String, Map<String, Double>> techWeightsByZone = new LinkedHashMap<>();
        techWeightsByZone.put("FR01", Map.of("tech_a", 1.0));

        Map<String, Map<String, String>> seriesByZoneAndTech = new LinkedHashMap<>();
        seriesByZoneAndTech.put("FR01", Map.of("tech_b", "fr01_tech_b.arrow"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                invokePrivate(
                        "validateFrAggregation",
                        new Class[]{String.class, Map.class, Map.class, Map.class},
                        "wind_offshore",
                        zoneWeights,
                        techWeightsByZone,
                        seriesByZoneAndTech
                )
        );
        assertTrue(exception.getMessage().contains("technology keys mismatch"));
    }

    @Test
    void prefixFromGroup_withBlankValue_shouldThrow() throws Exception {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                invokePrivate("prefixFromGroup", new Class[]{String.class}, "   "));
        assertTrue(exception.getMessage().contains("Invalid RES group value for series prefix"));
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ResGenerationAssemblerServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }
}

