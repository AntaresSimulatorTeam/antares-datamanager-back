package com.rte_france.antares.datamanager_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class StStorageTrajectoryDataDTO implements TrajectoryDataDTO {

    private String cluster;
    private String series;
}
