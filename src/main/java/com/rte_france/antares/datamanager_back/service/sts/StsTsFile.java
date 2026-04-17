package com.rte_france.antares.datamanager_back.service.sts;

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
                Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(ADDITIONAL_CONSTRAINTS)));

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

         public static String[] allFileNames() {
            return Arrays.stream(values())
                .filter(e -> e != ADDITIONAL_CONSTRAINTS)
                .map(StsTsFile::fileName)
                .toArray(String[]::new);
            }
    }

