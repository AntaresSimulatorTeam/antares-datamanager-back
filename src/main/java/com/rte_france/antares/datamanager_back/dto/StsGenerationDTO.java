package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StsGenerationDTO {

    public static class StsClustersViews {
        public interface Properties {}
        public interface Series {}
        public interface Constraints {}
    }

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("group")
    private String groupe;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("injection_nominal_capacity")
    private Integer injection;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("withdrawal_nominal_capacity")
    private Double withdrawal;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("reservoir_capacity")
    private Double storage;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("efficiency")
    private Double efficiencyInjection;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("efficiency_withdrawal")
    private Double efficiencyWithdrawal;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("initial_level")
    private Double initialLevel;

    @JsonView(StsGenerationDTO.StsClustersViews.Properties.class)
    @JsonProperty("initial_level_optim")
    private Boolean initialLevelOptim;

    @JsonView(StsGenerationDTO.StsClustersViews.Series.class)
    @JsonProperty("series")
    private List<String> stsTsList;

    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    @JsonProperty("stsConstraintsSeriesList")
    private List<String> stsConstraintsSeriesList = new ArrayList<>();

    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    @JsonProperty("constraintParameters")
    private Map<String, StsConstraintParameterDTO> constraintParameters = new HashMap<>();
}
