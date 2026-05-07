package com.rte_france.antares.datamanager_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class LinkTrajectoryDataDTO implements TrajectoryDataDTO{

    private String name;
    private Double winterHpDirectMw;
    private Double winterHpIndirectMw;
    private Double winterHcDirectMw;
    private Double winterHcIndirectMw;
    private Double summerHpDirectMw;
    private Double summerHpIndirectMw;
    private Double summerHcDirectMw;
    private Double summerHcIndirectMw;
    private String flowbasedPerimeter;
    private Double hvdcMwDirect;
    private Double hvdcMwIndirect;
    private Double hvdcNb;
    private Double hvdcfoRate;
    private String hvdc;
    private Double hurdleCost;
}
