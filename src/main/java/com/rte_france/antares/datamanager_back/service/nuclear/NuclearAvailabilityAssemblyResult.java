package com.rte_france.antares.datamanager_back.service.nuclear;

import com.rte_france.antares.datamanager_back.dto.NuclearSMRMixageDTO;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;

import java.util.Map;

/**
 * Result of nuclear availability assembly, keyed by the same {@link AreaClusterRefKey} used
 * during thermal cluster assembly. (FR node and y_nuc_modulation node independently project these into their own json key formats)
 */
public record NuclearAvailabilityAssemblyResult(
        Map<AreaClusterRefKey, String> seriesByCluster,
        Map<AreaClusterRefKey, NuclearSMRMixageDTO> smrMixageByCluster
) {}
