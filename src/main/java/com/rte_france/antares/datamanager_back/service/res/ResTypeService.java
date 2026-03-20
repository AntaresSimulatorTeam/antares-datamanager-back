package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import java.util.List;

public interface ResTypeService {
    List<ResTypeEntity> getAllResTypes();
}

