package com.rte_france.antares.datamanager_back.dto;

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
public class FsTrajectoryDTO {


    @JsonProperty("trajectoryName")
    String fileName;

    @JsonProperty("type")
    String type;

    @JsonProperty("lastModifiedDate")
    LocalDateTime lastModifiedDate;
}
