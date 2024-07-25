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

    @JsonProperty("study_name")
    String name;

    @JsonProperty("user_name")
    String createdBy;

    @JsonProperty("creation_date")
    LocalDateTime creationDate;

    List<String> tags;

    @JsonProperty("project")
    String projet;

    @JsonProperty("status")
    String status;

    @JsonProperty("horizon")
    String horizon;
}
