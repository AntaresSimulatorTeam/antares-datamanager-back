package com.rte_france.antares.datamanager_back.service.flowbased;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkWeightEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;

import java.nio.file.Path;
import java.util.List;

public interface FlowbasedFileProcessorService {

    TrajectoryEntity processFlowbasedFiles(Path trajectoryFilePath, String trajectoryToUse, Integer studyId, String horizon);

    List<FlowbasedLinkWeightEntity> buildFlowbasedLinkWeightList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedVirtualNodesEntity> buildFlowbasedVirtualNodesList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedLinkCapacityEntity> buildFlowbasedLinkCapacityList(Path trajectoryFilePath) throws Exception;

    List<FlowbasedTypeDayEntity> buildFlowbasedTypeDayList(Path trajectoryFilePath) throws Exception;

    void validateRequiredFiles(Path trajectoryFilePath);

}
