package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StStorageTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StStorageMapper {
    public static StStorageTrajectoryDataDTO toStStorageTrajectoryDataDTO(StStorageEntity stsStorageEntity) {
        return StStorageTrajectoryDataDTO.builder()
                .cluster(String.format("%s - %s - %s", stsStorageEntity.getArea().toUpperCase(), stsStorageEntity.getGroup(), stsStorageEntity.getName()))
                .series(String.valueOf(stsStorageEntity.getSeries()).toUpperCase())
                .build();
    }

    public static StsGenerationDTO mapToStsGenerationDTO(StStorageEntity entity) {
        return StsGenerationDTO.builder()
                .enabled(entity.getEnabled() != null ? entity.getEnabled() : false)
                .group(entity.getGroup())
                .injection(entity.getInjection() != null ? entity.getInjection().intValue() : 0)
                .withdrawal(entity.getWithdrawal() != null ? entity.getWithdrawal().doubleValue() : 0.0)
                .storage(entity.getStorage() != null ? entity.getStorage().doubleValue() : 0.0)
                .efficiencyInjection(entity.getEfficiencyInjection() != null ? entity.getEfficiencyInjection().doubleValue() : 0.0)
                .efficiencyWithdrawal(entity.getEfficiencyWithdrawal() != null ? entity.getEfficiencyWithdrawal().doubleValue() : 0.0)
                .initialLevel(entity.getInitialLevel() != null ? entity.getInitialLevel().doubleValue() : 0.0)
                .initialLevelOptim(entity.getInitialLevelOptim())
                .build();
    }
}
