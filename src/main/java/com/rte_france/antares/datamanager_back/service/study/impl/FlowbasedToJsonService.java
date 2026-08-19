package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.SettingsAdvancedParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.SettingsGeneralParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.SettingsOptimizationParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.SettingsSeedsParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedToJsonService {

    private final FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    private final FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    private final SettingsAdvancedParametersRepository advancedParametersRepository;
    private final SettingsSeedsParametersRepository seedsParametersRepository;

    /**
     * Builds flowbased JSON from database entities by trajectory ID
     * @param trajectoryId the trajectory ID to fetch flowbased for
     * @return Map containing flowbased with structure: {recalculate_ts, type_days, virtual_nodes, links}
     */
    public Map<String, Object> buildFlowbasedMap(Integer trajectoryId, boolean recalculate) {
        Map<String, Object> flowbased = new LinkedHashMap<>();
        
        Optional<FlowbasedTypeDayEntity> dayTypesParams = flowbasedTypeDaysRepository.findByTrajectoryId(trajectoryId);
        Optional<FlowbasedVirtualNodesEntity> virtualNodesParams = flowbasedVirtualNodesRepository.findByTrajectoryId(trajectoryId);
        Optional<SettingsAdvancedParametersEntity> linksParams = advancedParametersRepository.findByTrajectoryId(trajectoryId);
        
        flowbased.put("recalculate_ts", recalculate);
        
        if (recalculate) {
            flowbased.put("type_days", buildOptimizationParametersMap(dayTypesParams.get()));
        } else {
            flowbased.put("type_days", new LinkedHashMap<>());
        }

        if (virtualNodesParams.isPresent()) {
            flowbased.put("virtual_nodes", buildAdvancedParametersMap(virtualNodesParams.get()));
        } else {
            flowbased.put("virtual_nodes", new LinkedHashMap<>());
        }

        if (linksParams.isPresent()) {
            flowbased.put("links", buildGeneralParametersMap(generalParams.get()));
        } else {
            flowbased.put("links", new LinkedHashMap<>());
        }

        return flowbased;
    }

    private Map<String, Object> buildSeedsParametersMap(SettingsSeedsParametersEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        addIfNotNull(map, "seed_tsgen_thermal", entity.getSeedTsgenThermal());
        addIfNotNull(map, "seed_tsnumbers", entity.getSeedTsnumbers());
        addIfNotNull(map, "seed_unsupplied_energy_costs", entity.getSeedUnsuppliedEnergyCosts());
        addIfNotNull(map, "seed_spilled_energy_costs", entity.getSeedSpilledEnergyCosts());
        addIfNotNull(map, "seed_thermal_costs", entity.getSeedThermalCosts());
        addIfNotNull(map, "seed_hydro_costs", entity.getSeedHydroCosts());
        addIfNotNull(map, "seed_initial_reservoir_levels", entity.getSeedInitialReservoirLevels());
        return map;
    }

    private void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
