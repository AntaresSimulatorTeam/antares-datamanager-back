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
public class WarningDTO {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("level")
    private String level;

    @JsonProperty("code")
    private String code;

    @JsonProperty("generatedBy")
    private String generatedBy;

    @JsonProperty("generatedAt")
    private LocalDateTime generatedAt;

    @JsonProperty("trajectoryId")
    private Integer trajectoryId;

    @JsonProperty("trajectoryName")
    private String trajectoryName;

    @JsonProperty("secondTrajectory")
    private String secondTrajectory;

    @JsonProperty("isAck")
    private Boolean isAck;
}