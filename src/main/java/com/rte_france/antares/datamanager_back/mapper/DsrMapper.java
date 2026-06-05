package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.Optional;
@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DsrMapper {

    private static final String GROUP="DSR";
    public static DsrGenerationDTO mapToDsrGenerationDTO(DsrClusterEntity dsrEntity) {
        Double nominalCapacity = Optional.ofNullable(dsrEntity.getCapacity()).map(Number::doubleValue).orElse(0.0);
        Integer unitCount = Optional.ofNullable(dsrEntity.getNbUnits()).map(Number::intValue).orElse(0);
        
        return DsrGenerationDTO.builder()
                .enabled(Optional.ofNullable(dsrEntity.getToUse()).orElse(false))
                .group(GROUP)
                .nominalCapacity(unitCount == 0 ? 0.0 : (nominalCapacity / unitCount))
                .unitCount(unitCount)
                .marginalCost(Optional.ofNullable(dsrEntity.getPrice()).map(Number::doubleValue).orElse(0.0))
                .marketBidCost(Optional.ofNullable(dsrEntity.getPrice()).map(Number::doubleValue).orElse(0.0))
                .foDuration(Optional.ofNullable(dsrEntity.getFoDuration()).map(Number::doubleValue).orElse(0.0))
                .foMonthlyRate(Collections.nCopies(12, Optional.ofNullable(dsrEntity.getFoRate()).map(Number::doubleValue).orElse(0.0)))
                .nbHourPerDay(Optional.ofNullable(dsrEntity.getNbHourPerDay()).map(Number::doubleValue).orElse(0.0))
                .maxHourPerDay(Optional.ofNullable(dsrEntity.getMaxHourPerDay()).map(Number::doubleValue).orElse(0.0))
                .build();

    }
}
