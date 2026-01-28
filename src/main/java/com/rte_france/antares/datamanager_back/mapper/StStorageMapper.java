package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StStorageTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StStorageMapper {
    public static StStorageTrajectoryDataDTO toStStorageTrajectoryDataDTO(StStorageEntity stsStorageEntity) {
        return StStorageTrajectoryDataDTO.builder()
                .cluster(String.format("%s - %s - %s", stsStorageEntity.getArea().toUpperCase(), stsStorageEntity.getGroupe(), stsStorageEntity.getName()))
                .series(String.valueOf(stsStorageEntity.getSeries()).toUpperCase())
                .build();
    }
}
