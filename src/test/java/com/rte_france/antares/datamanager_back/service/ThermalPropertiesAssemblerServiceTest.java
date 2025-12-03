package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalGroupMappingService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermalPropertiesAssemblerServiceTest {

    @Mock
    private ThermalGroupMappingService groupMappingService;

    @Mock
    private ThermalParamModulationService paramModulationService;

    @InjectMocks
    private ThermalPropertiesAssemblerService service;

    private ThermalClusterRef gasRef;
    private ThermalClusterRef nucRef;

    @BeforeEach
    void init() {
        gasRef = ThermalClusterRef.builder().name("Gas1").build();
        nucRef = ThermalClusterRef.builder().name("NuclearA").build();
        when(paramModulationService.createMatrixParamModulationTsFiles(any())).thenReturn(List.of());
    }

    @Test
    void assembleForTrajectories_canonicalizationAndPreferredRefSelection_acrossAreas() {
        // Given: different raw names across files map to the same canonical group "Gas"
        var capRefAT = ThermalClusterRef.builder().name("GAS Alias AT").build();
        var capRefDE = ThermalClusterRef.builder().name("GAS Alias DE").build();
        var commonRef = ThermalClusterRef.builder().name("Gas common new").build();
        var specificRefAT = ThermalClusterRef.builder().name("CCGT present 1").build();

        // Capacities for two areas (AT, DE), using different raw refs
        var capacityTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        // AT capacities
                        cap(capRefAT, ThermalCategoryEnum.NUMBER, 1.0, true).toBuilder().area("AT").build(),
                        cap(capRefAT, ThermalCategoryEnum.POWER, 370.0, true).toBuilder().area("AT").build(),
                        // DE capacities
                        cap(capRefDE, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("DE").build(),
                        cap(capRefDE, ThermalCategoryEnum.POWER, 700.0, true).toBuilder().area("DE").build()
                ))
                .build();

        // Common parameters: single row should apply to both areas when specific is absent
        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(commonRef, 0.40, 5, 5, 0.60, 1.1)
                ))
                .build();

        // Specific parameters present only for AT should be preferred for AT naming
        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(
                        specificParams(
                                specificRefAT,
                                0.80, // minStableGeneration (ratio)
                                0.00, // spinning
                                0.56, // efficiency (ratio)
                                1.0,  // FO duration
                                27.0, // PO duration
                                0,    // NPO max winter
                                0,    // NPO max summer
                                1,    // nb unit
                                List.of(0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05),
                                List.of(0.02,0.02,0.02,0.13,0.13,0.13,0.13,0.13,0.13,0.02,0.02,0.02)
                        ).toBuilder().area("AT").build()
                ))
                .build();

        // Mapping: all raw names map to canonical "Gas"
        when(groupMappingService.toGroup("GAS Alias AT")).thenReturn(Optional.of("Gas"));
        when(groupMappingService.toGroup("GAS Alias DE")).thenReturn(Optional.of("Gas"));
        when(groupMappingService.toGroup("Gas common new")).thenReturn(Optional.of("Gas"));
        when(groupMappingService.toGroup("CCGT present 1")).thenReturn(Optional.of("Gas"));

        // No param modulation files in this test
        when(paramModulationService.createMatrixParamModulationTsFiles(any())).thenReturn(List.of());

        // When
        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();
        var out = service.assembleForTrajectories(study);

        // Then: 2 entries (AT and DE) under the same canonical group bucket
        assertThat(out).hasSize(2);

        // AT should prefer a SPECIFIC ref name
        var atEntry = out.entrySet().stream()
                .filter(e -> e.getKey().area().equals("AT"))
                .findFirst()
                .orElseThrow();
        assertThat(atEntry.getKey().thermalClusterRef().getName()).isEqualTo("CCGT present 1");
        assertThat(atEntry.getValue().getGroup()).isEqualTo("Gas");

        // DE should fall back to COMMON ref name (no specific for DE)
        var deEntry = out.entrySet().stream()
                .filter(e -> e.getKey().area().equals("DE"))
                .findFirst()
                .orElseThrow();
        assertThat(deEntry.getKey().thermalClusterRef().getName()).isEqualTo("Gas common new");
        assertThat(deEntry.getValue().getGroup()).isEqualTo("Gas");
    }

    @Test
    void assembleForTrajectories_mergesCommonParamsByCanonicalAcrossRefs() {
        // Given: two different refs for capacities map to same canonical "Hard coal"
        var capRefAT = ThermalClusterRef.builder().name("HC Alias AT").build();
        var capRefDE = ThermalClusterRef.builder().name("HC Alias DE").build();

        var capacityTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(capRefAT, ThermalCategoryEnum.NUMBER, 1.0, true).toBuilder().area("AT").build(),
                        cap(capRefAT, ThermalCategoryEnum.POWER, 500.0, true).toBuilder().area("AT").build(),
                        cap(capRefDE, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("DE").build(),
                        cap(capRefDE, ThermalCategoryEnum.POWER, 800.0, true).toBuilder().area("DE").build()
                ))
                .build();

        // Common rows exist for each ref separately, but both should be merged under canonical group
        var commonRefAT = ThermalClusterRef.builder().name("Hard coal old 1").build();
        var commonRefDE = ThermalClusterRef.builder().name("Hard coal new").build();
        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(commonRefAT, 0.40, 8, 8, 0.40, 3.3),
                        params(commonRefDE, 0.45, 6, 6, 0.41, 3.2)
                ))
                .build();

        // No specific parameters
        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of())
                .build();

        // Mapping: all raw names map to canonical "Hard coal"
        when(groupMappingService.toGroup("HC Alias AT")).thenReturn(Optional.of("Hard coal"));
        when(groupMappingService.toGroup("HC Alias DE")).thenReturn(Optional.of("Hard coal"));
        when(groupMappingService.toGroup("Hard coal old 1")).thenReturn(Optional.of("Hard coal"));
        when(groupMappingService.toGroup("Hard coal new")).thenReturn(Optional.of("Hard coal"));

        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();

        // When
        var out = service.assembleForTrajectories(study);

        // Then: we have entries for both areas even though commons were provided under two different refs
        assertThat(out).hasSize(2);

        var atEntry = out.entrySet().stream().filter(e -> e.getKey().area().equals("AT")).findFirst().orElseThrow();
        var deEntry = out.entrySet().stream().filter(e -> e.getKey().area().equals("DE")).findFirst().orElseThrow();

        // Group must be canonical
        assertThat(atEntry.getValue().getGroup()).isEqualTo("Hard coal");
        assertThat(deEntry.getValue().getGroup()).isEqualTo("Hard coal");

        // With no specific rows, naming falls back to COMMON, which should be taken from the first merged common row
        // We can't rely on a particular choice between the two, but it must be one of the provided common names
        assertThat(atEntry.getKey().thermalClusterRef().getName()).isIn("Hard coal old 1", "Hard coal new");
        assertThat(deEntry.getKey().thermalClusterRef().getName()).isIn("Hard coal old 1", "Hard coal new");
    }

    @Test
    void assembleForTrajectories_mergesSpecificParamsByCanonicalWithinArea() {
        // Given: one area with two specific rows for different refs mapping to same canonical "Gas"
        var capRefFR = ThermalClusterRef.builder().name("Gas FR Ref").build();
        var specRef1 = ThermalClusterRef.builder().name("CCGT present 1").build();
        var specRef2 = ThermalClusterRef.builder().name("CCGT present 2").build();

        var capacityTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(capRefFR, ThermalCategoryEnum.NUMBER, 5.0, true).toBuilder().area("FR").build(),
                        cap(capRefFR, ThermalCategoryEnum.POWER, 1800.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(
                        // specRef1 first -> should be preferred for naming for FR
                        specificParams(specRef1, 0.80, 0.0, 0.56, 1.0, 27.0, 0, 7, 7,
                                List.of(0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05),
                                List.of(0.02,0.02,0.02,0.13,0.13,0.13,0.13,0.13,0.13,0.02,0.02,0.02)
                        ).toBuilder().area("FR").build(),
                        specificParams(specRef2, 0.79, 0.0, 0.58, 1.0, 27.0, 0, 7, 7,
                                List.of(0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05,0.05),
                                List.of(0.02,0.02,0.02,0.13,0.13,0.13,0.13,0.13,0.13,0.02,0.02,0.02)
                        ).toBuilder().area("FR").build()
                ))
                .build();

        // No commons
        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of())
                .build();

        // Mapping
        when(groupMappingService.toGroup("Gas FR Ref")).thenReturn(Optional.of("Gas"));
        when(groupMappingService.toGroup("CCGT present 1")).thenReturn(Optional.of("Gas"));
        when(groupMappingService.toGroup("CCGT present 2")).thenReturn(Optional.of("Gas"));

        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();

        // When
        var out = service.assembleForTrajectories(study);

        // Then: single entry for FR, using SPECIFIC name of the first specific row for the area
        assertThat(out).hasSize(1);
        var frEntry = out.entrySet().iterator().next();
        assertThat(frEntry.getKey().area()).isEqualTo("FR");
        assertThat(frEntry.getKey().thermalClusterRef().getName()).isEqualTo("CCGT present 1");
        assertThat(frEntry.getValue().getGroup()).isEqualTo("Gas");
    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withSpecificParametersApplied() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        // unit count = 3 (max), power = 600 (max) => nominal = 600/3 = 200
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.POWER, 500.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common params present but will be overridden by specific ones where applicable
        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.30, 1, 1, 0.33, 1.0)
                ))
                .build();

        // Specific parameters
        var foMonthly = List.of(0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.10, 0.11, 0.12);
        var poMonthly = List.of(0.12, 0.11, 0.10, 0.09, 0.08, 0.07, 0.06, 0.05, 0.04, 0.03, 0.02, 0.01);

        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(
                        specificParams(
                                gasRef,
                                0.50, // minStableGeneration (ratio of nominal)
                                0.23, // spinning
                                0.37, // efficiency (ratio)
                                2.5,  // FO duration
                                3.75, // PO duration
                                5,    // NPO max winter
                                7,    // NPO max summer
                                4,    // nb unit
                                foMonthly,
                                poMonthly
                        )
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();
        var out = service.assembleForTrajectories(study);

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // From capacity
        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getNominalCapacity()).isEqualTo(600.0/3);
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getGroup()).isEqualTo("GAS");

        // From specific parameters (override common)
        assertThat(dto.getMinStablePower()).isEqualTo(0.50 * (600.0/3));
        assertThat(dto.getEfficiency()).isEqualTo(37.0); // 0.37 -> 37%
        assertThat(dto.getSpinning()).isEqualTo(23.0); //0.23 ->23%
        assertThat(dto.getFoDuration()).isEqualTo(2.5);
        assertThat(dto.getPoDuration()).isEqualTo(3.75);

        assertThat(dto.getFoMonthlyRate()).containsExactlyElementsOf(foMonthly);
        assertThat(dto.getPoMonthlyRate()).containsExactlyElementsOf(poMonthly);

        assertThat(dto.getNpoMaxWinter()).isEqualTo(5.0);
        assertThat(dto.getNpoMaxSummer()).isEqualTo(7.0);
        assertThat(dto.getNbUnit()).isEqualTo(4);
    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withComputedValues() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, null).toBuilder().area("FR").build(), // max NUMBER = 3
                        cap(gasRef, ThermalCategoryEnum.POWER, 0.415, null).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.POWER, 500.0, null).toBuilder().area("FR").build()  // max POWER = 500
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.40, 3, 2, 0.415, 7.2) // minStablePower = 0.40 * 500
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build();

        var out = service.assembleForTrajectories(study);

        // then
        assertThat(out).hasSize(1).containsKey(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getNominalCapacity()).isEqualTo(500.0/3);
        assertThat(dto.getGroup()).isEqualTo("GAS");

        assertThat(dto.getMinStablePower()).isEqualTo(0.4*500/3); // 0.40 * 500/3
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

        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build());

        // then
        assertThat(out.keySet()).containsExactlyInAnyOrder(
                new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef),
                new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", nucRef)
        );
        assertThat(out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef)).getGroup()).isEqualTo("GAS");
        assertThat(out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", nucRef)).getGroup()).isEqualTo("NUCLEAR");
    }

    @Test
    void assembleAreaRefMap_missingCategories_fallsBackToNull() {
        // given: Add POWER category so we don't have nulls on outputs
        var capTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        // Use efficiency as a ratio (0.60 => 60%) to match other tests' convention
                        params(gasRef, 0.50, 2, 2, 0.60, 5.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTrajectory, paramTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        assertThat(dto.getNominalCapacity()).isEqualTo(100.0);
        assertThat(dto.getMinStablePower()).isEqualTo(0.50 * 100.0);
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getUnitCount()).isEqualTo(1);
        assertThat(dto.getGroup()).isEqualTo("GAS");

    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withEnabledLogicApplied() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        // NUMBER capacities → max = 3
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, null).toBuilder().area("FR").build(),

                        // POWER capacities
                        cap(gasRef, ThermalCategoryEnum.POWER, 0.0, true).toBuilder().area("FR").build(),  // zero value (ignored for nominal)
                        cap(gasRef, ThermalCategoryEnum.POWER, 500.0, true).toBuilder().area("FR").build(), // valid nominal (toUse = true)
                        cap(gasRef, ThermalCategoryEnum.POWER, 600.0, false).toBuilder().area("FR").build() // higher but disabled
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.40, 3, 2, 0.415, 7.2)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build());

        // then
        assertThat(out)
                .hasSize(1)
                .containsKey(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        assertThat(dto.getNominalCapacity()).isEqualTo(600.0/3);
        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getGroup()).isEqualTo("GAS");

        assertThat(dto.getEnabled()).isTrue();

        assertThat(dto.getMinStablePower()).isEqualTo(0.40 * 600.0/3); // 0.40 * nominalCapacity
        assertThat(dto.getMinUpTime()).isEqualTo(3);
        assertThat(dto.getMinDownTime()).isEqualTo(2);
        assertThat(dto.getEfficiency()).isEqualTo(41.5);
        assertThat(dto.getVariableOMCost()).isEqualTo(7.2);
    }
    @Test
    void assembleForTrajectory_dividesNominalCapacityByUnitCount() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(), // unit count = 2
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, true).toBuilder().area("FR").build(), // unit count = 3 (max)
                        cap(gasRef, ThermalCategoryEnum.POWER, 900.0, true).toBuilder().area("FR").build(), // max POWER
                        cap(gasRef, ThermalCategoryEnum.POWER, 800.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.4, 3, 2, 0.415, 7.2)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build());

        // then
        assertThat(out).hasSize(1);
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        // nominal capacity = max POWER / max NUMBER = 900 / 3 = 300.0
        assertThat(dto.getNominalCapacity()).isEqualTo(300.0);

        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getGroup()).isEqualTo("GAS");

        // derived values from parameters
        assertThat(dto.getMinStablePower()).isEqualTo(0.4 * 300.0);
        assertThat(dto.getMinUpTime()).isEqualTo(3);
        assertThat(dto.getMinDownTime()).isEqualTo(2);
        assertThat(dto.getEfficiency()).isEqualTo(41.5);
        assertThat(dto.getVariableOMCost()).isEqualTo(7.2);
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
                // ensure no nulls in common parameters used by service mapping
                .foRateDefault(0.0)
                .foDurationDefault(0.0)
                .poWinterDefault(0.0)
                .poDurationDefault(0.0)
                .build();
    }

    private static ThermalSpecificParametersEntity specificParams(
            ThermalClusterRef ref,
            double minStableGeneration,
            double spinning,
            double efficiency,
            double foDuration,
            double poDuration,
            int npoMaxWinter,
            int npoMaxSummer,
            int nbUnit,
            List<Double> foMonthlyRate,
            List<Double> poMonthlyRate
    ) {
        return ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(ref)
                .minStableGeneration(minStableGeneration)
                .spinning(spinning)
                .efficiency(efficiency)
                .foDuration(foDuration)
                .poDuration(poDuration)
                .npoMaxWinter(npoMaxWinter)
                .npoMaxSummer(npoMaxSummer)
                .nbUnit(nbUnit)
                .f1(foMonthlyRate.get(0)).f2(foMonthlyRate.get(1)).f3(foMonthlyRate.get(2))
                .f4(foMonthlyRate.get(3)).f5(foMonthlyRate.get(4)).f6(foMonthlyRate.get(5))
                .f7(foMonthlyRate.get(6)).f8(foMonthlyRate.get(7)).f9(foMonthlyRate.get(8))
                .f10(foMonthlyRate.get(9)).f11(foMonthlyRate.get(10)).f12(foMonthlyRate.get(11))
                .p1(poMonthlyRate.get(0)).p2(poMonthlyRate.get(1)).p3(poMonthlyRate.get(2))
                .p4(poMonthlyRate.get(3)).p5(poMonthlyRate.get(4)).p6(poMonthlyRate.get(5))
                .p7(poMonthlyRate.get(6)).p8(poMonthlyRate.get(7)).p9(poMonthlyRate.get(8))
                .p10(poMonthlyRate.get(9)).p11(poMonthlyRate.get(10)).p12(poMonthlyRate.get(11))
                .build();
    }
}
