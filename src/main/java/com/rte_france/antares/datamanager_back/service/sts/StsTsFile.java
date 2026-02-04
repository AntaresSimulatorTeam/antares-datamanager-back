package com.rte_france.antares.datamanager_back.service.sts;

import java.nio.file.Path;

public enum StsTsFile {

        INFLOWS("inflows.xlsx"),
        LOWER_CURVE("lower_curve.xlsx"),
        MAX_POWER_INJECTION("Pmax_injection.xlsx"),
        MAX_POWER_WITHDRAWAL("Pmax_soutirage.xlsx"),
        UPPER_CURVE("upper_curve.xlsx");

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
    }


