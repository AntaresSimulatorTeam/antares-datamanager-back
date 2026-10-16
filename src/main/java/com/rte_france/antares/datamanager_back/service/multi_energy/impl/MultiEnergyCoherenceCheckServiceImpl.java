package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.ConstraintRowData;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyCoherenceCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.extractAreaNamesFromAreaMe;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiEnergyCoherenceCheckServiceImpl implements MultiEnergyCoherenceCheckService {
    private static final String MSG_NODE_MUST_BELONG_AREA_ME = "Node {0} in {1} tab must belong to AREA_ME trajectory";
    private static final String MSG_NODE_MUST_BELONG_AREA_DESC = "Node {0} in {1} tab must belong to listArea_desc tab";
    private static final String MSG_CLUSTER_MUST_BELONG_DESC = "Cluster {0} in {1} tab must belong to listCluster_desc tab";
    private static final String MSG_NO_AREA_ME = "No area {0} from the {1} trajectory is present in the AREA_ME trajectory";
    private static final String PREFIX_EFFICIENCY_AREA = "z_p2g";
    private final TrajectoryRepository trajectoryRepository;
    private final AreaRepository areaRepository;

    @Override
    public void checkAreaMETrajectoryConsistency(Integer studyId, String trajectoryType, TrajectoryEntity trajectory) {
        if (studyId == null || trajectoryType == null || trajectory == null) {
            return;
        }

        Set<String> areasList = getAreasMe(studyId);
        
        Set<String> nodeList = getNodeListFromTrajectory(trajectoryType, trajectory);
        boolean hasMatchingArea = nodeList.stream()
                .anyMatch(areasList::contains);        

        if (!hasMatchingArea) {
            String typeToUse = trajectoryType.equalsIgnoreCase(TrajectoryType.THERMAL_CAPACITY_ME.name()) ? "THERMAL_ME" : trajectoryType;
            if (trajectoryType.equalsIgnoreCase(TrajectoryType.HYDRO_CAPACITY_ME.name())) {
                typeToUse = "HYDRO_ME";
            }
            throw createValidationError(typeToUse);
        }
    }
    
    public Set<String> getAreasMe(Integer studyId) {
        List<TrajectoryEntity> areaMeTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId);
        return extractAreaNamesFromAreaMe(areaMeTrajectories);
    }

    private BusinessException createValidationError(String typeToUse) {
        return BusinessException.builder()
                .message(MSG_NO_AREA_ME)
                .errorMessageArguments(List.of(typeToUse.equalsIgnoreCase(TrajectoryType.EFFICIENCY_ME.name()) ? PREFIX_EFFICIENCY_AREA : "", typeToUse))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private <T> Set<String> extractAreas(Collection<T> entities, Function<T, String> areaExtractor) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptySet();
        }

        return entities.stream()
                .map(areaExtractor)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(area -> !area.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private Set<String> getNodeListFromTrajectory(String trajectoryType, TrajectoryEntity trajectory) {
        if (trajectory == null || trajectoryType == null) {
            return Collections.emptySet();
        }

        return switch (TrajectoryType.valueOf(trajectoryType)) {
            case LOAD_ME -> extractAreas(trajectory.getLoadEntities(), LoadEntity::getArea);
            case STS_ME  -> extractAreas(trajectory.getStStorageEntities(), StStorageEntity::getArea);
            case THERMAL_CAPACITY_ME  -> extractAreas(trajectory.getThermalMeEntities(), ThermalMeEntity::getNode);
            case HYDRO_CAPACITY_ME  -> extractAreas(trajectory.getHydroCapacityMeEntities(), HydroCapacityMeEntity::getNode);
            case EFFICIENCY_ME  -> extractAreas(trajectory.getEfficiencyMeEntities(), entity -> entity.getNodeCluster().startsWith(PREFIX_EFFICIENCY_AREA)
                    ? entity.getNodeCluster()
                    : null);
            default -> Collections.emptySet();
        };
    }

    @Override
    public void validateHydroCapacityMeCoherence(Integer studyId, TrajectoryEntity trajectoryEntity) {

        Set<String> areasMeList = extractNodesFromAreaMeTrajectory(studyId);

        Set<String> hydroMeCapacityAreas = trajectoryEntity.getHydroCapacityMeEntities().stream()
                .map(HydroCapacityMeEntity::getNode)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!areasMeList.containsAll(hydroMeCapacityAreas)) {
            hydroMeCapacityAreas.removeAll(areasMeList);
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", hydroMeCapacityAreas)))
                    .message("Areas {0} from HYDRO_ME Capacity trajectory is (are) not present in AREAS_ME trajectory")
                    .build();
        }

    }

    @Override
    public void validateConstraintMeCoherence(Integer studyId, TrajectoryEntity trajectory) {

        //RG sheet :listArea_desc

        //get list area from group area  from Constraint trajectory
        Set<String> areasMeList = trajectory.getGroupAreaDescEntities()
                .stream()
                .flatMap(groupAreaDescEntity ->
                        groupAreaDescEntity.getAreas().stream().map(area -> area.getArea().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toSet());

        //get area from area trajectory by study id and trajectory type AREA
        Set<String> listAreaFromAreaTrajectory = areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA.name())
                .stream()
                .map(areaEntity -> areaEntity.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        //all area constraint must be present in area trajectory
        if (!listAreaFromAreaTrajectory.containsAll(areasMeList)) {
            areasMeList.removeAll(listAreaFromAreaTrajectory);
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(String.join(", ", areasMeList)))
                    .message("Areas {0} from CONSTRAINT_ME trajectory is (are) not present in AREA trajectory")
                    .build();
        }

    }

    public Set<String> extractNodesFromAreaMeTrajectory(Integer studyId) {
        return  areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA_ME.name()).stream()
                .map(area -> area.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public void validateConstraintData(ConstraintRowData data, Set<String> areaMeNodes,
                                       Set<String> groupAreaNames, Set<String> groupClusterNames, String horizonYear) {
        validateNode(data.getNoeud1Gauche(), areaMeNodes, horizonYear, MSG_NODE_MUST_BELONG_AREA_ME);
        validateNode(data.getNoeud2Gauche(), areaMeNodes, horizonYear, MSG_NODE_MUST_BELONG_AREA_ME);
        validateCluster(data.getClusterGauche(), groupClusterNames, horizonYear);
        validateNode(data.getNoeud1Droite(), groupAreaNames, horizonYear, MSG_NODE_MUST_BELONG_AREA_DESC);
        validateNode(data.getNoeud2Droite(), groupAreaNames, horizonYear, MSG_NODE_MUST_BELONG_AREA_DESC);
        validateCluster(data.getClusterDroite(), groupClusterNames, horizonYear);
    }

    private void validateNode(String node, Set<String> validNodes, String horizonYear, String errorMsg) {
        if (isEmptyString(node) && !validNodes.contains(node.trim().toLowerCase(Locale.ROOT))) {
            throw BusinessException.builder()
                    .message(errorMsg)
                    .errorMessageArguments(List.of(node.trim(), horizonYear))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateCluster(String cluster, Set<String> validClusters, String horizonYear) {
        if (isEmptyString(cluster) && !validClusters.contains(cluster.trim().toLowerCase(Locale.ROOT))) {
            throw BusinessException.builder()
                    .message(MultiEnergyCoherenceCheckServiceImpl.MSG_CLUSTER_MUST_BELONG_DESC)
                    .errorMessageArguments(List.of(cluster.trim(), horizonYear))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
    private boolean isEmptyString(String str) {
        return str != null && !str.trim().isEmpty();
    }

}
