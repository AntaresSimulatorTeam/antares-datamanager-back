package com.rte_france.antares.datamanager_back.mapper;

import com.rte_france.antares.datamanager_back.dto.AreaDTO;
import com.rte_france.antares.datamanager_back.dto.trajectoryData.AreaTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.dto.trajectoryData.LinkTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LinkMapper {

    public static LinkTrajectoryDataDTO toLinkTrajectoryDataDTO(LinkEntity linkEntity) {
        return LinkTrajectoryDataDTO.builder().
                name(linkEntity.getName())
                .winterHpDirectMw(linkEntity.getWinterHpDirectMw())
                .winterHpIndirectMw(linkEntity.getWinterHpIndirectMw())
                .summerHcDirectMw(linkEntity.getSummerHcDirectMw())
                .winterHcIndirectMw(linkEntity.getWinterHcIndirectMw())
                .summerHpDirectMw(linkEntity.getSummerHpDirectMw())
                .summerHpIndirectMw(linkEntity.getSummerHpIndirectMw())
                .summerHcDirectMw(linkEntity.getSummerHcDirectMw())
                .summerHcIndirectMw(linkEntity.getSummerHcIndirectMw())
                .flowbasedPerimeter(String.valueOf(linkEntity.getFlowbasedPerimeter()))
                .hvdc(String.valueOf(linkEntity.getHvdc()))
                .specificTs(String.valueOf(linkEntity.getSpecificTs()))
                .forcedOutageHvac(String.valueOf(linkEntity.getForcedOutageHvac()))
                .hurdleCost(linkEntity.getHurdleCost())
                .build();
    }

    public static List<LinkTrajectoryDataDTO> toLinkTrajectoryDataDTO( List<LinkEntity> linkEntities) {
        return linkEntities.stream()
                .map(LinkMapper::toLinkTrajectoryDataDTO)
                .toList();
    }


}
