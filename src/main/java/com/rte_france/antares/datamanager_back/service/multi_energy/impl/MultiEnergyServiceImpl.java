package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiEnergyServiceImpl implements MultiEnergyService {

    private final AdequacySettingsAssemblerService adequacySettingsAssemblerService;

    private static final String PROPERTIES = "properties";
    private static final String UI = "ui";
    private static final String ENERGY_COST_UNSUPPLIED = "energy_cost_unsupplied";
    private static final String ENERGY_COST_SPILLED = "energy_cost_spilled";
    private static final String ADEQUACY_PATCH_MODE = "adequacy_patch_mode";
    private static final String AREA_UI_PLACEHOLDER = "AreaUI class as JSON";

    @Override
    public Map<String, Object> buildMultiEnergyMap(StudyEntity study, TrajectoryEntity areaMeTrajectory) {
        log.info("Building Multi-Energy map for study id={} name={} with trajectory={}",
                study != null ? study.getId() : null,
                study != null ? study.getName() : null,
                areaMeTrajectory != null ? areaMeTrajectory.getFileName() : null);

        if (areaMeTrajectory == null || areaMeTrajectory.getAreaConfigEntities() == null) {
            return Collections.emptyMap();
        }

        Map<String, String> adequacyModeByArea = study != null
                ? adequacySettingsAssemblerService.assembleAdequacyModeByArea(study)
                : Collections.emptyMap();

        Map<String, Object> meMap = new LinkedHashMap<>();

        for (AreaConfigEntity areaConfig : areaMeTrajectory.getAreaConfigEntities()) {
            if (areaConfig.getArea() == null || areaConfig.getArea().getName() == null) {
                continue;
            }

            String areaName = areaConfig.getArea().getName();

            Map<String, Object> areaEntryMap = new LinkedHashMap<>();

            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put(ENERGY_COST_UNSUPPLIED, areaConfig.getUnsuppliedEnergyCost());
            propertiesMap.put(ENERGY_COST_SPILLED, areaConfig.getSpilledEnergyCost());

            String adequacyMode = findAdequacyMode(areaName, adequacyModeByArea);
            propertiesMap.put(ADEQUACY_PATCH_MODE, adequacyMode);

            areaEntryMap.put(PROPERTIES, propertiesMap);
            areaEntryMap.put(UI, AREA_UI_PLACEHOLDER);

            meMap.put(areaName, areaEntryMap);
        }

        return meMap;
    }

    @Override
    public Map<String, Object> buildMultiEnergyMap(StudyEntity study) {
        if (study == null || study.getTrajectories() == null) {
            return Collections.emptyMap();
        }
        Optional<TrajectoryEntity> areaMeTrajectory = study.getTrajectories().stream()
                .filter(t -> TrajectoryType.AREA_ME.name().equals(t.getType()))
                .findFirst();
        return areaMeTrajectory.map(trajectory -> buildMultiEnergyMap(study, trajectory))
                .orElse(Collections.emptyMap());
    }

    private String findAdequacyMode(String areaName, Map<String, String> adequacyModeByArea) {
        if (adequacyModeByArea == null || adequacyModeByArea.isEmpty()) {
            return null;
        }
        return adequacyModeByArea.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(areaName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
