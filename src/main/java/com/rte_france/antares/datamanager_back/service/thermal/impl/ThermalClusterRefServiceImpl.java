package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalClusterRefServiceImpl implements ThermalClusterRefService {

    private final ThermalClusterRefRepository thermalClusterRefRepository;
    private final ThermalTechnologyRepository thermalTechnologyRepository;


    @Transactional
    public ThermalClusterRef findOrCreateThermalClusterRef(String technologyName, String clusterName, String namePemmdb) {
        String trimmedName = clusterName.trim();

        Optional<ThermalClusterRef> optionalThermalClusterRef = technologyName != null ?
                thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase(technologyName, trimmedName) :
                thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase(trimmedName);

        return optionalThermalClusterRef.map(ref -> updatePemmdbIfNeeded(ref, namePemmdb)).orElseGet(() -> {

            ThermalTechnology thermalTechnology = technologyName != null ? getThermalTechnology(technologyName) : null;

            ThermalClusterRef ref = buildThermalClusterRef(namePemmdb, trimmedName, thermalTechnology);

            return thermalClusterRefRepository.save(ref);
        });
    }

    private ThermalTechnology getThermalTechnology(String technologyName) {
        return thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase(technologyName).orElseThrow(() -> BusinessException.builder().message("Technology " + technologyName + " does not exist").build());
    }

    private static ThermalClusterRef buildThermalClusterRef(String namePemmdb, String trimmedName, ThermalTechnology technology) {
        return ThermalClusterRef.builder().name(trimmedName).namePemmdb(namePemmdb != null && !namePemmdb.isBlank() ? namePemmdb : "NA").thermalTechnology(technology).build();
    }

    private ThermalClusterRef updatePemmdbIfNeeded(ThermalClusterRef ref, String namePemmdb) {
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            String current = ref.getNamePemmdb();
            if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                ref.setNamePemmdb(namePemmdb);
            }
        }
        return ref;
    }
}
