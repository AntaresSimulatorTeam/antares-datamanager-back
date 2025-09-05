package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.repository.ThermalGroupMappingRepository;
import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ThermalGroupMappingService {
  private final ThermalGroupMappingRepository thermalGroupMappingRepository;

  @Cacheable("thermal-group-map")
  public String toGroup(String raw) {
    Objects.requireNonNull(raw);
    if (raw.isBlank()) return "OTHER1";
    return thermalGroupMappingRepository.findByClusterIgnoreCase(raw.trim())
               .map(ThermalGroupMappingEntity::getGroupName)
               .orElse("OTHER1"); // antares-craft default value
  }
}