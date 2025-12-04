package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;

public interface ThermalClusterRefService {

    ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name);

    ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb);

}
