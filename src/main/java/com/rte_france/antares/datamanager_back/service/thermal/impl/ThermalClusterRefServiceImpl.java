package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalClusterRefServiceImpl  implements ThermalClusterRefService {

    private final ThermalClusterRefRepository thermalClusterRefRepository;

    private final ThermalTechnologyRepository thermalTechnologyRepository;

    private List<ThermalClusterRef> cachedClusterRefs;


    /**
     * Finds or creates a ThermalClusterRef entity based on the provided technology and name.
     * If the entity does not exist, it will be created with default values.
     *
     * @param technology The name of the thermal technology associated with the cluster (can be null).
     * @param name The name of the thermal cluster (cannot be null).
     * @return The existing or newly created ThermalClusterRef entity.
     */
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name) {


        return findOrCreateThermalClusterRef(technology, name, null);
    }

    /**
     * Finds or creates a ThermalClusterRef entity based on the provided technology, name, and optional PEMMDB name.
     * If the entity does not exist, it will be created and saved to the database.
     * If the entity exists but the PEMMDB name is missing or invalid, it will be updated.
     *
     * @param technology The name of the thermal technology associated with the cluster (can be null).
     * @param name The name of the thermal cluster (cannot be null).
     * @param namePemmdb The PEMMDB name for the cluster (can be null or blank).
     * @return The existing or newly created ThermalClusterRef entity.
     */
    public ThermalClusterRef findOrCreateThermalClusterRef(String technology, String name, String namePemmdb) {
        ensureClusterRefsLoaded();

        // supprimer uniquement les espaces en fin de chaîne
        String trimmedName = name != null ? name.trim() : null;

        // Check if the cluster already exists in the cache
        Optional<ThermalClusterRef> existingOpt = findCachedClusterRef(technology, trimmedName);

        if (existingOpt.isPresent()) {
            // Update the PEMMDB name if necessary and return the existing entity
            return updatePemmdbIfNeeded(existingOpt.get(), namePemmdb);
        }

        // Create a new ThermalClusterRef entity if it does not exist
        ThermalTechnology thermalTechnology = technology != null ? findOrCreateTechnology(technology) : null;
        ThermalClusterRef newRef = buildClusterRef(trimmedName, thermalTechnology, namePemmdb);
        ThermalClusterRef saved = thermalClusterRefRepository.save(newRef);
        cachedClusterRefs.add(saved); // Add the new entity to the cache
        return saved;
    }



    private void ensureClusterRefsLoaded() {
        if (cachedClusterRefs == null) {
            loadAllThermalClusterRefs();
        }
    }

    private void loadAllThermalClusterRefs() {
        List<ThermalClusterRef> list = thermalClusterRefRepository.findAll();
        cachedClusterRefs = new ArrayList<>(list);
    }

    private Optional<ThermalClusterRef> findCachedClusterRef(String technology, String name) {
        return cachedClusterRefs.stream()
                .filter(ref -> ref.getName() != null && ref.getName().equalsIgnoreCase(name)
                        && (technology == null || technology.isBlank()
                        || (ref.getThermalTechnology() != null
                        && ref.getThermalTechnology().getName() != null
                        && ref.getThermalTechnology().getName().equalsIgnoreCase(technology))))
                .findFirst();
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

    private ThermalTechnology findOrCreateTechnology(String technology) {
        return thermalTechnologyRepository.findThermalTechnologyByName(technology)
                .orElseGet(() -> thermalTechnologyRepository.save(
                        ThermalTechnology.builder().name(technology).build()));
    }

    private ThermalClusterRef buildClusterRef(String name, ThermalTechnology technology, String namePemmdb) {
        return ThermalClusterRef.builder()
                .name(name)
                .thermalTechnology(technology)
                .namePemmdb((namePemmdb != null && !namePemmdb.isBlank()) ? namePemmdb : "NA")
                .build();
    }
}
