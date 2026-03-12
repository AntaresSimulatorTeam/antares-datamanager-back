package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.dto.AreaTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AreaMapper {

    public static AreaDTO toAreaDto(AreaConfigEntity areaConfigEntity) {
        return AreaDTO.builder()
                .name(areaConfigEntity.getArea().getName().toUpperCase())
                .unsuppliedEnergyCost(String.valueOf(areaConfigEntity.getUnsuppliedEnergyCost()))
                .spilledEnergyCost(String.valueOf(areaConfigEntity.getSpilledEnergyCost()))
                .build();
    }

    public static List<AreaDTO> toAreaDTOs(List<AreaConfigEntity> areaConfigEntities) {
        return areaConfigEntities.stream()
                .map(AreaMapper::toAreaDto)
                .toList();
    }

    public static AreaTrajectoryDataDTO toAreaTrajectoryDataDTO(Object[] row) {
        return AreaTrajectoryDataDTO.builder()
                .areaName((String) row[0])
                .spilledEnergyCost(String.valueOf(row[1]))
                .unsuppliedEnergyCost(String.valueOf(row[2]))
                .build();
    }

}