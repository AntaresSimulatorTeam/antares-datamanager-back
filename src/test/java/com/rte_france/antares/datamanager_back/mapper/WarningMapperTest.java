package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.repository.model.WarningLevel;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WarningMapperTest {

    @Test
    void toWarningMessageDTO_returnsCorrectDTO() {
        var entity = new WarningMessageEntity();
        entity.setId(1);
        entity.setWarningCode(WarningCode.LINKS_ALL_VALUES_ZERO);
        entity.setWarningLevel(WarningLevel.WARNING_LEVEL);
        entity.setTrajectory(TrajectoryEntity.builder().id(1).build());
        entity.setWarningContent("Test content");

        var dto = WarningMapper.toWarningMessageDTO(entity);

        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getWarningLevel().name(), dto.getLevel());
        assertEquals(entity.getWarningContent(), dto.getContent());
    }

    @Test
    void toWarningMessageEntity_returnsCorrectEntity() {
        var dto = WarningDTO.builder()
                .id(1)
                .level("WARNING_LEVEL")
                .content("Test content")
                .build();

        var entity = WarningMapper.toWarningMessageEntity(dto);

        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getLevel(), entity.getWarningLevel().name());
        assertEquals(dto.getContent(), entity.getWarningContent());
    }

    @Test
    void toWarningMessageDTOs_returnsCorrectDTOSet() {
        var entity1 = new WarningMessageEntity();
        entity1.setId(1);
        entity1.setWarningCode(WarningCode.LINKS_ALL_VALUES_ZERO);
        entity1.setWarningLevel(WarningLevel.WARNING_LEVEL);
        entity1.setTrajectory(TrajectoryEntity.builder().id(1).build());
        entity1.setWarningContent("Test content 1");

        var entity2 = new WarningMessageEntity();
        entity2.setId(2);
        entity2.setWarningCode(WarningCode.LINKS_UNILATERAL_VALUES_ZERO);
        entity2.setWarningLevel(WarningLevel.ERROR_LEVEL);
        entity2.setTrajectory(TrajectoryEntity.builder().id(1).build());
        entity2.setWarningContent("Test content 2");

        var entities = Set.of(entity1, entity2);

        var dtos = WarningMapper.toWarningMessageDTOs(entities);

        assertEquals(2, dtos.size());
        assertTrue(dtos.stream().anyMatch(dto -> dto.getId().equals(entity1.getId())));
        assertTrue(dtos.stream().anyMatch(dto -> dto.getId().equals(entity2.getId())));
    }

    @Test
    void toWarningMessageEntities_returnsCorrectEntitySet() {
        var dto1 = WarningDTO.builder()
                .id(1)
                .level("WARNING_LEVEL")
                .content("Test content 1")
                .build();

        var dto2 = WarningDTO.builder()
                .id(2)
                .level("ERROR_LEVEL")
                .content("Test content 2")
                .build();

        var dtos = Set.of(dto1, dto2);

        var entities = WarningMapper.toWarningMessageEntities(dtos);

        assertEquals(2, entities.size());
        assertTrue(entities.stream().anyMatch(entity -> entity.getId().equals(dto1.getId())));
        assertTrue(entities.stream().anyMatch(entity -> entity.getId().equals(dto2.getId())));
    }

    @Test
    void toWarningMessageDTOs_returnsEmptySet_whenInputIsNull() {
        var dtos = WarningMapper.toWarningMessageDTOs(null);
        assertTrue(dtos.isEmpty());
    }

    @Test
    void toWarningMessageEntities_returnsEmptySet_whenInputIsNull() {
        var entities = WarningMapper.toWarningMessageEntities(null);
        assertTrue(entities.isEmpty());
    }

    @Test
    void toWarningMessageDTO_handlesSecondTrajectoryNullAndNotNull() {
        var entity = new WarningMessageEntity();
        entity.setId(1);
        entity.setWarningCode(WarningCode.LINKS_ALL_VALUES_ZERO);
        entity.setWarningLevel(WarningLevel.WARNING_LEVEL);
        entity.setWarningContent("Test");
        entity.setTrajectory(TrajectoryEntity.builder().id(1).build());
        entity.setSecondTrajectory(null);

        var dto = WarningMapper.toWarningMessageDTO(entity);
        assertNull(dto.getSecondTrajectory());

        var secondTrajectory = new TrajectoryEntity();
        secondTrajectory.setFileName("test.txt");
        entity.setSecondTrajectory(secondTrajectory);

        dto = WarningMapper.toWarningMessageDTO(entity);
        assertEquals("test.txt", dto.getSecondTrajectory());
    }

    @Test
    void lombokBuilderAndValueAnnotations_test() {
        var dto = WarningDTO.builder()
                .id(1)
                .level("WARNING_LEVEL")
                .content("Test")
                .build();
        assertEquals(1, dto.getId());
        assertEquals("WARNING_LEVEL", dto.getLevel());
        assertEquals("Test", dto.getContent());
    }
}