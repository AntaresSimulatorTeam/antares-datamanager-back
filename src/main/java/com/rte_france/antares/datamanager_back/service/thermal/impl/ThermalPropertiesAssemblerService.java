package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.THERMAL_CAPACITY;
import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER;

@Service
@RequiredArgsConstructor
public class ThermalPropertiesAssemblerService {
  private final ThermalGroupMappingService thermalGroupMappingService;

  public record AreaRefKey(String area, ThermalClusterRef ref) {}

  /**
   * Builds thermal properties by {@code (area, cluster_ref)} from the given trajectories.
   * Uses {@code THERMAL_CAPACITY} rows (grouped by capacity.area + cluster_ref) and aggregates with
   * {@code THERMAL_PARAMETER} by cluster_ref
   *
   * @param trajectories input trajectories
   * @return map with {@link AreaRefKey} as keys and {@link ThermalClusterPropertiesDto} values
   * @throws NullPointerException if {@code trajectories} is null
   * @throws IllegalArgumentException if a trajectory has an invalid {@link TrajectoryType}
   */
  public Map<AreaRefKey, ThermalClusterPropertiesDto> assembleForTrajectories(Collection<TrajectoryEntity> trajectories) {
    Objects.requireNonNull(trajectories);

    var capacityTrajectories = trajectories.stream()
            .filter(Objects::nonNull)
            .filter(t -> THERMAL_CAPACITY.equals(TrajectoryType.valueOf(t.getType())))
            .toList();

    var parameterTrajs = trajectories.stream()
            .filter(Objects::nonNull)
            .filter(t -> THERMAL_TECHNICAL_COMMON_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
            .toList();

    var capacitiesByAreaRef = extractThermalCapacitiesByAreaClusterRef(capacityTrajectories);
    var commonsByRef = extractCommonParamsByClusterRef(parameterTrajs);

    var out = new LinkedHashMap<AreaRefKey, ThermalClusterPropertiesDto>();

    for (var entry : capacitiesByAreaRef.entrySet()) {
      var areaRef = entry.getKey();
      var thermalCapacities = entry.getValue();

      var ref = areaRef.ref();
      var commonsForRef = commonsByRef.getOrDefault(ref, List.of());

      var dto = computeClusterProperties(thermalCapacities, commonsForRef);

      out.put(new AreaRefKey(areaRef.area(), ref), dto);
    }

    return out;
  }

  private static LinkedHashMap<ThermalClusterRef, List<ThermalCommonParameterEntity>> extractCommonParamsByClusterRef(List<TrajectoryEntity> parameterTrajs) {
    return parameterTrajs.stream()
            .flatMap(t -> Optional.ofNullable(t.getThermalCommonParameters()).orElseGet(List::of).stream())
            .collect(Collectors.groupingBy(
                    ThermalCommonParameterEntity::getThermalClusterRef,
                    LinkedHashMap::new,
                    Collectors.toList()
            ));
  }

  public static LinkedHashMap<AreaRefKey, List<ThermalClusterCapacityEntity>> extractThermalCapacitiesByAreaClusterRef(List<TrajectoryEntity> capacityTrajs) {
    return capacityTrajs.stream()
            .flatMap(t -> Optional.ofNullable(t.getThermalClusterCapacities())
                    .orElseGet(List::of).stream()
                    .map(cap -> Map.entry(
                            new AreaRefKey(cap.getArea(), cap.getThermalClusterRef()),
                            cap
                    )))
            .collect(Collectors.groupingBy(
                    Map.Entry::getKey,
                    LinkedHashMap::new,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));
  }

  private ThermalClusterPropertiesDto computeClusterProperties(
      List<ThermalClusterCapacityEntity> thermalClusterCapacities,
      List<ThermalCommonParameterEntity> thermalCommonParameters
  ) {
    var builder = ThermalClusterPropertiesDto.builder();

    buildFromClusterCapacity(thermalClusterCapacities, builder);
    buildFromClusterParameters(thermalCommonParameters, builder);

    return builder.build();
  }

  private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterPropertiesDto.ThermalClusterPropertiesDtoBuilder builder) {

      // max POWER capacity
      OptionalDouble maxPowerOpt = thermalClusterCapacities.stream()
              .filter(cap -> cap.getCategory() == ThermalCategoryEnum.POWER)
              .mapToDouble(ThermalClusterCapacityEntity::getValue)
              .max();
      //max unit count
      OptionalDouble unitCountOpt = thermalClusterCapacities.stream()
              .filter(cap -> cap.getCategory() == ThermalCategoryEnum.NUMBER)
              .mapToDouble(ThermalClusterCapacityEntity::getValue)
              .max();
      // nominal capacity (max POWER / unitCount) ---
      if (maxPowerOpt.isPresent()) {
          double maxPower = maxPowerOpt.getAsDouble();
          double nominalCapacity = maxPower;

          if (unitCountOpt.isPresent() && unitCountOpt.getAsDouble() != 0.0) {
              nominalCapacity = maxPower / unitCountOpt.getAsDouble();
          }

          builder.nominalCapacity(nominalCapacity);
      }
      // enabled
      boolean enabled = maxPowerOpt.isPresent()
              && maxPowerOpt.getAsDouble() != 0.0
              && thermalClusterCapacities.stream()
              .anyMatch(cap -> Boolean.TRUE.equals(cap.getToUse()));

      builder.enabled(enabled);

      // unit_count
      unitCountOpt.ifPresent(unitCount -> builder.unitCount((int) unitCount));


    // group
    thermalClusterCapacities.stream()
            .map(ThermalClusterCapacityEntity::getThermalClusterRef)
            .map(ThermalClusterRef::getName)
            .map(thermalGroupMappingService::toGroup)
            .flatMap(Optional::stream)
            .findFirst()
            .ifPresent(builder::group);
  }
  private void buildFromClusterParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterPropertiesDto.ThermalClusterPropertiesDtoBuilder builder) {
    // min_stable_power
    var nominalCapacity = builder.build().getNominalCapacity();
    if (nominalCapacity != null) {
      thermalCommonParameters.stream()
              .mapToDouble(ThermalCommonParameterEntity::getMinStableGenerationDefault)
              .findFirst()
              .ifPresent(minStableGen -> builder.minStablePower(minStableGen * nominalCapacity));
    }

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
