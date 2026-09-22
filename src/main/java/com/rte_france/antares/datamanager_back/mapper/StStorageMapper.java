package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StStorageTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StStorageMapper {
    public static StStorageTrajectoryDataDTO toStStorageTrajectoryDataDTO(StStorageEntity stsStorageEntity) {
        return StStorageTrajectoryDataDTO.builder()
                .cluster(String.format("%s - %s - %s", stsStorageEntity.getArea().toUpperCase(), stsStorageEntity.getGroupe(), stsStorageEntity.getName()))
                .series(String.valueOf(stsStorageEntity.getSeries()).toUpperCase())
                .build();
    }

    public static StsGenerationDTO mapToStsGenerationDTO(StStorageEntity entity) {
        return commonFields(entity)
                .efficiencyWithdrawal(Optional.ofNullable(entity.getEfficiencyWithdrawal()).map(Number::doubleValue).orElse(0.0))
                .build();
    }

    /**
     * ME STS doesn't use efficiency_withdrawal, leave it empty for default value
     */
    public static StsGenerationDTO mapToStsGenerationDTOForMe(StStorageEntity entity) {
        return commonFields(entity).build();
    }

    private static StsGenerationDTO.StsGenerationDTOBuilder commonFields(StStorageEntity entity) {
        return StsGenerationDTO.builder()
                .enabled(Optional.ofNullable(entity.getEnabled()).orElse(false))
                .groupe(entity.getGroupe())
                .injection(Optional.ofNullable(entity.getInjection()).map(Number::intValue).orElse(0))
                .withdrawal(Optional.ofNullable(entity.getWithdrawal()).map(Number::doubleValue).orElse(0.0))
                .storage(Optional.ofNullable(entity.getStorage()).map(Number::doubleValue).orElse(0.0))
                .efficiencyInjection(Optional.ofNullable(entity.getEfficiencyInjection()).map(Number::doubleValue).orElse(0.0))
                .initialLevel(Optional.ofNullable(entity.getInitialLevel()).map(Number::doubleValue).orElse(0.0))
                .initialLevelOptim(Optional.ofNullable(entity.getInitialLevelOptim()).orElse(false));
    }
}
