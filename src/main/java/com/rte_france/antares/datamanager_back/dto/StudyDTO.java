package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class StudyDTO {

    @JsonProperty("id")
    Integer id;

    String name;

    String createdBy;

    LocalDateTime creationDate;

    LocalDateTime generationDate;

    @JsonProperty("keywords")
    List<String> tags;

    @JsonProperty("project")
    String project;

    @JsonProperty("projectId")
    String projectId;

    @JsonProperty("status")
    String status;

    @JsonProperty("horizon")
    String horizon;

    @JsonProperty("hvdc")
    Boolean hvdc;

    @JsonProperty("trajectoryIds")
    List<Integer> trajectoryIds;
}
