package com.rte_france.antares.datamanager_back.service.nuclear;

import java.util.Locale;

public final class NuclearClusterNames {

    private static final String NUCLEAR_MARKER = "nuclear";
    private static final String PEAK_MARKER = "peak";
    private static final String EPR_MARKER = "epr";
    private static final String SMR_MARKER = "smr";

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

    public static boolean isEpr(String clusterName) {
        return normalize(clusterName).contains(EPR_MARKER);
    }

    public static boolean isSmr(String clusterName) {
        return normalize(clusterName).contains(SMR_MARKER);
    }
}
