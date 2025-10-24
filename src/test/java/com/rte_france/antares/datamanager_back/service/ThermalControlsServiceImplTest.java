package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalControlsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
 class ThermalControlsServiceImplTest {
    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private ThermalControlsServiceImpl thermalControlsService;

    @Test
    void checkMissingClusters_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA", "ClusterB");
        Set<String> installedPowerClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of(
                        TrajectoryEntity.builder()
                                .thermalClusterCapacities(installedPowerClusters.stream()
                                        .map(cluster -> ThermalClusterCapacityEntity.builder().thermalClusterRef(ThermalClusterRef.builder().name(cluster).build()).build())
                                        .toList())
                                .build()
                ));

        assertDoesNotThrow(() -> thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null));
    }

    @Test
    void checkMissingClusters_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA/FR");
        Set<String> installedPowerClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of(
                        TrajectoryEntity.builder()
                                .thermalClusterCapacities(installedPowerClusters.stream()
                                        .map(cluster -> ThermalClusterCapacityEntity.builder().area("FR").thermalClusterRef(ThermalClusterRef.builder().name(cluster).build()).build())
                                        .toList())
                                .build()
                ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER,"FR"));

        assertTrue(exception.getMessage().contains("Clusters : ClusterB/FR are not in Specific trajectory"));
    }

    @Test
    void checkMissingClusters_shouldNotThrowExceptionWhenNoInstalledPowerClustersExist() {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> paramClusters = Set.of("ClusterA", "ClusterB");

        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.THERMAL_CAPACITY.name()))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> thermalControlsService.checkMissingClusters(studyId, horizon, paramClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER,null));
    }


    @Test
    void verifyClustersInCommonParamTrajectory_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity commonParamTrajectory = TrajectoryEntity.builder()
                .thermalCommonParameters(List.of(
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ))
                .fileName("CommonParamFile")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(commonParamTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );

        assertTrue(exception.getMessage().contains("Clusters ClusterA are not in Common trajectory CommonParamFile"));
    }

    @Test
    void verifyClustersInCommonParamTrajectory_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity commonParamTrajectory = TrajectoryEntity.builder()
                .thermalCommonParameters(List.of(
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                ))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(commonParamTrajectory));

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );
    }

    @Test
    void verifyClustersInCommonParamTrajectory_shouldNotThrowExceptionWhenNoCommonParamTrajectoryExists() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );
        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of());

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInCommonParamTrajectory(studyId, horizon, capacities)
        );
    }


    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldThrowExceptionWhenClustersAreMissing() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder().area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity specificParamTrajectory = TrajectoryEntity.builder()
                .thermalSpecificParameters(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .area("FR")
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ))
                .fileName("SpecificParamFile")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(specificParamTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );

        assertTrue(exception.getMessage().contains("Clusters ClusterA/FR are not in Specific trajectory SpecificParamFile"));
    }

    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldNotThrowExceptionWhenAllClustersArePresent() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        TrajectoryEntity specificParamTrajectory = TrajectoryEntity.builder()
                .thermalSpecificParameters(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build()
                ))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of(specificParamTrajectory));

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );
    }

    @org.junit.jupiter.api.Test
    void verifyClustersInSpecificParamTrajectory_shouldNotThrowExceptionWhenNoSpecificParamTrajectoryExists() {
        Integer studyId = 1;
        String horizon = "2025-2026";
        List<ThermalClusterCapacityEntity> capacities = List.of(
                ThermalClusterCapacityEntity.builder()
                        .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                        .build()
        );

        when(trajectoryRepository.findByTypeAndStudyId(any(), any()))
                .thenReturn(List.of());

        assertDoesNotThrow(() ->
                thermalControlsService.verifyClustersInSpecificParamTrajectory(studyId, horizon, capacities)
        );
    }
}
