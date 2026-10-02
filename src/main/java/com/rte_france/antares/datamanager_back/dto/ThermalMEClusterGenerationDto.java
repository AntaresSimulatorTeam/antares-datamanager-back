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

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("marginal_cost_modulation")
    private Integer marginalCostModulation;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("marginal_cost_modulation_file")
    private String marginalCostModulationFile;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("market_bid_cost_modulation")
    private Integer marketBidCostModulation;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("market_bid_cost_modulation_file")
    private String marketBidCostModulationFile;
    
    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("mr_modulation")
    private Integer mrModulation;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("mr_modulation_file")
    private String mrModulationFile;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("cm_modulation")
    private Integer cmModulation;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("cm_modulation_file")
    private String cmModulationFile;
    
    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("modulation")
    private List<String> modulationTsList;

}
