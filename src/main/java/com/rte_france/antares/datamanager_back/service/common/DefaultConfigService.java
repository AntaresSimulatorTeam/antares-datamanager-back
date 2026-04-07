package com.rte_france.antares.datamanager_back.service.common;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;

import java.util.List;

public interface DefaultConfigService {
  List<DefaultLoadDTO> fetchAllDefaults();
  boolean isDefaultArea(String area);
  List<DefaultThermalTechnologyDTO> fetchAllThermalTechnologies();
}
