package com.rte_france.antares.datamanager_back.service.nuclear;

import java.util.Locale;

public final class NuclearClusterNames {

    private static final String NUCLEAR_MARKER = "nuclear";
    private static final String PEAK_MARKER = "peak";

    private NuclearClusterNames() {
    }

    public static String normalize(String clusterName) {
        return clusterName.toLowerCase(Locale.ROOT);
    }

    public static boolean isNuclear(String clusterName) {
        return normalize(clusterName).contains(NUCLEAR_MARKER);
    }

    public static boolean isPeak(String clusterName) {
        return normalize(clusterName).contains(PEAK_MARKER);
    }
}
