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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalClusterRefServiceImpl implements ThermalClusterRefService {

    private final ThermalClusterRefRepository thermalClusterRefRepository;
    private final ThermalTechnologyRepository thermalTechnologyRepository;

    /**
     * Cache d'entités MANAGÉES (clé = technology + name)
     * ⚠️ valide uniquement dans la transaction d'import
     */
    private Map<ClusterKey, ThermalClusterRef> cachedClusterRefs;

    @Transactional
    @Override
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        ensureCacheLoaded();

        String trimmedName = name != null ? name.trim() : null;
        ClusterKey key = new ClusterKey(technology, trimmedName);

        ThermalClusterRef ref = cachedClusterRefs.get(key);
        if (ref != null) {
            return updatePemmdbIfNeeded(ref, namePemmdb);
        }

        // Création si absent
        ThermalTechnology thermalTechnology = technology != null ? findThermalTechnology(technology) : null;

        ref = buildClusterRef(trimmedName, thermalTechnology, namePemmdb);
        thermalClusterRefRepository.save(ref);

        cachedClusterRefs.put(key, ref);
        return ref;
    }

    // ==========================
    // Cache
    // ==========================

    private void ensureCacheLoaded() {
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
    }

    private void loadAllThermalClusterRefs() {
        List<ThermalClusterRef> refs = thermalClusterRefRepository.findAll();
        Map<ClusterKey, ThermalClusterRef> map = new HashMap<>();

        for (ThermalClusterRef ref : refs) {
            String techName = ref.getThermalTechnology() != null ? ref.getThermalTechnology().getName() : null;

            map.put(new ClusterKey(techName, ref.getName()), ref);
        }
        cachedClusterRefs = map;
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
