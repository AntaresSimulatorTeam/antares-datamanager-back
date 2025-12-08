package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultThermalTechnologyMapper {


    public static DefaultThermalTechnologyDTO toDefaultThermalTechnologyDTO(ThermalTechnology thermalTechnology) {
        return DefaultThermalTechnologyDTO.builder()
                .name(thermalTechnology.getName())
                .build();
    }

    public static List<DefaultThermalTechnologyDTO> toDefaultThermalTechnologyDTOs(List<ThermalTechnology> thermalTechnologies) {
        return thermalTechnologies.stream()
                .map(DefaultThermalTechnologyMapper::toDefaultThermalTechnologyDTO)
                .toList();
    }
}
