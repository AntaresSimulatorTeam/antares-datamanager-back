package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultThermalTechnologyMapperTest {
   @Test
    void testThermalTechnologyDefaultMapper() {
       //Given
       ThermalTechnology entity = ThermalTechnology.builder()
               .name("Nuclear")
               .id(1)
               .build();

       //When
       DefaultThermalTechnologyDTO defaultThermalTechnologyDTO = DefaultThermalTechnologyMapper.toDefaultThermalTechnologyDTO(entity);

       //Then
       assertNotNull(defaultThermalTechnologyDTO);
       assertEquals("Nuclear", defaultThermalTechnologyDTO.getName());

   }

   @Test
   void toLinkTrajectoryDataDTO_List_ShouldMapAllElements() {
       // Given
       ThermalTechnology entity1 = ThermalTechnology.builder()
               .name("Nuclear")
               .id(1)
               .build();
       ThermalTechnology entity2 = ThermalTechnology.builder()
               .name("CCGT")
               .id(2)
               .build();
       List<ThermalTechnology> entities = List.of(entity1, entity2);

       //When
       List< DefaultThermalTechnologyDTO> thermalTechnologyList = DefaultThermalTechnologyMapper.toDefaultThermalTechnologyDTOs(entities);

       //Then
       assertNotNull(thermalTechnologyList);
       assertEquals(2, entities.size());

   }

}
