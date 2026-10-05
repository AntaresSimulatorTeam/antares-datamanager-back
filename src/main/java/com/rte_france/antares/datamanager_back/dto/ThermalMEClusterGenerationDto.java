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
        public interface Series {}
    }

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("must_run")
    private Boolean mustRun;

    @JsonView(ThermalClusterViews.Properties.class)
    @JsonProperty("unit_count")
    private Integer unitCount;

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
    @JsonProperty("market_bid_modulation")
    private Integer marketBidModulation;

    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("capacity_modulation")
    private Integer capacityModulation;
    
    @JsonView(ThermalClusterViews.Modulation.class)
    @JsonProperty("must_run_modulation")
    private Integer mustRunModulation;
    
    @JsonView(ThermalClusterViews.Series.class)
    @JsonProperty("series")
    private List<String> tsList;

}
