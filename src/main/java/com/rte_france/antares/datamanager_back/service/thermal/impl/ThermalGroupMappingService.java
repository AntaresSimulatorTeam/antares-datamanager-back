package com.rte_france.antares.datamanager_back.service.thermal.impl;

import com.rte_france.antares.datamanager_back.repository.ThermalGroupMappingRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ThermalGroupMappingService {
  private final ThermalGroupMappingRepository thermalGroupMappingRepository;

  @Cacheable("thermal-group-map")
  @Transactional(readOnly = true)
  public Optional<String> toGroup(String raw) {
    Objects.requireNonNull(raw);
    var normalized = raw.trim().toUpperCase(Locale.ROOT);
    var exactMatch = thermalGroupMappingRepository.findByClusterIgnoreCase(normalized)
            .map(ThermalGroupMappingEntity::getGroupName);

    if (exactMatch.isPresent()) {
      return exactMatch;
    }

    if (normalized.contains("NUCLEAR")) {
      return Optional.of("Nuclear");
    }

    if (normalized.contains("LIGNITE")) {
      return Optional.of("Lignite");
    }

    if (normalized.contains("CCGT") && !normalized.contains("H2")) {
      return Optional.of("Gas");
    }

    return Optional.empty();
  }
}