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
    void assembleResProperties_nonFrSubAreas_shouldResolveSeriesUsingExactAreaCode() throws IOException {
        Path itcsSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("solar thermo")
                .resolve("cluster")
                .resolve("solar_ITcs_thermo_2030-2031.csv");
        Path itcaSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("solar thermo")
                .resolve("cluster")
                .resolve("solar_ITca_thermo_2030-2031.csv");
        Files.createDirectories(itcsSeries.getParent());
        Files.writeString(itcsSeries, "v\n0.2\n");
        Files.writeString(itcaSeries, "v\n0.3\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("ITcs")
                .groupe("solar thermo")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(900))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        assertTrue(result.containsKey("ITCS"));
        @SuppressWarnings("unchecked")
        Map<String, Object> groupPayload = (Map<String, Object>) result.get("ITCS").get("solar_thermo");
        @SuppressWarnings("unchecked")
        List<String> series = (List<String>) groupPayload.get("series");
        assertEquals(List.of("solar_ITcs_thermo_2030-2031.csv.arrow"), series);
    }

    @Test
    void assembleResProperties_nonFr_shouldNotMixAreaPrefixesWhenResolvingSeries() throws IOException {
        Path itSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("cluster")
                .resolve("wind_IT_onshore_alpha_2030-2031.csv");
        Path itsSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("cluster")
                .resolve("wind_ITS_onshore_alpha_2030-2031.csv");
        Files.createDirectories(itSeries.getParent());
        Files.writeString(itSeries, "v\n0.2\n");
        Files.writeString(itsSeries, "v\n0.3\n");

        when(nasFileService.saveMatrixToNas(any(Path.class), eq("output"))).thenAnswer(inv ->
                ((Path) inv.getArgument(0)).getFileName().toString() + ".arrow");

        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        TrajectoryEntity resLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("IT")
                .groupe("wind onshore")
                .cluster("1")
                .capacityByYear(BigDecimal.valueOf(900))
                .build();
        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        @SuppressWarnings("unchecked")
        Map<String, Object> groupPayload = (Map<String, Object>) result.get("IT").get("wind_onshore");
        @SuppressWarnings("unchecked")
        List<String> series = (List<String>) groupPayload.get("series");
        assertEquals(List.of("wind_IT_onshore_alpha_2030-2031.csv.arrow"), series);
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
                .capacityByYear(100.0)
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
    void assembleResProperties_fr_shouldResolveSeriesWhenTechDistributionIncludesGroupPrefix() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("solar pv")
                .resolve("cluster")
                .resolve("solar_FR01_pv_utility non-tracking_2030-2031.csv");
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
                        .groupe("solar pv")
                        .cluster("global")
                        .capacityByYear(BigDecimal.valueOf(1000))
                        .build()))
                .build();

        TrajectoryEntity resZonal = TrajectoryEntity.builder()
                .type("RES_ZONAL_DISTRIBUTION")
                .resZonalDistributionCapacityEntities(List.of(ResZonalDistributionEntity.builder()
                        .area("FR")
                        .groupe("solar pv")
                        .pecdZone("FR01")
                        .capacityByYear(BigDecimal.valueOf(100))
                        .build()))
                .build();

        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(ResTechnologyDistributionEntity.builder()
                        .area("FR")
                        .groupe("solar pv")
                        .pecdZone("FR01")
                        .pecdTechnology("solar_pv_utility_non_tracking")
                        .capacityByYear(100.0)
                        .build()))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) result.get("FR").get("solar_pv");
        @SuppressWarnings("unchecked")
        Map<String, Object> frAggregation = (Map<String, Object>) cluster.get("fr_aggregation");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> seriesByZoneAndTech = (Map<String, Map<String, String>>) frAggregation.get("series_by_zone_and_tech");

        assertEquals(
                "solar_FR01_pv_utility non-tracking_2030-2031.csv.arrow",
                seriesByZoneAndTech.get("FR01").get("solar_pv_utility_non_tracking")
        );
    }

    @Test
    void assembleResProperties_fr_shouldKeepTechnologyTokenEndingWithYearWhenHorizonIsYearPair() throws IOException {
        Path csv = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind offshore")
                .resolve("cluster")
                .resolve("wind_FR01_offshore_tech_2025_2030_2031.csv");
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
                .pecdTechnology("tech 2025")
                .capacityByYear(100.0)
                .build();
        TrajectoryEntity resTech = TrajectoryEntity.builder()
                .type("RES_TECHNOLOGY_DISTRIBUTION")
                .resTechnologyDistributionCapacityEntities(List.of(tech))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        Map<String, Map<String, Object>> result = service.assembleResProperties(study);

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) result.get("FR").get("wind_offshore");
        @SuppressWarnings("unchecked")
        Map<String, Object> frAggregation = (Map<String, Object>) cluster.get("fr_aggregation");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> seriesByZoneAndTech = (Map<String, Map<String, String>>) frAggregation.get("series_by_zone_and_tech");

        assertEquals("wind_FR01_offshore_tech_2025_2030_2031.csv.arrow", seriesByZoneAndTech.get("FR01").get("tech_2025"));
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
                .capacityByYear(100.0)
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
    void assembleResProperties_fr_withZeroInstalledPowerWithoutDistribution_shouldNotThrow() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();

        ResClusterCapacityEntity capacity = ResClusterCapacityEntity.builder()
                .toUse(true)
                .area("FR")
                .groupe("wind offshore")
                .cluster("global")
                .capacityByYear(BigDecimal.ZERO)
                .build();

        TrajectoryEntity resCapacity = TrajectoryEntity.builder()
                .type("RES_CAPACITY")
                .resClusterCapacityEntities(List.of(capacity))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resCapacity)));

        Map<String, Map<String, Object>> result = assertDoesNotThrow(() -> service.assembleResProperties(study));

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) result.get("FR").get("wind_offshore");
        @SuppressWarnings("unchecked")
        Map<String, Object> frAggregation = (Map<String, Object>) cluster.get("fr_aggregation");
        assertNotNull(frAggregation);
        assertTrue(((Map<?, ?>) frAggregation.get("zone_weights")).isEmpty());
        assertTrue(((Map<?, ?>) frAggregation.get("tech_weights_by_zone")).isEmpty());
        assertTrue(((Map<?, ?>) frAggregation.get("series_by_zone_and_tech")).isEmpty());
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
    void assembleResProperties_shouldReturnEmpty_whenTrajectoriesIsEmpty() {
        StudyEntity study = StudyEntity.builder().id(1).name("S").build();
        study.setTrajectories(new LinkedHashSet<>());

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
                        .capacityByYear(100.0)
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
                        .capacityByYear(100.0)
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
    void assembleResProperties_shouldIgnoreFilesUnderOldDirectory() throws IOException {
        Path validSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("1")
                .resolve("wind_DE_onshore_alpha_2030-2031.csv");
        Path archivedSeries = tempDir
                .resolve("INPUT")
                .resolve("RES/load factor")
                .resolve("BP23_A_ref")
                .resolve("wind onshore")
                .resolve("old")
                .resolve("wind_DE_onshore_beta_2030-2031.csv");
        Files.createDirectories(validSeries.getParent());
        Files.createDirectories(archivedSeries.getParent());
        Files.writeString(validSeries, "v\n0.2\n");
        Files.writeString(archivedSeries, "v\n0.3\n");

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
    void assembleResProperties_shouldSkipBlankResLoadTrajectoryFilename() throws IOException {
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
        TrajectoryEntity blankResLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName(" ").build();
        TrajectoryEntity validResLoad = TrajectoryEntity.builder().type("RES_LOAD").fileName("BP23_A_ref").build();
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
        study.setTrajectories(new LinkedHashSet<>(List.of(blankResLoad, validResLoad, resCapacity)));

        assertDoesNotThrow(() -> service.assembleResProperties(study));
        verify(nasFileService, times(1)).saveMatrixToNas(any(Path.class), eq("output"));
    }

    @Test
    void assembleResProperties_fr_shouldIgnoreTechnologyRowsFromOtherGroup() throws IOException {
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
                                .capacityByYear(100.0)
                                .build(),
                        ResTechnologyDistributionEntity.builder()
                                .area("FR")
                                .groupe("solar pv")
                                .pecdZone("FR01")
                                .pecdTechnology("tech_should_be_ignored")
                                .capacityByYear(100.0)
                                .build()
                ))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        assertDoesNotThrow(() -> service.assembleResProperties(study));
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
                                .capacityByYear(60.0)
                                .build(),
                        ResTechnologyDistributionEntity.builder()
                                .area("FR")
                                .groupe("wind offshore")
                                .pecdZone("FR01")
                                .pecdTechnology("tech_b")
                                .capacityByYear(40.0)
                                .build()
                ))
                .build();
        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        assertTrue(exception.getMessage().contains("series resolution must return exactly one arrow"));
    }

    @Test
    void assembleResProperties_fr_shouldIgnoreTechnologyWithZeroWeightEvenIfMissingSeriesFile() throws IOException {
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
                                .capacityByYear(100.0)  // Has weight: must have series
                                .build(),
                        ResTechnologyDistributionEntity.builder()
                                .area("FR")
                                .groupe("wind offshore")
                                .pecdZone("FR01")
                                .pecdTechnology("tech_b_missing")
                                .capacityByYear(0.0)  // Zero weight: no need for series file
                                .build()
                ))
                .build();

        study.setTrajectories(new LinkedHashSet<>(List.of(resLoad, resCapacity, resZonal, resTech)));

        // Should succeed because tech_b_missing has 0 weight (file not needed)
        Map<String, Map<String, Object>> result = assertDoesNotThrow(() -> service.assembleResProperties(study));

        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) result.get("FR").get("wind_offshore");
        @SuppressWarnings("unchecked")
        Map<String, Object> frAggregation = (Map<String, Object>) cluster.get("fr_aggregation");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> seriesByZoneAndTech = (Map<String, Map<String, String>>) frAggregation.get("series_by_zone_and_tech");

        // Only tech_a should have a series, tech_b_missing should not be in the map
        assertEquals(1, seriesByZoneAndTech.get("FR01").size());
        assertTrue(seriesByZoneAndTech.get("FR01").containsKey("tech_a"));
        assertFalse(seriesByZoneAndTech.get("FR01").containsKey("tech_b_missing"));
    }

    // ...existing code...
}
