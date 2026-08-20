package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LinkCapacityValues {
    private Integer winterHPDirectMW;
    private Integer winterHPIndirectMW;
    private Integer winterHCDirectMW;
    private Integer winterHCIndirectMW;
    private Integer summerHPDirectMW;
    private Integer summerHPIndirectMW;
    private Integer summerHCDirectMW;
    private Integer summerHCIndirectMW;
    private FlowbasedLinkCapacityType type;
}
