package com.rte_france.antares.datamanager_back.service.flowbased.impl;

import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegerCellValue {
    private Integer value;
    private FlowbasedLinkCapacityType type;
}
