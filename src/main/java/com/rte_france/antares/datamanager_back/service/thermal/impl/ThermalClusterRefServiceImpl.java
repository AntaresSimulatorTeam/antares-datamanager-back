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

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThermalClusterRefServiceImpl implements ThermalClusterRefService {

    private final ThermalClusterRefRepository thermalClusterRefRepository;
    private final ThermalTechnologyRepository thermalTechnologyRepository;

    /**
     * Finds an existing ThermalClusterRef based on the provided parameters or creates a new one if it does not exist.
     * The method ensures that the provided technology and cluster name are used to identify the ThermalClusterRef,
     * while also validating and updating the PEMMDB information if required.
     *
     * @param technologyName the name of the thermal technology. Can be null, in which case the search is scoped accordingly.
     * @param clusterName the name of the thermal cluster. It is trimmed of any leading/trailing whitespace before processing.
     * @param namePemmdb the PEMMDB name associated with the cluster. It can be null or "NA" if there is no valid PEMMDB.
     * @return the found or newly created ThermalClusterRef entity.
     * @throws BusinessException if the given technologyName does not correspond to any existing ThermalTechnology.
     */
    @Transactional
    public ThermalClusterRef findOrCreateThermalClusterRef(String technologyName, String clusterName, String namePemmdb) {
        String trimmedName = clusterName.trim();
        boolean hasPemmdb = isValidPemmdb(namePemmdb);

        Optional<ThermalClusterRef> optionalThermalClusterRef = findExistingCluster(technologyName, trimmedName, namePemmdb, hasPemmdb);

        if (optionalThermalClusterRef.isPresent()) {
            return updatePemmdbIfNeeded(optionalThermalClusterRef.get(), namePemmdb);
        }

        ThermalTechnology thermalTechnology = technologyName != null ? getThermalTechnology(technologyName) : null;
        ThermalClusterRef ref = buildThermalClusterRef(namePemmdb, trimmedName, thermalTechnology);
        return thermalClusterRefRepository.save(ref);
    }

    private Optional<ThermalClusterRef> findExistingCluster(String technologyName, String trimmedName, String namePemmdb, boolean hasPemmdb) {
        Optional<ThermalClusterRef> clusterRef;

        // 1. Try finding by technology and name
        if (technologyName != null) {
            clusterRef = findByTechnologyAndName(technologyName, trimmedName, namePemmdb, hasPemmdb);
        } else {
            // 2. Try finding by name with null technology
            clusterRef = findByNullTechnologyAndName(trimmedName, namePemmdb, hasPemmdb);
        }

        // 3. Try finding by PEMMDB if still not found
        if (clusterRef.isEmpty() && hasPemmdb) {
            clusterRef = findByPemmdb(technologyName, namePemmdb);
        }

        // 4. Try finding by name (any technology) as a fallback when technology is null
        if (clusterRef.isEmpty() && technologyName == null) {
            clusterRef = findByNameFallback(trimmedName, namePemmdb, hasPemmdb);
        }

        return clusterRef;
    }

    private Optional<ThermalClusterRef> findByTechnologyAndName(String technologyName, String trimmedName, String namePemmdb, boolean hasPemmdb) {
        return thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase(technologyName, trimmedName)
                .filter(ref -> matchesPemmdb(ref, namePemmdb, hasPemmdb));
    }

    private Optional<ThermalClusterRef> findByNullTechnologyAndName(String trimmedName, String namePemmdb, boolean hasPemmdb) {
        List<ThermalClusterRef> techNullRefs = thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase(trimmedName);
        return techNullRefs.stream()
                .filter(ref -> matchesPemmdb(ref, namePemmdb, hasPemmdb))
                .findFirst();
    }

    private Optional<ThermalClusterRef> findByPemmdb(String technologyName, String namePemmdb) {
        List<ThermalClusterRef> pemmdbRefs = thermalClusterRefRepository.findByNamePemmdbIgnoreCase(namePemmdb.trim());
        if (pemmdbRefs.size() == 1) {
            return Optional.of(pemmdbRefs.get(0));
        } else if (pemmdbRefs.size() > 1 && technologyName != null) {
            return pemmdbRefs.stream()
                    .filter(r -> r.getThermalTechnology() != null && r.getThermalTechnology().getName().equalsIgnoreCase(technologyName))
                    .findFirst();
        }
        return Optional.empty();
    }

    private Optional<ThermalClusterRef> findByNameFallback(String trimmedName, String namePemmdb, boolean hasPemmdb) {
        List<ThermalClusterRef> refs = thermalClusterRefRepository.findByNameIgnoreCase(trimmedName);
        ThermalClusterRef found = null;
        for (ThermalClusterRef ref : refs) {
            if (matchesPemmdb(ref, namePemmdb, hasPemmdb)) {
                if (found == null) {
                    found = ref;
                } else {
                    return Optional.empty(); // Ambiguous
                }
            }
        }
        return Optional.ofNullable(found);
    }

    private boolean isValidPemmdb(String namePemmdb) {
        return namePemmdb != null && !namePemmdb.isBlank() && !"NA".equalsIgnoreCase(namePemmdb.trim());
    }

    private boolean matchesPemmdb(ThermalClusterRef ref, String namePemmdb, boolean hasPemmdb) {
        String refPemmdb = ref.getNamePemmdb();
        boolean refHasPemmdb = isValidPemmdb(refPemmdb);

        if (hasPemmdb != refHasPemmdb) {
            return false;
        }
        return !hasPemmdb || refPemmdb.trim().equalsIgnoreCase(namePemmdb.trim());
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
