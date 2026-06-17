package com.rte_france.antares.datamanager_back.mapper;
import com.rte_france.antares.datamanager_back.dto.LinkTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class LinkMapperTest {

    @Test
    void toLinkTrajectoryDataDTO_SingleEntity_ShouldMapCorrectly() {
        // Given
        LinkEntity linkEntity = LinkEntity.builder()
                .name("Test Link")
                .winterHpDirectMw(100.0)
                .winterHpIndirectMw(50.0)
                .winterHcIndirectMw(20.0)
                .winterHcDirectMw(30.2)
                .summerHcDirectMw(30.0)
                .summerHpDirectMw(60.0)
                .summerHpIndirectMw(40.0)
                .summerHcIndirectMw(15.0)
                .flowbasedPerimeter(true)
                .hvdcMwDirect(75.5)
                .hvdcMwIndirect(25.3)
                .hvdcNbDirect(2.0)
                .hvdcNbIndirect(1.0)
                .hvdcFoRateDirect(1.0)
                .hvdcFoRateIndirect(1.0)
                .hurdleCost(0.5)
                .build();
        //When
        LinkTrajectoryDataDTO dto = LinkMapper.toLinkTrajectoryDataDTO(linkEntity);

        // Then
        assertNotNull(dto);
        assertEquals("Test Link", dto.getName());
        assertEquals(100.0, dto.getWinterHpDirectMw());
        assertEquals(50.0, dto.getWinterHpIndirectMw());
        assertEquals(20.0, dto.getWinterHcIndirectMw());
        assertEquals(30.2,dto.getWinterHcDirectMw());
        assertEquals(30.0, dto.getSummerHcDirectMw());
        assertEquals(60.0, dto.getSummerHpDirectMw());
        assertEquals(40.0, dto.getSummerHpIndirectMw());
        assertEquals(15.0, dto.getSummerHcIndirectMw());
        assertEquals("true", dto.getFlowbasedPerimeter());
        assertEquals(75.5, dto.getHvdcMwDirect());
        assertEquals(25.3, dto.getHvdcMwIndirect());
        assertEquals(2.0, dto.getHvdcNbDirect());
        assertEquals(1.0, dto.getHvdcNbIndirect());
        assertEquals(1.0, dto.getHvdcFoRateDirect());
        assertEquals(1.0, dto.getHvdcFoRateIndirect());
        assertEquals(0.5, dto.getHurdleCost());
    }

    @Test
    void toLinkTrajectoryDataDTO_List_ShouldMapAllElements() {
        // Given
        LinkEntity link1 = LinkEntity.builder().name("Link1").winterHpDirectMw(100.0).build();
        LinkEntity link2 = LinkEntity.builder().name("Link2").winterHpDirectMw(200.0).build();

        List<LinkEntity> linkEntities = List.of(link1, link2);

        // When
        List<LinkTrajectoryDataDTO> dtoList = LinkMapper.toLinkTrajectoryDataDTO(linkEntities);

        // Then
        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());
        assertEquals("Link1", dtoList.get(0).getName());
        assertEquals(100.0, dtoList.get(0).getWinterHpDirectMw());
        assertEquals("Link2", dtoList.get(1).getName());
        assertEquals(200.0, dtoList.get(1).getWinterHpDirectMw());
    }
}
