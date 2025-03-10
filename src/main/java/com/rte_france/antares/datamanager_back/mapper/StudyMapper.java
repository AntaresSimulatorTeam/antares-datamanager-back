package com.rte_france.antares.datamanager_back.mapper;


import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.Objects;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StudyMapper {

    public static StudyDTO toStudyDTO(StudyEntity entity) {
        Objects.requireNonNull(entity);
        return StudyDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .createdBy(entity.getCreatedBy())
                .creationDate(entity.getCreationDate())
                .project(entity.getProject().getName())
                .tags(entity.getTags())
                .horizon(entity.getHorizon())
                .status(entity.getStatus().name())
                .trajectoryIds(entity.getTrajectories() != null ? entity.getTrajectories().stream().map(TrajectoryEntity::getId).toList() : Collections.emptyList())
                .build();
    }

    public static Page<StudyDTO> toStudyPage(Page<StudyEntity> page) {
        return page.map(StudyMapper::toStudyDTO);
    }
}
