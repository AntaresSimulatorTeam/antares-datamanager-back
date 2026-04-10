package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StsConstraintParameterDTO {
    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    private String variable;
    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    private String operator;
    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    private String enabled;
    @JsonView(StsGenerationDTO.StsClustersViews.Constraints.class)
    private List<List<Integer>> hours;
}
