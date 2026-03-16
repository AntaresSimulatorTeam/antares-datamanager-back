package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MiscGenMapper {

    private static final String GROUP="DSR";
    private static final Set<String> KNOWN_GROUPS = Set.of("biomass", "biogas", "waste", "other", "geothermal");

    public static MiscGenerationDTO mapToMiscGenerationDTO(MiscClusterCapacityEntity miscClusterCapacityEntity) {
        String group = miscClusterCapacityEntity.getGroupe();
        if (group == null || !KNOWN_GROUPS.contains(group.toLowerCase())) {
            group = "other";
        }
        return MiscGenerationDTO.builder()
                .groupe(group)
                .capacity(miscClusterCapacityEntity.getCapacityByYear().doubleValue())
                .build();

    }
}
