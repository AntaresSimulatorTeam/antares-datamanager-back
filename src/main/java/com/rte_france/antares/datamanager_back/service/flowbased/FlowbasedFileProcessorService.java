package com.rte_france.antares.datamanager_back.service.flowbased;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedAreaEntity;

import java.nio.file.Path;
import java.util.List;

public interface FlowbasedFileProcessorService {

    TrajectoryEntity processFlowbasedFiles(Path trajectoryFilePath, String trajectoryToUse, Integer studyId, String horizon);
    
    List<FlowbasedVirtualNodesEntity> buildFlowbasedVirtualNodesList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedLinkCapacityEntity> buildFlowbasedLinkCapacityList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedTypeDayEntity> buildFlowbasedTypeDayList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedAreaEntity> buildAreasNecessaryList(Path trajectoryFilePath);

    void validateRequiredFiles(Path trajectoryFilePath);

    void validateVirtualNodesHvdcLinks(List<FlowbasedVirtualNodesEntity> virtualNodes, Integer studyId);

    /**
     * Validates the proposed LINK contents before a replacement or removal changes the study selection.
     */
    void validateVirtualNodesHvdcLinks(List<FlowbasedVirtualNodesEntity> virtualNodes, List<LinkEntity> links);

    void validateAreasExistInStudy(List<FlowbasedAreaEntity> areas, Integer studyId);

}
