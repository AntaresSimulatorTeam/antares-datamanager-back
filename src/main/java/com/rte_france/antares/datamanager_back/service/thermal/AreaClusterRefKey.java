package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;

import java.util.Objects;

/**
 * Key of a thermal cluster in an area. Two keys are equal when they have the same area key and the same cluster name.
 *
 * @param area              the key of the area
 * @param thermalClusterRef the cluster reference, compared by name; can be null
 */
public record AreaClusterRefKey(AreaKey area, ThermalClusterRef thermalClusterRef) {

    /**
     * Builds the key of a thermal cluster in an area.
     *
     * @param areaName          the area name, in any case
     * @param thermalClusterRef the cluster reference, can be null
     * @return the key of the cluster
     * @throws IllegalArgumentException if the area name is null or blank
     */
    public static AreaClusterRefKey of(String areaName, ThermalClusterRef thermalClusterRef) {
        return new AreaClusterRefKey(AreaKey.of(areaName), thermalClusterRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AreaClusterRefKey(AreaKey area1, ThermalClusterRef clusterRef))) return false;
        String thisName = this.thermalClusterRef != null ? this.thermalClusterRef.getName() : null;
        String thatName = clusterRef != null ? clusterRef.getName() : null;
        return Objects.equals(this.area, area1) && Objects.equals(thisName, thatName);
    }

    @Override
    public int hashCode() {
        String name = this.thermalClusterRef != null ? this.thermalClusterRef.getName() : null;
        return Objects.hash(area, name);
    }
}
