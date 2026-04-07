package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.res.impl.ResGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                .resolve("alpha")
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
                .cluster("alpha")
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
        Map<String, Object> properties = (Map<String, Object>) cluster.get("properties");
        assertEquals("wind_onshore", properties.get("group"));
        assertEquals(3150.0, properties.get("capacity"));

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
        Map<String, Object> properties = (Map<String, Object>) cluster.get("properties");
        assertEquals("wind_offshore", properties.get("group"));

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
}

