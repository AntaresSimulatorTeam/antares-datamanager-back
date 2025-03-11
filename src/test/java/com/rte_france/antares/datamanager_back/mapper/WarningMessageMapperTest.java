package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.WarningMessageDTO;
import com.rte_france.antares.datamanager_back.repository.model.WarningCode;
import com.rte_france.antares.datamanager_back.repository.model.WarningLevel;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningMessageMapperTest {

    @Test
    void toWarningMessageDTO_returnsCorrectDTO() {
        var entity = new WarningMessageEntity();
        entity.setId(1);
        entity.setCode(WarningCode.WARNING_CODE_1);
        entity.setLevel(WarningLevel.WARNING_LEVEL);

        var dto = WarningMessageMapper.toWarningMessageDTO(entity);

        assertEquals(entity.getId(), dto.getId());
        assertEquals(entity.getCode().name(), dto.getCode());
        assertEquals(entity.getLevel().name(), dto.getLevel());
    }

    @Test
    void toWarningMessageEntity_returnsCorrectEntity() {
        var dto = WarningMessageDTO.builder()
                .id(1)
                .code("WARNING_CODE_1")
                .level("WARNING_LEVEL")
                .build();

        var entity = WarningMessageMapper.toWarningMessageEntity(dto);

        assertEquals(dto.getId(), entity.getId());
        assertEquals(dto.getCode(), entity.getCode().name());
        assertEquals(dto.getLevel(), entity.getLevel().name());
    }

    @Test
    void toWarningMessageDTOs_returnsCorrectDTOSet() {
        var entity1 = new WarningMessageEntity();
        entity1.setId(1);
        entity1.setCode(WarningCode.WARNING_CODE_1);
        entity1.setLevel(WarningLevel.WARNING_LEVEL);

        var entity2 = new WarningMessageEntity();
        entity2.setId(2);
        entity2.setCode(WarningCode.WARNING_CODE_2);
        entity2.setLevel(WarningLevel.ERROR_LEVEL);

        var entities = Set.of(entity1, entity2);

        var dtos = WarningMessageMapper.toWarningMessageDTOs(entities);

        assertEquals(2, dtos.size());
        assertTrue(dtos.stream().anyMatch(dto -> dto.getId().equals(entity1.getId())));
        assertTrue(dtos.stream().anyMatch(dto -> dto.getId().equals(entity2.getId())));
    }

    @Test
    void toWarningMessageEntities_returnsCorrectEntitySet() {
        var dto1 = WarningMessageDTO.builder()
                .id(1)
                .code("WARNING_CODE_1")
                .level("WARNING_LEVEL")
                .build();

        var dto2 = WarningMessageDTO.builder()
                .id(2)
                .code("WARNING_CODE_2")
                .level("ERROR_LEVEL")
                .build();

        var dtos = Set.of(dto1, dto2);

        var entities = WarningMessageMapper.toWarningMessageEntities(dtos);

        assertEquals(2, entities.size());
        assertTrue(entities.stream().anyMatch(entity -> entity.getId().equals(dto1.getId())));
        assertTrue(entities.stream().anyMatch(entity -> entity.getId().equals(dto2.getId())));
    }
}