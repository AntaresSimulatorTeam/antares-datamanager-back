package com.rte_france.antares.datamanager_back.repository.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum MiscGroupEnum {

    BIOMASS("biomass"),
    BIOGAS("biogas"),
    GEOTHERMAL("geothermal"),
    OTHER("other"),
    WASTE("waste"),
    WAVE("wave"),
    HYDROKINETIC("hydrokinetic");

    private final String value;

    MiscGroupEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    private static final Set<String> GENERATOR_KNOWN_GROUPS = Set.of(
            BIOMASS.value,
            BIOGAS.value,
            GEOTHERMAL.value,
            OTHER.value,
            WASTE.value
    );

    public static final Set<String> VALID_INPUT_GROUPS = Arrays.stream(values())
            .map(MiscGroupEnum::value)
            .collect(Collectors.toUnmodifiableSet());

    public static final List<String> LOAD_FACTOR_GROUPS = Arrays.stream(values())
            .map(MiscGroupEnum::value)
            .toList();

    public static boolean isValidInputGroup(String group) {
        return group != null && VALID_INPUT_GROUPS.contains(group.trim().toLowerCase(Locale.ROOT));
    }

    public static String normalizeForGenerator(String group) {
        if (group == null) {
            return OTHER.value;
        }

        String normalizedGroup = group.trim().toLowerCase(Locale.ROOT);
        if (GENERATOR_KNOWN_GROUPS.contains(normalizedGroup)) {
            return normalizedGroup;
        }
        return OTHER.value;
    }

    public static boolean matchesSeriesForGroup(String fileName, String group) {
        if (fileName == null || group == null) {
            return false;
        }

        String upperName = fileName.toUpperCase(Locale.ROOT);
        String normalizedGroup = group.trim().toLowerCase(Locale.ROOT);

        return upperName.contains("_" + normalizedGroup.toUpperCase(Locale.ROOT) + ".");
    }
}



