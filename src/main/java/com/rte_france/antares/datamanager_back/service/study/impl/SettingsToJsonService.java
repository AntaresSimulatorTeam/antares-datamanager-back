package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsToJsonService {

    private final SettingsGeneralParametersRepository generalParametersRepository;
    private final SettingsOptimizationParametersRepository optimizationParametersRepository;
    private final SettingsAdvancedParametersRepository advancedParametersRepository;
    private final SettingsSeedsParametersRepository seedsParametersRepository;

    /**
     * Builds settings JSON from database entities by trajectory ID
     * @param trajectoryId the trajectory ID to fetch settings for
     * @return Map containing settings with structure: {optimization_parameters, advanced_parameters, general_parameters, seeds_parameters}
     */
    public Map<String, Object> buildSettingsMap(Integer trajectoryId) {
        Map<String, Object> settings = new LinkedHashMap<>();

        Optional<SettingsGeneralParametersEntity> generalParams = generalParametersRepository.findByTrajectoryId(trajectoryId);
        Optional<SettingsOptimizationParametersEntity> optimizationParams = optimizationParametersRepository.findByTrajectoryId(trajectoryId);
        Optional<SettingsAdvancedParametersEntity> advancedParams = advancedParametersRepository.findByTrajectoryId(trajectoryId);
        Optional<SettingsSeedsParametersEntity> seedsParams = seedsParametersRepository.findByTrajectoryId(trajectoryId);

        if (optimizationParams.isPresent()) {
            settings.put("optimization_parameters", buildOptimizationParametersMap(optimizationParams.get()));
        } else {
            settings.put("optimization_parameters", new LinkedHashMap<>());
        }

        if (advancedParams.isPresent()) {
            settings.put("advanced_parameters", buildAdvancedParametersMap(advancedParams.get()));
        } else {
            settings.put("advanced_parameters", new LinkedHashMap<>());
        }

        if (generalParams.isPresent()) {
            settings.put("general_parameters", buildGeneralParametersMap(generalParams.get()));
        } else {
            settings.put("general_parameters", new LinkedHashMap<>());
        }

        if (seedsParams.isPresent()) {
            settings.put("seeds_parameters", buildSeedsParametersMap(seedsParams.get()));
        } else {
            settings.put("seeds_parameters", new LinkedHashMap<>());
        }

        return settings;
    }

    private Map<String, Object> buildOptimizationParametersMap(SettingsOptimizationParametersEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        addIfNotNull(map, "simplex_range", entity.getSimplexRange());
        addIfNotNull(map, "transmission_capacities", entity.getTransmissionCapacities());
        addIfNotNull(map, "include_constraints", entity.getIncludeConstraints());
        addIfNotNull(map, "include_hurdlecosts", entity.getIncludeHurdlecosts());
        addIfNotNull(map, "include_tc_minstablepower", entity.getIncludeTcMinstablepower());
        addIfNotNull(map, "include_tc_min_ud_time", entity.getIncludeTcMinUdTime());
        addIfNotNull(map, "include_dayahead", entity.getIncludeDayahead());
        addIfNotNull(map, "include_strategicreserve", entity.getIncludeStrategicreserve());
        addIfNotNull(map, "include_spinningreserve", entity.getIncludeSpinningreserve());
        addIfNotNull(map, "include_primaryreserve", entity.getIncludePrimaryreserve());
        addIfNotNull(map, "include_exportmps", entity.getIncludeExportmps());
        addIfNotNull(map, "include_unfeasible_problem_behavior", entity.getIncludeUnfeasibleProblemBehavior());
        return map;
    }

    private Map<String, Object> buildAdvancedParametersMap(SettingsAdvancedParametersEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        addIfNotNull(map, "hydro_heuristic_policy", entity.getHydroHeuristicPolicy());
        addIfNotNull(map, "hydro_pricing_mode", entity.getHydroPricingMode());
        addIfNotNull(map, "power_fluctuations", entity.getPowerFluctuations());
        addIfNotNull(map, "shedding_policy", entity.getSheddingPolicy());
        addIfNotNull(map, "unit_commitment_mode", entity.getUnitCommitmentMode());
        addIfNotNull(map, "number_of_cores_mode", entity.getNumberOfCoresMode());
        addIfNotNull(map, "renewable_generation_modelling", entity.getRenewableGenerationModelling());
        addIfNotNull(map, "accurate_shave_peaks_include_short_term_storage", entity.getAccurateShavePeaksIncludeShortTermtorage());
        return map;
    }

    private Map<String, Object> buildGeneralParametersMap(SettingsGeneralParametersEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        addIfNotNull(map, "mode", entity.getMode());
        addIfNotNull(map, "horizon", entity.getHorizon());
        addIfNotNull(map, "nb_years", entity.getNbYears());
        addIfNotNull(map, "simulation_start", entity.getSimulationStart());
        addIfNotNull(map, "simulation_end", entity.getSimulationEnd());
        addIfNotNull(map, "january_first", entity.getJanuaryFirst());
        addIfNotNull(map, "first_month_in_year", entity.getFirstMonthInYear());
        addIfNotNull(map, "first_week_day", entity.getFirstWeekDay());
        addIfNotNull(map, "leap_year", entity.getLeapYear());
        addIfNotNull(map, "year_by_year", entity.getYearByYear());
        addIfNotNull(map, "simulation_synthesis", entity.getSimulationSynthesis());
        addIfNotNull(map, "building_mode", entity.getBuildingMode());
        addIfNotNull(map, "user_playlist", entity.getUserPlaylist());
        addIfNotNull(map, "thematic_trimming", entity.getThematicTrimming());
        addIfNotNull(map, "geographic_trimming", entity.getGeographicTrimming());
        addIfNotNull(map, "nb_timeseries_thermal", entity.getNbTimeseriesThermal());
        addIfNotNull(map, "store_new_set", entity.getStoreNewSet());
        return map;
    }

    private Map<String, Object> buildSeedsParametersMap(SettingsSeedsParametersEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        addIfNotNull(map, "seed_tsgen_thermal", entity.getSeedTsgenThermal());
        addIfNotNull(map, "seed_tsnumbers", entity.getSeedTsnumbers());
        addIfNotNull(map, "seed_unsupplied_energy_costs", entity.getSeedUnsuppliedEnergyCosts());
        addIfNotNull(map, "seed_spilled_energy_costs", entity.getSeedSpilledEnergyCosts());
        addIfNotNull(map, "seed_thermal_costs", entity.getSeedThermalCosts());
        addIfNotNull(map, "seed_hydro_costs", entity.getSeedHydroCosts());
        addIfNotNull(map, "seed_initial_reservoir_levels", entity.getSeedInitialReservoirLevels());
        return map;
    }

    private void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
