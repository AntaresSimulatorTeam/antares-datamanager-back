package com.rte_france.antares.datamanager_back.dto;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ThermalClusterCapacityDto {
    private  List<ThermalClusterCapacityEntity> thermalClusterCapacities;
    private  WarningMessageEntity warningMessage;
    private String checksum;
    private  int version;
}
