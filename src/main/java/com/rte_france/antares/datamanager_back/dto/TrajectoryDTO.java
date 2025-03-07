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
public class TrajectoryDTO {

    @JsonProperty("id")
    Integer id;

    @JsonProperty("trajectoryName")
    String fileName;

    @JsonProperty("type")
    String type;

    @JsonProperty("version")
    int version;

    @JsonProperty("userName")
    String createdBy;

    @JsonProperty("creationDate")
    LocalDateTime creationDate;

}
