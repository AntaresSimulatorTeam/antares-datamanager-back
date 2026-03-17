package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.repository.ResTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.service.res.ResTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResTypeServiceImpl implements ResTypeService {

    private final ResTypeRepository resTypeRepository;

    @Override
    public List<ResTypeEntity> getAllResTypes() {
        return resTypeRepository.findAll();
    }
}

