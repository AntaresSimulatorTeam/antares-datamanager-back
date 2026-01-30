package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalCostAssembler;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
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

    private final ThermalCostAssembler thermalCostAssembler = new ThermalCostAssembler();

    private ThermalClusterRef gasRef;
    private ThermalClusterRef nucRef;

    @BeforeEach
    void init() throws Exception {
        var field = ThermalPropertiesAssemblerService.class.getDeclaredField("thermalCostAssembler");
        field.setAccessible(true);
        field.set(service, thermalCostAssembler);

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

        assertThat(dto.getMinStablePower()).isEqualTo(66.68); // 0.40 * nominalCapacity (rounded)
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
        // given: Add a POWER category so we don't have nulls on outputs
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
        var capacityTrajectory = TrajectoryEntity.builder()
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

        var parameterTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(
                        params(gasRef, 0.40, 3, 2, 0.415, 7.2)
                ))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, parameterTrajectory)).build());

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
        var capacityTrajectory = TrajectoryEntity.builder()
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
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, paramTraj)).build());

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
        var capacityTrajectory = TrajectoryEntity.builder()
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
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS"))
                .thenReturn(Optional.of(ThermalCostTypeEntity.builder()
                        .fuel("GAS")
                        .country("FR")
                        .ratioNcvHcv(0.9)
                        .build()));

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, paramTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // Calculation: (100.0 / 1000) / (40.0 / 100) / 0.9 = 0.1 / 0.4 / 0.9 = 0.25 / 0.9 = 0.2777777... -> 0.278
        assertThat(dto.getCo2()).isEqualTo(0.278);
    }

    @Test
    void assembleForTrajectory_retrievesRatioNcvHcvViaAreaTrajectoryLink() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("AREA_FR")
                                .fuel("GAS")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.2, 1, 1, 0.5, 10.0);
        commonParam.setFuel("gas"); // Lowercase fuel
        commonParam.setCo2(null); // Force fallback

        var economicCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("gas") // Lowercase fuel
                .year(2025)
                .co2EmissionFuel(new BigDecimal("1000"))
                .build();

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2025")
                .thermalCommonParameters(List.of(commonParam))
                .thermalEconomicCo2s(List.of(economicCo2))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // Match with area linkage (AREA_FR -> COUNTRY_FR)
        ThermalCostTypeEntity gasCostType = ThermalCostTypeEntity.builder()
                .fuel("GAS")
                .country("COUNTRY_FR")
                .ratioNcvHcv(0.8)
                .build();

        // Mock findByFuel
        when(thermalCostTypeRepository.findByFuelIgnoreCase("gas"))
                .thenReturn(Optional.of(gasCostType));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("AREA_FR", gasRef));

        // Formula: (co2EmissionFuel / 1000) / (efficiency / 100) / ratioNcvHcv
        // co2EmissionFuel = 1000
        // efficiency = 50 (from 0.5 * 100)
        // ratioNcvHcv = 0.8
        // co2 = (1000 / 1000) / (50 / 100) / 0.8 = 1 / 0.5 / 0.8 = 2 / 0.8 = 2.5
        assertThat(dto.getCo2()).isEqualTo(2.5);
    }

    @Test
    void assembleForTrajectory_returnsNullCo2WhenNoLink() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("AT") // Area AT
                                .fuel("GAS")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.2, 1, 1, 0.5, 10.0);
        commonParam.setFuel("GAS");
        commonParam.setCo2(null);

        var economicCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("GAS")
                .year(2025)
                .co2EmissionFuel(new BigDecimal("1000"))
                .build();

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2025")
                .thermalCommonParameters(List.of(commonParam))
                .thermalEconomicCo2s(List.of(economicCo2))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // Return empty from findByFuel (no link exists in DB)
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS"))
                .thenReturn(Optional.empty());

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("AT", gasRef));
        assertThat(dto.getCo2()).isNull();
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
    @Test
    void assembleForTrajectory_computesStartupCost() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(gasRef)
                                .fuel("GAS")
                                .value(50.0) // startup_fuel
                                .area("FR")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 0.40, 7.2, 100.0);
        commonParam.setFuel("GAS");
        commonParam.setStartUpFixCost(1000.0);

        var paramTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder()
                                .value(new BigDecimal("2.0")) // ener_value
                                .build()
                ))
                .build();

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marginalCost(30.0) // marginal_cost
                .minStableGeneration(0.4)
                .spinning(0.0)
                .efficiency(0.40)
                .foDuration(0.0)
                .poDuration(0.0)
                .f1(0.0).f2(0.0).f3(0.0).f4(0.0).f5(0.0).f6(0.0).f7(0.0).f8(0.0).f9(0.0).f10(0.0).f11(0.0).f12(0.0)
                .p1(0.0).p2(0.0).p3(0.0).p4(0.0).p5(0.0).p6(0.0).p7(0.0).p8(0.0).p9(0.0).p10(0.0).p11(0.0).p12(0.0)
                .npoMaxWinter(0)
                .npoMaxSummer(0)
                .nbUnit(3)
                .area("FR")
                .build();

        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, paramTraj, specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // startup_fuel (50) * ener_value (2.0) * efficiency (0.4) * marginal_cost (30.0) + startup_fix_cost (1000)
        // 50 * 2.0 * 0.4 * 30.0 + 1000 = 100 * 12 + 1000 = 1200 + 1000 = 2200
        assertThat(dto.getStartupCost()).isEqualTo(2200.0);
    }

    @Test
    void assembleForTrajectory_computesStartupCost_withFallbackMarginalCost() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(gasRef)
                                .fuel("GAS")
                                .value(50.0) // startup_fuel
                                .area("FR")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 0.50, 7.2, 0.0); // efficiency 0.5 (50%), om_cost 7.2
        commonParam.setFuel("GAS");
        commonParam.setStartUpFixCost(1000.0);

        // Economic CO2 for computeCo2
        var econCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("GAS")
                .year(2026)
                .co2EmissionFuel(new BigDecimal("90.0")) // kg/MWht
                .build();

        var parameterTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2026")
                .thermalCommonParameters(List.of(commonParam))
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder()
                                .value(new BigDecimal("2.0")) // ener_value
                                .build()
                ))
                .thermalEconomicCo2s(List.of(econCo2))
                .build();

        // Fuel costs for fallback marginal cost
        var gasCostType = ThermalCostTypeEntity.builder().fuel("GAS").country("FR").ratioNcvHcv(0.9).build();
        var gasCost = ThermalCostEntity.builder().thermalType(gasCostType).cost(40.0).build();
        gasCostType.setThermalCostEntities(List.of(gasCost));

        var co2CostType = ThermalCostTypeEntity.builder().fuel("CO2").country("FR").build();
        var co2Cost = ThermalCostEntity.builder().thermalType(co2CostType).cost(25.0).build();
        co2CostType.setThermalCostEntities(List.of(co2Cost));

        var costTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .thermalCosts(List.of(gasCost, co2Cost))
                .build();
        gasCost.setTrajectory(costTraj);
        co2Cost.setTrajectory(costTraj);

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS")).thenReturn(Optional.of(gasCostType));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, parameterTrajectory, costTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        // 1. CO2 calculation: (90 / 1000) / (50 / 100) / 0.9 = 0.09 / 0.5 / 0.9 = 0.18 / 0.9 = 0.2
        assertThat(dto.getCo2()).isCloseTo(0.2, within(0.0001));

        // 2. Marginal cost fallback: (fuelCost / efficiency) + (co2Cost * co2) + omCost
        // (40 / 0.5) + (25 * 0.2) + 7.2 = 80 + 5 + 7.2 = 92.2
        // 3. Startup cost: startup_fuel (50) * ener_value (2.0) * efficiency (0.5) * marginal_cost (92.2) + startup_fix_cost (1000)
        // 50 * 2.0 * 0.5 * 92.2 + 1000 = 50 * 92.2 + 1000 = 4610 + 1000 = 5610
        assertThat(dto.getStartupCost()).isCloseTo(5610.0, within(0.0001));
    }

    @Test
    void assembleForTrajectory_computesMarketBidCost() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 0.50, 7.2, 100.0);
        commonParam.setFuel("GAS");

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marketBid(105.0) // market_bid exists
                .marginalCost(100.0)
                .spinning(0.0)
                .efficiency(0.40)
                .foDuration(0.0)
                .poDuration(0.0)
                .f1(0.0).f2(0.0).f3(0.0).f4(0.0).f5(0.0).f6(0.0).f7(0.0).f8(0.0).f9(0.0).f10(0.0).f11(0.0).f12(0.0)
                .p1(0.0).p2(0.0).p3(0.0).p4(0.0).p5(0.0).p6(0.0).p7(0.0).p8(0.0).p9(0.0).p10(0.0).p11(0.0).p12(0.0)
                .nbUnit(3)
                .area("FR")
                .build();

        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .build(), specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        assertThat(dto.getMarketBidCost()).isEqualTo(105.0);
    }

    @Test
    void assembleForTrajectory_computesMarketBidCost_fallbackToMarginalMinusOm() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 0.50, 7.2, 100.0); // om_cost = 7.2
        commonParam.setFuel("GAS");

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marketBid(null) // market_bid missing
                .marginalCost(100.0)
                .spinning(0.0)
                .efficiency(0.40)
                .foDuration(0.0)
                .poDuration(0.0)
                .f1(0.0).f2(0.0).f3(0.0).f4(0.0).f5(0.0).f6(0.0).f7(0.0).f8(0.0).f9(0.0).f10(0.0).f11(0.0).f12(0.0)
                .p1(0.0).p2(0.0).p3(0.0).p4(0.0).p5(0.0).p6(0.0).p7(0.0).p8(0.0).p9(0.0).p10(0.0).p11(0.0).p12(0.0)
                .nbUnit(3)
                .area("FR")
                .build();

        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .build(), specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // market_bid_cost = marginal_cost (100.0) - om_cost (7.2) = 92.8
        assertThat(dto.getMarketBidCost()).isEqualTo(92.8);
    }

    @Test
    void assembleForTrajectory_computesCosts_correctlyPopulated() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .fuel("GAS")
                                .build()
                ))
                .build();

        var commonParam = ThermalCommonParameterEntity.builder()
                .thermalClusterRef(gasRef)
                .efficiencyDefault(0.5)
                .minStableGenerationDefault(0.2)
                .minUpTime(1.0)
                .minDownTime(1.0)
                .foRateDefault(0.0)
                .foDurationDefault(0.0)
                .poWinterDefault(0.0)
                .poDurationDefault(0.0)
                .omCost(10.0)
                .co2(100.0) // 100 * 0.0036 = 0.36
                .build();

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marginalCost(200.0)
                .area("FR")
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(
                capTraj,
                TrajectoryEntity.builder().type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name()).thermalCommonParameters(List.of(commonParam)).build(),
                TrajectoryEntity.builder().type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name()).thermalSpecificParameters(List.of(specificParam)).build()
        )).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        assertThat(dto.getCo2()).isEqualTo(0.36);
        assertThat(dto.getMarginalCost()).isEqualTo(200.0);
        assertThat(dto.getMarketBidCost()).isEqualTo(190.0); // 200 - 10
    }

    @Test
    void assembleForTrajectory_computesFallbackCo2_withCaseInsensitiveFuelAndArea() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("fr")
                                .fuel("Gas")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.2, 1, 1, 0.5, 10.0);
        commonParam.setFuel("Gas");
        commonParam.setCo2(null); // Force fallback

        var economicCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("Gas")
                .year(2025)
                .co2EmissionFuel(new BigDecimal("1000"))
                .build();

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2025")
                .thermalCommonParameters(List.of(commonParam))
                .thermalEconomicCo2s(List.of(economicCo2))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));
        when(thermalCostTypeRepository.findByFuelIgnoreCase("Gas"))
                .thenReturn(Optional.of(ThermalCostTypeEntity.builder().fuel("GAS").country("FR").ratioNcvHcv(0.5).build()));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("fr", gasRef));

        // Formula: (co2EmissionFuel / 1000) / (efficiency / 100) / ratioNcvHcv
        // co2EmissionFuel = 1000
        // efficiency = 50 (from 0.5 * 100)
        // ratioNcvHcv = 0.5
        // co2 = (1000 / 1000) / (50 / 100) / 0.5 = 1 / 0.5 / 0.5 = 2 / 0.5 = 4.0
        assertThat(dto.getCo2()).isEqualTo(4.0);
    }

    @Test
    void assembleForTrajectory_computesCosts_evenWhenCommonParametersMissing() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, ThermalCategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .fuel("GAS") // fuel is in capacity
                                .build()
                ))
                .build();

        // NO THERMAL_TECHNICAL_COMMON_PARAMETER trajectory

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marginalCost(100.0) // specifically set marginal cost
                .efficiency(0.50)
                .area("FR")
                .build();

        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        assertThat(dto).isNotNull();
        assertThat(dto.getMarginalCost()).isEqualTo(100.0);
        // om_cost should be 0.0 since common parameters are missing
        // market_bid_cost = marginal_cost (100.0) - om_cost (0.0) = 100.0
        assertThat(dto.getMarketBidCost()).isEqualTo(100.0);
    }
}
