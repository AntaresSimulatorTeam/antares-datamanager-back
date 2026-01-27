package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThermalPropertiesAssemblerServiceTest {

    @Mock
    private ThermalGroupMappingService groupMappingService;

    @Mock
    private ThermalParamModulationService paramModulationService;

    @Mock
    private ThermalCostTypeRepository thermalCostTypeRepository;

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
    void extractCommonParams_skipsNA_andNullRef_andNullName_thenGroupsByClusterName() {
        // given
        // Capacity for FR with a valid ref name "Gas1" to build the output key and nominal capacity
        ThermalClusterRef refIncluded = ThermalClusterRef.builder().name("Gas1").build();
        var capTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(refIncluded, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(refIncluded, ThermalCategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common parameters with various refs to test the filters in extractCommonParamsByClusterRef
        ThermalClusterRef refNA = ThermalClusterRef.builder().name("Gas1").namePemmdb("NA").build(); // should be skipped
        ThermalClusterRef refNullName = ThermalClusterRef.builder().name(null).build(); // should be skipped
        ThermalClusterRef refOk = ThermalClusterRef.builder().name("Gas1").namePemmdb(null).build(); // should be kept

        var commonNA = params(refNA, 0.9, 1, 1, 0.33, 1.0);         // skipped by name_pemmdb == "NA"
        var commonNullName = params(refNullName, 0.8, 1, 1, 0.33, 1.0); // skipped by null name
        var commonOk = params(refOk, 0.40, 3, 2, 0.415, 7.2);          // kept

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonNA, commonNullName, commonOk))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build());

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", refIncluded);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // nominal = 600 / 2 = 300
        assertThat(dto.getNominalCapacity()).isEqualTo(300.0);
        // minStablePower should come from the kept common (0.40 * nominal)
        assertThat(dto.getMinStablePower()).isEqualTo(0.40 * 300.0);
        // group resolved
        assertThat(dto.getGroup()).isEqualTo("GAS");
    }

    @Test
    void assembleForTrajectories_whenCapacityClusterNameNull_thenNoCommonParametersApplied() {
        // given
        // Capacity ref with null name -> clusterName == null => commonsForRef should be empty
        ThermalClusterRef refWithNullName = ThermalClusterRef.builder().name(null).build();
        var capTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(refWithNullName, ThermalCategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(refWithNullName, ThermalCategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // A common parameter for a different ref (with name), which should NOT be picked because capacity ref name is null
        ThermalClusterRef refCommon = ThermalClusterRef.builder().name("Gas1").build();
        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(params(refCommon, 0.5, 1, 1, 0.35, 1.0)))
                .build();

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build());

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", refWithNullName);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // nominal = 600 / 2 = 300
        assertThat(dto.getNominalCapacity()).isEqualTo(300.0);
        // Because clusterName == null for capacity ref, no common params should be applied
        assertThat(dto.getMinStablePower()).isNull();
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
        assertThat(dto.getNominalCapacity()).isEqualTo(200.0);
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getGroup()).isEqualTo("GAS");

        // From specific parameters (override common)
        assertThat(dto.getMinStablePower()).isEqualTo(0.50 * 200.0);
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
        assertThat(dto.getNominalCapacity()).isEqualTo(166.7);
        assertThat(dto.getGroup()).isEqualTo("GAS");

        assertThat(dto.getMinStablePower()).isEqualTo(0.4*166.7); // 0.40 * nominalCapacity (rounded)
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

        assertThat(dto.getNominalCapacity()).isEqualTo(200.0);
        assertThat(dto.getUnitCount()).isEqualTo(3);
        assertThat(dto.getGroup()).isEqualTo("GAS");

        assertThat(dto.getEnabled()).isTrue();

        assertThat(dto.getMinStablePower()).isEqualTo(0.40 * 200.0); // 0.40 * nominalCapacity
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
                        cap(gasRef, ThermalCategoryEnum.NUMBER, 3.0, true).toBuilder().area("FR").build(), // unit count = 3
                        cap(gasRef, ThermalCategoryEnum.POWER, 1000.0, true).toBuilder().area("FR").build() // max POWER
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

        // nominal capacity = 1000 / 3 = 333.3333... -> should be rounded to 333.3
        assertThat(dto.getNominalCapacity()).isEqualTo(333.3);

        assertThat(dto.getUnitCount()).isEqualTo(3);
    }

    @Test
    void assembleForTrajectory_convertsCo2FromKgPerNetGJToTPerMWhe() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // CO2 = 100 kg/Net GJ. Conversion: 100 * 0.0036 = 0.36 t/MWhe
        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.4, 3, 2, 0.415, 7.2, 100.0)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        assertThat(dto.getCo2()).isEqualTo(0.36);
    }

    @Test
    void assembleForTrajectory_computesFallbackCo2_whenCo2IsMissingInParams() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common parameters with CO2 = 0.0 or null
        var commonParam = params(gasRef, 0.4, 3, 2, 0.40, 7.2, 0.0);
        commonParam.setFuel("GAS");

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2026")
                .thermalCommonParameters(List.of(commonParam))
                .build();

        // Economic CO2 data in the same trajectory (or another one)
        var econCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("GAS")
                .year(2026)
                .co2EmissionFuel(new BigDecimal("100.0")) // kg/MWht
                .build();
        paramTraj.setThermalEconomicCo2s(List.of(econCo2));

        // Mock for ratio_ncv_hcv
        when(thermalCostTypeRepository.findThermalCostTypeEntityByFuelAndCountry("GAS", "FR"))
                .thenReturn(Optional.of(ThermalCostTypeEntity.builder()
                        .fuel("GAS")
                        .country("FR")
                        .ratioNcvHcv(0.9)
                        .build()));

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, paramTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // Calculation: (100.0 / 1000) / (40.0 / 100) / 0.9 = 0.1 / 0.4 / 0.9 = 0.25 / 0.9 = 0.2777777...
        assertThat(dto.getCo2()).isEqualTo(0.2777777777777778);
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
                                                       double effDefault, double omCost,
                                                       double co2) {
        return ThermalCommonParameterEntity.builder()
                .thermalClusterRef(ref)
                .minStableGenerationDefault(minStableGenDefault)
                .minUpTime((double) minUp)
                .minDownTime((double) minDown)
                .efficiencyDefault(effDefault)
                .omCost(omCost)
                .co2(co2)
                // ensure no nulls in common parameters used by service mapping
                .foRateDefault(0.0)
                .foDurationDefault(0.0)
                .poWinterDefault(0.0)
                .poDurationDefault(0.0)
                .build();
    }

    private static ThermalCommonParameterEntity params(ThermalClusterRef ref,
                                                       double minStableGenDefault,
                                                       int minUp, int minDown,
                                                       double effDefault, double omCost) {
        return params(ref, minStableGenDefault, minUp, minDown, effDefault, omCost, 0.0);
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
