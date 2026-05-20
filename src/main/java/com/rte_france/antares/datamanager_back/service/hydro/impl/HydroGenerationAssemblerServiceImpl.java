package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.mapper.HydroMapper;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroGenerationAssemblerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class HydroGenerationAssemblerServiceImpl implements HydroGenerationAssemblerService {

    @Override
    public Map<String, List<HydroGenerationDTO>> assembleHydroProperties(StudyEntity studyEntity) {
        Map<String, List<HydroGenerationDTO>> hydroProperties = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.HYDRO_PARAMETERS.name().equals(t.getType()))
                .map(TrajectoryEntity::getHydroParametersEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(
                        hp -> hp.getNode().toUpperCase(),
                        Collectors.mapping(
                                HydroMapper::mapToHydroGenerationDTO,
                                Collectors.toList()
                        )
                ));

        Map<String, Map<String, Double>> allocations = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.HYDRO_ALLOCATION.name().equals(t.getType()))
                .map(TrajectoryEntity::getHydroAllocationEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(
                        ha -> ha.getHydro().toUpperCase(),
                        Collectors.toMap(
                                ha -> ha.getLoad().toUpperCase(),
                                ha -> ha.getAllocation() != null ? ha.getAllocation().doubleValue() : 0.0,
                                (existing, replacement) -> replacement
                        )
                ));

        allocations.forEach((hydro, allocMap) -> {
            if (hydroProperties.containsKey(hydro)) {
                hydroProperties.get(hydro).forEach(dto -> dto.setAllocation(allocMap));
            }
        });

        return hydroProperties;
    }
}
