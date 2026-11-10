package com.rte_france.antares.datamanager_back.validation;

import com.rte_france.antares.datamanager_back.exception.AntaresErrorCode;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.TrajectoryPrefixes.*;

@Component
public class TrajectoryValidator {

    private static final Pattern TRAJECTORY_PREFIX = Pattern.compile(
            "^(?:" + Stream.of(AREAS_PREFIX, DSR_PREFIX, DSR_CAPACITY_PREFIX, LINKS_PREFIX,
                            MISC_CAPACITY_PREFIX, RES_CAPACITY_PREFIX, RES_TECHNOLOGY_DISTRIBUTION_PREFIX,
                            RES_ZONAL_DISTRIBUTION_PREFIX, SETTINGS_PREFIX, SCENARIO_BUILDER_PREFIX,
                            NUCLEAR_TALON_PREFIX, NUCLEAR_TALON_EPR_PREFIX, NUCLEAR_TALON_SMR_PREFIX,
                            THERMAL_CAPACITY_PREFIX, THERMAL_COMMON_PREFIX, THERMAL_SPECIFIC_PREFIX,
                            THERMAL_ECONOMIC_COST_PREFIX, THERMAL_ECONOMIC_PREFIX)
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"))
                    + "|" + Pattern.quote(STS_PREFIX) + "[^_]+_)",
            Pattern.CASE_INSENSITIVE);

    public void validate(String trajectoryName, int maxLength) {
        if (trajectoryName == null) {
            return;
        }

        Matcher prefixMatcher = TRAJECTORY_PREFIX.matcher(trajectoryName);
        String nameWithoutPrefix = prefixMatcher.find() ? trajectoryName.substring(prefixMatcher.end()) : trajectoryName;

        if (nameWithoutPrefix.length() > maxLength) {
            throw BusinessException.builder()
                    .antaresErrorCode(AntaresErrorCode.INVALID_TRAJECTORY_NAME)
                    .message("Trajectory name cannot exceed " + maxLength + " characters excluding its prefix")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (nameWithoutPrefix.trim().isEmpty()) {
            throw BusinessException.builder()
                    .antaresErrorCode(AntaresErrorCode.INVALID_TRAJECTORY_NAME)
                    .message("The name of the trajectory cannot contain only spaces")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }
}
