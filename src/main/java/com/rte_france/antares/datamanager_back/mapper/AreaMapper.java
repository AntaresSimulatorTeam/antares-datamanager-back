package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;


import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AreaMapper {

    public static AreaDTO toAreaDto(AreaEntity areaEntity) {
        return AreaDTO.builder()
                .name(areaEntity.getName())
                .build();
    }

    public static List<AreaDTO> toAreaDTOs(List<AreaEntity> areaEntities) {
        return areaEntities.stream()
                .map(AreaMapper::toAreaDto)
                .toList();
    }

}