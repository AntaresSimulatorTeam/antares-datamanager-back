package com.rte_france.antares.datamanager_back.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * How areas are named and compared.
 * <p>
 * An area name stays as written in the AREA file and is only used for display.
 * To compare or look up areas, use the keys below: they ignore case and outer spaces.
 * Only {@link StudyAreas} turns a key back into a name.
 * <p>
 * Backend only: an exception thrown here is a bug. Keep this class free of services, entities and DTOs.
 */
public final class AreaNaming {

    public static final String OTHERS = "OTHERS";

    private AreaNaming() {
    }

    /**
     * Checks if an area name is OTHERS, ignoring case and outer spaces.
     *
     * @param areaName the area name, can be null
     * @return true if the name is OTHERS, false otherwise (also false for null)
     */
    public static boolean isOthers(String areaName) {
        return areaName != null && OTHERS.equalsIgnoreCase(areaName.trim());
    }

    /**
     * Trims an area name for imports
     *
     * @param raw the raw name, can be null
     * @return the name without outer spaces, or null if it is null or blank
     */
    public static String trimName(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /**
     * Checks if two names are the same area, ignoring case and outer spaces.
     *
     * @param first  an area name, can be null
     * @param second another area name, can be null
     * @return true if both name the same area; false if one of them is null or blank
     */
    public static boolean sameArea(String first, String second) {
        return trimName(first) != null && trimName(second) != null && AreaKey.of(first).equals(AreaKey.of(second));
    }

    /**
     * Checks if a list of names contains an area, ignoring case and outer spaces.
     *
     * @param names the area names; null or blank names never match
     * @param name  the area name to find, can be null
     * @return true if one of the names is the same area; false if the name is null or blank
     */
    public static boolean containsArea(Collection<String> names, String name) {
        return names.stream().anyMatch(candidate -> sameArea(candidate, name));
    }

    /**
     * Trims and uppercases a name.
     *
     * @param raw the name to normalize
     * @return the trimmed, uppercase name
     * @throws IllegalArgumentException if the name is null or blank
     */
    private static String normalize(String raw) {
        String name = trimName(raw);
        if (name == null) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
        return name.toUpperCase(Locale.ROOT);
    }

    /**
     * Key of an area. Ignores case and outer spaces.
     *
     * @param value the normalized area name (trimmed, uppercase)
     */
    public record AreaKey(String value) {

        public AreaKey {
            value = normalize(value);
        }

        /**
         * Builds the key of an area.
         *
         * @param name the area name, in any case
         * @return the key of the area
         * @throws IllegalArgumentException if the name is null or blank
         */
        public static AreaKey of(String name) {
            return new AreaKey(name);
        }

        // Used for debugging
        @Override
        public String toString() {
            return "AreaKey[" + value + "]";
        }
    }

    /**
     * Key of a cluster in an area. Ignores case (follows AW standard)
     * Use it instead of "AREA_cluster" strings, so area X never picks up the clusters of area X_Y.
     *
     * @param area    the key of the area
     * @param cluster the normalized cluster name (trimmed, uppercase)
     */
    public record AreaClusterKey(AreaKey area, String cluster) {

        public AreaClusterKey {
            Objects.requireNonNull(area, "area");
            cluster = normalize(cluster);
        }

        /**
         * Builds the key of a cluster in an area.
         *
         * @param areaName    the area name, in any case
         * @param clusterName the cluster name, in any case
         * @return the key of the cluster
         * @throws IllegalArgumentException if a name is null or blank
         */
        public static AreaClusterKey of(String areaName, String clusterName) {
            return new AreaClusterKey(AreaKey.of(areaName), clusterName);
        }

        @Override
        public String toString() {
            return "AreaClusterKey[" + area.value() + ", " + cluster + "]";
        }
    }

    /**
     * The areas of one study. Gives the name of an area from its key.
     * Rejects two names that differ only by case, because AW can't create both.
     */
    public static final class StudyAreas {

        private final Map<AreaKey, String> displayNameByKey;

        private StudyAreas(Map<AreaKey, String> displayNameByKey) {
            this.displayNameByKey = displayNameByKey;
        }

        /**
         * Builds the areas of a study from their names.
         *
         * @param displayNames the area names, as written in the AREA file
         * @return the areas of the study
         * @throws IllegalArgumentException if a name is null or blank, or if two names differ only by case
         */
        public static StudyAreas of(Collection<String> displayNames) {
            Map<AreaKey, String> byKey = new LinkedHashMap<>();
            for (String raw : displayNames) {
                String name = trimName(raw);
                String existing = byKey.putIfAbsent(AreaKey.of(name), name);
                if (existing != null && !existing.equals(name)) {
                    throw new IllegalArgumentException("Areas " + existing + " and " + name + " are the same area in different cases. An area name must be written the same way everywhere.");
                }
            }
            return new StudyAreas(Collections.unmodifiableMap(byKey));
        }

        /**
         * Checks if the study has an area.
         *
         * @param key the key of the area
         * @return true if the study has this area
         */
        public boolean contains(AreaKey key) {
            return displayNameByKey.containsKey(key);
        }

        /**
         * Gives the name of an area, as written in the AREA file.
         *
         * @param key the key of the area
         * @return the area name
         * @throws IllegalArgumentException if the study does not have this area
         */
        public String displayName(AreaKey key) {
            String name = displayNameByKey.get(key);
            if (name == null) {
                throw new IllegalArgumentException("Unknown area " + key);
            }
            return name;
        }

        /**
         * Gives the cluster name shown in AW: area name + "_" + suffix.
         *
         * @param key    the key of the area
         * @param suffix the cluster name given by the input data, kept as is
         * @return the cluster name, for example {@code DEkf_gas}
         * @throws IllegalArgumentException if the suffix is null or blank, or if the study does not have this area
         */
        public String clusterDisplayName(AreaKey key, String suffix) {
            if (suffix == null || suffix.isBlank()) {
                throw new IllegalArgumentException("Cluster suffix must not be null or blank");
            }
            return displayName(key) + "_" + suffix;
        }
    }
}
