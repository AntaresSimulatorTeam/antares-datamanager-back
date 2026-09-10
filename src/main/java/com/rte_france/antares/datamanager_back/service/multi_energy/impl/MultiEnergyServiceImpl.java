package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyService;
import com.rte_france.antares.datamanager_back.service.study.impl.LoadToJsonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiEnergyServiceImpl implements MultiEnergyService {

    private final AdequacySettingsAssemblerService adequacySettingsAssemblerService;
    private final LoadToJsonService loadToJsonService;

    private static final String AREA_ME = "area_me";
    private static final String LINKS_ME = "links_me";
    private static final String LOADS_ME = "loads_me";
    private static final String LOADS_PREFIX = "loads_";
    private static final String PROPERTIES = "properties";
    private static final String UI = "ui";
    private static final String ENERGY_COST_UNSUPPLIED = "energy_cost_unsupplied";
    private static final String ENERGY_COST_SPILLED = "energy_cost_spilled";
    private static final String ADEQUACY_PATCH_MODE = "adequacy_patch_mode";
    private static final String AREA_UI_PLACEHOLDER = "AreaUI class as JSON";
    private static final String DIRECT_MW = "directMw";
    private static final String INDIRECT_MW = "indirectMw";
    private static final String HURDLE_COST_DIRECT = "hurdleCostDirect";
    private static final String HURDLE_COST_INDIRECT = "hurdleCostIndirect";

    @Override
    public Map<String, Object> buildMultiEnergyMap(StudyEntity study, TrajectoryEntity trajectory) {
        if (trajectory != null && TrajectoryType.LINK_ME.name().equals(trajectory.getType())) {
            return buildMultiEnergyMap(study, null, trajectory);
        }
        return buildMultiEnergyMap(study, trajectory, null);
    }

    @Override
    public Map<String, Object> buildMultiEnergyMap(StudyEntity study, TrajectoryEntity areaMeTrajectory, TrajectoryEntity linkMeTrajectory) {
        log.info("Building Multi-Energy map for study id={} name={} with areaMeTrajectory={} linkMeTrajectory={}",
                study != null ? study.getId() : null,
                study != null ? study.getName() : null,
                areaMeTrajectory != null ? areaMeTrajectory.getFileName() : null,
                linkMeTrajectory != null ? linkMeTrajectory.getFileName() : null);

        Map<String, Object> meMap = new LinkedHashMap<>();

        Map<String, Object> areasMap = buildAreasMap(study, areaMeTrajectory);
        if (!areasMap.isEmpty()) {
            meMap.put(AREA_ME, areasMap);
        }

        Map<String, Object> loadsMap = buildLoadsMeMap(study, areaMeTrajectory);
        if (!loadsMap.isEmpty()) {
            meMap.put(LOADS_ME, loadsMap);
        }

        Map<String, Object> linksMap = buildLinksMeMap(linkMeTrajectory);
        if (!linksMap.isEmpty()) {
            meMap.put(LINKS_ME, linksMap);
        }

        return meMap;
    }

    private Map<String, Object> buildAreasMap(StudyEntity study, TrajectoryEntity areaMeTrajectory) {
        if (areaMeTrajectory == null || areaMeTrajectory.getAreaConfigEntities() == null) {
            return Collections.emptyMap();
        }
        TrajectoryEntity adequacyTrajectory = adequacySettingsAssemblerService.findAdequacyTrajectory(study).orElse(null);
        Map<String, String> adequacyModeByArea = study != null
                ? adequacySettingsAssemblerService.assembleAdequacyModeByArea(study)
                : Collections.emptyMap();

        Map<String, Object> areasMap = new LinkedHashMap<>();

        for (AreaConfigEntity areaConfig : areaMeTrajectory.getAreaConfigEntities()) {
            if (areaConfig.getArea() == null || areaConfig.getArea().getName() == null) {
                continue;
            }

            String areaName = areaConfig.getArea().getName();

            Map<String, Object> areaEntryMap = new LinkedHashMap<>();

            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put(ENERGY_COST_UNSUPPLIED, areaConfig.getUnsuppliedEnergyCost());
            propertiesMap.put(ENERGY_COST_SPILLED, areaConfig.getSpilledEnergyCost());

            String adequacyMode = adequacySettingsAssemblerService.resolveMode(areaName, adequacyTrajectory, adequacyModeByArea).orElse(null);
            if (StringUtils.isNotBlank(adequacyMode)) {
                propertiesMap.put(ADEQUACY_PATCH_MODE, adequacyMode);
            }


            areaEntryMap.put(PROPERTIES, propertiesMap);
            areaEntryMap.put(UI, AREA_UI_PLACEHOLDER);

            areasMap.put(areaName, areaEntryMap);
        }

        return areasMap;
    }

    private Map<String, Object> buildLinksMeMap(TrajectoryEntity linkMeTrajectory) {
        if (linkMeTrajectory == null || linkMeTrajectory.getLinkMeEntities() == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> linksMap = new LinkedHashMap<>();

        for (LinkMeEntity linkMe : linkMeTrajectory.getLinkMeEntities()) {
            if (linkMe == null || linkMe.getNodeFrom() == null || linkMe.getNodeTo() == null) {
                continue;
            }

            String linkKey = linkMe.getNodeFrom() + "/" + linkMe.getNodeTo();

            Map<String, Object> linkEntryMap = new LinkedHashMap<>();
            linkEntryMap.put(DIRECT_MW, linkMe.getDirectMw());
            linkEntryMap.put(INDIRECT_MW, linkMe.getIndirectMw());
            linkEntryMap.put(HURDLE_COST_DIRECT, linkMe.getHurdleCostsDirect());
            linkEntryMap.put(HURDLE_COST_INDIRECT, linkMe.getHurdleCostsIndirect());

            linksMap.put(linkKey, linkEntryMap);
        }

        return linksMap;
    }

    private Map<String, Object> buildLoadsMeMap(StudyEntity study, TrajectoryEntity areaMeTrajectory) {
        if (study == null || study.getTrajectories() == null || areaMeTrajectory == null
                || areaMeTrajectory.getAreaConfigEntities() == null || loadToJsonService == null) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> loadFilesByArea = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);
        if (loadFilesByArea == null || loadFilesByArea.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> loadsMeMap = new LinkedHashMap<>();

        for (AreaConfigEntity areaConfig : areaMeTrajectory.getAreaConfigEntities()) {
            if (areaConfig.getArea() == null || StringUtils.isBlank(areaConfig.getArea().getName())) {
                continue;
            }

            String areaName = areaConfig.getArea().getName();
            List<String> loadFiles = findLoadFilesForArea(loadFilesByArea, areaName);

            if (loadFiles != null && !loadFiles.isEmpty()) {
                String key = LOADS_PREFIX + areaName.toLowerCase(Locale.ROOT);
                loadsMeMap.put(key, loadFiles);
            }
        }

        return loadsMeMap;
    }

    private List<String> findLoadFilesForArea(Map<String, List<String>> loadFilesByArea, String areaName) {
        if (loadFilesByArea.containsKey(areaName)) {
            return loadFilesByArea.get(areaName);
        }
        if (loadFilesByArea.containsKey(areaName.toUpperCase(Locale.ROOT))) {
            return loadFilesByArea.get(areaName.toUpperCase(Locale.ROOT));
        }
        for (Map.Entry<String, List<String>> entry : loadFilesByArea.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(areaName)) {
                return entry.getValue();
            }
        }
        return Collections.emptyList();
    }


}
