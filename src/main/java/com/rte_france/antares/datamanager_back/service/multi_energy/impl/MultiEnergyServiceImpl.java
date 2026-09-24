package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.study.impl.LoadToJsonService;
import com.rte_france.antares.datamanager_back.service.study.impl.StsToJsonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiEnergyServiceImpl implements MultiEnergyService {

    private static final String AREA_ME = "area_me";
    private static final String LINKS_ME = "links_me";
    private static final String STS_ME = "sts_me";
    private static final String PROPERTIES = "properties";
    private static final String BINDING_CONSTRAINTS_ME = "binding_constraints_me";
    private static final String UI = "ui";
    private static final String LOADS = "loads";
    private static final String NO_LOAD_FILES = "No LOAD files for this area";
    private static final String ENERGY_COST_UNSUPPLIED = "energy_cost_unsupplied";
    private static final String ENERGY_COST_SPILLED = "energy_cost_spilled";
    private static final String ADEQUACY_PATCH_MODE = "adequacy_patch_mode";
    private static final String AREA_UI_PLACEHOLDER = "AreaUI class as JSON";
    private static final String DIRECT_MW = "directMw";
    private static final String INDIRECT_MW = "indirectMw";
    private static final String HURDLE_COST_DIRECT = "hurdleCostDirect";
    private static final String HURDLE_COST_INDIRECT = "hurdleCostIndirect";
    private static final String CONSTRAINTS_P2G = "constraints_P2G";
    private static final String CONSTRAINTS_G2P = "constraints_G2P";
    private static final String NODE = "node";
    private static final String EFFICIENCY = "efficiency";
    private static final String G2P = "G2P";
    private static final String NAME = "name";
    private static final String ENABLED = "enabled";
    private static final String TYPE = "type";
    private static final String OPERATOR = "operator";
    private static final String NODE_1_LEFT = "node_1_left";
    private static final String NODE_2_LEFT = "node_2_left";
    private static final String NODE_RIGHT_AREA = "node_right_area";

    private final AdequacySettingsAssemblerService adequacySettingsAssemblerService;
    private final StsGenerationAssemblerService stPropertiesAssemblerService;
    private final LoadToJsonService loadToJsonService;
    private final StsToJsonService stsToJsonService;

    private record P2gBindingConstraint(String node, BigDecimal efficiency) {
    }

    @Override
    public Map<String, Object> buildMultiEnergyMap(
            StudyEntity study,
            TrajectoryEntity... trajectories) {

        Map<TrajectoryType, TrajectoryEntity> trajectoriesByType =
                dispatchTrajectories(trajectories);

        return buildMultiEnergyMap(study, trajectoriesByType);
    }

    private Map<TrajectoryType, TrajectoryEntity> dispatchTrajectories(
            TrajectoryEntity... trajectories) {

        Map<TrajectoryType, TrajectoryEntity> trajectoriesByType =
                new EnumMap<>(TrajectoryType.class);

        if (trajectories == null) {
            return trajectoriesByType;
        }

        for (TrajectoryEntity trajectory : trajectories) {
            if (trajectory == null) {
                continue;
            }

            TrajectoryType type = resolveTrajectoryType(trajectory);

            if (type != null) {
                trajectoriesByType.put(type, trajectory);
            }
        }


        return trajectoriesByType;
    }

    private TrajectoryType resolveTrajectoryType(TrajectoryEntity trajectory) {
        if (trajectory.getType() != null) {
            try {
                return TrajectoryType.valueOf(trajectory.getType());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown trajectory type: {}", trajectory.getType());
                return null;
            }
        }

        if (trajectory.getLinkMeEntities() != null
                && !trajectory.getLinkMeEntities().isEmpty()) {
            return TrajectoryType.LINK_ME;
        }

        if (trajectory.getStStorageEntities() != null
                && !trajectory.getStStorageEntities().isEmpty()) {
            return TrajectoryType.STS_ME;
        }


        if (trajectory.getLoadEntities() != null
                && !trajectory.getLoadEntities().isEmpty()) {
            return TrajectoryType.LOAD_ME;
        }

        if (trajectory.getEfficiencyMeEntities() != null
                && !trajectory.getEfficiencyMeEntities().isEmpty()) {
            return TrajectoryType.EFFICIENCY_ME;
        }

        if (trajectory.getMeConstraintEntities() != null
                && !trajectory.getMeConstraintEntities().isEmpty()) {
            return TrajectoryType.CONSTRAINT_ME;
        }

        return TrajectoryType.AREA_ME;
    }

    private Map<String, Object> buildMultiEnergyMap(
            StudyEntity study,
            Map<TrajectoryType, TrajectoryEntity> trajectoriesByType) {

        TrajectoryEntity areaMeTrajectory =
                trajectoriesByType.get(TrajectoryType.AREA_ME);

        TrajectoryEntity linkMeTrajectory =
                trajectoriesByType.get(TrajectoryType.LINK_ME);


        TrajectoryEntity stsMeTrajectory =
                trajectoriesByType.get(TrajectoryType.STS_ME);

        TrajectoryEntity efficiencyMeTrajectory =
                trajectoriesByType.get(TrajectoryType.EFFICIENCY_ME);

        TrajectoryEntity constraintMeTrajectory =
                trajectoriesByType.get(TrajectoryType.CONSTRAINT_ME);

        Map<String, Object> meMap = new LinkedHashMap<>();

        Map<String, Object> areasMap = buildAreasMap(study, areaMeTrajectory, stsMeTrajectory);
        if (!areasMap.isEmpty()) {
            meMap.put(AREA_ME, areasMap);
        }

        Map<String, Object> linksMap = buildLinksMeMap(linkMeTrajectory);
        if (!linksMap.isEmpty()) {
            meMap.put(LINKS_ME, linksMap);
        }

        Map<String, Object> bindingConstraintsMeMap =
                buildBindingConstraintsMeMap(
                        efficiencyMeTrajectory,
                        constraintMeTrajectory);
        if (!bindingConstraintsMeMap.isEmpty()) {
            meMap.put(BINDING_CONSTRAINTS_ME, bindingConstraintsMeMap);
        }

        return meMap;
    }

    private Map<String, Object> buildAreasMap(
            StudyEntity study,
            TrajectoryEntity areaMeTrajectory, TrajectoryEntity stsMeTrajectory) {

        if (areaMeTrajectory == null
                || areaMeTrajectory.getAreaConfigEntities() == null) {
            return Collections.emptyMap();
        }

        TrajectoryEntity adequacyTrajectory =
                adequacySettingsAssemblerService
                        .findAdequacyTrajectory(study)
                        .orElse(null);

        Map<String, String> adequacyModeByArea =
                study != null
                        ? adequacySettingsAssemblerService
                        .assembleAdequacyModeByArea(study)
                        : Collections.emptyMap();

        Map<String, List<String>> loadFilesByArea =
                study != null && loadToJsonService != null
                        ? loadToJsonService.getListArrowLoadMeFilesFromStudy(study)
                        : Collections.emptyMap();

        var areaStsClusterGenerationDtoMap =
                stPropertiesAssemblerService != null
                        ? stPropertiesAssemblerService.assembleStsMeProperties(study, stsMeTrajectory)
                        : Collections.<String, com.rte_france.antares.datamanager_back.dto.StsGenerationDTO>emptyMap();

        Map<String, Object> areasMap = new LinkedHashMap<>();

        for (AreaConfigEntity areaConfig :
                areaMeTrajectory.getAreaConfigEntities()) {

            if (areaConfig.getArea() == null
                    || areaConfig.getArea().getName() == null) {
                continue;
            }

            String areaName = areaConfig.getArea().getName();

            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put(
                    ENERGY_COST_UNSUPPLIED,
                    areaConfig.getUnsuppliedEnergyCost());
            propertiesMap.put(
                    ENERGY_COST_SPILLED,
                    areaConfig.getSpilledEnergyCost());

            String adequacyMode =
                    adequacySettingsAssemblerService
                            .resolveMode(
                                    areaName,
                                    adequacyTrajectory,
                                    adequacyModeByArea)
                            .orElse(null);

            if (StringUtils.isNotBlank(adequacyMode)) {
                propertiesMap.put(ADEQUACY_PATCH_MODE, adequacyMode);
            }

            Map<String, Object> areaEntryMap = new LinkedHashMap<>();
            areaEntryMap.put(PROPERTIES, propertiesMap);
            areaEntryMap.put(UI, AREA_UI_PLACEHOLDER);

            List<String> loadFiles =
                    findLoadFilesForArea(loadFilesByArea, areaName);

            areaEntryMap.put(
                    LOADS,
                    loadFiles != null && !loadFiles.isEmpty()
                            ? loadFiles
                            : NO_LOAD_FILES);
            Map<String, Object> stsMap = stsToJsonService.stsMeMapGenerator(areaName, areaStsClusterGenerationDtoMap);

            areaEntryMap.put(
                    STS_ME,
                    stsMap);

            areasMap.put(areaName, areaEntryMap);
        }

        return areasMap;
    }

    private List<String> findLoadFilesForArea(
            Map<String, List<String>> loadFilesByArea,
            String areaName) {

        if (loadFilesByArea == null
                || loadFilesByArea.isEmpty()
                || StringUtils.isBlank(areaName)) {
            return Collections.emptyList();
        }

        if (loadFilesByArea.containsKey(areaName)) {
            List<String> files = loadFilesByArea.get(areaName);
            return files != null ? files : Collections.emptyList();
        }

        String upperCaseAreaName = areaName.toUpperCase(Locale.ROOT);

        if (loadFilesByArea.containsKey(upperCaseAreaName)) {
            List<String> files = loadFilesByArea.get(upperCaseAreaName);
            return files != null ? files : Collections.emptyList();
        }

        for (Map.Entry<String, List<String>> entry :
                loadFilesByArea.entrySet()) {

            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(areaName)) {
                return entry.getValue() != null ? entry.getValue() : Collections.emptyList();
            }
        }

        String normalizedArea =
                areaName.toLowerCase(Locale.ROOT)
                        .replaceFirst("^loads?_", "");

        for (Map.Entry<String, List<String>> entry :
                loadFilesByArea.entrySet()) {

            if (entry.getKey() == null) {
                continue;
            }

            String normalizedKey =
                    entry.getKey()
                            .toLowerCase(Locale.ROOT)
                            .replaceFirst("^loads?_", "");

            if (normalizedKey.equals(normalizedArea)) {
                return entry.getValue() != null ? entry.getValue() : Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    private Map<String, Object> buildLinksMeMap(
            TrajectoryEntity linkMeTrajectory) {

        if (linkMeTrajectory == null
                || linkMeTrajectory.getLinkMeEntities() == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> linksMap = new LinkedHashMap<>();

        for (LinkMeEntity linkMe :
                linkMeTrajectory.getLinkMeEntities()) {

            if (linkMe == null
                    || linkMe.getNodeFrom() == null
                    || linkMe.getNodeTo() == null) {
                continue;
            }

            String linkKey =
                    linkMe.getNodeFrom() + "/" + linkMe.getNodeTo();

            Map<String, Object> linkEntryMap = new LinkedHashMap<>();
            linkEntryMap.put(DIRECT_MW, linkMe.getDirectMw());
            linkEntryMap.put(INDIRECT_MW, linkMe.getIndirectMw());
            linkEntryMap.put(
                    HURDLE_COST_DIRECT,
                    linkMe.getHurdleCostsDirect());
            linkEntryMap.put(
                    HURDLE_COST_INDIRECT,
                    linkMe.getHurdleCostsIndirect());

            linksMap.put(linkKey, linkEntryMap);
        }

        return linksMap;
    }

    private Map<String, Object> buildBindingConstraintsMeMap(
            TrajectoryEntity efficiencyMeTrajectory,
            TrajectoryEntity constraintMeTrajectory) {

        Map<String, Object> bindingConstraintsMeMap = new LinkedHashMap<>();

        List<Map<String, Object>> p2gConstraints = buildBindingP2GConstraints(efficiencyMeTrajectory);
        if (!p2gConstraints.isEmpty()) {
            bindingConstraintsMeMap.put(CONSTRAINTS_P2G, p2gConstraints);
        }

        List<Map<String, Object>> g2pConstraints = buildBindingG2PConstraints(constraintMeTrajectory);
        if (!g2pConstraints.isEmpty()) {
            bindingConstraintsMeMap.put(CONSTRAINTS_G2P, g2pConstraints);
        }

        return bindingConstraintsMeMap;
    }

    private List<Map<String, Object>> buildBindingP2GConstraints(
            TrajectoryEntity efficiencyMeTrajectory) {
        if (efficiencyMeTrajectory == null
                || efficiencyMeTrajectory.getEfficiencyMeEntities() == null) {
            return Collections.emptyList();
        }

        Set<P2gBindingConstraint> constraints = new LinkedHashSet<>();

        for (EfficiencyMeEntity efficiencyMe : efficiencyMeTrajectory.getEfficiencyMeEntities()) {
            if (efficiencyMe == null
                    || StringUtils.isBlank(efficiencyMe.getNodeCluster())) {
                continue;
            }

            constraints.add(new P2gBindingConstraint(
                    efficiencyMe.getNodeCluster(),
                    efficiencyMe.getEfficiency()));
        }

        if (constraints.isEmpty()) {
            return Collections.emptyList();
        }

        return getBindingP2GEntries(constraints);
    }

    private List<Map<String, Object>> buildBindingG2PConstraints(
            TrajectoryEntity constraintMeTrajectory) {
        if (constraintMeTrajectory == null
                || constraintMeTrajectory.getMeConstraintEntities() == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> constraintG2PEntries = new ArrayList<>();

        for (MeConstraintEntity constraint : constraintMeTrajectory.getMeConstraintEntities()) {
            if (constraint == null
                    || !G2P.equalsIgnoreCase(constraint.getType())
                    || StringUtils.isBlank(constraint.getName())) {
                continue;
            }

            Map<String, Object> constraintG2PEntry = new LinkedHashMap<>();
            constraintG2PEntry.put(NAME, constraint.getName());
            constraintG2PEntry.put(ENABLED, Boolean.TRUE.equals(constraint.getEnabled()));
            constraintG2PEntry.put(TYPE, constraint.getTemporality());
            constraintG2PEntry.put(OPERATOR, constraint.getSign());
            constraintG2PEntry.put(NODE_1_LEFT, constraint.getNoeud1Gauche());
            constraintG2PEntry.put(NODE_2_LEFT, constraint.getNoeud2Gauche());
            constraintG2PEntry.put(
                    NODE_RIGHT_AREA,
                    buildRightAreaClusters(constraint, constraintMeTrajectory));
            constraintG2PEntries.add(constraintG2PEntry);
        }

        return constraintG2PEntries;
    }

    private Map<String, Object> buildRightAreaClusters(
            MeConstraintEntity constraint,
            TrajectoryEntity constraintMeTrajectory) {
        List<String> areas =
                findAreasForGroup(
                        constraintMeTrajectory.getGroupAreaDescEntities(),
                        constraint.getNoeud1Droite());
        List<String> clusters =
                findClustersForGroup(
                        constraintMeTrajectory.getGroupClusterDescEntities(),
                        constraint.getClusterDroite());

        Map<String, Object> rightAreaClusters = new LinkedHashMap<>();
        for (String area : areas) {
            Map<String, Object> clusterEntries = new LinkedHashMap<>();
            for (String cluster : clusters) {
                Map<String, Object> clusterEntry = new LinkedHashMap<>();
                clusterEntry.put(EFFICIENCY, null);
                clusterEntries.put(cluster, clusterEntry);
            }
            rightAreaClusters.put(area, clusterEntries);
        }

        return rightAreaClusters;
    }

    private List<String> findAreasForGroup(
            List<GroupAreaDescEntity> groupAreaDescEntities,
            String groupName) {
        if (groupAreaDescEntities == null || StringUtils.isBlank(groupName)) {
            return Collections.emptyList();
        }

        return groupAreaDescEntities.stream()
                .filter(group -> group != null
                        && group.getGroupName() != null
                        && group.getGroupName().equalsIgnoreCase(groupName)
                        && group.getAreas() != null)
                .flatMap(group -> group.getAreas().stream())
                .filter(area -> area != null && StringUtils.isNotBlank(area.getArea()))
                .map(ListAreaDescEntity::getArea)
                .distinct()
                .toList();
    }

    private List<String> findClustersForGroup(
            List<GroupClusterDescEntity> groupClusterDescEntities,
            String groupName) {
        if (groupClusterDescEntities == null || StringUtils.isBlank(groupName)) {
            return Collections.emptyList();
        }

        return groupClusterDescEntities.stream()
                .filter(group -> group != null
                        && group.getGroupName() != null
                        && group.getGroupName().equalsIgnoreCase(groupName)
                        && group.getClusters() != null)
                .flatMap(group -> group.getClusters().stream())
                .filter(cluster -> cluster != null && StringUtils.isNotBlank(cluster.getCluster()))
                .map(ListClusterDescEntity::getCluster)
                .distinct()
                .toList();
    }

    private static @NonNull List<Map<String, Object>> getBindingP2GEntries(Set<P2gBindingConstraint> constraints) {
        List<Map<String, Object>> constraintP2GEntries = new ArrayList<>();
        for (P2gBindingConstraint constraint : constraints) {
            Map<String, Object> constraintP2GEntry = new LinkedHashMap<>();
            constraintP2GEntry.put(NODE, constraint.node());
            constraintP2GEntry.put(EFFICIENCY, constraint.efficiency());
            constraintP2GEntries.add(constraintP2GEntry);
        }

        return constraintP2GEntries;
    }


}