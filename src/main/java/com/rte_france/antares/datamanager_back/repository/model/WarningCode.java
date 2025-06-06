package com.rte_france.antares.datamanager_back.repository.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum WarningCode {
    DATA_NOT_FOUND("data.not.found"),
    LINKS_ALL_VALUES_ZERO("links.all_values_zero"),
    LINKS_UNILATERAL_VALUES_ZERO("links.unilateral_values_zero"),
    LINKS_AREA_NOT_PRESENT("links.area_not_present"),
    AREAS_NOT_ORDERED_ALPHABETICALLY("areas.not_alphabetically_ordered"),
    LOAD_MISSING_TRAJECTORY_FOR_AREAS("load.missing_trajectories_for_areas");

    private final String value;

    public String value() {
        return value;
    }
}