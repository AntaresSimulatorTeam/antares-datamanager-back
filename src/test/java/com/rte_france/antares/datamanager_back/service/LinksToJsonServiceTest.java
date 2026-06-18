package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.study.impl.LinksToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LinksToJsonServiceTest {

    private LinksToJsonService linksToJsonService;

    @BeforeEach
    void setUp() {
        linksToJsonService = new LinksToJsonService();
    }

    @Test
    void buildLinksDataMap_shouldMapBasicFields() {
        // Given
        LinkEntity link = LinkEntity.builder()
                .name("area1-area2")
                .winterHpDirectMw(100.0)
                .winterHpIndirectMw(110.0)
                .winterHcDirectMw(120.0)
                .winterHcIndirectMw(130.0)
                .summerHpDirectMw(140.0)
                .summerHpIndirectMw(150.0)
                .summerHcDirectMw(160.0)
                .summerHcIndirectMw(170.0)
                .hurdleCost(5.0)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName("links_test")
                .linkEntities(List.of(link))
                .build();

        StudyEntity study = StudyEntity.builder().hvdc(false).build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        assertThat(linksMap).containsKey("area1/area2");
        Map<String, Object> linkData = (Map<String, Object>) linksMap.get("area1/area2");
        assertThat(linkData)
                .containsEntry("winterHpDirectMw", 100.0)
                .containsEntry("winterHpIndirectMw", 110.0)
                .containsEntry("winterHcDirectMw", 120.0)
                .containsEntry("winterHcIndirectMw", 130.0)
                .containsEntry("summerHpDirectMw", 140.0)
                .containsEntry("summerHpIndirectMw", 150.0)
                .containsEntry("summerHcDirectMw", 160.0)
                .containsEntry("summerHcIndirectMw", 170.0)
                .containsEntry("hurdleCost", 5.0);

        assertThat(linkData).doesNotContainKeys(
                "hvdcMwDirect", "hvdcMwIndirect", "hvdcNbDirect",
                "hvdcNbIndirect", "hvdcFoRateDirect", "hvdcFoRateIndirect"
        );
    }

    @Test
    void buildLinksDataMap_withHvdcTrue_shouldMapHvdcFields() {
        // Given
        LinkEntity link = LinkEntity.builder()
                .name("area1-area2")
                .hvdcMwDirect(10.0)
                .hvdcMwIndirect(11.0)
                .hvdcNbDirect(1.0)
                .hvdcNbIndirect(1.0)
                .hvdcFoRateDirect(0.01)
                .hvdcFoRateIndirect(0.02)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .linkEntities(List.of(link))
                .build();

        StudyEntity study = StudyEntity.builder().hvdc(true).build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        Map<String, Object> linkData = (Map<String, Object>) linksMap.get("area1/area2");
        assertThat(linkData)
                .containsEntry("hvdcMwDirect", 10.0)
                .containsEntry("hvdcMwIndirect", 11.0)
                .containsEntry("hvdcNbDirect", 1.0)
                .containsEntry("hvdcNbIndirect", 1.0)
                .containsEntry("hvdcFoRateDirect", 0.01)
                .containsEntry("hvdcFoRateIndirect", 0.02);
    }

    @Test
    void buildLinksDataMap_withNullLinkEntities_shouldNotCrash() {
        // Given
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName("empty_test")
                .linkEntities(null)
                .build();

        StudyEntity study = StudyEntity.builder().build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        assertThat(linksMap).isEmpty();
    }

    @Test
    void buildLinksDataMap_withStudyHvdcNull_shouldNotMapHvdcFields() {
        // Given
        LinkEntity link = LinkEntity.builder()
                .name("A-B")
                .hvdcMwDirect(10.0)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .linkEntities(List.of(link))
                .build();

        StudyEntity study = StudyEntity.builder().hvdc(null).build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        Map<String, Object> linkData = (Map<String, Object>) linksMap.get("A/B");
        assertThat(linkData).doesNotContainKey("hvdcMwDirect");
    }

    @Test
    void buildLinksDataMap_shouldHandleDashInNames() {
        // Given
        LinkEntity link = LinkEntity.builder().name("AREA-NORTH-AREA-SOUTH").build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().linkEntities(List.of(link)).build();
        StudyEntity study = StudyEntity.builder().build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        assertThat(linksMap).containsKey("AREA/NORTH/AREA/SOUTH");
    }

    @Test
    void buildLinksDataMap_withDuplicateLinkNames_shouldKeepFirst() {
        // Given
        LinkEntity link1 = LinkEntity.builder().name("A-B").winterHpDirectMw(100.0).build();
        LinkEntity link2 = LinkEntity.builder().name("A-B").winterHpDirectMw(200.0).build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .linkEntities(List.of(link1, link2))
                .build();

        StudyEntity study = StudyEntity.builder().build();
        Map<String, Object> linksMap = new HashMap<>();

        // When
        linksToJsonService.buildLinksDataMap(trajectory, linksMap, study);

        // Then
        assertThat(linksMap).hasSize(1);
        Map<String, Object> linkData = (Map<String, Object>) linksMap.get("A/B");
        assertThat(linkData).containsEntry("winterHpDirectMw", 100.0);
    }
}
