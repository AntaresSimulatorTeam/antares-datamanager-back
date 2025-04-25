package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

 class DefaultLoadMapperTest {
    @Test
     void testLoadDefaultMapper() {
        //Given
        DefaultLoadEntity entity = DefaultLoadEntity.builder()
                .name("FR")
                .isDefault(TRUE)
                .id(1).entity("entity")
                .build();

        //When
        DefaultLoadDTO defaultLoadDTO = DefaultLoadMapper.toLoadDefaultDto(entity);

        //Then
        assertNotNull(defaultLoadDTO);
        assertEquals("FR", defaultLoadDTO.getName());

    }

    @Test
    void toLinkTrajectoryDataDTO_List_ShouldMapAllElements() {
        // Given
        DefaultLoadEntity entity1 = DefaultLoadEntity.builder().name("FR").build();
        DefaultLoadEntity entity2  = DefaultLoadEntity.builder().name("OTHERS").build();
        List<DefaultLoadEntity> entities = List.of(entity1, entity2);

        //When
        List<DefaultLoadDTO> loadEntities = DefaultLoadMapper.toLoadDefaultDTOs(entities);

        //Then
        assertNotNull(loadEntities);
        assertEquals(2, loadEntities.size());

    }

}
