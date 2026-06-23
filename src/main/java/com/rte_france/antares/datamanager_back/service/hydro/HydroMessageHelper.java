package com.rte_france.antares.datamanager_back.service.hydro;

public class HydroMessageHelper {

    private HydroMessageHelper() {}

    public static String getSeriesLabel(boolean isPsp) {
        return isPsp ? "PSP_Virtual Series" : "hydro series";
    }

    public static String getTechnicalParametersLabel(boolean isPsp) {
        return isPsp ? "PSP_Virtual TechnicalParameters" : "Hydro TechnicalParameters";
    }

    public static String getFileLabel(String baseLabel, boolean isPsp) {
        return isPsp ? "PSP_Virtual " + baseLabel : baseLabel;
    }
}
