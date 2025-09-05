package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterPropertiesDto;
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
import java.util.Map;
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
        var t = TrajectoryEntity.builder()
                .type("AREA")
                .area("FR")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true),
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, null), // max= 3
                        cap(gasRef, ThermalCategoryEnum.POWER, 450.0, null),
                        cap(gasRef, ThermalCategoryEnum.POWER, 500.0, null) // max= 500
                ))
                .thermalClusterParameters(List.of(
                        params(gasRef, 0.40, 3, 2, 41.5, 7.2) // minStablePower = 0.40 * 500
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn("GAS");

        // when
        var out = service.assembleForTrajectory(t);

        // then
        assertThat(out).hasSize(1).containsKey("FR_Gas1");
        ThermalClusterPropertiesDto dto = out.get("FR_Gas1");

        assertThat(dto.isEnabled()).isTrue();
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
        var t = TrajectoryEntity.builder()
                .type("AREA")
                .area("FR")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true),
                        cap(nucRef, ThermalCategoryEnum.POWER, 1200.0, true)
                ))
                .thermalClusterParameters(List.of(
                        params(gasRef, 0.30, 1, 1, 55.0, 1.0),
                        params(nucRef, 0.90, 10, 8, 33.0, 3.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn("GAS");
        when(groupMappingService.toGroup("NuclearA")).thenReturn("NUCLEAR");

        // when
        Map<String, ThermalClusterPropertiesDto> out = service.assembleForTrajectory(t);

        // then
        assertThat(out.keySet()).containsExactlyInAnyOrder("FR_Gas1", "FR_NuclearA");
        assertThat(out.get("FR_Gas1").getGroup()).isEqualTo("GAS");
        assertThat(out.get("FR_NuclearA").getGroup()).isEqualTo("NUCLEAR");
    }

    @Test
    void assembleForTrajectory_missingCategories_fallsBackToDtoDefaults() {
        // given: no POWER category => nominalCapacity stays default (0) => minStablePower = 0
        var t = TrajectoryEntity.builder()
                .type("AREA")
                .area("FR")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 1.0, null) // only NUMBER provided
                ))
                .thermalClusterParameters(List.of(
                        params(gasRef, 0.50, 2, 2, 60.0, 5.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn("GAS");

        // when
        var out = service.assembleForTrajectory(t);

        // then
        var dto = out.get("FR_Gas1");
        assertThat(dto.getNominalCapacity()).isEqualTo(0.0);
        assertThat(dto.getMinStablePower()).isEqualTo(0.0);
        assertThat(dto.isEnabled()).isTrue();
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
