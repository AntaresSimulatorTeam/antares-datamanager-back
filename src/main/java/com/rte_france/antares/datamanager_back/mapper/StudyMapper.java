package com.rte_france.antares.datamanager_back.mapper;


import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StudyMapper {

    public static StudyDTO toStudyDTO(StudyEntity entity) {
        return StudyDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .createdBy(entity.getCreatedBy())
                .creationDate(entity.getCreationDate())
                .projet(entity.getProject().getName())
                .tags(entity.getTags())
                .horizon(entity.getHorizon())
                .status(entity.getStatus().name())
                .build();
    }

    public static Page<StudyDTO> toStudyPage(Page<StudyEntity> page) {
        return page.map(StudyMapper::toStudyDTO);
    }
}
