package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.repository.model.WarningLevel;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarningMapper {
    public static Set<WarningDTO> toWarningMessageDTOs(Set<WarningMessageEntity> entities) {
        if (entities == null) {
            return Set.of();
        }
        return entities.stream()
                .map(WarningMapper::toWarningMessageDTO)
                .collect(Collectors.toCollection(LinkedHashSet::new)); // préserve l'ordre
    }

    public static WarningDTO toWarningMessageDTO(WarningMessageEntity entity) {
        return WarningDTO.builder()
                .id(entity.getId())
                .content(entity.getWarningContent())
                .level(entity.getWarningLevel().name())
                .code(entity.getWarningCode().name())
                .generatedAt(entity.getCreationDate())
                .generatedBy(entity.getCreatedBy())
                .secondTrajectory(entity.getSecondTrajectory() != null ? entity.getSecondTrajectory().getFileName() : null)
                .isAck(entity.getIsAck())
                .build();
    }

    public static Set<WarningMessageEntity> toWarningMessageEntities(Set<WarningDTO> dtos) {
        if (dtos == null) {
            return Set.of();
        }
        return dtos.stream()
                .map(WarningMapper::toWarningMessageEntity)
                .collect(Collectors.toSet());
    }

    static WarningMessageEntity toWarningMessageEntity(WarningDTO dto) {
        WarningMessageEntity entity = new WarningMessageEntity();
        entity.setId(dto.getId());
        entity.setWarningContent(dto.getContent());
        entity.setWarningLevel(WarningLevel.valueOf(dto.getLevel()));
        return entity;
    }
}
