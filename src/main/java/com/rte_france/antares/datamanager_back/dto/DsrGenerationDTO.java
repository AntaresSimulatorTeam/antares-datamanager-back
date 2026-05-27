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
public class DsrGenerationDTO {

    public static class DsrClustersViews {
        public interface Properties {}
        public interface Data{}
        public interface Modulation {}
    }
    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("group")
    private String group;

    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("nominal_capacity")
    private Double nominalCapacity;

    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("unit_count")
    private Integer unitCount;

    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("marginal_cost")
    private Double marginalCost;

    @JsonView(DsrClustersViews.Properties.class)
    @JsonProperty("market_bid_cost")
    private Double marketBidCost;

    @JsonView(DsrClustersViews.Modulation.class)
    @JsonProperty("modulation")
    private List<String> dsrTsList;

    @JsonView(DsrClustersViews.Data.class)
    @JsonProperty("fo_duration")
    private Double foDuration;

    @JsonView(DsrClustersViews.Data.class)
    @JsonProperty("fo_monthly_rate")
    private List<Double> foMonthlyRate;

    @JsonView(DsrClustersViews.Data.class)
    @JsonProperty("nb_hour_per_day")
    private Double nbHourPerDay;

    @JsonView(DsrClustersViews.Data.class)
    @JsonProperty("max_hour_per_day")
    private Double maxHourPerDay;

}
