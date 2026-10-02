package com.rte_france.antares.datamanager_back.util;

import java.util.Locale;

public final class HydroWaterValuesFileUtil {

    private HydroWaterValuesFileUtil() {
    }

    public static boolean matchesNode(String fileName, String node) {
        String normalizedFile = fileName.toLowerCase(Locale.ROOT);
        String normalizedNode = node.trim().toLowerCase(Locale.ROOT);
        return normalizedFile.equals("watervalues_" + normalizedNode + ".xlsx")
                || normalizedFile.equals(normalizedNode + "_water_values.xlsx");
    }
}
