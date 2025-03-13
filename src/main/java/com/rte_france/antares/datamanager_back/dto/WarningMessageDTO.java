package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class WarningMessageDTO {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("content")
    private String content;

    @JsonProperty("level")
    private String level;
}