package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final EntityManager entityManager;


    private Map<ClusterKey, Integer> cachedClusterRefIds;



    @Transactional
    @Override
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        ensureCacheLoaded();

        String trimmedName = name != null ? name.trim() : null;
        ClusterKey key = new ClusterKey(technology, trimmedName);

        Integer existingId = cachedClusterRefIds.get(key);
        if (existingId != null) {
            ThermalClusterRef ref = entityManager.getReference(ThermalClusterRef.class, existingId);
            return updatePemmdbIfNeeded(ref, namePemmdb);
        }

        ThermalTechnology thermalTechnology = technology != null ? findThermalTechnology(technology) : null;

        ThermalClusterRef newRef = buildClusterRef(trimmedName, thermalTechnology, namePemmdb);
        ThermalClusterRef saved = thermalClusterRefRepository.save(newRef);

        // Sécurise la FK
        thermalClusterRefRepository.flush();

        cachedClusterRefIds.put(key, saved.getId());
        return saved;
    }



    private void ensureCacheLoaded() {
        if (cachedClusterRefIds == null) {
            loadAllThermalClusterRefs();
        }
    }

    private void loadAllThermalClusterRefs() {
        List<ThermalClusterRef> refs = thermalClusterRefRepository.findAll();
        Map<ClusterKey, Integer> map = new HashMap<>();

        for (ThermalClusterRef ref : refs) {
            String techName = ref.getThermalTechnology() != null ? ref.getThermalTechnology().getName() : null;
            map.put(new ClusterKey(techName, ref.getName()), ref.getId());
        }
        cachedClusterRefIds = map;
    }


    private ThermalClusterRef updatePemmdbIfNeeded(ThermalClusterRef ref, String namePemmdb) {
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            String current = ref.getNamePemmdb();
            if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                ref.setNamePemmdb(namePemmdb);
                return thermalClusterRefRepository.save(ref);
            }
        }
        return ref;
    }

    private ThermalTechnology findThermalTechnology(String technology) {
        return thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase(technology)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Technology {0} does not exist in the technology reference table.")
                        .errorMessageArguments(Collections.singletonList(technology))
                        .build());
    }

    private ThermalClusterRef buildClusterRef(String name, ThermalTechnology technology, String namePemmdb) {
        return ThermalClusterRef.builder()
                .name(name)
                .thermalTechnology(technology)
                .namePemmdb((namePemmdb != null && !namePemmdb.isBlank()) ? namePemmdb : "NA")
                .build();
    }



    private record ClusterKey(String technology, String name) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClusterKey(String technology1, String name1))) return false;
            return equalsIgnoreCase(technology, technology1)
                    && equalsIgnoreCase(name, name1);
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
