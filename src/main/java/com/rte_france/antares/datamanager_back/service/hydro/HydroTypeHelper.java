package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;

public class HydroTypeHelper {

    private HydroTypeHelper() {}

    public static boolean isPsp(TrajectoryType type) {
        return type == TrajectoryType.HYDRO_PSP_SERIES || type == TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS;
    }

    public static String getSeriesLabel(boolean isPsp) {
        return getPrefixedLabel("Series", isPsp);
    }

    public static String getTechnicalParametersLabel(boolean isPsp) {
        return getPrefixedLabel("TechnicalParameters", isPsp);
    }

    private static String getPrefixedLabel(String suffix, boolean isPsp) {
        return (isPsp ? "PSP_Virtual " : "Hydro ") + suffix;
    }

    public static String getFileLabel(String baseLabel, boolean isPsp) {
        return isPsp ? "PSP_Virtual " + baseLabel : baseLabel;
    }
}
