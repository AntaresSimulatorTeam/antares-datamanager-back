package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyMapperTest {

@Test
void toStudyDTO_returnsCorrectDTO() {
    StudyEntity entity = new StudyEntity();
    entity.setId(1);
    entity.setName("testStudy");
    entity.setCreatedBy("testUser");
    entity.setCreationDate(LocalDateTime.now());
    entity.setProject(ProjectEntity.builder().name("project11").build());
    entity.setTags(Arrays.asList("tag1", "tag2"));
    entity.setHorizon("testHorizon");
    entity.setStatus(StudyStatus.IN_PROGRESS);

    StudyDTO dto = StudyMapper.toStudyDTO(entity);

    assertEquals(entity.getId(), dto.getId());
    assertEquals(entity.getName(), dto.getName());
    assertEquals(entity.getCreatedBy(), dto.getCreatedBy());
    assertEquals(entity.getCreationDate(), dto.getCreationDate());
    assertEquals(entity.getProject().getName(), dto.getProjet());
    assertEquals(entity.getTags(), dto.getTags());
    assertEquals(entity.getHorizon(), dto.getHorizon());
    assertEquals(entity.getStatus().name(), dto.getStatus());
}

@Test
void toStudyPage_returnsCorrectPage() {
    StudyEntity entity1 = new StudyEntity();
    entity1.setId(1);
    entity1.setName("testStudy1");
    entity1.setCreatedBy("testUser1");
    entity1.setCreationDate(LocalDateTime.now());
    entity1.setProject(ProjectEntity.builder().name("project1").build());
    entity1.setTags(Arrays.asList("tag1", "tag2"));
    entity1.setHorizon("testHorizon1");
    entity1.setStatus(StudyStatus.IN_PROGRESS);

    StudyEntity entity2 = new StudyEntity();
    entity2.setId(2);
    entity2.setName("testStudy2");
    entity2.setCreatedBy("testUser2");
    entity2.setCreationDate(LocalDateTime.now());
    entity2.setProject(ProjectEntity.builder().name("project1").build());
    entity2.setTags(Arrays.asList("tag3", "tag4"));
    entity2.setHorizon("testHorizon2");
    entity2.setStatus(StudyStatus.IN_PROGRESS);

    List<StudyEntity> entities = Arrays.asList(entity1, entity2);
    Page<StudyEntity> pageEntity = new PageImpl<>(entities);

    Page<StudyDTO> pageDto = StudyMapper.toStudyPage(pageEntity);

    assertEquals(2, pageDto.getContent().size());
    assertEquals(entity1.getId(), pageDto.getContent().get(0).getId());
    assertEquals(entity1.getName(), pageDto.getContent().get(0).getName());
    assertEquals(entity1.getCreatedBy(), pageDto.getContent().get(0).getCreatedBy());
    assertEquals(entity1.getCreationDate(), pageDto.getContent().get(0).getCreationDate());
    assertEquals(entity1.getProject().getName(), pageDto.getContent().get(0).getProjet());
    assertEquals(entity1.getTags(), pageDto.getContent().get(0).getTags());
    assertEquals(entity1.getHorizon(), pageDto.getContent().get(0).getHorizon());
    assertEquals(entity1.getStatus().name(), pageDto.getContent().get(0).getStatus());
    assertEquals(entity2.getId(), pageDto.getContent().get(1).getId());
    assertEquals(entity2.getName(), pageDto.getContent().get(1).getName());
    assertEquals(entity2.getCreatedBy(), pageDto.getContent().get(1).getCreatedBy());
    assertEquals(entity2.getCreationDate(), pageDto.getContent().get(1).getCreationDate());
    assertEquals(entity2.getProject().getName(), pageDto.getContent().get(1).getProjet());
    assertEquals(entity2.getTags(), pageDto.getContent().get(1).getTags());
    assertEquals(entity2.getHorizon(), pageDto.getContent().get(1).getHorizon());
    assertEquals(entity2.getStatus().name(), pageDto.getContent().get(1).getStatus());
}
}
