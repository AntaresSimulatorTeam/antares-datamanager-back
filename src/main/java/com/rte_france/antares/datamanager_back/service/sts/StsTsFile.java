package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum StsTsFile {

    INFLOWS("inflows.xlsx"),
    LOWER_CURVE("lower_curve.xlsx"),
    MAX_POWER_INJECTION("Pmax_injection.xlsx"),
    MAX_POWER_WITHDRAWAL("Pmax_soutirage.xlsx"),
    UPPER_CURVE("upper_curve.xlsx"),
    ADDITIONAL_CONSTRAINTS("Additional-constraints.xlsx");

    /** All STS time-series files except {@link #ADDITIONAL_CONSTRAINTS}. */
    protected static final Set<StsTsFile> REQUIRED =
            Collections.unmodifiableSet(
                    EnumSet.complementOf(
                            EnumSet.of(ADDITIONAL_CONSTRAINTS)));

    /** STS_ME time-series files. */
    protected static final Set<StsTsFile> REQUIRED_ME =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            LOWER_CURVE,
                            MAX_POWER_INJECTION,
                            MAX_POWER_WITHDRAWAL,
                            UPPER_CURVE));

    private final String fileName;

    StsTsFile(String fileName) {
        this.fileName = fileName;
    }

    public Path resolve(Path baseDir) {
        return baseDir.resolve(fileName);
    }

    public String fileName() {
        return fileName;
    }

    public static Set<StsTsFile> requiredFiles() {
        return REQUIRED;
    }

    public static Set<StsTsFile> requiredFiles(TrajectoryType trajectoryType) {
        return trajectoryType == TrajectoryType.STS_ME
                ? REQUIRED_ME
                : REQUIRED;
    }

    public static String[] allFileNames() {
        return Arrays.stream(values())
                .filter(e -> e != ADDITIONAL_CONSTRAINTS)
                .map(StsTsFile::fileName)
                .toArray(String[]::new);
    }
}
