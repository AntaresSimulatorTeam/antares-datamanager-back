package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    // Only validated
    TRANSMISSION_CAPACITIES(Set.of(
            "local-values",
            "null-for-all-links",
            "infinite-for-all-links",
            "null-for-physical-links",
            "infinite-for-physical-links"
    )),

    EXPORT_MPS(Set.of("none", "optim-1", "optim-2", "both-optims")),

    UNFEASIBLE_PROBLEM_BEHAVIOR(Set.of("warning-dry", "warning-verbose", "error-dry", "error-verbose")),

    HYDRO_HEURISTIC_POLICY(Set.of("accommodate rule curves", "maximize generation")),

    POWER_FLUCTUATIONS(Set.of("free modulations", "minimize excursions", "minimize ramping")),

    SHEDDING_POLICY(Set.of("shave peaks", "minimize duration", "accurate shave peaks"));

    private final Set<String> allowedValues;
    private final Map<String, String> translations;

    SettingsValueMapper(Map<String, String> translations) {
        this.allowedValues = translations.keySet();
        this.translations = translations;
    }

    SettingsValueMapper(Set<String> allowedValues) {
        this.allowedValues = allowedValues;
        this.translations = Map.of();
    }

    public String map(String parameterName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!allowedValues.contains(value)) {
            throw BusinessException.builder()
                    .message("Invalid value {0} for settings parameter {1}. Allowed values: {2}")
                    .errorMessageArguments(List.of(rawValue, parameterName, String.join(", ", new TreeSet<>(allowedValues))))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return translations.getOrDefault(value, value);
    }
}
