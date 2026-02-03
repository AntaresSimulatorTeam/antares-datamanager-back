package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalCostAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThermalCostAssemblerTest {

    private final ThermalCostAssembler thermalCostAssembler = new ThermalCostAssembler();

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

        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setOmCost(10.0);

        // when
        thermalCostAssembler.computeStartupCost(dto, commonParam, fuel, List.of(), List.of(), List.of(trajectory));

        // then
        ThermalCostTypeEntity co2Type = new ThermalCostTypeEntity();
        co2Type.setFuel("CO2");
        ThermalCostEntity co2CostEntity = new ThermalCostEntity();
        co2CostEntity.setThermalType(co2Type);
        co2CostEntity.setCost(50.0);
        trajectory.setThermalCosts(List.of(costEntity, co2CostEntity));

        thermalCostAssembler.computeStartupCost(dto, commonParam, fuel, List.of(), List.of(), List.of(trajectory));

        assertThat(dto.getMarginalCost()).isNotNull();
        // fuelCost = 123.45, co2Cost = 50.0, efficiency = 50.0 (0.5), omCost = 10.0, dto.co2 = 0.0 (default)
        // marginalCost = (123.45 / 0.5) + (50.0 * 0.0) + 10.0 = 246.9 + 10.0 = 256.9
        assertThat(dto.getMarginalCost()).isEqualTo(256.9);
    }

    @Test
    void computeStartupCost_shouldBeCaseInsensitiveForFuelInCapacities() {
        // given
        String fuel = "gas";
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        ThermalClusterCapacityEntity capacity = new ThermalClusterCapacityEntity();
        capacity.setFuel("GAS"); // Case mismatch
        capacity.setValue(10.0);

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(2.0));

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setThermalEconomicEnerContents(List.of(enerContent));

        // when
        thermalCostAssembler.computeStartupCost(dto, null, fuel, List.of(), List.of(capacity), List.of(trajectory));

        // then
        // startup_fuel * ener_value * efficiency * marginal_cost + startup_fix_cost
        // startupFuelCap = 10.0 (because of case-insensitive match)
        // enerValue = 2.0
        // efficiency = 1.0 (100%)
        // marginalCostValue is null -> findFuelCost is called.
        // We haven't set up findFuelCost to return something, so marginalCostValue remains null.
        // But we can check if startupCost was set if we provide marginal cost in specific params.

        ThermalSpecificParametersEntity specificParam = new ThermalSpecificParametersEntity();
        specificParam.setMarginalCost(5.0);

        thermalCostAssembler.computeStartupCost(dto, null, fuel, List.of(specificParam), List.of(capacity), List.of(trajectory));

        // startupCost = 10.0 * 2.0 * 1.0 * 5.0 + 0.0 = 100.0
        assertThat(dto.getStartupCost()).isEqualTo(100.0);
    }

    @Test
    void rounding_shouldRoundToThreeDecimalPlaces() {
        // given
        ThermalClusterGenerationDto dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();

        // 1. Test CO2 rounding
        ThermalCommonParameterEntity commonParam = new ThermalCommonParameterEntity();
        commonParam.setCo2(10.23333333331); // 10.23333333331 * 3.6 / 1000 = 0.036839999999916
        thermalCostAssembler.computeCo2(dto, commonParam, null, List.of(), null);
        assertThat(dto.getCo2()).isEqualTo(0.037);

        // 2. Test Marginal Cost rounding (fallback)
        // Formula: (fuelCost / (efficiency / 100.0)) + (co2Cost * co2) + om_cost
        // fuelCost = 10.2333, efficiency = 100, co2Cost = 2.0, co2 = 0.037, om_cost = 0.5
        // (10.2333 / 1.0) + (2.0 * 0.037) + 0.5 = 10.2333 + 0.074 + 0.5 = 10.8073 -> 10.807
        TrajectoryEntity trajectory = new TrajectoryEntity();
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
        trajectory.setThermalCosts(List.of(fuelCostEntity, co2CostEntity));

        ThermalCommonParameterEntity commonParam2 = new ThermalCommonParameterEntity();
        commonParam2.setOmCost(0.5);

        thermalCostAssembler.computeStartupCost(dto, commonParam2, "gas", List.of(), List.of(), List.of(trajectory));
        assertThat(dto.getMarginalCost()).isEqualTo(10.807);

        // 3. Test Startup Cost rounding
        // Formula: startup_fuel * ener_value * efficiency * marginal_cost + startup_fix_cost
        // startup_fuel = 1.2345, ener_value = 1.0, efficiency = 1.0, marginal_cost = 10.807, startup_fix_cost = 0.1
        // 1.2345 * 1.0 * 1.0 * 10.807 + 0.1 = 13.3412415 + 0.1 = 13.4412415 -> 13.441
        ThermalClusterCapacityEntity capacity = new ThermalClusterCapacityEntity();
        capacity.setFuel("gas");
        capacity.setValue(1.2345);

        ThermalEconomicEnerContentEntity enerContent = new ThermalEconomicEnerContentEntity();
        enerContent.setValue(BigDecimal.valueOf(1.0));
        trajectory.setThermalEconomicEnerContents(List.of(enerContent));

        commonParam2.setStartUpFixCost(0.1);

        thermalCostAssembler.computeStartupCost(dto, commonParam2, "gas", List.of(), List.of(capacity), List.of(trajectory));
        assertThat(dto.getStartupCost()).isEqualTo(13.441);

        // 4. Test Market Bid Cost rounding
        // Formula: marginalCost - omCost
        // 10.807 - 0.5678 = 10.2392 -> 10.239
        commonParam2.setOmCost(0.5678);
        thermalCostAssembler.computeMarketBidCost(dto, commonParam2, List.of());
        assertThat(dto.getMarketBidCost()).isEqualTo(10.239);
    }
}
