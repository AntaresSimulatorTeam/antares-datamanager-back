package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalControlService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalFileProcessorService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicCostAndRateService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for TrajectoryServiceImpl.processThermalEconomicCostTrajectory
 */
class TrajectoryServiceImplEconomicCostTest {

    @Mock private AreaRepository areaRepository;
    @Mock private TrajectoryRepository trajectoryRepository;
    @Mock private AreaConfigRepository areaConfigRepository;
    @Mock private LinkRepository linkRepository;
    @Mock private ThermalFileProcessorService thermalFileProcessorService;
    @Mock private ThermalControlService thermalControlService;
    @Mock private StudyRepository studyRepository;
    @Mock private StudyTrajectoryRepository studyTrajectoryRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private UserService userService;
    @Mock private LoadFileProcessorServiceImpl loadFileProcessorService;
    @Mock private ThermalSpecificParametersRepository thermalSpecificParametersRepository;
    @Mock private AntaressDataManagerProperties props;
    @Mock private com.rte_france.antares.datamanager_back.service.area_link.AreaFileProcessorService areaFileProcessorService;
    @Mock private com.rte_france.antares.datamanager_back.service.area_link.LinkFileProcessorService linkFileProcessorService;
    @Mock private ThermalEconomicCostAndRateService thermalEconomicCostAndRateService;

    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(props.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(props.getTrajectoryFilePath()).thenReturn("trajectories");
        when(props.getThermalParameterDirectory()).thenReturn("thermal");
    }

    @Test
    void processThermalEconomicCostTrajectory_shouldReturnSavedTrajectory_whenCostsPresent() throws IOException {
        // Arrange
        String trajectoryToUse = "economic_costs_test";
        String horizon = "2024-2025";
        Integer studyId = 1;
        Path fakePath = Paths.get("/tmp/mnt/nas/trajectories/thermal_cost/" + trajectoryToUse + ".xlsx");

        TrajectoryServiceImpl spy = Mockito.spy(trajectoryService);
        doReturn(fakePath).when(spy).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER), eq(trajectoryToUse), eq(""));

        List<ThermalCostTypeEntity> costs = List.of(
                ThermalCostTypeEntity.builder()
                        .fuel("Gas").country("FR")
                        .thermalCostEntities(List.of(ThermalCostEntity.builder().year(2025).cost(12.3).build()))
                        .build()
        );
        List<ThermalCostsRateEntity> rates = List.of(
                ThermalCostsRateEntity.builder().rateType("euro/dollar").year(2025).build()
        );

        when(thermalEconomicCostAndRateService.buildThermalEconomicCostValueList(trajectoryToUse, fakePath, "2025", studyId))
                .thenReturn(costs);
        when(thermalEconomicCostAndRateService.buildThermalEconomicRateValueList(trajectoryToUse, fakePath,"2025", studyId))
                .thenReturn(rates);

        TrajectoryEntity saved = TrajectoryEntity.builder().id(10).fileName("economic_costs_test").build();
        when(thermalFileProcessorService.processThermalEconomicCostsAndRatesFile(fakePath, horizon, costs, rates, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER))
                .thenReturn(saved);

        // Act
        TrajectoryEntity result = spy.processThermalEconomicCostTrajectory(trajectoryToUse, horizon, studyId);

        // Assert
        assertSame(saved, result);
        verify(thermalEconomicCostAndRateService, times(1)).buildThermalEconomicCostValueList(trajectoryToUse, fakePath, "2025", studyId);
        verify(thermalEconomicCostAndRateService, times(1)).buildThermalEconomicRateValueList(trajectoryToUse, fakePath,"2025", studyId);
        verify(thermalFileProcessorService, times(1)).processThermalEconomicCostsAndRatesFile(fakePath, horizon, costs, rates, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER);
    }

    @Test
    void processThermalEconomicCostTrajectory_shouldThrowBusinessException_whenCostsEmpty() throws IOException {
        // Arrange
        String trajectoryToUse = "economic_costs_empty";
        String horizon = "2030-20301";
        Integer studyId = 2;
        Path fakePath = Paths.get("/tmp/mnt/nas/trajectories/thermal_cost/" + trajectoryToUse + ".xlsx");

        TrajectoryServiceImpl spy = Mockito.spy(trajectoryService);
        doReturn(fakePath).when(spy).getTrajectoryFilePath(eq(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER), eq(trajectoryToUse), eq(""));

        when(thermalEconomicCostAndRateService.buildThermalEconomicCostValueList(trajectoryToUse, fakePath, horizon, studyId))
                .thenReturn(new ArrayList<>());
        // Rate list shouldn't matter, but stub to avoid NPE if called (it shouldn't be used further)
        when(thermalEconomicCostAndRateService.buildThermalEconomicRateValueList(trajectoryToUse, fakePath,horizon, studyId))
                .thenReturn(new ArrayList<>());

        // Act + Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> spy.processThermalEconomicCostTrajectory(trajectoryToUse, horizon, studyId));
        assertTrue(ex.getMessage().contains("No valid thermal common parameter"));
        // Ensure we did not delegate to file processor to save
        verify(thermalFileProcessorService, never()).processThermalEconomicCostsAndRatesFile(any(), any(), anyList(), anyList(), any());
    }
}
