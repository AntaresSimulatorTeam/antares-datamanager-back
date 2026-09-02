package com.rte_france.antares.datamanager_back.service.p2g.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.P2gGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.P2GCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.P2GCostRepository;
import com.rte_france.antares.datamanager_back.repository.P2GParametersRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GParametersEntity;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P2gGenerationAssemblerServiceImplTest {

    @Mock private P2GCapacityRepository p2gCapacityRepository;
    @Mock private P2GCostRepository p2gCostRepository;
    @Mock private P2GParametersRepository p2gParametersRepository;
    @Mock private ResGenerationAssemblerService resGenerationAssemblerService;
    @Mock private AntaresDataManagerProperties properties;

    private P2gGenerationAssemblerServiceImpl assembler;

    private static final Integer TRAJECTORY_ID = 1;
    private final StudyEntity study = StudyEntity.builder().horizon("2026-2027").build();
    private final TrajectoryEntity capacityCostTrajectory = trajectory(TRAJECTORY_ID, "P2G_traj");
    private final TrajectoryEntity marketModulationTrajectory = trajectory(2, "FE60_liv_same");

    @BeforeEach
    void setUp() {
        assembler = new P2gGenerationAssemblerServiceImpl(
                p2gCapacityRepository, p2gCostRepository, p2gParametersRepository, resGenerationAssemblerService, properties);

        when(properties.getP2gMarketModulationDirectory()).thenReturn("thermal/economic parameters/market_bid_marg_cost_modulation");

        when(p2gCapacityRepository.findByTrajectoryId(TRAJECTORY_ID)).thenReturn(List.of(
                capacity("AT", 2.0, 100.0, 90.0, 50.0, 30.0, 20.0),
                capacity("BE", 1.0, 40.0, 35.0, null, 10.0, null)
        ));
        when(p2gCostRepository.findByTrajectoryId(TRAJECTORY_ID)).thenReturn(List.of(
                cost("Base", "H2", 78.0),
                cost("Marginal", "Gaz", 78.0),
                cost("Methanation", "H2", 78.0),
                cost("Asservi", "H2", 78.0)
        ));
        when(p2gParametersRepository.findByTrajectoryId(TRAJECTORY_ID)).thenReturn(List.of(
                P2GParametersEntity.builder().fcElectrolyseur(0.5).facteurSurdimensionEnr(1.2).partPvMix(0.9).build()
        ));
        when(resGenerationAssemblerService.assembleResProperties(study)).thenReturn(Map.of(
                "AT", Map.of(
                        "solar", new ResClusterGenerationDto(new ResClusterPropertiesDto(10.0, "solar_pv"), List.of(), null),
                        "wind", new ResClusterGenerationDto(new ResClusterPropertiesDto(10.0, "wind_onshore"), List.of(), null)
                )
        ));
    }

    @Test
    void assembleP2g_happyPath_shouldBuildCorrectDto() {
        P2gGenerationDTO dto = assembler.assembleP2g(study, capacityCostTrajectory, marketModulationTrajectory);

        String expectedPath = Paths.get(
                "thermal/economic parameters/market_bid_marg_cost_modulation",
                "FE60_liv_same",
                "MB_MC_modulation_FE60_liv_same_2027.csv"
        ).toString();
        assertThat(dto.marketModulation()).isEqualTo(expectedPath);

        assertThat(dto.base().properties().nominalCapacity()).isEqualTo(140.0);
        assertThat(dto.base().properties().cost()).isEqualTo(78.0);
        assertThat(dto.base().modulation()).isEqualTo("H2");
        assertThat(dto.base().links()).hasSize(2);
        assertThat(dto.base().links().get("AT").capacity()).isEqualTo(90.0);
        assertThat(dto.base().links().get("AT").fatalBand()).isEqualTo(2.0);
        assertThat(dto.base().links().get("BE").capacity()).isEqualTo(35.0);
        assertThat(dto.base().links().get("BE").fatalBand()).isEqualTo(1.0);

        assertThat(dto.marg().properties().nominalCapacity()).isEqualTo(50.0);
        assertThat(dto.marg().modulation()).isEqualTo("Gaz");
        assertThat(dto.marg().links()).hasSize(1);
        assertThat(dto.marg().links().get("AT").capacity()).isEqualTo(50.0);
        assertThat(dto.marg().links().get("AT").fatalBand()).isNull();
        assertThat(dto.marg().links()).doesNotContainKey("BE");

        assertThat(dto.methanation().properties().nominalCapacity()).isEqualTo(40.0);
        assertThat(dto.methanation().links()).hasSize(2);

        assertThat(dto.asservi().properties().nominalCapacity()).isEqualTo(20.0);
        assertThat(dto.asservi().links()).hasSize(1);
        assertThat(dto.asservi().links()).doesNotContainKey("BE");
        assertThat(dto.asservi().parameters().fcElectrolyseur()).isEqualTo(0.5);
        assertThat(dto.asservi().parameters().facteurSurdimensionEnr()).isEqualTo(1.2);
        assertThat(dto.asservi().parameters().partPvMix()).isEqualTo(0.9);
        assertThat(dto.base().parameters()).isNull();
    }

    @Test
    void assembleP2g_shouldThrowBusinessException_whenMarketModulationTrajectoryMissing() {
        assertThatThrownBy(() -> assembler.assembleP2g(study, capacityCostTrajectory, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assembleP2g_shouldThrowBusinessException_whenAsserviAreaMissingResCoverage() {
        when(resGenerationAssemblerService.assembleResProperties(study)).thenReturn(Map.of(
                "AT", Map.of("solar", new ResClusterGenerationDto(new ResClusterPropertiesDto(10.0, "solar_pv"), List.of(), null))
        ));

        assertThatThrownBy(() -> assembler.assembleP2g(study, capacityCostTrajectory, marketModulationTrajectory))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getMessage()).contains("P2G asservi is defined for area {0}");
                    assertThat(be.getErrorMessageArguments()).containsExactly("AT", "wind_onshore");
                });
    }

    @Test
    void assembleP2g_shouldThrowTechnicalException_whenCostEntryMissingForType() {
        when(p2gCostRepository.findByTrajectoryId(TRAJECTORY_ID)).thenReturn(List.of(
                cost("Marginal", "Gaz", 78.0),
                cost("Methanation", "H2", 78.0),
                cost("Asservi", "H2", 78.0)
        ));

        assertThatThrownBy(() -> assembler.assembleP2g(study, capacityCostTrajectory, marketModulationTrajectory))
                .isInstanceOf(TechnicalException.class)
                .satisfies(ex -> {
                    TechnicalException te = (TechnicalException) ex;
                    assertThat(te.getMessage()).contains("Missing P2G cost entry for type {0} in trajectory {1}");
                    assertThat(te.getErrorMessageArguments()).containsExactly("Base", "P2G_traj");
                });
    }

    private static TrajectoryEntity trajectory(Integer id, String fileName) {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(id);
        trajectory.setFileName(fileName);
        return trajectory;
    }

    private static P2GCapacityEntity capacity(String area, Double fatalBand, Double baseEff, Double baseCapacity,
                                               Double margCapacity, Double methanationCapacity, Double asserviCapacity) {
        return P2GCapacityEntity.builder()
                .area(area)
                .baseFatalBand(fatalBand)
                .baseEff(baseEff)
                .baseCapacity(baseCapacity)
                .margCapacity(margCapacity)
                .methanationCapacity(methanationCapacity)
                .asserviCapacity(asserviCapacity)
                .build();
    }

    private static P2GCostEntity cost(String type, String modulation, Double costValue) {
        return P2GCostEntity.builder()
                .type(type)
                .modulation(modulation)
                .cost(costValue)
                .build();
    }
}
