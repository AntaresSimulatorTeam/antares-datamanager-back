package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.MiscGroupEnum;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiscGenMapper {

    public static MiscGenerationDTO mapToMiscGenerationDTO(MiscClusterCapacityEntity miscClusterCapacityEntity) {
        String group = MiscGroupEnum.normalizeForGenerator(miscClusterCapacityEntity.getGroupe());
        return MiscGenerationDTO.builder()
                .groupe(group)
                .capacity(miscClusterCapacityEntity.getCapacityByYear().doubleValue())
                .build();

    }
}
