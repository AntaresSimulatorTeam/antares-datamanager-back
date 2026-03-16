package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class MiscGenerationDTO {

    @JsonProperty("capacity")
    private Double capacity;

    @JsonProperty("group")
    private String groupe;

    @JsonProperty("series")
    private List<String> miscGenTsList;
}
