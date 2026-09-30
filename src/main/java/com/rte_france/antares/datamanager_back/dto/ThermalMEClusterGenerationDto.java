package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThermalMEClusterGenerationDto {

    public static class ThermalClusterViews {
        public interface Properties {}
        public interface Data {}
        public interface Modulation {}
    }

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("must_run")
    private Boolean mustRun;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nb_unit")
    private Integer nbUnit;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("nominal_capacity")
    private Double nominalCapacity;
    
    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("marginal_cost")
    private Double marginalCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("market_bid_cost")
    private Double marketBidCost;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("group")
    private String group;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("marginal_cost_timestep")
    private String marginalCostTimestep;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("marginal_cost_modulation")
    private Integer marginalCostModulation;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("market_bid_cost_timestep")
    private String marketBidCostTimestep;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("market_bid_cost_modulation")
    private Integer marketBidCostModulation;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("mr_timestep")
    private String mrTimestep;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("mr_modulation")
    private Integer mrModulation;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("cm_timestep")
    private String cmTimestep;

    @JsonView(ThermalClusterViews.Data.class)
    @JsonProperty("cm_modulation")
    private Integer cmModulation;
    
    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("modulation")
    private List<String> modulationTsList;

}
