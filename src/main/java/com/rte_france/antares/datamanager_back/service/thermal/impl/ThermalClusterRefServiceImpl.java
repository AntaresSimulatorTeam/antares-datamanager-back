  package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ThermalClusterRefRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
        log.info("Entering findOrCreateThermalClusterRef with technology='{}', cluster='{}', pemmdb='{}'",
                technologyName, clusterName, namePemmdb);
        String trimmedName = clusterName.trim();
        boolean hasPemmdb = isValidPemmdb(namePemmdb);

        Optional<ThermalClusterRef> optionalThermalClusterRef = findExistingCluster(technologyName, trimmedName, namePemmdb, hasPemmdb);

        if (optionalThermalClusterRef.isPresent()) {
            ThermalClusterRef existing = optionalThermalClusterRef.get();
            log.info("Existing cluster found (no insert): id={} name='{}' pemmdb='{}' tech={}",
                    existing.getId(), existing.getName(), existing.getNamePemmdb(),
                    existing.getThermalTechnology() == null ? "null" : existing.getThermalTechnology().getName());
            return updatePemmdbIfNeeded(existing, namePemmdb);
        }

        ThermalTechnology thermalTechnology = technologyName != null ? getThermalTechnology(technologyName) : null;
        ThermalClusterRef ref = buildThermalClusterRef(namePemmdb, trimmedName, thermalTechnology);
        log.info("Creating new ThermalClusterRef: name='{}' pemmdb='{}' tech={}",
                trimmedName, ref.getNamePemmdb(), thermalTechnology == null ? "null" : thermalTechnology.getName());
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

        if (clusterRef.isEmpty()) {
            log.info("No matching cluster found for technology='{}', name='{}', pemmdb='{}'", technologyName, trimmedName, namePemmdb);
        }
        return clusterRef;
    }

    private Optional<ThermalClusterRef> findByTechnologyAndName(String technologyName, String trimmedName, String namePemmdb, boolean hasPemmdb) {
        Optional<ThermalClusterRef> found = thermalClusterRefRepository.findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase(technologyName, trimmedName);
        if (found.isPresent()) {
            ThermalClusterRef ref = found.get();
            if (matchesPemmdb(ref, namePemmdb, hasPemmdb)) {
                log.info("Found by technology and name: tech='{}' name='{}' id={} pemmdb='{}'", technologyName, trimmedName, ref.getId(), ref.getNamePemmdb());
                return Optional.of(ref);
            } else {
                log.info("Found by technology/name but PEMMDB mismatch: id={} name='{}' clusterPemmdb='{}' requestedPemmdb='{}'",
                        ref.getId(), ref.getName(), ref.getNamePemmdb(), namePemmdb);
                return Optional.empty();
            }
        }
        log.info("No cluster found by technology='{}' and name='{}'", technologyName, trimmedName);
        return Optional.empty();
    }

    private Optional<ThermalClusterRef> findByNullTechnologyAndName(String trimmedName, String namePemmdb, boolean hasPemmdb) {
        List<ThermalClusterRef> techNullRefs = thermalClusterRefRepository.findByThermalTechnologyIsNullAndNameIgnoreCase(trimmedName);
        log.info("Found {} clusters with null technology and name='{}'", techNullRefs.size(), trimmedName);
        return techNullRefs.stream()
                .filter(ref -> {
                    boolean match = matchesPemmdb(ref, namePemmdb, hasPemmdb);
                    if (!match) {
                        log.info("Skipping cluster id={} name='{}' due to PEMMDB mismatch (clusterPemmdb='{}' requested='{}')",
                                ref.getId(), ref.getName(), ref.getNamePemmdb(), namePemmdb);
                    }
                    return match;
                })
                .findFirst();
    }

    private Optional<ThermalClusterRef> findByPemmdb(String technologyName, String namePemmdb) {
        List<ThermalClusterRef> pemmdbRefs = thermalClusterRefRepository.findByNamePemmdbIgnoreCase(namePemmdb.trim());
        log.info("PEMMDB lookup for '{}' returned {} result(s)", namePemmdb, pemmdbRefs.size());
        if (pemmdbRefs.size() == 1) {
            ThermalClusterRef ref = pemmdbRefs.get(0);
            log.info("Found unique cluster by PEMMDB: id={} name='{}' tech={}", ref.getId(), ref.getName(),
                    ref.getThermalTechnology() == null ? "null" : ref.getThermalTechnology().getName());
            return Optional.of(ref);
        } else if (pemmdbRefs.size() > 1 && technologyName != null) {
            Optional<ThermalClusterRef> filtered = pemmdbRefs.stream()
                    .filter(r -> r.getThermalTechnology() != null && r.getThermalTechnology().getName().equalsIgnoreCase(technologyName))
                    .findFirst();
            if (filtered.isPresent()) {
                ThermalClusterRef ref = filtered.get();
                log.info("Found cluster by PEMMDB and technology match: id={} name='{}' tech='{}'", ref.getId(), ref.getName(), technologyName);
                return filtered;
            } else {
                log.info("Multiple PEMMDB matches but none matched technology='{}'", technologyName);
            }
        }
        return Optional.empty();
    }

    private Optional<ThermalClusterRef> findByNameFallback(String trimmedName, String namePemmdb, boolean hasPemmdb) {
        List<ThermalClusterRef> refs = thermalClusterRefRepository.findByNameIgnoreCase(trimmedName);
        log.info("Fallback name lookup found {} result(s) for name='{}'", refs.size(), trimmedName);
        ThermalClusterRef found = null;
        for (ThermalClusterRef ref : refs) {
            if (matchesPemmdb(ref, namePemmdb, hasPemmdb)) {
                if (found == null) {
                    found = ref;
                } else {
                    log.info("Ambiguous matches for name='{}' with pemmdb='{}' (at least two compatible clusters found), will not insert", trimmedName, namePemmdb);
                    return Optional.empty(); // Ambiguous
                }
            } else {
                log.info("Skipping cluster id={} name='{}' in fallback due to PEMMDB mismatch (clusterPemmdb='{}' requested='{}')",
                        ref.getId(), ref.getName(), ref.getNamePemmdb(), namePemmdb);
            }
        }
        if (found != null) {
            log.info("Fallback found unique cluster id={} name='{}'", found.getId(), found.getName());
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
            log.info("PEMMDB presence mismatch for cluster id={} name='{}' : refHasPemmdb={} requestedHasPemmdb={}",
                    ref.getId(), ref.getName(), refHasPemmdb, hasPemmdb);
            return false;
        }
        if (!hasPemmdb) {
            return true;
        }
        boolean equal = refPemmdb.trim().equalsIgnoreCase(namePemmdb.trim());
        if (!equal) {
            log.info("PEMMDB value mismatch for cluster id={} name='{}' : refPemmdb='{}' requested='{}'",
                    ref.getId(), ref.getName(), refPemmdb, namePemmdb);
        }
        return equal;
    }

    private ThermalTechnology getThermalTechnology(String technologyName) {
        Optional<ThermalTechnology> opt = thermalTechnologyRepository.findThermalTechnologyByNameIgnoreCase(technologyName);
        if (opt.isEmpty()) {
            log.info("Technology '{}' does not exist", technologyName);
            throw BusinessException.builder().message("Technology " + technologyName + " does not exist").httpStatus(HttpStatus.BAD_REQUEST).build();
        }
        return opt.get();
    }

    private static ThermalClusterRef buildThermalClusterRef(String namePemmdb, String trimmedName, ThermalTechnology technology) {
        String pemdbValue = namePemmdb != null && !namePemmdb.isBlank() ? namePemmdb : "NA";
        log.info("Building ThermalClusterRef(name='{}', pemmdb='{}', tech={})", trimmedName, pemdbValue, technology == null ? "null" : technology.getName());
        return ThermalClusterRef.builder().name(trimmedName).namePemmdb(pemdbValue).thermalTechnology(technology).build();
    }

    private ThermalClusterRef updatePemmdbIfNeeded(ThermalClusterRef ref, String namePemmdb) {
        if (namePemmdb != null && !namePemmdb.isBlank()) {
            String current = ref.getNamePemmdb();
            if (current == null || current.isBlank() || "NA".equalsIgnoreCase(current)) {
                log.info("Updating PEMMDB for existing cluster id={} name='{}' from '{}' to '{}'", ref.getId(), ref.getName(), current, namePemmdb);
                ref.setNamePemmdb(namePemmdb);
            } else {
                log.info("No PEMMDB update needed for cluster id={} name='{}' current PEMMDB='{}'", ref.getId(), ref.getName(), current);
            }
        } else {
            log.info("No PEMMDB provided for update for cluster id={} name='{}'", ref.getId(), ref.getName());
        }
        return ref;
    }
}
