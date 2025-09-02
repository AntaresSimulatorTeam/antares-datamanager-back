package com.rte_france.antares.datamanager_back.exception;

public enum AntaresErrorCode {
    SERVER_ERROR("server.error"),
    DATA_NOT_FOUND("data.not.found"),
    LINKS_ALL_VALUES_ZERO("links.all_values_zero"),
    LINKS_UNILATERAL_VALUES_ZERO("links.unilateral_values_zero"),
    LINKS_AREA_NOT_PRESENT("links.area_not_present");

    private final String value;

    AntaresErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

}
