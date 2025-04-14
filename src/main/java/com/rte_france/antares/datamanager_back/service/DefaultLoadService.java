package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;

import java.util.List;

public interface DefaultLoadService {
  List<DefaultLoadDTO> fetchAllDefaults();
}
