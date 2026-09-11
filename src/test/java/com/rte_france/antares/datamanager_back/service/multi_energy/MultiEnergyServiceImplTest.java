package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.service.multi_energy.impl.MultiEnergyServiceImpl;
import com.rte_france.antares.datamanager_back.service.study.impl.LoadToJsonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiEnergyServiceImplTest {

    @Mock
    private AdequacySettingsAssemblerService adequacySettingsAssemblerService;

    @Mock
    private LoadToJsonService loadToJsonService;

    @InjectMocks
    private MultiEnergyServiceImpl multiEnergyService;

    private StudyEntity studyEntity;
    private TrajectoryEntity areaMeTrajectory;
    private TrajectoryEntity linkMeTrajectory;

    @BeforeEach
    void setUp() {
        AreaEntity areaEntity = AreaEntity.builder()
                .name("area_me")
                .x(1.0)
                .y(2.0)
                .r(1.0)
                .g(2.0)
                .b(3.0)
                .build();

        AreaConfigEntity areaConfigEntity = AreaConfigEntity.builder()
                .district("district_me")
                .unsuppliedEnergyCost(4000.0)
                .spilledEnergyCost(200.0)
                .area(areaEntity)
                .build();

        areaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfigEntity))
                .build();

        LinkMeEntity linkMeEntity = LinkMeEntity.builder()
                .nodeFrom("ME")
                .nodeTo("FR")
                .directMw(1200.0)
                .indirectMw(1300.0)
                .hurdleCostsDirect(0.1)
                .hurdleCostsIndirect(0.3)
                .build();

        linkMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LINK_ME.name())
                .fileName("link_me.xlsx")
                .linkMeEntities(List.of(linkMeEntity))
                .build();

        studyEntity = StudyEntity.builder()
                .id(1)
                .name("testStudy")
                .trajectories(Set.of(areaMeTrajectory, linkMeTrajectory))
                .build();
    }

    @Test
    void buildMultiEnergyMap_withAdequacyMode_shouldReturnExpectedStructure() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("area_me", "outside"));
        when(adequacySettingsAssemblerService.resolveMode(eq("area_me"), any(), any()))
                .thenReturn(Optional.of("outside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areas.get("area_me");
        assertThat(areaData).containsEntry("ui", "AreaUI class as JSON");
        assertThat(areaData).containsKey("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) areaData.get("properties");
        assertThat(properties)
                .containsEntry("energy_cost_unsupplied", 4000.0)
                .containsEntry("energy_cost_spilled", 200.0)
                .containsEntry("adequacy_patch_mode", "outside");
    }

    @Test
    void buildMultiEnergyMap_withoutAdequacyMode_shouldReturnNullAdequacyPatchMode() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areas.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) areaData.get("properties");

        assertThat(properties)
                .containsEntry("energy_cost_unsupplied", 4000.0)
                .containsEntry("energy_cost_spilled", 200.0);
    }


    @Test
    void buildMultiEnergyMap_whenTrajectoryOrConfigsNull_shouldReturnEmptyMap() {
        assertThat(multiEnergyService.buildMultiEnergyMap(studyEntity, null)).isEmpty();

        TrajectoryEntity emptyTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(null)
                .build();
        assertThat(multiEnergyService.buildMultiEnergyMap(studyEntity, emptyTrajectory)).isEmpty();

        TrajectoryEntity emptyLinkMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(null)
                .build();
        assertThat(multiEnergyService.buildMultiEnergyMap(studyEntity, emptyLinkMeTrajectory)).isEmpty();
    }

    @Test
    void buildMultiEnergyMap_multipleAreas_shouldReturnAllAreas() {
        // Given
        AreaEntity area1 = AreaEntity.builder().name("AREA1_ME").build();
        AreaEntity area2 = AreaEntity.builder().name("AREA2_ME").build();

        AreaConfigEntity config1 = AreaConfigEntity.builder()
                .area(area1)
                .unsuppliedEnergyCost(3000.0)
                .spilledEnergyCost(100.0)
                .build();

        AreaConfigEntity config2 = AreaConfigEntity.builder()
                .area(area2)
                .unsuppliedEnergyCost(5000.0)
                .spilledEnergyCost(300.0)
                .build();

        TrajectoryEntity multipleAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config1, config2))
                .build();
        AdequacyModeEntity adequacyModeAreaMe = AdequacyModeEntity.builder().area("area1_me").mode("inside").build();
        TrajectoryEntity adequacyTrajectory = TrajectoryEntity.builder()
                .type("ADEQUACY_PATCH")
                .fileName("adq.xlsx")
                .adequacyModeEntities(List.of(adequacyModeAreaMe, AdequacyModeEntity.builder().area("AREA1_ME").mode("inside").build()))
                .adequacySettingsEntities(Collections.emptyList())
                .build();

        when(adequacySettingsAssemblerService.findAdequacyTrajectory(any())).thenReturn(Optional.of(adequacyTrajectory));
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("AREA1_ME", "inside", "AREA2_ME", "outside"));
        when(adequacySettingsAssemblerService.resolveMode(eq("AREA1_ME"), any(), any()))
                .thenReturn(Optional.of("inside"));
        when(adequacySettingsAssemblerService.resolveMode(eq("AREA2_ME"), any(), any()))
                .thenReturn(Optional.of("outside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, multipleAreaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).hasSize(2).containsKeys("AREA1_ME", "AREA2_ME");

        @SuppressWarnings("unchecked")
        Map<String, Object> area1Data = (Map<String, Object>) areas.get("AREA1_ME");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1Props = (Map<String, Object>) area1Data.get("properties");
        assertThat(area1Props)
                .containsEntry("energy_cost_unsupplied", 3000.0)
                .containsEntry("energy_cost_spilled", 100.0)
                .containsEntry("adequacy_patch_mode", "inside");

        @SuppressWarnings("unchecked")
        Map<String, Object> area2Data = (Map<String, Object>) areas.get("AREA2_ME");
        @SuppressWarnings("unchecked")
        Map<String, Object> area2Props = (Map<String, Object>) area2Data.get("properties");
        assertThat(area2Props)
                .containsEntry("energy_cost_unsupplied", 5000.0)
                .containsEntry("energy_cost_spilled", 300.0)
                .containsEntry("adequacy_patch_mode", "outside");
    }

    @Test
    void buildMultiEnergyMap_withLinkMeTrajectory_shouldReturnLinksMeStructure() {
        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, null, linkMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("links_me").doesNotContainKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> linksMe = (Map<String, Object>) result.get("links_me");
        assertThat(linksMe).isNotNull().containsKey("ME/FR");

        @SuppressWarnings("unchecked")
        Map<String, Object> linkEntry = (Map<String, Object>) linksMe.get("ME/FR");
        assertThat(linkEntry)
                .containsEntry("directMw", 1200.0)
                .containsEntry("indirectMw", 1300.0)
                .containsEntry("hurdleCostDirect", 0.1)
                .containsEntry("hurdleCostIndirect", 0.3);
    }

    @Test
    void buildMultiEnergyMap_withLinkMeTrajectoryPassedToSingleArgMethod_shouldReturnLinksMeStructure() {
        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, linkMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("links_me").doesNotContainKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> linksMe = (Map<String, Object>) result.get("links_me");
        assertThat(linksMe).isNotNull().containsKey("ME/FR");
    }

    @Test
    void buildMultiEnergyMap_withBothAreaMeAndLinkMe_shouldReturnBothSections() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("area_me", "inside"));
        when(adequacySettingsAssemblerService.resolveMode(eq("area_me"), any(), any()))
                .thenReturn(Optional.of("inside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory, linkMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "links_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> linksMe = (Map<String, Object>) result.get("links_me");
        assertThat(linksMe).containsKey("ME/FR");
    }

    @Test
    void buildMultiEnergyMap_withInvalidLinkMeEntities_shouldSkipInvalidEntries() {
        // Given
        LinkMeEntity invalidLink1 = LinkMeEntity.builder().nodeFrom(null).nodeTo("FR").build();
        LinkMeEntity invalidLink2 = LinkMeEntity.builder().nodeFrom("ME").nodeTo(null).build();
        LinkMeEntity validLink = LinkMeEntity.builder()
                .nodeFrom("ME")
                .nodeTo("DE")
                .directMw(500.0)
                .indirectMw(600.0)
                .hurdleCostsDirect(0.05)
                .hurdleCostsIndirect(0.05)
                .build();

        TrajectoryEntity trajWithInvalidLinks = TrajectoryEntity.builder()
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(List.of(invalidLink1, invalidLink2, validLink))
                .build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, null, trajWithInvalidLinks);

        // Then
        assertThat(result).containsKey("links_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> linksMe = (Map<String, Object>) result.get("links_me");
        assertThat(linksMe).hasSize(1).containsKey("ME/DE");
    }

    @Test
    void buildMultiEnergyMap_withLoads_shouldReturnLoadsMeStructure() {
        // Given
        AreaEntity areaMeEntity = AreaEntity.builder().name("V_ME_H2_SHORT_FR").build();
        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(areaMeEntity).unsuppliedEnergyCost(5376.0).spilledEnergyCost(0.0).build();
        TrajectoryEntity customAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity))
                .thenReturn(Map.of("V_ME_H2_SHORT_FR", List.of("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "loads_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> loadsMe = (Map<String, Object>) result.get("loads_me");
        assertThat(loadsMe).isNotNull().containsKey("loads_v_me_h2_short_fr");

        @SuppressWarnings("unchecked")
        List<String> arrowFiles = (List<String>) loadsMe.get("loads_v_me_h2_short_fr");
        assertThat(arrowFiles).containsExactly("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow");
    }

    @Test
    void buildMultiEnergyMap_withLoadsCaseInsensitive_shouldReturnLowercaseKey() {
        // Given
        AreaEntity areaMeEntity = AreaEntity.builder().name("v_me_h2_short_fr").build();
        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(areaMeEntity).unsuppliedEnergyCost(5376.0).spilledEnergyCost(0.0).build();
        TrajectoryEntity customAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity))
                .thenReturn(Map.of("V_ME_H2_SHORT_FR", List.of("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "loads_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> loadsMe = (Map<String, Object>) result.get("loads_me");
        assertThat(loadsMe).containsKey("loads_v_me_h2_short_fr");
    }

    @Test
    void buildMultiEnergyMap_whenLoadsEmpty_shouldNotReturnLoadsMe() {
        // Given
        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity))
                .thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me").doesNotContainKey("loads_me");
    }

    @Test
    void buildMultiEnergyMap_withBothAreaMeLinkMeAndLoads_shouldReturnAllSections() {
        // Given
        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity))
                .thenReturn(Map.of("AREA_ME", List.of("load_area_me_2026-2027.txt.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory, linkMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "loads_me", "links_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> loadsMe = (Map<String, Object>) result.get("loads_me");
        assertThat(loadsMe).containsKey("loads_area_me");
        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) loadsMe.get("loads_area_me");
        assertThat(files).containsExactly("load_area_me_2026-2027.txt.arrow");
    }

    @Test
    void buildMultiEnergyMap_withLoadMeTrajectoryFiles_shouldMapLoadsMeCorrectly() {
        // Given
        AreaEntity area1 = AreaEntity.builder().name("V_ME_H2_SHORT_FR").build();
        AreaEntity area2 = AreaEntity.builder().name("V_ME_GAZ_SHORT_FR").build();

        AreaConfigEntity config1 = AreaConfigEntity.builder().area(area1).unsuppliedEnergyCost(5000.0).spilledEnergyCost(100.0).build();
        AreaConfigEntity config2 = AreaConfigEntity.builder().area(area2).unsuppliedEnergyCost(6000.0).spilledEnergyCost(200.0).build();

        TrajectoryEntity customAreaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config1, config2))
                .build();

        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(studyEntity))
                .thenReturn(Map.of(
                        "V_ME_H2_SHORT_FR", List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"),
                        "V_ME_GAZ_SHORT_FR", List.of("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow")
                ));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMe);

        // Then
        assertThat(result).containsKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> loadsMe = (Map<String, Object>) result.get("loads_me");
        assertThat(loadsMe).containsKeys("loads_v_me_h2_short_fr", "loads_v_me_gaz_short_fr");
        assertThat(loadsMe.get("loads_v_me_h2_short_fr")).isEqualTo(List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"));
        assertThat(loadsMe.get("loads_v_me_gaz_short_fr")).isEqualTo(List.of("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow"));
    }
}
