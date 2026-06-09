package com.rte_france.antares.datamanager_back.service.res.impl;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

/**
 * Utils and business constants for RES.
 */
final class ResDomainRules {

    static final String FR_AREA = "FR";
    static final String OTHERS_AREA = "OTHERS";

    // Areas that need zonal naming (FR01, FR02, ..). Only FR for now.
    static final Set<String> ZONAL_AREAS = Set.of(FR_AREA);

    // filesystem rules
    static final String IGNORED_DIR_OLD = "old";
    static final String LOCK_FILE_PREFIX = ".~lock.";
    static final Set<String> SUPPORTED_TRAJECTORY_EXTENSIONS = Set.of(".csv", ".txt", ".xlsx");

    private ResDomainRules() {}

    static String extractBaseArea(String token) {
        int i = token.length() - 1;

        while (i >= 0 && Character.isDigit(token.charAt(i))) {
            i--;
        }

        return token.substring(0, i + 1).toUpperCase(Locale.ROOT);
    }

    static boolean containsMalformedZonalToken(String fileName) {
        int extIndex = fileName.lastIndexOf('.');
        String nameWithoutExt = extIndex > 0 ? fileName.substring(0, extIndex) : fileName;
        String[] tokens = nameWithoutExt.split("_");

        for (String token : tokens) {
            if (token.isBlank()) continue;
            String normalizedToken = token.toUpperCase(Locale.ROOT);
            String baseArea = extractBaseArea(normalizedToken);

            if (ZONAL_AREAS.contains(baseArea) && normalizedToken.equals(baseArea)) {
                return true;
            }
        }
        return false;
    }

    static boolean isBaselineTrajectoryFile(Path path, BasicFileAttributes attrs) {
        if (!attrs.isRegularFile()) {
            return false;
        }

        // exclude ignored directories
        for (int i = 0; i < path.getNameCount(); i++) {
            if (IGNORED_DIR_OLD.equalsIgnoreCase(path.getName(i).toString())) {
                return false;
            }
        }

        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (lowerName.startsWith(LOCK_FILE_PREFIX)) {
            return false;
        }

        return SUPPORTED_TRAJECTORY_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }
}