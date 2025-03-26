package com.rte_france.antares.datamanager_back.dto.trajectoryData;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class AreaTrajectoryDataDTO implements TrajectoryDataDTO{

    private String areaName;

    private String powerToGas;

    private String shortTermStorage;
}
