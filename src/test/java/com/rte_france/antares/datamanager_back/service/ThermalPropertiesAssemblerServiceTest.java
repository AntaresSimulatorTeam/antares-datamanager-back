package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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

    private ThermalCostAssembler thermalCostAssembler;

    private ThermalClusterRef gasRef;
    private ThermalClusterRef nucRef;

    @BeforeEach
    void init() throws Exception {
        thermalCostAssembler = new ThermalCostAssembler(thermalCostTypeRepository);
        var field = ThermalPropertiesAssemblerService.class.getDeclaredField("thermalCostAssembler");
        field.setAccessible(true);
        field.set(service, thermalCostAssembler);

        gasRef = ThermalClusterRef.builder().name("Gas1").build();
        nucRef = ThermalClusterRef.builder().name("NuclearA").build();
        lenient().when(paramModulationService.createMatrixParamModulationTsFiles(any())).thenReturn(List.of());
    }

    @Test
    void extractCommonParams_skipsNA_andNullRef_andNullName_thenGroupsByClusterName() {
        // given
        // Capacity for FR with a valid ref name "Gas1" to build the output key and nominal capacity
        ThermalClusterRef refIncluded = ThermalClusterRef.builder().name("Gas1").build();
        var capTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(refIncluded, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(refIncluded, CategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
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
                        cap(refWithNullName, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(refWithNullName, CategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 3.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 500.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 600.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 3.0, null).toBuilder().area("FR").build(), // max NUMBER = 3
                        cap(gasRef, CategoryEnum.POWER, 0.415, null).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 500.0, null).toBuilder().area("FR").build()  // max POWER = 500
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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(nucRef, CategoryEnum.POWER, 1200.0, true).toBuilder().area("FR").build(),
                        cap(nucRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 3.0, null).toBuilder().area("FR").build(),

                        // POWER capacities
                        cap(gasRef, CategoryEnum.POWER, 0.0, true).toBuilder().area("FR").build(),  // zero value (ignored for nominal)
                        cap(gasRef, CategoryEnum.POWER, 500.0, true).toBuilder().area("FR").build(), // valid nominal (toUse = true)
                        cap(gasRef, CategoryEnum.POWER, 600.0, false).toBuilder().area("FR").build() // higher but disabled
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
                        cap(gasRef, CategoryEnum.NUMBER, 3.0, true).toBuilder().area("FR").build(), // unit count = 3
                        cap(gasRef, CategoryEnum.POWER, 1000.0, true).toBuilder().area("FR").build() // max POWER
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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // CO2 = 100 kg/Net GJ. Conversion: 100 * (3.6 / 1000) / (efficiency / 100)
        // efficiency = 0.415 (41.5%)
        // Result = 100 * 0.0036 / 0.415 = 0.36 / 0.415 = 0.8674... -> 0.867
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
        assertThat(dto.getCo2()).isEqualTo(0.87);
    }

    @Test
    void assembleForTrajectory_computesFallbackCo2_whenCo2IsMissingInParams() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
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

        var econCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("GAS")
                .year(2026)
                .co2EmissionFuel(new BigDecimal("100.0")) // kg/MWht
                .build();

        var enerContent = ThermalEconomicEnerContentEntity.builder()
                .value(new BigDecimal("1.0"))
                .unit("mwht/gj")
                .build();

        var economicTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .thermalEconomicCo2s(List.of(econCo2))
                .thermalEconomicEnerContents(List.of(enerContent))
                .build();
        enerContent.setTrajectory(economicTrajectory);
        econCo2.setTrajectory(economicTrajectory);

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .area("FR")
                .marginalCost(10.0)
                .build();
        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        // Mock for ratio_ncv_hcv
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS"))
                .thenReturn(Optional.of(ThermalCostTypeEntity.builder()
                        .fuel("GAS")
                        .country("FR")
                        .ratioNcvHcv(0.9)
                        .build()));

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, paramTraj, economicTrajectory, specificTrajectory)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // Calculation: (100.0 / 1000) / (40.0 / 100) / 0.9 = 0.1 / 0.4 / 0.9 = 0.25 / 0.9 = 0.2777... -> rounded to 0.28
        assertThat(dto.getCo2()).isEqualTo(0.28);
    }

    @Test
    void assembleForTrajectory_retrievesRatioNcvHcvViaAreaTrajectoryLink() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .fuel("GAS")
                                .build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder()
                                .area("FR")
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

        var econTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .horizon("2025")
                .thermalEconomicCo2s(List.of(economicCo2))
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder().unit("mwht/gj").value(BigDecimal.ONE).build()
                ))
                .build();
        for (ThermalEconomicEnerContentEntity e : econTraj.getThermalEconomicEnerContents()) {
            e.setTrajectory(econTraj);
        }

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
        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2025")
                .thermalCommonParameters(List.of(commonParam))
                .build();

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .area("FR")
                .marginalCost(10.0)
                .build();
        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();


        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj, specificTrajectory,econTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("AT") // Area AT
                                .fuel("GAS")
                                .build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder()
                                .area("AT").build()
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
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder().unit("mwht/gj").value(BigDecimal.ONE).build()
                ))
                .build();
        for (ThermalEconomicEnerContentEntity e : commonTraj.getThermalEconomicEnerContents()) {
            e.setTrajectory(commonTraj);
        }

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // Return empty from findByFuel (no link exists in DB)
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS"))
                .thenReturn(Optional.empty());

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .area("AT")
                .marginalCost(10.0)
                .build();
        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj, specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("AT", gasRef));
        assertThat(dto.getCo2()).isNull();
    }

    private static ThermalClusterCapacityEntity cap(ThermalClusterRef ref, CategoryEnum cat, double value, Boolean toUse) {
        var capacity = ThermalClusterCapacityEntity.builder()
                .thermalClusterRef(ref)
                .category(cat)
                .value(value)
                .toUse(toUse)
                .build();
        capacity.setTrajectory(TrajectoryEntity.builder().fileName("test-trajectory.xlsx").build());
        return capacity;
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
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(gasRef)
                                .fuel("GAS")
                                .area("FR")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 40.0, 7.2, 100.0);
        commonParam.setFuel("GAS");
        commonParam.setStartUpFixCost(1000.0);
        commonParam.setStartUpFuel(500.0);

        var econTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder()
                                .value(new BigDecimal("2.0")) // ener_value
                                .unit("mwht/gj")
                                .build()
                ))
                .build();
        for (ThermalEconomicEnerContentEntity e : econTraj.getThermalEconomicEnerContents()) {
            e.setTrajectory(econTraj);
        }

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .build();

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .marginalCost(30.0) // marginal_cost
                .minStableGeneration(0.4)
                .spinning(0.0)
                .efficiency(0.4)
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
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTraj, econTraj, specificTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));
        // (startup_fuel (500) * 1/(3.6) * efficiency (0.4) * marginal_cost (30.0) + startup_fix_cost (1000))*nominalCapacity (100)
        // (500 * 1/3.6 * 0.4 * 30.0 + 1000)*100 = 100 * 12 + 1000 = 1200 + 1000 = 22600
        assertThat(dto.getStartupCost()).isEqualTo(266667);
    }

    @Test
    void assembleForTrajectory_computesStartupCost_withFallbackMarginalCost() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        ThermalClusterCapacityEntity.builder()
                                .thermalClusterRef(gasRef)
                                .fuel("GAS")
                                .area("FR")
                                .build()
                ))
                .build();

        var commonParam = params(gasRef, 0.4, 3, 2, 0.50, 7.2, 0.0); // efficiency 0.5 (50%), om_cost 7.2
        commonParam.setFuel("GAS");
        commonParam.setStartUpFixCost(1000.0);
        commonParam.setStartUpFuel(500.0);

        // Economic CO2 for computeCo2
        var econCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("GAS")
                .year(2026)
                .co2EmissionFuel(new BigDecimal("90.0")) // kg/MWht
                .build();

        // Fuel costs for fallback marginal cost
        var gasCostType = ThermalCostTypeEntity.builder().fuel("GAS").country("FR").ratioNcvHcv(0.9).build();
        var gasCost = ThermalCostEntity.builder().thermalType(gasCostType).cost(40.0).build();
        gasCostType.setThermalCostEntities(List.of(gasCost));

        var co2CostType = ThermalCostTypeEntity.builder().fuel("CO2").country("FR").build();
        var co2Cost = ThermalCostEntity.builder().thermalType(co2CostType).cost(25.0).build();
        co2CostType.setThermalCostEntities(List.of(co2Cost));

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2026")
                .thermalCommonParameters(List.of(commonParam))
                .build();

        var econTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .horizon("2026")
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder()
                                .value(new BigDecimal("2.0")) // ener_value
                                .unit("mwht/gj")
                                .build()
                ))
                .thermalEconomicCo2s(List.of(econCo2))
                .build();
        for (ThermalEconomicEnerContentEntity e : econTraj.getThermalEconomicEnerContents()) {
            e.setTrajectory(econTraj);
        }
        econCo2.setTrajectory(econTraj);

        var costTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.name())
                .horizon("2026")
                .thermalCosts(List.of(gasCost, co2Cost))
                .build();
        gasCost.setTrajectory(costTraj);
        co2Cost.setTrajectory(costTraj);

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));
        when(thermalCostTypeRepository.findByFuelIgnoreCase("GAS")).thenReturn(Optional.of(gasCostType));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTraj, econTraj, costTraj)).build());

        // then
        var dto = out.get(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef));

        // 1. CO2 calculation: (90 / 1000) / (50 / 100) / 0.9 = 0.09 / 0.5 / 0.9 = 0.18 / 0.9 = 0.2
        assertThat(dto.getCo2()).isCloseTo(0.2, within(0.0001));

        // 2. Marginal cost fallback: (fuelCost / efficiency) + (co2Cost * co2) + omCost
        // (40 / 0.5) + (25 * 0.2) + 7.2 = 80 + 5 + 7.2 = 92.2
        // 3. Startup cost: startup_fuel (500) * 1/3.6 * efficiency (0.5) * marginal_cost (92.2) + startup_fix_cost (1000)
        // 500 * 3.6 * 0.5 * 92.2 + 1000 = 50 * 92.2 + 1000 = 4610 + 1000 = 83980
        assertThat(dto.getStartupCost()).isEqualTo(738889);
    }

    @Test
    void assembleForTrajectory_computesMarketBidCost() {
        // given
        var capTraj = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .fuel("GAS")
                                .build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build()
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

        assertThat(dto.getCo2()).isEqualTo(0.72);
        assertThat(dto.getMarginalCost()).isEqualTo(200);
        assertThat(dto.getMarketBidCost()).isEqualTo(190); // 200 - 10
    }

    @Test
    void assembleForTrajectory_computesFallbackCo2_withCaseInsensitiveFuelAndArea() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("fr").fuel("Gas").build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("fr").fuel("Gas").build()
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

        var economicTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_PARAMETER.name())
                .horizon("2025")
                .thermalEconomicCo2s(List.of(economicCo2))
                .thermalEconomicEnerContents(List.of(
                        ThermalEconomicEnerContentEntity.builder().unit("mwht/gj").value(BigDecimal.ONE).build()
                ))
                .build();
        for (ThermalEconomicEnerContentEntity e : economicTrajectory.getThermalEconomicEnerContents()) {
            e.setTrajectory(economicTrajectory);
        }

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));
        when(thermalCostTypeRepository.findByFuelIgnoreCase("Gas"))
                .thenReturn(Optional.of(ThermalCostTypeEntity.builder().fuel("GAS").country("FR").ratioNcvHcv(0.5).build()));

        // when
        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon("2025")
                .thermalCommonParameters(List.of(commonParam))
                .build();

        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .area("FR")
                .marginalCost(10.0)
                .build();

        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();


        var out = service.assembleForTrajectories(StudyEntity.builder()
                .trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory, economicTrajectory)).build());

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
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .fuel("GAS") // fuel is in capacity
                                .build(),
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder()
                                .area("FR").build()

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
        var resultMapFinal = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, specificTraj)).build());

        // then
        assertNotNull(resultMapFinal);
        assertFalse(resultMapFinal.isEmpty());
        ThermalClusterGenerationDto dtoFound = resultMapFinal.values().iterator().next();
        assertNotNull(dtoFound);
        assert(dtoFound.getMarginalCost() == 100.0);
        // om_cost should be 0.0 since common parameters are missing
        // market_bid_cost = marginal_cost (100.0) - om_cost (0.0) = 100.0
        assertEquals(100.0, dtoFound.getMarketBidCost());
    }

    @Test
    void assembleForTrajectories_doesNotThrow_andKeepsCommonFieldsNull_whenCommonPropertiesAreNull() {
        // given
        // Capacity trajectory: ensures nominalCapacity is computed (POWER max / NUMBER max)
        var capTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.NUMBER, 2.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 200.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common parameters: all fields used by buildFromCommonParameters are null
        var commonAllNull = ThermalCommonParameterEntity.builder()
                .thermalClusterRef(gasRef)
                .minStableGenerationDefault(null)
                .minUpTime(null)
                .minDownTime(null)
                .efficiencyDefault(null)
                .omCost(null)
                .foRateDefault(null)
                .foDurationDefault(null)
                .poWinterDefault(null)
                .poDurationDefault(null)
                .build();

        var commonTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonAllNull))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(
                StudyEntity.builder().trajectories(Set.of(capTraj, commonTraj)).build()
        );

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef);
        assertThat(out).containsKey(key);

        var dto = out.get(key);

        // capacity-derived values exist
        assertThat(dto.getNominalCapacity()).isNotNull();
        assertThat(dto.getEnabled()).isTrue();

        // common-parameter-derived values remain null (because inputs were null and should not NPE)
        assertThat(dto.getMinStablePower()).isNull();
        assertThat(dto.getMinUpTime()).isNull();
        assertThat(dto.getMinDownTime()).isNull();
        assertThat(dto.getEfficiency()).isNull();
        assertThat(dto.getVariableOMCost()).isNull();
        assertThat(dto.getFoCommonRate()).isNull();
        assertThat(dto.getFoCommonDuration()).isNull();
        assertThat(dto.getPoCommonRate()).isNull();
        assertThat(dto.getPoCommonDuration()).isNull();
    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withSpecificParametersFallbackToCommon() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common parameters with durations
        var commonParam = params(gasRef, 0.30, 1, 1, 0.33, 1.0);
        commonParam.setFoDurationDefault(10.0);
        commonParam.setPoDurationDefault(20.0);

        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .build();

        // Specific parameters with NULL durations
        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .foDuration(null)
                .poDuration(null)
                .build();

        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();
        var out = service.assembleForTrajectories(study);

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // Fallback to common duration values
        assertThat(dto.getFoDuration()).isEqualTo(10.0);
        assertThat(dto.getPoDuration()).isEqualTo(20.0);

        // Common duration fields should also be populated as before
        assertThat(dto.getFoCommonDuration()).isEqualTo(10.0);
        assertThat(dto.getPoCommonDuration()).isEqualTo(20.0);
    }

    @Test
    void assembleForTrajectory_buildsOneCluster_withSpecificMonthlyRatesFallbackToCommon() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.NUMBER, 1.0, true).toBuilder().area("FR").build(),
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder().area("FR").build()
                ))
                .build();

        // Common parameters with default rates
        var commonParam = params(gasRef, 0.30, 1, 1, 0.33, 1.0);
        commonParam.setFoRateDefault(0.05);
        commonParam.setPoWinterDefault(0.15);

        var commonTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .thermalCommonParameters(List.of(commonParam))
                .build();

        // Specific parameters with NULL monthly rates (f1 and p1 are null)
        var specificParam = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(gasRef)
                .f1(null).f2(null).f3(null).f4(null).f5(null).f6(null).f7(null).f8(null).f9(null).f10(null).f11(null).f12(null)
                .p1(null).p2(null).p3(null).p4(null).p5(null).p6(null).p7(null).p8(null).p9(null).p10(null).p11(null).p12(null)
                .build();

        var specificTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(specificParam))
                .build();

        when(groupMappingService.toGroup("Gas1")).thenReturn(Optional.of("GAS"));

        // when
        StudyEntity study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory, commonTrajectory, specificTrajectory)).build();
        var out = service.assembleForTrajectories(study);

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", gasRef);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // Fallback to common rate values for all 12 months
        assertThat(dto.getFoMonthlyRate()).hasSize(12).allSatisfy(rate -> assertThat(rate).isEqualTo(0.05));
        assertThat(dto.getPoMonthlyRate()).hasSize(12).allSatisfy(rate -> assertThat(rate).isEqualTo(0.15));
    }

    @Test
    void assembleForTrajectories_prioritizesNASpecificParameters() {
        // given
        // 1. Standard Ref
        var standardRef = ThermalClusterRef.builder()
                .id(101)
                .name("ClusterX")
                .namePemmdb("SomePemmdb")
                .thermalTechnology(new ThermalTechnology())
                .build();

        // 2. NA Ref with same name
        var naRef = ThermalClusterRef.builder()
                .id(102)
                .name("ClusterX")
                .namePemmdb("NA")
                .thermalTechnology(null)
                .build();

        // Capacity Trajectory for standard ref
        var capTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of(
                        cap(standardRef, CategoryEnum.POWER, 100.0, true)
                                .toBuilder().area("FR").build(),
                        cap(standardRef, CategoryEnum.NUMBER, 1.0, true)
                                .toBuilder().area("FR").build()

                ))
                .build();

        // Specific Trajectory containing both standard and NA parameters
        var standardParams = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(standardRef)
                .efficiency(0.5) // 50%
                .area("FR")
                .build();

        var naParams = ThermalSpecificParametersEntity.builder()
                .thermalClusterRef(naRef)
                .efficiency(0.8) // 80% - this should be prioritized
                .area("FR")
                .build();

        var specificTraj = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .thermalSpecificParameters(List.of(standardParams, naParams))
                .build();

        when(groupMappingService.toGroup("ClusterX")).thenReturn(Optional.of("GAS"));

        // when
        var out = service.assembleForTrajectories(StudyEntity.builder().trajectories(Set.of(capTraj, specificTraj)).build());

        // then
        var key = new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", standardRef);
        assertThat(out).containsKey(key);
        var dto = out.get(key);

        // Efficiency should be 80.0 (from NA params) not 50.0 (from standard params)
        assertThat(dto.getEfficiency()).isEqualTo(80.0);
    }
    @Test
    void assembleForTrajectories_shouldThrowExceptionWhenUnitCountIsMissing() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .fileName("capacity.xlsx")
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .build()
                        // No CategoryEnum.NUMBER provided, or it will be empty
                ))
                .build();
        for (var cap : capacityTrajectory.getThermalClusterCapacities()) {
            cap.setTrajectory(capacityTrajectory);
        }

        var study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory)).build();

        // when & then
        var exception = assertThrows(BusinessException.class, () -> service.assembleForTrajectories(study));

        assertThat(exception.getMessage()).contains("Failed to generate study. unit count must not be zero for thermal cluster");
        assertThat(exception.getErrorMessageArguments()).contains(gasRef.getName(), "capacity.xlsx");
    }

    @Test
    void assembleForTrajectories_shouldThrowExceptionWhenUnitCountIsZero() {
        // given
        var capacityTrajectory = TrajectoryEntity.builder()
                .fileName("capacity_null.xlsx")
                .type("THERMAL_CAPACITY")
                .thermalClusterCapacities(List.of(
                        cap(gasRef, CategoryEnum.POWER, 100.0, true).toBuilder()
                                .area("FR")
                                .build(),
                        cap(gasRef, CategoryEnum.NUMBER, 0.0, true).toBuilder()
                                .area("FR")
                                .build()
                ))
                .build();
        for (var cap : capacityTrajectory.getThermalClusterCapacities()) {
            cap.setTrajectory(capacityTrajectory);
        }

        var study = StudyEntity.builder().trajectories(Set.of(capacityTrajectory)).build();

        // when & then
        var exception = assertThrows(BusinessException.class, () -> service.assembleForTrajectories(study));

        assertThat(exception.getMessage()).contains("Failed to generate study. unit count must not be zero for thermal cluster");
        assertThat(exception.getErrorMessageArguments()).contains(gasRef.getName(), "capacity_null.xlsx");
    }
}
