package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.*;

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
   * @return map with {@link AreaRefKey} as keys and {@link ThermalClusterGenerationDto} values
   * @throws NullPointerException if {@code trajectories} is null
   * @throws IllegalArgumentException if a trajectory has an invalid {@link TrajectoryType}
   */
  public Map<AreaRefKey, ThermalClusterGenerationDto> assembleForTrajectories(Collection<TrajectoryEntity> trajectories) {
    Objects.requireNonNull(trajectories);

    var capacityTrajectories = trajectories.stream()
            .filter(Objects::nonNull)
            .filter(t -> THERMAL_CAPACITY.equals(TrajectoryType.valueOf(t.getType())))
            .toList();

    var commonTrajectories = trajectories.stream()
            .filter(Objects::nonNull)
            .filter(t -> THERMAL_TECHNICAL_COMMON_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
            .toList();
      var specificTrajectories = trajectories.stream()
              .filter(Objects::nonNull)
              .filter(t -> THERMAL_TECHNICAL_SPECIFIC_PARAMETER.equals(TrajectoryType.valueOf(t.getType())))
              .toList();

    var capacitiesByAreaRef = extractThermalCapacitiesByAreaClusterRef(capacityTrajectories);
    var commonsByRef = extractCommonParamsByClusterRef(commonTrajectories);
    var specificsByRef = extractSpecificParamsByClusterRef(specificTrajectories);

    var out = new LinkedHashMap<AreaRefKey, ThermalClusterGenerationDto>();

    for (var entry : capacitiesByAreaRef.entrySet()) {
      var areaRef = entry.getKey();
      var thermalCapacities = entry.getValue();

      var ref = areaRef.ref();
      var commonsForRef = commonsByRef.getOrDefault(ref, List.of());
      var specificForRef = specificsByRef.getOrDefault(ref, List.of());

      var dto = computeClusterProperties(thermalCapacities, commonsForRef, specificForRef);

      out.put(new AreaRefKey(areaRef.area(), ref), dto);
    }

    return out;
  }

    private static LinkedHashMap<ThermalClusterRef, List<ThermalSpecificParametersEntity>>extractSpecificParamsByClusterRef(List<TrajectoryEntity> specificTrajectories) {
        return specificTrajectories.stream()
                .flatMap(t -> Optional.ofNullable(t.getThermalSpecificParameters()).orElseGet(List::of).stream())
                .collect(Collectors.groupingBy(
                        ThermalSpecificParametersEntity::getThermalClusterRef,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
  }

    private static LinkedHashMap<ThermalClusterRef, List<ThermalCommonParameterEntity>> extractCommonParamsByClusterRef(List<TrajectoryEntity> parameterTrajectories) {
    return parameterTrajectories.stream()
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

  private ThermalClusterGenerationDto computeClusterProperties(
      List<ThermalClusterCapacityEntity> thermalClusterCapacities,
      List<ThermalCommonParameterEntity> thermalCommonParameters,
      List<ThermalSpecificParametersEntity> thermalSpecificParameters
  ) {
    var builder = ThermalClusterGenerationDto.builder();

    buildFromClusterCapacity(thermalClusterCapacities, builder);
    buildFromCommonParameters(thermalCommonParameters, builder);
    buildFromSpecificParameters(thermalSpecificParameters, builder);

    return builder.build();
  }



    private void buildFromClusterCapacity(List<ThermalClusterCapacityEntity> thermalClusterCapacities, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {

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
  private void buildFromCommonParameters(List<ThermalCommonParameterEntity> thermalCommonParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
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
            .mapToDouble(thermalCommonParam -> thermalCommonParam.getEfficiencyDefault() * 100) // convert to percentage
            .findFirst()
            .ifPresent(builder::efficiency);

    // variable_o_m_cost
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getOmCost)
            .findFirst()
            .ifPresent(builder::variableOMCost);

    //FO rate
    thermalCommonParameters.stream()
            .mapToDouble(ThermalCommonParameterEntity::getFoRateDefault)
            .findFirst()
            .ifPresent(builder::foCommonRate);

      //FO duration
      thermalCommonParameters.stream()
              .mapToDouble(ThermalCommonParameterEntity::getFoDurationDefault)
              .findFirst()
              .ifPresent(builder::foCommonDuration);

      //PO rate
      thermalCommonParameters.stream()
              .mapToDouble(ThermalCommonParameterEntity::getPoWinterDefault)
              .findFirst()
              .ifPresent(builder::poCommonRate);
      //PO duration
      thermalCommonParameters.stream()
              .mapToDouble(ThermalCommonParameterEntity::getPoDurationDefault)
              .findFirst()
              .ifPresent(builder::poCommonDuration);


  }

    private void buildFromSpecificParameters(List<ThermalSpecificParametersEntity> thermalSpecificParameters, ThermalClusterGenerationDto.ThermalClusterGenerationDtoBuilder builder) {
        // min_stable_power
        var nominalCapacity = builder.build().getNominalCapacity();
        if (nominalCapacity != null) {
            thermalSpecificParameters.stream()
                    .mapToDouble(ThermalSpecificParametersEntity::getMinStableGeneration)
                    .findFirst()
                    .ifPresent(minStableGen -> builder.minStablePower(minStableGen * nominalCapacity));
        }
        //spinning
        thermalSpecificParameters.stream()
                .map(thermalSpecificParam -> thermalSpecificParam.getSpinning() * 100)
                .findFirst()
                .ifPresent(builder::spinning);
        //efficiency
        thermalSpecificParameters.stream()
                .mapToDouble(thermalSpecificParam -> thermalSpecificParam.getEfficiency() * 100)
                .findFirst()
                .ifPresent(builder::efficiency);

        //FO duration
        thermalSpecificParameters.stream()
                .mapToDouble(thermalSpecificParam->thermalSpecificParam.getFoDuration())
                .findFirst()
                .ifPresent(builder::foDuration);

        //PO duration
        thermalSpecificParameters.stream()
                .mapToDouble(thermalSpecificParam->thermalSpecificParam.getPoDuration())
                .findFirst()
                .ifPresent(builder::poDuration);
        //FO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
                    List<Double> forcedOutageMonthly = List.of(
                            param.getF1(),
                            param.getF2(),
                            param.getF3(),
                            param.getF4(),
                            param.getF5(),
                            param.getF6(),
                            param.getF7(),
                            param.getF8(),
                            param.getF9(),
                            param.getF10(),
                            param.getF11(),
                            param.getF12()
                    );
                    builder.foMonthlyRate(forcedOutageMonthly);
                });

    //PO Monthly rate
        thermalSpecificParameters.stream()
                .findFirst()
                .ifPresent(param -> {
        List<Double> plannedOutageMonthly = List.of(
                param.getP1(),
                param.getP2(),
                param.getP3(),
                param.getP4(),
                param.getP5(),
                param.getP6(),
                param.getP7(),
                param.getP8(),
                param.getP9(),
                param.getP10(),
                param.getP11(),
                param.getP12()
        );
        builder.poMonthlyRate(plannedOutageMonthly);
    });
        //NPO_MAX_winter
        thermalSpecificParameters.stream().mapToDouble(ThermalSpecificParametersEntity::getNpoMaxWinter)
                .findFirst()
                .ifPresent(builder::npoMaxWinter);
        //NPO_MAX_summer
        thermalSpecificParameters.stream().mapToDouble(ThermalSpecificParametersEntity::getNpoMaxSummer)
                .findFirst()
                .ifPresent(builder::npoMaxSummer);
        //nb_unit
        thermalSpecificParameters.stream().mapToInt(ThermalSpecificParametersEntity::getNbUnit)
                .findFirst()
                .ifPresent(builder::nbUnit);

    }
}
