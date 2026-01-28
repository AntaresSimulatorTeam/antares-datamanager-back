package com.rte_france.antares.datamanager_back.repository.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum WarningCode {
    DATA_NOT_FOUND("data.not.found"),
    LINKS_ALL_VALUES_ZERO("links.all_values_zero"),
    LINKS_UNILATERAL_VALUES_ZERO("links.unilateral_values_zero"),
    LINKS_AREA_NOT_PRESENT("links.area_not_present"),
    LOAD_MISSING_TRAJECTORY_FOR_AREAS("load.missing_trajectories_for_areas"),
    DUPLICATION_MISSING_TRAJECTORIES("duplication.missing_trajectories"),
    THERMAL_INSTALLED_POWER_MISSING_AREAS("thermal.installed_power_missing_areas"),
    THERMAL_SPECIFIC_PARAM_MISSING_AREAS("thermal.specific_param_missing_areas"),
    THERMAL_SPECIFIC_PARAM_ANY_CM_MR_REQUIRED("thermal.specific_param_any_cm_mr_required"),
    STS_MISSING_AREAS("sts_missing_areas");

    private final String value;

    public String value() {
        return value;
    }
}