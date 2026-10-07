package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;

import java.util.Objects;

public record AreaClusterRefKey(String area, ThermalClusterRef thermalClusterRef) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AreaClusterRefKey(String area1, ThermalClusterRef clusterRef))) return false;
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