package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.WarningMessageDTO;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.repository.model.WarningLevel;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Set;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarningMessageMapper {
  public static Set<WarningMessageDTO> toWarningMessageDTOs(Set<WarningMessageEntity> entities) {
    if (entities == null) {
      return Set.of();
    }
    return entities.stream()
            .map(WarningMessageMapper::toWarningMessageDTO)
            .collect(Collectors.toSet());
  }

  static WarningMessageDTO toWarningMessageDTO(WarningMessageEntity entity) {
    return WarningMessageDTO.builder()
            .id(entity.getId())
            .content(entity.getContent())
            .level(entity.getLevel().name())
            .build();
  }

  public static Set<WarningMessageEntity> toWarningMessageEntities(Set<WarningMessageDTO> dtos) {
    if (dtos == null) {
      return Set.of();
    }
    return dtos.stream()
            .map(WarningMessageMapper::toWarningMessageEntity)
            .collect(Collectors.toSet());
  }

  static WarningMessageEntity toWarningMessageEntity(WarningMessageDTO dto) {
    WarningMessageEntity entity = new WarningMessageEntity();
    entity.setId(dto.getId());
    entity.setContent(dto.getContent());
    entity.setLevel(WarningLevel.valueOf(dto.getLevel()));
    return entity;
  }
}
