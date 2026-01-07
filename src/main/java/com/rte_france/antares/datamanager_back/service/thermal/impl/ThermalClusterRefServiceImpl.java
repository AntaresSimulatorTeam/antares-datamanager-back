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

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalClusterRefServiceImpl implements ThermalClusterRefService {

    private final ThermalClusterRefRepository thermalClusterRefRepository;
    private final ThermalTechnologyRepository thermalTechnologyRepository;


    @Transactional
    @Override
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        String trimmedName = name != null ? name.trim() : null;

        return thermalClusterRefRepository.findByNameAndTechnologyName(trimmedName, technology)
                .map(ref -> updatePemmdbIfNeeded(ref, namePemmdb))
                .orElseGet(() -> {
                    // Création si absent
                    ThermalTechnology thermalTechnology = technology != null ? findThermalTechnology(technology) : null;
                    ThermalClusterRef ref = buildClusterRef(trimmedName, thermalTechnology, namePemmdb);
                    return thermalClusterRefRepository.save(ref);
                });
    }

    // ==========================
    // Helpers
    // ==========================

    private ThermalClusterRef updatePemmdbIfNeeded(ThermalClusterRef ref, String namePemmdb) {
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            String current = ref.getNamePemmdb();
            if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                ref.setNamePemmdb(namePemmdb);
            }
        }
        return ref;
    }

    private ThermalTechnology findThermalTechnology(String technology) {
        return thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase(technology)
                .orElseThrow(() -> BusinessException.builder().message("Technology {0} does not exist in the technology reference table.")
                        .errorMessageArguments(Collections.singletonList(technology))
                        .build());
    }

    private ThermalClusterRef buildClusterRef(String name, ThermalTechnology technology, String namePemmdb) {
        return ThermalClusterRef.builder()
                .name(name).thermalTechnology(technology).namePemmdb((namePemmdb != null && !namePemmdb.isBlank()) ? namePemmdb : "NA")
                .build();
    }

    // ==========================
    // Key
    // ==========================

    private record ClusterKey(String technology, String name) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClusterKey other)) return false;
            return equalsIgnoreCase(technology, other.technology) && equalsIgnoreCase(name, other.name);
        }

        @Override
        public int hashCode() {
            return (normalize(technology) + "|" + normalize(name)).hashCode();
        }

        private static boolean equalsIgnoreCase(String a, String b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.equalsIgnoreCase(b);
        }

        private static String normalize(String s) {
            return s == null ? "" : s.toLowerCase();
        }
    }
}
