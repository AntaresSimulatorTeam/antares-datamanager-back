package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiEnergyCoherenceCheckServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private MultiEnergyCoherenceCheckServiceImpl coherenceCheckService;

    private Integer studyId;

    @BeforeEach
    void setUp() {
        studyId = 1;
    }

    private TrajectoryEntity createAreaMeTrajectory(String... areaNames) {
        List<AreaConfigEntity> areaConfigs = List.of();
        if (areaNames != null) {
            areaConfigs = java.util.Arrays.stream(areaNames)
                    .map(name -> AreaConfigEntity.builder()
                            .area(AreaEntity.builder().name(name).build())
                            .build())
                    .toList();
        }
        return TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(areaConfigs)
                .build();
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenLoadMeContainsAllAreaMeAreas_shouldPass() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity load1 = LoadEntity.builder().area("FR").build();
        LoadEntity load2 = LoadEntity.builder().area("be").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load1, load2))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenLoadMeIsMissingArea_shouldThrowBusinessException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE", "DE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity load1 = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load1))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .hasMessage("Areas {0} from AREA_ME trajectory is (are) not present in {1} trajectory")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorMessageArguments()).hasSize(2);
                    assertThat(be.getErrorMessageArguments().get(1)).isEqualTo(TrajectoryType.LOAD_ME.name());
                    String missingAreas = be.getErrorMessageArguments().get(0);
                    assertThat(missingAreas).contains("BE");
                    assertThat(missingAreas).contains("DE");
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStsMeContainsAllAreaMeAreas_shouldPass() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        StStorageEntity sts1 = StStorageEntity.builder().area("FR").build();
        StStorageEntity sts2 = StStorageEntity.builder().area(" BE ").build();
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(List.of(sts1, sts2))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStsMeIsMissingArea_shouldThrowBusinessException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        StStorageEntity sts1 = StStorageEntity.builder().area("FR").build();
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(List.of(sts1))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .hasMessage("Areas {0} from AREA_ME trajectory is (are) not present in {1} trajectory")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorMessageArguments()).containsExactly("BE", TrajectoryType.STS_ME.name());
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenNoAreaMeTrajectories_shouldPass() {
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(Collections.emptyList());

        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(Collections.emptyList())
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenAreaMeHasNullOrEmptyAreaConfigs_shouldPass() {
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(null)
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(null)
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenEntitiesHaveNullOrBlankAreas_shouldFilterThemOut() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity loadWithNullArea = LoadEntity.builder().area(null).build();
        LoadEntity loadWithBlankArea = LoadEntity.builder().area("   ").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(loadWithNullArea, loadWithBlankArea))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorMessageArguments()).containsExactly("FR", TrajectoryType.LOAD_ME.name());
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryIsNull_shouldNotThrowException() {
        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), null)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryTypeIsNull_shouldNotThrowException() {
        LoadEntity load = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, null, loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStudyIdIsNull_shouldNotThrowException() {
        LoadEntity load = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(null, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryTypeIsOther_andAreaMeNotEmpty_shouldThrowException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        TrajectoryEntity thermalTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY_ME.name())
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.THERMAL_CAPACITY_ME.name(), thermalTrajectory)
        ).isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorMessageArguments()).containsExactly("FR", TrajectoryType.THERMAL_CAPACITY_ME.name());
                });
    }
}
