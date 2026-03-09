package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalCostAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThermalCostAssemblerTest {

    private ThermalCostTypeRepository thermalCostTypeRepository;
    private ThermalCostAssembler thermalCostAssembler;

    @BeforeEach
    void setUp() {
        thermalCostTypeRepository = mock(ThermalCostTypeRepository.class);
        thermalCostAssembler = new ThermalCostAssembler(thermalCostTypeRepository);
    }

    @Test
    void computeCo2_shouldCalculateCorrectlyWithEfficiencyAsPercentage() {
        // given
        // Case 1: efficiency > 1 (e.g., 66.0 for 66%)
        // Example: CO2 = 57, Efficiency = 66
        // Result = 57 * (3.6 / 1000) / (66 / 100) = 57 * 0.0036 / 0.66 = 0.2052 / 0.66 = 0.3109... -> 0.311
        ThermalClusterGenerationDto dto1 = ThermalClusterGenerationDto.builder().efficiency(66.0).build();
        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setCo2(57.0);

        // Case 2: efficiency <= 1 (e.g., 0.66 for 66%)
        ThermalClusterGenerationDto dto2 = ThermalClusterGenerationDto.builder().efficiency(0.66).build();

        // when
        thermalCostAssembler.computeCo2(dto1, List.of(commonParam), null);
        thermalCostAssembler.computeCo2(dto2, List.of(commonParam), null);

        // then
        assertThat(dto1.getCo2()).isEqualTo(0.311);
        assertThat(dto2.getCo2()).isEqualTo(0.311);
    }

    @Test
    void findFuelCost_shouldBeCaseInsensitive() {
        // given
        String fuel = "gas";
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(50.0).build();

        ThermalCostTypeEntity type = new ThermalCostTypeEntity();
        type.setFuel("GAS"); // Uppercase in DB

        ThermalCostEntity costEntity = new ThermalCostEntity();
        costEntity.setThermalType(type);
        costEntity.setCost(123.45);

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setThermalCosts(List.of(costEntity));
        costEntity.setTrajectory(trajectory);

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setOmCost(10.0);

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();


        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(specificParam), List.of(), null, trajectory);

        // then
        ThermalCostTypeEntity co2Type = new ThermalCostTypeEntity();
        co2Type.setFuel("CO2");
        ThermalCostEntity co2CostEntity = new ThermalCostEntity();
        co2CostEntity.setThermalType(co2Type);
        co2CostEntity.setCost(50.0);
        co2CostEntity.setTrajectory(trajectory);

        trajectory.setThermalCosts(List.of(costEntity, co2CostEntity));
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(specificParam), List.of(), null, trajectory);

        assertThat(dto.getMarginalCost()).isNotNull();
        // fuelCost = 123.45, co2Cost = 50.0, efficiency = 50.0 (0.5), omCost = 10.0, dto.co2 = 0.0 (default)
        // marginalCost = (123.45 / 0.5) + (50.0 * 0.0) + 10.0 = 246.9 + 10.0 = 256.9
        assertThat(dto.getMarginalCost()).isEqualTo(256.9);
    }

    @Test
    void computeStartupAndMarginalCost_shouldUseStartUpFixCostFromCommonParam() {
        // given
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        ThermalTechnology tech = new ThermalTechnology();
        tech.setId(1);

        ThermalClusterRef clusterRef = new ThermalClusterRef();
        clusterRef.setThermalTechnology(tech);

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setThermalClusterRef(clusterRef);
        commonParam.setStartUpFixCost(10.0);
        commonParam.setStartUpFuel(50.0);

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(2.0));
        enerContent.setUnit("mwht/gj");

        TrajectoryEntity economicTrajectory = new TrajectoryEntity();
        economicTrajectory.setThermalEconomicEnerContents(List.of(enerContent));
        enerContent.setTrajectory(economicTrajectory);

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setMarginalCost(5.0);
        specificParam.setThermalClusterRef(clusterRef);


        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(specificParam), List.of(), enerContent, null);

        // then
        // startup_fuel * COEFF 3.6 * efficiency * marginal_cost + startup_fix_cost =910.0
        // startupFuel = 50.0 (from commonParam.getStartUpFuel())
        // efficiency = 1.0 (100%)
        // marginalCostValue is 5.0 from specificParam.
        // startup_fix_cost = 10.0

        assertThat(dto.getStartupCost()).isEqualTo(910.0);
    }

    @Test
    void rounding_shouldRoundToThreeDecimalPlaces() {
        // given
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder()
                .efficiency(100.0)
                .build();

        ThermalTechnology tech = new ThermalTechnology();
        tech.setId(1);

        ThermalClusterRef clusterRef = new ThermalClusterRef();
        clusterRef.setThermalTechnology(tech);

        // 1. Test CO2 rounding
        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setCo2(10.23333333331); // 10.23333333331 * 3.6 / 1000 = 0.036839999999916
        thermalCostAssembler.computeCo2(dto, List.of(commonParam), null);
        assertThat(dto.getCo2()).isEqualTo(0.037); // Correct rounding to 3 decimals

        // 2. Test Marginal Cost rounding (fallback)
        // Formula: (fuelCost / (efficiency / 100.0)) + (co2Cost * co2) + om_cost
        // fuelCost = 10.2333, efficiency = 100, co2Cost = 2.0, co2 = 0.037, om_cost = 0.5
        ThermalCostTypeEntity fuelType = new ThermalCostTypeEntity();
        fuelType.setFuel("gas");
        ThermalCostEntity fuelCostEntity = new ThermalCostEntity();
        fuelCostEntity.setThermalType(fuelType);
        fuelCostEntity.setCost(10.2333);

        ThermalCostTypeEntity co2Type = new ThermalCostTypeEntity();
        co2Type.setFuel("CO2");
        ThermalCostEntity co2CostEntity = new ThermalCostEntity();
        co2CostEntity.setThermalType(co2Type);
        co2CostEntity.setCost(2.0);

        TrajectoryEntity costTrajectory = new TrajectoryEntity();
        costTrajectory.setThermalCosts(List.of(fuelCostEntity, co2CostEntity));
        fuelCostEntity.setTrajectory(costTrajectory);
        co2CostEntity.setTrajectory(costTrajectory);

        ThermalCommonParameterEntity commonParam2 = new ThermalCommonParameterEntity();
        commonParam2.setFuel("gas");
        commonParam2.setOmCost(0.5);
        commonParam2.setStartUpFixCost(20.0);
        commonParam2.setStartUpFuel(10.0);
        commonParam2.setThermalClusterRef(clusterRef);

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setThermalClusterRef(clusterRef);

        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam2), List.of(specificParam), List.of(), null, costTrajectory);
        // Expected marginal cost: 10.2333 / (100/100) + 2.0 * 0.037 + 0.5 = 10.2333 + 0.074 + 0.5 = 10.8073 -> rounded to 10.807
        assertThat(dto.getMarginalCost()).isEqualTo(10.807);

        // 3. Test Startup Cost rounding
        // Formula: startup_fuel * 3.6 * efficiency * marginal_cost + startup_fix_cost
        // startup_fuel = 10.0, coefficient = 3.6, efficiency = 1.0, marginal_cost = 10.807, startup_fix_cost = 20.1
        commonParam2.setStartUpFixCost(20.1);

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(1.0));
        enerContent.setUnit("mwht/gj");
        TrajectoryEntity economicTrajectory = new TrajectoryEntity();
        economicTrajectory.setThermalEconomicEnerContents(List.of(enerContent));
        enerContent.setTrajectory(economicTrajectory);

        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam2), List.of(specificParam), List.of(), enerContent, costTrajectory);
        // startupCost = 10 * 3.6 * 10.807 + 20.1 = 389,052 + 20.1 = 408.552 -> 3 decimals = 408.552
        assertThat(dto.getStartupCost()).isEqualTo(409.152);

        // 4. Test Market Bid Cost rounding
        // Formula: marginalCost - omCost
        // 10.807 - 0.5678 = 10.2392 -> rounded to 10.239
        commonParam2.setOmCost(0.5678);
        thermalCostAssembler.computeMarketBidCost(dto, List.of(commonParam2), List.of(specificParam));
        assertThat(dto.getMarketBidCost()).isEqualTo(10.239);
    }
    @Test
    void getEnergyValue_shouldFilterByUnit() {
        // given
        ThermalTechnology tech = new ThermalTechnology();
        tech.setId(1);

        ThermalClusterRef clusterRef = new ThermalClusterRef();
        clusterRef.setThermalTechnology(tech);

        ThermalEconomicEnerContentEntity correctUnit = new ThermalEconomicEnerContentEntity();
        correctUnit.setValue(BigDecimal.valueOf(1.23));
        correctUnit.setUnit("mwht/gj");

        ThermalEconomicEnerContentEntity wrongUnit = new ThermalEconomicEnerContentEntity();
        wrongUnit.setValue(BigDecimal.valueOf(4.56));
        wrongUnit.setUnit("other");

        TrajectoryEntity economicTrajectory = new TrajectoryEntity();
        economicTrajectory.setThermalEconomicEnerContents(List.of(wrongUnit, correctUnit));
        wrongUnit.setTrajectory(economicTrajectory);
        correctUnit.setTrajectory(economicTrajectory);

        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setThermalClusterRef(clusterRef);
        commonParam.setStartUpFixCost(5.0);
        commonParam.setStartUpFuel(2.0);

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setMarginalCost(10.0);
        specificParam.setThermalClusterRef(clusterRef);

        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(specificParam), List.of(), correctUnit, null);

        // then
        // startupCost = startup_fuel * ener_value * efficiency * marginal_cost + startup_fix_cost
        // startup_fuel = 2.0 (from startUpFuel), coefficient = 3.6 , efficiency = 1.0, marginal_cost = 10.0, startup_fix_cost = 5.0
        // startupCost = 2.0 * 3.6 * 1.0 * 10.0 + 5.0 = 77.0
        assertThat(dto.getStartupCost()).isEqualTo(77.0);
    }

    @Test
    void getStartupFuelCapacity_shouldFilterByClusterRef() {
        // given
        ThermalTechnology tech = new ThermalTechnology();
        tech.setId(1);

        ThermalClusterRef ref1 = new ThermalClusterRef();
        ref1.setId(1);
        ref1.setName("GAS NEW DK6");
        ref1.setThermalTechnology(tech);

        ThermalClusterRef ref2 = new ThermalClusterRef();
        ref2.setId(2);
        ref2.setName("OTHER CLUSTER");
        ref2.setThermalTechnology(tech);

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setStartUpFixCost(100.0);
        commonParam.setStartUpFuel(200.0);
        commonParam.setThermalClusterRef(ref1);

        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();
        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setMarginalCost(60.0);
        specificParam.setThermalClusterRef(ref1);

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(1.0));
        enerContent.setUnit("mwht/gj");

        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(specificParam), List.of(), enerContent, null);

        // then
        // startupCost = startupFuel * coefficient 3.6 * efficiency * marginalCost + startupFixCost
        // startupFuel = 200.0 (from commonParam.getStartUpFuel())
        // efficiency = 1.0 (100%)
        // marginalCost = 1.0
        // startupFixCost = 100.0 (from commonParam)
        // startupCost = 200.0 * 3.6 * 1.0 * 60.0 + 100.0 = 43300.0
        assertThat(dto.getStartupCost()).isEqualTo(43300.0);
    }

    @Test
    void computeStartupAndMarginalCost_shouldUseCorrectSpecificParam() {
        // given
        ThermalClusterRef refCommon = new ThermalClusterRef();
        refCommon.setId(1);
        refCommon.setName("Cluster1");

        ThermalClusterRef refSpec1 = new ThermalClusterRef();
        refSpec1.setId(2);
        refSpec1.setName("Cluster1"); // same name as common

        ThermalClusterRef refSpec2 = new ThermalClusterRef();
        refSpec2.setId(3);
        refSpec2.setName("Cluster2"); // different name

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setThermalClusterRef(refCommon);

        ThermalSpecificParametersEntity spec1 = new ThermalSpecificParametersEntity();
        spec1.setThermalClusterRef(refSpec1);
        spec1.setMarginalCost(10.0);

        ThermalSpecificParametersEntity spec2 = new ThermalSpecificParametersEntity();
        spec2.setThermalClusterRef(refSpec2);
        spec2.setMarginalCost(20.0);

        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(spec1, spec2), List.of(), null, null);

        // then
        // Should pick spec1 because the commonParam.name is Cluster1
        assertThat(dto.getMarginalCost()).isEqualTo(10.0);

        // when
        refCommon.setName("Cluster2");
        dto.setMarginalCost(null);
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(commonParam), List.of(spec1, spec2), List.of(), null, null);

        // then
        // Should pick spec2 because the commonParam.name is Cluster2
        assertThat(dto.getMarginalCost()).isEqualTo(20.0);
    }

    @Test
    void getStartupFuelCapacity_shouldReturnZeroIfCommonParamIsNull() {
        // given
        ThermalClusterCapacityEntity capacity = new ThermalClusterCapacityEntity();
        capacity.setFuel("gas");
        capacity.setValue(100.0);

        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        // when
        thermalCostAssembler.computeStartupAndMarginalCost(dto, List.of(), List.of(new ThermalSpecificParametersEntity()), List.of(capacity), null, null);

        // then
        assertThat(dto.getStartupCost()).isNull(); // because marginalCost is null, startupCost is not even computed if marginal cost is null
    }


    @Test
    void frCCGT_new_shouldCalculateCorrectly() {

        // Given: plant configuration from a reference issue
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder()
                .efficiency(15.0)
                .unitCount(13)
                .nominalCapacity(452.6)
                .spinning(0.0)
                .build();

        String fuel = "Gas";

        ThermalTechnology tech = new ThermalTechnology();
        tech.setId(1);

        ThermalClusterRef clusterRef = new ThermalClusterRef();
        clusterRef.setThermalTechnology(tech);

        // Common parameters used in cost computations
        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel(fuel);
        commonParam.setThermalClusterRef(clusterRef);
        commonParam.setCo2(57.0);        // Produces CO2 ≈ 1.368 for 15% efficiency
        commonParam.setOmCost(1.6);
        commonParam.setStartUpFixCost(1607.63202);
        commonParam.setStartUpFuel(100.0);

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setThermalClusterRef(clusterRef);

        // Fuel and CO2 costs used to compute marginal cost
        ThermalCostEntity fuelCost = new ThermalCostEntity();
        fuelCost.setThermalType(ThermalCostTypeEntity.builder().fuel(fuel).build());
        fuelCost.setCost(38.26095);

        ThermalCostEntity co2Cost = new ThermalCostEntity();
        co2Cost.setThermalType(ThermalCostTypeEntity.builder().fuel("CO2").build());
        co2Cost.setCost(100.0);

        TrajectoryEntity costTrajectory = new TrajectoryEntity();
        costTrajectory.setThermalCosts(List.of(fuelCost, co2Cost));

        fuelCost.setTrajectory(costTrajectory);
        co2Cost.setTrajectory(costTrajectory);

        // Energy content used for startup calculation
        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(1.0));
        enerContent.setUnit("mwht/gj");

        TrajectoryEntity economicTrajectory = new TrajectoryEntity();
        economicTrajectory.setThermalEconomicEnerContents(List.of(enerContent));
        enerContent.setTrajectory(economicTrajectory);

        // When
        thermalCostAssembler.computeCo2(dto, List.of(commonParam), enerContent);
        thermalCostAssembler.computeStartupAndMarginalCost(
                dto,
                List.of(commonParam),
                List.of(specificParam),
                List.of(),
                enerContent,
                costTrajectory
        );
        thermalCostAssembler.computeMarketBidCost(dto, List.of(commonParam), List.of(specificParam));

        // Then
        assertThat(dto.getCo2()).isEqualTo(1.368);
        assertThat(dto.getMarginalCost()).isEqualTo(393.473);
        assertThat(dto.getStartupCost()).isEqualTo(22855.174);
        assertThat(dto.getMarketBidCost()).isEqualTo(391.873);
    }

    @Test
    void computeCo2_shouldUseFallbackWhenCommonParamCo2IsNull() {
        // given
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(50.0).build();
        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setFuel("gas");
        commonParam.setCo2(null);

        // Scenario 1: findEconomicCo2 returns a value
        ThermalEconomicCo2Entity economicCo2 = ThermalEconomicCo2Entity.builder()
                .fuel("gas")
                .year(2025)
                .co2EmissionFuel(BigDecimal.valueOf(100.0))
                .build();

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setHorizon("2025");
        trajectory.setThermalEconomicCo2s(List.of(economicCo2));

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setTrajectory(trajectory);

        when(thermalCostTypeRepository.findByFuelIgnoreCase("gas")).thenReturn(Optional.empty());

        // when
        thermalCostAssembler.computeCo2(dto, List.of(commonParam), enerContent);

        // then
        // Formula: (co2EmissionFuel / 1000) / efficiency / ratio
        // (100.0 / 1000.0) / 0.5 / 1.0 = 0.1 / 0.5 = 0.2
        assertThat(dto.getCo2()).isEqualTo(0.2);

        // Scenario 2: findEconomicCo2 is empty, findCo2Cost returns a value
        dto.setCo2(null);
        trajectory.setThermalEconomicCo2s(List.of());

        ThermalCostTypeEntity co2Type = new ThermalCostTypeEntity();
        co2Type.setFuel("CO2");
        ThermalCostEntity co2CostEntity = new ThermalCostEntity();
        co2CostEntity.setThermalType(co2Type);
        co2CostEntity.setCost(50.0);

        trajectory.setThermalCosts(List.of(co2CostEntity));
        co2CostEntity.setTrajectory(trajectory);

        // when
        thermalCostAssembler.computeCo2(dto, List.of(commonParam), enerContent);

        // then
        // Formula: (co2Cost / 1000.0) / efficiency / ratio
        // (50.0 / 1000.0) / 0.5 / 1.0 = 0.05 / 0.5 = 0.1
        assertThat(dto.getCo2()).isEqualTo(0.1);

        // Scenario 3: With ratioNcvHcv
        dto.setCo2(null);
        ThermalCostTypeEntity gasType = new ThermalCostTypeEntity();
        gasType.setFuel("gas");
        gasType.setRatioNcvHcv(0.9);
        when(thermalCostTypeRepository.findByFuelIgnoreCase("gas")).thenReturn(Optional.of(gasType));

        // when
        thermalCostAssembler.computeCo2(dto, List.of(commonParam), enerContent);

        // then
        // Formula: (50.0 / 1000.0) / 0.5 / 0.9 = 0.05 / 0.5 / 0.9 = 0.1 / 0.9 = 0.11111... -> 0.111
        assertThat(dto.getCo2()).isEqualTo(0.111);
    }
}
