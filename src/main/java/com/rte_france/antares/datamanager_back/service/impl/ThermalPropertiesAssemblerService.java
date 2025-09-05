package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerService {
  private final ThermalGroupMappingService thermalGroupMappingService;


  public Map<String, ThermalClusterPropertiesDto> assembleForTrajectory(TrajectoryEntity trajectory) {
    var clusterCapacityRows = Optional.ofNullable(trajectory.getThermalClusterCapacities()).orElseGet(List::of);
    var commonParams = Optional.ofNullable(trajectory.getThermalClusterParameters()).orElseGet(List::of);

    var capacityByCluster = clusterCapacityRows.stream().collect(Collectors.groupingBy(ThermalClusterCapacityEntity::getThermalClusterRef));
    var commonParamsByCluster = commonParams.stream().collect(Collectors.groupingBy(ThermalCommonParameterEntity::getThermalClusterRef));

    var out = new LinkedHashMap<String, ThermalClusterPropertiesDto>();
    for (var entry : capacityByCluster.entrySet()) {
      var ref = entry.getKey();
      var thermalClusterCapacities = entry.getValue();
      var thermalCommonParams = commonParamsByCluster.get(ref);

      var dto = computeClusterProperties(thermalClusterCapacities, thermalCommonParams);
      out.put(trajectory.getArea() + "_" + ref.getName(), dto);
    }
    return out;
  }

  private ThermalClusterPropertiesDto computeClusterProperties(
      List<ThermalClusterCapacityEntity> thermalClusterCapacities,
      List<ThermalCommonParameterEntity> thermalCommonParameters
  ) {
    var builder = ThermalClusterPropertiesDto.defaults().toBuilder();

    buildFromClusterCapacity(thermalClusterCapacities, builder);
    buildFromClusterParameters(thermalCommonParameters, builder);

    return builder.build();
  }

  private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterPropertiesDto.ThermalClusterPropertiesDtoBuilder builder) {
    // enabled
    thermalClusterCapacities.stream()
            .map(ThermalClusterCapacityEntity::getToUse)
            .findFirst()
            .ifPresent(builder::enabled);

    // unit_count
    thermalClusterCapacities.stream()
            .filter(cap -> cap.getCategory() == ThermalCategoryEnum.NUMBER)
            .mapToDouble(ThermalClusterCapacityEntity::getValue)
            .max()
            .ifPresent(unitCount -> builder.unitCount((int) unitCount));

    // nominal_capacity
    thermalClusterCapacities.stream()
            .filter(cap -> cap.getCategory() == ThermalCategoryEnum.POWER)
            .mapToDouble(ThermalClusterCapacityEntity::getValue)
            .max()
            .ifPresent(builder::nominalCapacity);

    // group
    thermalClusterCapacities.stream()
            .map(ThermalClusterCapacityEntity::getThermalClusterRef)
            .map(ThermalClusterRef::getName)
            .map(thermalGroupMappingService::toGroup)
            .findFirst()
            .ifPresent(builder::group);
  }
  private void buildFromClusterParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterPropertiesDto.ThermalClusterPropertiesDtoBuilder builder) {
    // min_stable_power
    var nominalCapacity = builder.build().getNominalCapacity();
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getMinStableGenerationDefault)
            .findFirst()
            .ifPresent(minStableGen -> builder.minStablePower(minStableGen * nominalCapacity));

    // min_up_time
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getMinUpTime)
            .findFirst()
            .ifPresent(minUpTime -> builder.minUpTime((int) minUpTime));

    // min_down_time
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getMinDownTime)
            .findFirst()
            .ifPresent(minDownTime -> builder.minDownTime((int) minDownTime));

    // efficiency
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getEfficiencyDefault)
            .findFirst()
            .ifPresent(builder::efficiency);

    // variable_o_m_cost
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getOmCost)
            .findFirst()
            .ifPresent(builder::variableOMCost);
  }
}
