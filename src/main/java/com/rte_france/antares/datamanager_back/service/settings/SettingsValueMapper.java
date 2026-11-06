package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import static java.util.Map.entry;

/**
 * Maps user inputs of GeneralData file to the values expected by AntaresCraft.
 */
public enum SettingsValueMapper {

    MODE(Map.of(
            "adequacy", "adequacy",
            "economy", "economy",
            "economy (linear relaxation)", "expansion"
    )),

    FIRST_MONTH_IN_YEAR(Map.ofEntries(
            entry("jan - dec", "january"),
            entry("feb - jan", "february"),
            entry("mar - feb", "march"),
            entry("apr - mar", "april"),
            entry("may - apr", "may"),
            entry("jun - may", "june"),
            entry("jul - jun", "july"),
            entry("aug - jul", "august"),
            entry("sep - aug", "september"),
            entry("oct - sep", "october"),
            entry("nov - oct", "november"),
            entry("dec - nov", "december")
    )),

    FIRST_WEEK_DAY(Map.of(
            "mon - sun", "monday",
            "tue - mon", "tuesday",
            "wed - tue", "wednesday",
            "thu - wed", "thursday",
            "fri - thu", "friday",
            "sat - fri", "saturday",
            "sun - sat", "sunday"
    )),

    TRANSMISSION_CAPACITIES(Map.of(
            "local-values", "local_values",
            "null-for-all-links", "null_for_all_links",
            "infinite-for-all-links", "infinite_for_all_links",
            "null-for-physical-links", "null_for_physical_links",
            "infinite-for-physical-links", "infinite_for_physical_links"
    )),

    EXPORT_MPS(Map.of(
            "none", "false",
            "optim-1", "optim1",
            "optim-2", "optim2",
            "both-optims", "both_optims"
    )),

    UNFEASIBLE_PROBLEM_BEHAVIOR(Map.of(
            "warning-dry", "warning_dry",
            "warning-verbose", "warning_verbose",
            "error-dry", "error_dry",
            "error-verbose", "error_verbose"
    )),

    HYDRO_HEURISTIC_POLICY(Map.of(
            "accommodate rule curves", "accommodate_rule_curves",
            "maximize generation", "maximize_generation"
    )),

    POWER_FLUCTUATIONS(Map.of(
            "free modulations", "FREE_MODULATIONS",
            "minimize excursions", "MINIMIZE_EXCURSIONS",
            "minimize ramping", "MINIMIZE_RAMPING"
    )),

    SHEDDING_POLICY(Map.of(
            "shave peaks", "SHAVE_PEAKS",
            "minimize duration", "MINIMIZE_DURATION",
            "accurate shave peaks", "ACCURATE_SHAVE_PEAKS"
    ));

    private final Map<String, String> mapping;

    SettingsValueMapper(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public String map(String parameterName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        String mapped = mapping.get(rawValue.trim().toLowerCase(Locale.ROOT));
        if (mapped == null) {
            throw BusinessException.builder()
                    .message("Invalid value {0} for settings parameter {1}. Allowed values: {2}")
                    .errorMessageArguments(List.of(rawValue, parameterName, String.join(", ", new TreeSet<>(mapping.keySet()))))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return mapped;
    }
}
