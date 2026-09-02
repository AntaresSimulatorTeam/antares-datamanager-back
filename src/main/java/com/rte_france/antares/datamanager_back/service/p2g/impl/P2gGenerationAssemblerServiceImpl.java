package com.rte_france.antares.datamanager_back.service.p2g.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.P2gClusterGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.P2gGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.P2gPropertiesGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.P2GCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.P2GCostRepository;
import com.rte_france.antares.datamanager_back.repository.P2GParametersRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResGroupEnum;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GParametersEntity;
import com.rte_france.antares.datamanager_back.service.p2g.P2gFilePrefixes;
import com.rte_france.antares.datamanager_back.service.p2g.P2gGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class P2gGenerationAssemblerServiceImpl implements P2gGenerationAssemblerService {

    private static final String TYPE_BASE = "Base";
    private static final String TYPE_MARGINAL = "Marginal";
    private static final String TYPE_METHANATION = "Methanation";
    private static final String TYPE_ASSERVI = "Asservi";

    private final P2GCapacityRepository p2gCapacityRepository;
    private final P2GCostRepository p2gCostRepository;
    private final P2GParametersRepository p2gParametersRepository;
    private final ResGenerationAssemblerService resGenerationAssemblerService;
    private final AntaresDataManagerProperties properties;

    private record P2gRawData(List<P2GCapacityEntity> capacities, Map<String, P2GCostEntity> costsByType,
                               List<P2GParametersEntity> parameters) {}

    @Override
    public P2gGenerationDTO assembleP2g(StudyEntity study, TrajectoryEntity capacityCostTrajectory, TrajectoryEntity marketModulationTrajectory) {
        if (marketModulationTrajectory == null) {
            throw BusinessException.builder()
                    .message("P2G market bid modulation trajectory must be linked to the study when a P2G capacity/cost trajectory is present.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        P2gRawData data = loadP2gData(capacityCostTrajectory);
        String trajectoryName = capacityCostTrajectory.getFileName();

        validateAsserviResCoverage(study, data.capacities());

        P2gClusterGenerationDTO.AsserviParameters asserviParameters = buildAsserviParameters(data.parameters(), trajectoryName);

        P2gClusterGenerationDTO base = buildCluster(data.capacities(), requireCost(data.costsByType(), TYPE_BASE, trajectoryName),
                P2GCapacityEntity::getBaseEff, P2GCapacityEntity::getBaseCapacity, true, null);
        P2gClusterGenerationDTO marg = buildCluster(data.capacities(), requireCost(data.costsByType(), TYPE_MARGINAL, trajectoryName),
                P2GCapacityEntity::getMargCapacity, P2GCapacityEntity::getMargCapacity, false, null);
        P2gClusterGenerationDTO methanation = buildCluster(data.capacities(), requireCost(data.costsByType(), TYPE_METHANATION, trajectoryName),
                P2GCapacityEntity::getMethanationCapacity, P2GCapacityEntity::getMethanationCapacity, false, null);
        P2gClusterGenerationDTO asservi = buildCluster(data.capacities(), requireCost(data.costsByType(), TYPE_ASSERVI, trajectoryName),
                P2GCapacityEntity::getAsserviCapacity, P2GCapacityEntity::getAsserviCapacity, false, asserviParameters);

        String marketModulation = resolveMarketModulationPath(study, marketModulationTrajectory);

        return new P2gGenerationDTO(marketModulation, base, marg, methanation, asservi);
    }

    private P2gRawData loadP2gData(TrajectoryEntity capacityCostTrajectory) {
        List<P2GCapacityEntity> capacities = p2gCapacityRepository.findByTrajectoryId(capacityCostTrajectory.getId());
        List<P2GCostEntity> costs = p2gCostRepository.findByTrajectoryId(capacityCostTrajectory.getId());
        List<P2GParametersEntity> parameters = p2gParametersRepository.findByTrajectoryId(capacityCostTrajectory.getId());
        String trajectoryName = capacityCostTrajectory.getFileName();

        Map<String, P2GCostEntity> costsByType = costs.stream()
                .collect(Collectors.toMap(P2GCostEntity::getType, Function.identity(), (first, duplicate) -> {
                    throw TechnicalException.builder()
                            .errorMessageArguments(List.of(first.getType(), trajectoryName))
                            .message("Duplicate P2G cost entry found for type {0} in trajectory {1}")
                            .build();
                }));

        return new P2gRawData(capacities, costsByType, parameters);
    }

    // for sonar: ToDoubleFunc can't replace Function because we need a nullable double
    // and ToDouble returns primitive
    @SuppressWarnings("java:S4276")
    private P2gClusterGenerationDTO buildCluster(
            List<P2GCapacityEntity> capacities,
            P2GCostEntity cost,
            Function<P2GCapacityEntity, Double> nominalCapacityColumn,
            Function<P2GCapacityEntity, Double> linkCapacityColumn,
            boolean includeFatalBand,
            P2gClusterGenerationDTO.AsserviParameters parameters
    ) {
        double nominalCapacity = capacities.stream()
                .map(nominalCapacityColumn)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        Map<String, P2gClusterGenerationDTO.Link> links = new LinkedHashMap<>();
        for (P2GCapacityEntity capacity : capacities) {
            Double linkCapacity = linkCapacityColumn.apply(capacity);
            if (linkCapacity != null) {
                Double fatalBand = includeFatalBand ? capacity.getBaseFatalBand() : null;
                links.put(capacity.getArea(), new P2gClusterGenerationDTO.Link(linkCapacity, fatalBand));
            }
        }

        P2gPropertiesGenerationDTO clusterProperties = new P2gPropertiesGenerationDTO(nominalCapacity, cost.getCost());
        return new P2gClusterGenerationDTO(clusterProperties, cost.getModulation(), links, parameters);
    }

    private P2gClusterGenerationDTO.AsserviParameters buildAsserviParameters(List<P2GParametersEntity> parameters, String trajectoryName) {
        P2GParametersEntity entity = parameters.stream().findFirst()
                .orElseThrow(() -> TechnicalException.builder()
                        .errorMessageArguments(List.of(trajectoryName))
                        .message("Missing P2G parameters row for trajectory {0}")
                        .build());
        return new P2gClusterGenerationDTO.AsserviParameters(entity.getFcElectrolyseur(), entity.getFacteurSurdimensionEnr(), entity.getPartPvMix());
    }

    private P2GCostEntity requireCost(Map<String, P2GCostEntity> costsByType, String type, String trajectoryName) {
        P2GCostEntity cost = costsByType.get(type);
        if (cost == null) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(type, trajectoryName))
                    .message("Missing P2G cost entry for type {0} in trajectory {1}")
                    .build();
        }
        return cost;
    }

    private void validateAsserviResCoverage(StudyEntity study, List<P2GCapacityEntity> capacities) {
        List<String> asserviAreas = capacities.stream()
                .filter(c -> c.getAsserviCapacity() != null)
                .map(P2GCapacityEntity::getArea)
                .toList();
        if (asserviAreas.isEmpty()) {
            return;
        }

        Map<String, Map<String, ResClusterGenerationDto>> resPropsByArea = resGenerationAssemblerService.assembleResProperties(study);
        asserviAreas.forEach(area -> checkAreaHasFcCoverage(area, resPropsByArea.getOrDefault(area.toUpperCase(Locale.ROOT), Collections.emptyMap())));
    }

    private void checkAreaHasFcCoverage(String area, Map<String, ResClusterGenerationDto> areaResProps) {
        Set<String> groups = areaResProps.values().stream()
                .map(ResClusterGenerationDto::properties)
                .map(ResClusterPropertiesDto::group)
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        if (!groups.contains(ResGroupEnum.SOLAR_PV.value())) {
            missing.add(ResGroupEnum.SOLAR_PV.value());
        }
        if (!groups.contains(ResGroupEnum.WIND_ONSHORE.value())) {
            missing.add(ResGroupEnum.WIND_ONSHORE.value());
        }
        if (!missing.isEmpty()) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(area, String.join(", ", missing)))
                    .message("P2G asservi is defined for area {0} but RES load factor coverage is missing for: {1}. "
                            + "Link RES trajectories covering these technologies for this area before generating the study.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private String resolveMarketModulationPath(StudyEntity study, TrajectoryEntity marketModulationTrajectory) {
        String horizonYear = extractHorizonYear(study.getHorizon());
        String folderName = marketModulationTrajectory.getFileName();
        String fileName = P2gFilePrefixes.MODULATION_PREFIX + "_" + folderName + "_" + horizonYear + ".csv";
        return Paths.get(properties.getP2gMarketModulationDirectory(), folderName, fileName).toString();
    }

    private String extractHorizonYear(String horizon) {
        return horizon != null && horizon.contains("-") ? horizon.split("-")[1] : horizon;
    }
}
