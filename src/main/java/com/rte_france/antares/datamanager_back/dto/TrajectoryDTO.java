package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @JsonProperty("area")
    String area;

    @JsonProperty("technology")
    String technology;

    @JsonProperty("creationDate")
    LocalDateTime creationDate;
    
    @JsonProperty("hasTimeSeries")
    Boolean hasTimeSeries;

    @JsonProperty("horizon")
    String horizon;

}
