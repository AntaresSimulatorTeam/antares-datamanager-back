package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StStorageTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.util.Utils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StStorageMapper {
    public static StStorageTrajectoryDataDTO toStStorageTrajectoryDataDTO(StStorageEntity stsStorageEntity) {
        return StStorageTrajectoryDataDTO.builder()
                .name(String.format("%s - %s - %s", stsStorageEntity.getArea().toUpperCase(), Utils.toTitleCase(stsStorageEntity.getGroupe()), Utils.formatLabel(stsStorageEntity.getName())))
                .series(String.valueOf(stsStorageEntity.getSeries()).toUpperCase())
                .build();
    }
}
