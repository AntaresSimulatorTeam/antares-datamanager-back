package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsToJsonServiceTest {

    @Mock
    private SettingsGeneralParametersRepository generalParametersRepository;

    @Mock
    private SettingsOptimizationParametersRepository optimizationParametersRepository;

    @Mock
    private SettingsAdvancedParametersRepository advancedParametersRepository;

    @Mock
    private SettingsSeedsParametersRepository seedsParametersRepository;

    private SettingsToJsonService settingsToJsonService;

    @BeforeEach
    void setUp() {
        settingsToJsonService = new SettingsToJsonService(
                generalParametersRepository,
                optimizationParametersRepository,
                advancedParametersRepository,
                seedsParametersRepository
        );
    }

    @Test
    void buildSettingsMap_shouldReturnEmptySettings_whenNoParametersPresent() {
        Integer trajectoryId = 1;

        when(generalParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.empty());
        when(optimizationParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.empty());
        when(advancedParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.empty());
        when(seedsParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.empty());

        Map<String, Object> result = settingsToJsonService.buildSettingsMap(trajectoryId);

        assertThat(result)
                .containsKey("optimization_parameters")
                .containsKey("advanced_parameters")
                .containsKey("general_parameters")
                .containsKey("seeds_parameters");
        assertThat((Map<String, Object>) result.get("optimization_parameters")).isEmpty();
        assertThat((Map<String, Object>) result.get("advanced_parameters")).isEmpty();
        assertThat((Map<String, Object>) result.get("general_parameters")).isEmpty();
        assertThat((Map<String, Object>) result.get("seeds_parameters")).isEmpty();
    }

    @Test
    void buildSettingsMap_shouldReturnMappedValues_whenAllParametersPresent() {
        Integer trajectoryId = 1;

        SettingsGeneralParametersEntity generalParams = SettingsGeneralParametersEntity.builder()
                .mode("Economy")
                .horizon("Medium-term")
                .nbYears(5)
                .simulationStart(1)
                .simulationEnd(10)
                .januaryFirst("Monday")
                .firstMonthInYear("January")
                .firstWeekDay("Monday")
                .leapYear(false)
                .yearByYear(true)
                .simulationSynthesis(true)
                .buildingMode("default")
                .userPlaylist(false)
                .thematicTrimming(false)
                .geographicTrimming(false)
                .nbTimeseriesThermal(100)
                .storeNewSet(true)
                .build();

        SettingsOptimizationParametersEntity optimizationParams = SettingsOptimizationParametersEntity.builder()
                .simplexRange("Day")
                .transmissionCapacities("Local values")
                .includeConstraints(true)
                .includeHurdlecosts(true)
                .includeTcMinstablepower(false)
                .includeTcMinUdTime(false)
                .includeDayahead(true)
                .includeStrategicreserve(false)
                .includeSpinningreserve(false)
                .includePrimaryreserve(false)
                .includeExportmps("false")
                .includeUnfeasibleProblemBehavior("Error message only")
                .build();

        SettingsAdvancedParametersEntity advancedParams = SettingsAdvancedParametersEntity.builder()
                .hydroHeuristicPolicy("SPL")
                .hydroPricingMode("average cost")
                .powerFluctuations("free modulations")
                .sheddingPolicy("proportional")
                .unitCommitmentMode("fast")
                .numberOfCoresMode("auto")
                .renewableGenerationModelling("aggregated")
                .accurateShavePeaksIncludeShortTermtorage(false)
                .build();

        SettingsSeedsParametersEntity seedsParams = SettingsSeedsParametersEntity.builder()
                .seedTsgenThermal(123456)
                .seedTsnumbers(654321)
                .seedUnsuppliedEnergyCosts(111111)
                .seedSpilledEnergyCosts(222222)
                .seedThermalCosts(333333)
                .seedHydroCosts(444444)
                .seedInitialReservoirLevels(555555)
                .build();

        when(generalParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(generalParams));
        when(optimizationParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(optimizationParams));
        when(advancedParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(advancedParams));
        when(seedsParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(seedsParams));

        Map<String, Object> result = settingsToJsonService.buildSettingsMap(trajectoryId);

        Map<String, Object> generalMap = (Map<String, Object>) result.get("general_parameters");
        assertThat(generalMap)
                .containsEntry("mode", "Economy")
                .containsEntry("horizon", "Medium-term")
                .containsEntry("nb_years", 5)
                .containsEntry("simulation_start", 1)
                .containsEntry("simulation_end", 10)
                .containsEntry("leap_year", false)
                .containsEntry("year_by_year", true);

        Map<String, Object> optimizationMap = (Map<String, Object>) result.get("optimization_parameters");
        assertThat(optimizationMap)
                .containsEntry("simplex_range", "Day")
                .containsEntry("transmission_capacities", "Local values")
                .containsEntry("include_constraints", true)
                .containsEntry("include_hurdlecosts", true);

        Map<String, Object> advancedMap = (Map<String, Object>) result.get("advanced_parameters");
        assertThat(advancedMap)
                .containsEntry("hydro_heuristic_policy", "SPL")
                .containsEntry("hydro_pricing_mode", "average cost")
                .containsEntry("accurate_shave_peaks_include_short_term_storage", false);

        Map<String, Object> seedsMap = (Map<String, Object>) result.get("seeds_parameters");
        assertThat(seedsMap)
                .containsEntry("seed_tsgen_thermal", 123456)
                .containsEntry("seed_tsnumbers", 654321)
                .containsEntry("seed_thermal_costs", 333333);
    }

    @Test
    void buildSettingsMap_shouldOmitNullFields() {
        Integer trajectoryId = 1;

        SettingsGeneralParametersEntity generalParams = new SettingsGeneralParametersEntity();
        SettingsOptimizationParametersEntity optimizationParams = new SettingsOptimizationParametersEntity();
        SettingsAdvancedParametersEntity advancedParams = new SettingsAdvancedParametersEntity();
        SettingsSeedsParametersEntity seedsParams = new SettingsSeedsParametersEntity();

        when(generalParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(generalParams));
        when(optimizationParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(optimizationParams));
        when(advancedParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(advancedParams));
        when(seedsParametersRepository.findByTrajectoryId(trajectoryId)).thenReturn(Optional.of(seedsParams));

        Map<String, Object> result = settingsToJsonService.buildSettingsMap(trajectoryId);

        Map<String, Object> generalMap = (Map<String, Object>) result.get("general_parameters");
        assertThat(generalMap).isEmpty();

        Map<String, Object> optimizationMap = (Map<String, Object>) result.get("optimization_parameters");
        assertThat(optimizationMap).isEmpty();

        Map<String, Object> advancedMap = (Map<String, Object>) result.get("advanced_parameters");
        assertThat(advancedMap).isEmpty();

        Map<String, Object> seedsMap = (Map<String, Object>) result.get("seeds_parameters");
        assertThat(seedsMap).isEmpty();
    }
}
