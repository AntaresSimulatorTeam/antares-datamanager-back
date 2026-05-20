package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HydroMapper {

    public static HydroGenerationDTO mapToHydroGenerationDTO(HydroParametersEntity entity) {
        if (entity == null) {
            return null;
        }
        return HydroGenerationDTO.builder()
                .followLoadModulation(entity.getFollowLoad())
                .interDailyBreakdown(entity.getInterDailyBreakdown())
                .interDailyModulation(entity.getInterDailyModulation())
                .interMonthlyBreakdown(entity.getInterMonthlyBreakdown())
                .reservoirManagement(entity.getReservoir())
                .reservoirCapacity(entity.getReservoirCapacity() != null ? entity.getReservoirCapacity().intValue() : null)
                .pumpingEfficiency(entity.getPumpingEfficiency())
                .initializeReservoirDate(entity.getInitializeReservoirDate())
                .useWater(entity.getUseWater())
                .build();
    }
}
