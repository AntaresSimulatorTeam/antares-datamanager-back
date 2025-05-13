package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.ThermalBaseEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
@FunctionalInterface
public interface ThermalBuilder {
    @SuppressWarnings("squid:S1452")
    List<? extends ThermalBaseEntity> build(Path path) throws IOException;
}
