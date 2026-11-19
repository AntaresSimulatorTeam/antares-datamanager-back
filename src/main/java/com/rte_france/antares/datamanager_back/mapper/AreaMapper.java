package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.dto.AreaTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Objects;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AreaMapper {

    public static AreaDTO toAreaDto(AreaConfigEntity areaConfigEntity) {
        AreaEntity area = areaConfigEntity.getArea();
        return AreaDTO.builder()
                .name(area.getName().toUpperCase())
                .x(area.getX())
                .y(Objects.toString(area.getY(), null))
                .r(Objects.toString(area.getR(), null))
                .g(Objects.toString(area.getG(), null))
                .b(Objects.toString(area.getB(), null))
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