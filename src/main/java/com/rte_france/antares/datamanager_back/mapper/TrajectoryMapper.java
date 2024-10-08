package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TrajectoryMapper {

    public static TrajectoryDTO toTrajectoryDTO(TrajectoryEntity entity) {
        return TrajectoryDTO.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .type(entity.getType())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .creationDate(entity.getCreationDate())
                .build();
    }

    public static TrajectoryEntity toTrajectoryEntity(TrajectoryDTO dto) {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(dto.getId());
        entity.setFileName(dto.getFileName());
        entity.setType(dto.getType());
        entity.setVersion(dto.getVersion());
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setCreationDate(dto.getCreationDate());
        return entity;
    }

    public static List<TrajectoryDTO> toTrajectoryDtos(List<TrajectoryEntity> trajectoryEntities) {
        if (trajectoryEntities == null) {
            return Collections.emptyList();
        }
        return trajectoryEntities.stream()
                .map(TrajectoryMapper::toTrajectoryDTO)
                .toList();
    }

}
