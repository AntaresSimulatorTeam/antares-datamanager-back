package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrajectoryMapperTest {

    @Test
    void toTrajectoryDTO_returnsCorrectDTO() {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(1);
        entity.setFileName("testFile");
        entity.setType("AREA");
        entity.setVersion(1);
        entity.setCreatedBy("testUser");
        entity.setCreationDate(LocalDateTime.now());

        TrajectoryDTO dto = TrajectoryMapper.toTrajectoryDTO(entity);

        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getFileName(), dto.getFileName());
        assertEquals(entity.getType(), dto.getType());
        assertEquals(entity.getVersion(), dto.getVersion());
        assertEquals(entity.getCreatedBy(), dto.getCreatedBy());
        assertEquals(entity.getCreationDate(), dto.getCreationDate());
    }

    @Test
    void toTrajectoryEntity_returnsCorrectEntity() {
        TrajectoryDTO dto = TrajectoryDTO.builder()
                .id(1)
                .fileName("testFile")
                .type("AREA")
                .version(1)
                .createdBy("testUser")
                .creationDate(LocalDateTime.now())
                .build();

        TrajectoryEntity entity = TrajectoryMapper.toTrajectoryEntity(dto);

        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getFileName(), entity.getFileName());
        assertEquals(dto.getType(), entity.getType());
        assertEquals(dto.getVersion(), entity.getVersion());
        assertEquals(dto.getCreatedBy(), entity.getCreatedBy());
        assertEquals(dto.getCreationDate(), entity.getCreationDate());
    }

    @Test
    void toWidgetDtos_returnsCorrectDTOList() {
        TrajectoryEntity entity1 = new TrajectoryEntity();
        entity1.setId(1);
        entity1.setFileName("testFile1");
        entity1.setType("AREA");
        entity1.setVersion(1);
        entity1.setCreatedBy("testUser1");
        entity1.setCreationDate(LocalDateTime.now());

        TrajectoryEntity entity2 = new TrajectoryEntity();
        entity2.setId(2);
        entity2.setFileName("testFile2");
        entity2.setType("LINK");
        entity2.setVersion(2);
        entity2.setCreatedBy("testUser2");
        entity2.setCreationDate(LocalDateTime.now());

        List<TrajectoryEntity> entities = Arrays.asList(entity1, entity2);

        List<TrajectoryDTO> dtos = TrajectoryMapper.toTrajectoryDtos(entities);

        assertEquals(2, dtos.size());
        assertEquals(entity1.getId(), dtos.get(0).getId());
        assertEquals(entity1.getFileName(), dtos.get(0).getFileName());
        assertEquals(entity1.getType(), dtos.get(0).getType());
        assertEquals(entity1.getVersion(), dtos.get(0).getVersion());
        assertEquals(entity1.getCreatedBy(), dtos.get(0).getCreatedBy());
        assertEquals(entity1.getCreationDate(), dtos.get(0).getCreationDate());
        assertEquals(entity2.getId(), dtos.get(1).getId());
        assertEquals(entity2.getFileName(), dtos.get(1).getFileName());
        assertEquals(entity2.getType(), dtos.get(1).getType());
        assertEquals(entity2.getVersion(), dtos.get(1).getVersion());
        assertEquals(entity2.getCreatedBy(), dtos.get(1).getCreatedBy());
        assertEquals(entity2.getCreationDate(), dtos.get(1).getCreationDate());
    }
}