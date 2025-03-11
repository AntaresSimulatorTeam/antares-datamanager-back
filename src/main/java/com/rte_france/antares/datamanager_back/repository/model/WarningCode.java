package com.rte_france.antares.datamanager_back.repository.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum WarningCode {
    DATA_NOT_FOUND("data.not.found"),
    LINKS_ALL_VALUES_ZERO("links.all_values_zero"),
    LINKS_DIRECT_VALUES_ZERO("links.direct_values_zero"),
    LINKS_INDIRECT_VALUES_ZERO("links.indirect_values_zero");

    private final String value;

    public String value() {
        return value;
    }
}