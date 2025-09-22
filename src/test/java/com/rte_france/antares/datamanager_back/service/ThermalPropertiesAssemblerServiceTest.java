package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalGroupMappingService;
import com.rte_france.antares.datamanager_back.service.impl.ThermalPropertiesAssemblerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermalPropertiesAssemblerServiceTest {

    @Mock
    private ThermalGroupMappingService groupMappingService;

    @InjectMocks
    private ThermalPropertiesAssemblerService service;

    private ThermalClusterRef gasRef;
    private ThermalClusterRef nucRef;

    @BeforeEach
    void init() {
        gasRef = ThermalClusterRef.builder().name("Gas1").build();
        nucRef = ThermalClusterRef.builder().name("NuclearA").build();
    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withComputedValues() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, null).toBuilder().area("FR").build(), // max NUMBER = 3
                        cap(gasRef, ThermalCategoryEnum.POWER, 450.0, null).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.POWER, 500.0, null).toBuilder().area("FR").build()  // max POWER = 500
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.40, 3, 2, 41.5, 7.2) // minStablePower = 0.40 * 500
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(Set.of(capTraj, paramTraj));

        // then
        assertThat(out).hasSize(1).containsKey(new ThermalPropertiesAssemblerService.AreaRefKey("FR", gasRef));
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaRefKey("FR", gasRef));

        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getNominalCapacity()).isEqualTo(500.0);
        assertThat(dto.getGroup()).isEqualTo("GAS");

        assertThat(dto.getMinStablePower()).isEqualTo(200.0); // 0.40 * 500
        assertThat(dto.getMinUpTime()).isEqualTo(3);
        assertThat(dto.getMinDownTime()).isEqualTo(2);
        assertThat(dto.getEfficiency()).isEqualTo(41.5);
        assertThat(dto.getVariableOMCost()).isEqualTo(7.2);
    }

    @Test
    void assembleForTrajectory_groupsByClusterRef_multipleClusters() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(nucRef, ThermalCategoryEnum.POWER, 1200.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.30, 1, 1, 55.0, 1.0),
                        params(nucRef, 0.90, 10, 8, 33.0, 3.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));
        when(groupMappingService.toGroup("NuclearA")).thenReturn(Optional.of("NUCLEAR"));

        // when
        var out = service.assembleForTrajectories(Set.of(capTraj, paramTraj));

        // then
        assertThat(out.keySet()).containsExactlyInAnyOrder(
                new ThermalPropertiesAssemblerService.AreaRefKey("FR", gasRef),
                new ThermalPropertiesAssemblerService.AreaRefKey("FR", nucRef)
        );
        assertThat(out.get(new ThermalPropertiesAssemblerService.AreaRefKey("FR", gasRef)).getGroup()).isEqualTo("GAS");
        assertThat(out.get(new ThermalPropertiesAssemblerService.AreaRefKey("FR", nucRef)).getGroup()).isEqualTo("NUCLEAR");
    }

    @Test
    void assembleAreaRefMap_missingCategories_fallsBackToNull() {
        // given: no POWER category => nominalCapacity stays null => minStablePower stays null too
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 1.0, null).toBuilder().area("FR").build()
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.50, 2, 2, 60.0, 5.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(Set.of(capTraj, paramTraj));

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaRefKey("FR", gasRef));
        assertThat(dto.getNominalCapacity()).isNull();
        assertThat(dto.getMinStablePower()).isNull();
        assertThat(dto.getEnabled()).isNull();
    }


    private static ThermalClusterCapacityEntity cap(ThermalClusterRef ref, ThermalCategoryEnum cat, double value, Boolean toUse) {
        return ThermalClusterCapacityEntity.builder()
                .thermalClusterRef(ref)
                .category(cat)
                .value(value)
                .toUse(toUse)
                .build();
    }

    private static ThermalCommonParameterEntity params(ThermalClusterRef ref,
                                                       double minStableGenDefault,
                                                       int minUp, int minDown,
                                                       double effDefault, double omCost) {
        return ThermalCommonParameterEntity.builder()
                .thermalClusterRef(ref)
                .minStableGenerationDefault(minStableGenDefault)
                .minUpTime((double) minUp)
                .minDownTime((double) minDown)
                .efficiencyDefault(effDefault)
                .omCost(omCost)
                .build();
    }
}
