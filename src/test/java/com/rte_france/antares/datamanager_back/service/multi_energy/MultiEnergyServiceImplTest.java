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
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
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

    @Mock
    private StsGenerationAssemblerService stsPropertiesAssemblerService;


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
    void buildMultiEnergyMap_withNullTrajectory_shouldReturnEmptyMap() {
        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, (TrajectoryEntity) null);

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void buildMultiEnergyMap_withNullStudyAndNullTrajectories_shouldReturnEmptyMap() {
        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(null, null, null);

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void buildMultiEnergyMap_withStudyHavingNullIdAndName_shouldExecuteWithoutErrors() {
        // Given
        StudyEntity studyWithNulls = StudyEntity.builder().id(null).name(null).trajectories(null).build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyWithNulls, null, null);

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void buildMultiEnergyMap_withTrajectoryHavingNullType_shouldDelegateToAreaMe() {
        // Given
        TrajectoryEntity trajWithNullType = TrajectoryEntity.builder()
                .type(null)
                .fileName("custom.xlsx")
                .areaConfigEntities(null)
                .build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, trajWithNullType);

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void buildMultiEnergyMap_withNullStudyInBuildAreasMap_shouldAssembleWithEmptyAdequacyMap() {
        // Given
        AreaEntity areaEntity = AreaEntity.builder().name("area_null_study").build();
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(areaEntity)
                .unsuppliedEnergyCost(100.0)
                .spilledEnergyCost(50.0)
                .build();
        TrajectoryEntity traj = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config))
                .build();

        when(adequacySettingsAssemblerService.findAdequacyTrajectory(null)).thenReturn(Optional.empty());
        when(adequacySettingsAssemblerService.resolveMode(eq("area_null_study"), any(), eq(Collections.emptyMap())))
                .thenReturn(Optional.empty());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(null, traj, null);

        // Then
        assertThat(result).containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).containsKey("area_null_study");
    }

    @Test
    void buildMultiEnergyMap_withAreaConfigHavingNullAreaOrNullAreaName_shouldSkipThoseConfigs() {
        // Given
        AreaConfigEntity nullAreaConfig = AreaConfigEntity.builder().area(null).build();
        AreaConfigEntity nullNameConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name(null).build()).build();
        AreaConfigEntity validConfig = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("VALID_AREA").build())
                .unsuppliedEnergyCost(100.0)
                .spilledEnergyCost(20.0)
                .build();

        TrajectoryEntity traj = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(nullAreaConfig, nullNameConfig, validConfig))
                .build();

        when(adequacySettingsAssemblerService.findAdequacyTrajectory(studyEntity)).thenReturn(Optional.empty());
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(studyEntity)).thenReturn(Collections.emptyMap());
        when(adequacySettingsAssemblerService.resolveMode(eq("VALID_AREA"), any(), any())).thenReturn(Optional.empty());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, traj);

        // Then
        assertThat(result).containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        assertThat(areas).hasSize(1).containsKey("VALID_AREA");
    }

    @Test
    void buildMultiEnergyMap_withBlankAdequacyMode_shouldNotIncludeAdequacyPatchMode() {
        // Given
        AreaEntity areaEntity = AreaEntity.builder().name("area_blank_adequacy").build();
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(areaEntity)
                .unsuppliedEnergyCost(100.0)
                .spilledEnergyCost(50.0)
                .build();
        TrajectoryEntity traj = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config))
                .build();

        when(adequacySettingsAssemblerService.findAdequacyTrajectory(studyEntity)).thenReturn(Optional.empty());
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(studyEntity)).thenReturn(Collections.emptyMap());
        when(adequacySettingsAssemblerService.resolveMode(eq("area_blank_adequacy"), any(), any()))
                .thenReturn(Optional.of("   "));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, traj);

        // Then
        assertThat(result).containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areas = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areas.get("area_blank_adequacy");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) areaData.get("properties");
        assertThat(properties).doesNotContainKey("adequacy_patch_mode");
    }

    @Test
    void buildMultiEnergyMap_withLinkMeContainingNullLinkEntity_shouldSkipNullLinks() {
        // Given
        TrajectoryEntity trajWithNullLink = TrajectoryEntity.builder()
                .type(TrajectoryType.LINK_ME.name())
                .linkMeEntities(Collections.singletonList(null))
                .build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, null, trajWithNullLink);

        // Then
        assertThat(result).doesNotContainKey("links_me");
    }

    @Test
    void buildMultiEnergyMap_withNullLoadToJsonService_shouldReturnNoLoadFiles() {
        // Given
        MultiEnergyServiceImpl serviceWithoutLoadService = new MultiEnergyServiceImpl(adequacySettingsAssemblerService, stsPropertiesAssemblerService,loadToJsonService);
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("AREA1").build())
                .build();
        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config))
                .build();

        // When
        Map<String, Object> result = serviceWithoutLoadService.buildMultiEnergyMap(studyEntity, areaMe);

        // Then
        assertThat(result).doesNotContainKey("loads_me").containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1 = (Map<String, Object>) areaMeMap.get("AREA1");
        assertThat(area1.get("loads")).isEqualTo("No LOAD files for this area");
    }

    @Test
    void buildMultiEnergyMap_whenStudyTrajectoriesNull_shouldReturnNoLoadFiles() {
        // Given
        StudyEntity studyWithNullTrajectories = StudyEntity.builder().id(1).name("test").trajectories(null).build();
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("AREA1").build())
                .build();
        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config))
                .build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyWithNullTrajectories, areaMe);

        // Then
        assertThat(result).doesNotContainKey("loads_me").containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1 = (Map<String, Object>) areaMeMap.get("AREA1");
        assertThat(area1.get("loads")).isEqualTo("No LOAD files for this area");
    }

    @Test
    void buildMultiEnergyMap_whenLoadFilesByAreaIsNull_shouldReturnNoLoadFiles() {
        // Given
        AreaConfigEntity config = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("AREA1").build())
                .build();
        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config))
                .build();

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity)).thenReturn(null);

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMe);

        // Then
        assertThat(result).doesNotContainKey("loads_me").containsKey("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1 = (Map<String, Object>) areaMeMap.get("AREA1");
        assertThat(area1.get("loads")).isEqualTo("No LOAD files for this area");
    }

    @Test
    void buildMultiEnergyMap_withBlankKeysInLoads_shouldSkipThem() {
        // Given
        AreaConfigEntity validConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("AREA_EXACT").build()).build();

        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(validConfig))
                .build();

        Map<String, List<String>> loadFilesMap = new HashMap<>();
        loadFilesMap.put(null, List.of("file.arrow"));
        loadFilesMap.put("   ", List.of("file2.arrow"));
        loadFilesMap.put("AREA_EXACT", List.of("file3.arrow"));

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(loadFilesMap);

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMe);

        // Then
        assertThat(result).containsKey("area_me").doesNotContainKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaExact = (Map<String, Object>) areaMeMap.get("AREA_EXACT");
        assertThat(areaExact.get("loads")).isEqualTo(List.of("file3.arrow"));
    }

    @Test
    void buildMultiEnergyMap_withLoadsCaseMatchingVariants_shouldMatchAllVariants() {
        // Given
        AreaConfigEntity exactConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("exact_match").build()).build();
        AreaConfigEntity upperConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("upper_match").build()).build();
        AreaConfigEntity mixedConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("MixEd_MatCh").build()).build();
        AreaConfigEntity noMatchConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("no_match").build()).build();

        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(exactConfig, upperConfig, mixedConfig, noMatchConfig))
                .build();

        Map<String, List<String>> loadFilesMap = new HashMap<>();
        loadFilesMap.put("exact_match", List.of("exact.arrow"));
        loadFilesMap.put("UPPER_MATCH", List.of("upper.arrow"));
        loadFilesMap.put("mIXeD_mATcH", List.of("mixed.arrow"));
        loadFilesMap.put("other_area", List.of("other.arrow"));

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity)).thenReturn(loadFilesMap);

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMe);

        // Then
        assertThat(result).containsKey("area_me").doesNotContainKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> exactArea = (Map<String, Object>) areaMeMap.get("exact_match");
        @SuppressWarnings("unchecked")
        Map<String, Object> upperArea = (Map<String, Object>) areaMeMap.get("upper_match");
        @SuppressWarnings("unchecked")
        Map<String, Object> mixedArea = (Map<String, Object>) areaMeMap.get("MixEd_MatCh");
        @SuppressWarnings("unchecked")
        Map<String, Object> noMatchArea = (Map<String, Object>) areaMeMap.get("no_match");

        assertThat(exactArea.get("loads")).isEqualTo(List.of("exact.arrow"));
        assertThat(upperArea.get("loads")).isEqualTo(List.of("upper.arrow"));
        assertThat(mixedArea.get("loads")).isEqualTo(List.of("mixed.arrow"));
        assertThat(noMatchArea.get("loads")).isEqualTo("No LOAD files for this area");
    }

    @Test
    void buildMultiEnergyMap_withNullLoadFilesListInMap_shouldReturnNoLoadFiles() {
        // Given
        AreaConfigEntity configWithNullList = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("AREA_WITH_NULL_LIST").build())
                .build();
        AreaConfigEntity configWithEmptyList = AreaConfigEntity.builder()
                .area(AreaEntity.builder().name("AREA_WITH_EMPTY_LIST").build())
                .build();

        TrajectoryEntity areaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(configWithNullList, configWithEmptyList))
                .build();

        Map<String, List<String>> loadFilesMap = new HashMap<>();
        loadFilesMap.put("AREA_WITH_NULL_LIST", null);
        loadFilesMap.put("AREA_WITH_EMPTY_LIST", Collections.emptyList());

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity)).thenReturn(loadFilesMap);

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMe);

        // Then
        assertThat(result).containsKey("area_me").doesNotContainKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMeMap = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1 = (Map<String, Object>) areaMeMap.get("AREA_WITH_NULL_LIST");
        @SuppressWarnings("unchecked")
        Map<String, Object> area2 = (Map<String, Object>) areaMeMap.get("AREA_WITH_EMPTY_LIST");
        assertThat(area1.get("loads")).isEqualTo("No LOAD files for this area");
        assertThat(area2.get("loads")).isEqualTo("No LOAD files for this area");
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
    void buildMultiEnergyMap_withLoads_shouldReturnLoadsInsideAreaMeStructure() {
        // Given
        AreaEntity areaMeEntity = AreaEntity.builder().name("V_ME_H2_SHORT_FR").build();
        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(areaMeEntity).unsuppliedEnergyCost(5376.0).spilledEnergyCost(0.0).build();
        TrajectoryEntity customAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Map.of("V_ME_H2_SHORT_FR", List.of("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me").doesNotContainKey("loads_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaMe = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areaMe.get("V_ME_H2_SHORT_FR");
        assertThat(areaData).isNotNull().containsKey("loads");

        @SuppressWarnings("unchecked")
        List<String> arrowFiles = (List<String>) areaData.get("loads");
        assertThat(arrowFiles).containsExactly("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow");
    }

    @Test
    void buildMultiEnergyMap_withLoadsCaseInsensitive_shouldMatchAreaLoads() {
        // Given
        AreaEntity areaMeEntity = AreaEntity.builder().name("v_me_h2_short_fr").build();
        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(areaMeEntity).unsuppliedEnergyCost(5376.0).spilledEnergyCost(0.0).build();
        TrajectoryEntity customAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Map.of("V_ME_H2_SHORT_FR", List.of("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me").doesNotContainKey("loads_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaMe = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areaMe.get("v_me_h2_short_fr");
        assertThat(areaData.get("loads")).isEqualTo(List.of("load_V_ME_H2_SHORT_FR_2026-2027.txt.70bc925d-4887-463c-b9c6-2ac90ea44188.arrow"));
    }

    @Test
    void buildMultiEnergyMap_whenLoadsEmpty_shouldSetNoLoadFilesPlaceholderInAreaMe() {
        // Given
        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me").doesNotContainKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMe = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areaMe.get("area_me");
        assertThat(areaData.get("loads")).isEqualTo("No LOAD files for this area");
    }

    @Test
    void buildMultiEnergyMap_withBothAreaMeLinkMeAndLoads_shouldReturnBothSectionsWithoutLoadsMeKey() {
        // Given
        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Map.of("area_me", List.of("load_area_me_2026-2027.txt.arrow")));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory, linkMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "links_me").doesNotContainKey("loads_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaMe = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) areaMe.get("area_me");
        assertThat(areaData.get("loads")).isEqualTo(List.of("load_area_me_2026-2027.txt.arrow"));
    }

    @Test
    void buildMultiEnergyMap_withLoadMeTrajectoryFiles_shouldMapLoadsCorrectlyInsideEachArea() {
        // Given
        AreaEntity area1 = AreaEntity.builder().name("V_ME_H2_SHORT_FR").build();
        AreaEntity area2 = AreaEntity.builder().name("V_ME_GAZ_SHORT_FR").build();

        AreaConfigEntity config1 = AreaConfigEntity.builder().area(area1).unsuppliedEnergyCost(5000.0).spilledEnergyCost(100.0).build();
        AreaConfigEntity config2 = AreaConfigEntity.builder().area(area2).unsuppliedEnergyCost(6000.0).spilledEnergyCost(200.0).build();

        TrajectoryEntity customAreaMe = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config1, config2))
                .build();

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Map.of(
                        "V_ME_H2_SHORT_FR", List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"),
                        "V_ME_GAZ_SHORT_FR", List.of("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow")
                ));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, customAreaMe);

        // Then
        assertThat(result).containsKey("area_me").doesNotContainKey("loads_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> areaMe = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1Data = (Map<String, Object>) areaMe.get("V_ME_H2_SHORT_FR");
        @SuppressWarnings("unchecked")
        Map<String, Object> area2Data = (Map<String, Object>) areaMe.get("V_ME_GAZ_SHORT_FR");

        assertThat(area1Data.get("loads")).isEqualTo(List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"));
        assertThat(area2Data.get("loads")).isEqualTo(List.of("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow"));
    }

    @Test
    void buildMultiEnergyMap_withStsMeTrajectory_shouldDispatchProperly() {
        // Given
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .fileName("sts_me.xlsx")
                .build();

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, stsMeTrajectory);

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void buildMultiEnergyMap_withAreaMeLinkMeAndStsMe_shouldReturnExpectedSections() {
        // Given
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .fileName("sts_me.xlsx")
                .build();

        when(loadToJsonService.getListArrowLoadMeFilesFromStudy(studyEntity))
                .thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory, linkMeTrajectory, stsMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKeys("area_me", "links_me");
    }
}
