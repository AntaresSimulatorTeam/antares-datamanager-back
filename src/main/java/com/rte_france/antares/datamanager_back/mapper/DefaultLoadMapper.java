package com.rte_france.antares.datamanager_back.mapper;


import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultLoadMapper {
    public static DefaultLoadDTO toLoadDefaultDto(DefaultLoadEntity defaultLoadEntity) {
        return DefaultLoadDTO.builder()
                .name(defaultLoadEntity.getName())
                .build();
    }

    public static List<DefaultLoadDTO> toLoadDefaultDTOs(List<DefaultLoadEntity> areaEntities) {
        return areaEntities.stream()
                .map(DefaultLoadMapper::toLoadDefaultDto)
                .toList();
    }
}
