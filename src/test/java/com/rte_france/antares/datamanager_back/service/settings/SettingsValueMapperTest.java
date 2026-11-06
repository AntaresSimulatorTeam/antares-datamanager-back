package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class SettingsValueMapperTest {

    @ParameterizedTest
    @CsvSource({
            "MODE, adequacy, adequacy",
            "MODE, economy, economy",
            "MODE, economy (linear relaxation), expansion",
            "MODE, Economy (Linear Relaxation), expansion",
            "FIRST_MONTH_IN_YEAR, JAN - DEC, january",
            "FIRST_MONTH_IN_YEAR, FEB - JAN, february",
            "FIRST_MONTH_IN_YEAR, MAR - FEB, march",
            "FIRST_MONTH_IN_YEAR, APR - MAR, april",
            "FIRST_MONTH_IN_YEAR, MAY - APR, may",
            "FIRST_MONTH_IN_YEAR, JUN - MAY, june",
            "FIRST_MONTH_IN_YEAR, JUL - JUN, july",
            "FIRST_MONTH_IN_YEAR, AUG - JUL, august",
            "FIRST_MONTH_IN_YEAR, SEP - AUG, september",
            "FIRST_MONTH_IN_YEAR, OCT - SEP, october",
            "FIRST_MONTH_IN_YEAR, NOV - OCT, november",
            "FIRST_MONTH_IN_YEAR, DEC - NOV, december",
            "FIRST_WEEK_DAY, MON - SUN, monday",
            "FIRST_WEEK_DAY, TUE - MON, tuesday",
            "FIRST_WEEK_DAY, WED - TUE, wednesday",
            "FIRST_WEEK_DAY, THU - WED, thursday",
            "FIRST_WEEK_DAY, FRI - THU, friday",
            "FIRST_WEEK_DAY, SAT - FRI, saturday",
            "FIRST_WEEK_DAY, SUN - SAT, sunday",
            "TRANSMISSION_CAPACITIES, local-values, local_values",
            "TRANSMISSION_CAPACITIES, null-for-all-links, null_for_all_links",
            "TRANSMISSION_CAPACITIES, infinite-for-all-links, infinite_for_all_links",
            "TRANSMISSION_CAPACITIES, null-for-physical-links, null_for_physical_links",
            "TRANSMISSION_CAPACITIES, infinite-for-physical-links, infinite_for_physical_links",
            "EXPORT_MPS, none, false",
            "EXPORT_MPS, optim-1, optim1",
            "EXPORT_MPS, optim-2, optim2",
            "EXPORT_MPS, both-optims, both_optims",
            "UNFEASIBLE_PROBLEM_BEHAVIOR, warning-dry, warning_dry",
            "UNFEASIBLE_PROBLEM_BEHAVIOR, warning-verbose, warning_verbose",
            "UNFEASIBLE_PROBLEM_BEHAVIOR, error-dry, error_dry",
            "UNFEASIBLE_PROBLEM_BEHAVIOR, error-verbose, error_verbose",
            "HYDRO_HEURISTIC_POLICY, accommodate rule curves, accommodate_rule_curves",
            "HYDRO_HEURISTIC_POLICY, maximize generation, maximize_generation",
            "POWER_FLUCTUATIONS, free modulations, FREE_MODULATIONS",
            "POWER_FLUCTUATIONS, minimize excursions, MINIMIZE_EXCURSIONS",
            "POWER_FLUCTUATIONS, minimize ramping, MINIMIZE_RAMPING",
            "SHEDDING_POLICY, shave peaks, SHAVE_PEAKS",
            "SHEDDING_POLICY, minimize duration, MINIMIZE_DURATION",
            "SHEDDING_POLICY, accurate shave peaks, ACCURATE_SHAVE_PEAKS"
    })
    void shouldMapUserValueToAntaresCraftValue(SettingsValueMapper mapper, String rawValue, String expected) {
        assertEquals(expected, mapper.map("param", rawValue));
    }

    @Test
    void shouldTrimValueBeforeMapping() {
        assertEquals("monday", SettingsValueMapper.FIRST_WEEK_DAY.map("Week", "  MON - SUN  "));
    }

    @Test
    void shouldReturnEmptyStringForEmptyValue() {
        assertEquals("", SettingsValueMapper.MODE.map("Mode", ""));
        assertEquals("", SettingsValueMapper.MODE.map("Mode", null));
    }

    @Test
    void shouldRejectUnknownValueAndListAllowedValues() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> SettingsValueMapper.MODE.map("Mode", "draft"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Mode", exception.getErrorMessageArguments().get(1));
        assertEquals("adequacy, economy, economy (linear relaxation)", exception.getErrorMessageArguments().get(2));
    }
}
