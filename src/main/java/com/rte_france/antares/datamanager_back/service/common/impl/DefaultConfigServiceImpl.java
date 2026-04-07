package com.rte_france.antares.datamanager_back.service.common.impl;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.dto.DefaultThermalTechnologyDTO;
import com.rte_france.antares.datamanager_back.mapper.DefaultLoadMapper;
import com.rte_france.antares.datamanager_back.mapper.DefaultThermalTechnologyMapper;
import com.rte_france.antares.datamanager_back.repository.DefaultLoadRepository;
import com.rte_france.antares.datamanager_back.repository.ThermalTechnologyRepository;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultConfigServiceImpl implements DefaultConfigService {

    private final DefaultLoadRepository defaultLoadRepository;
    private final ThermalTechnologyRepository thermalTechnologyRepository;

    @Override
    public List<DefaultLoadDTO> fetchAllDefaults() {
        List< DefaultLoadEntity> defaultLoadEntities = defaultLoadRepository.findAllByIsDefaultIsTrue();
        return DefaultLoadMapper.toLoadDefaultDTOs(defaultLoadEntities)
                .stream()
                .sorted(Comparator.comparing(DefaultLoadDTO::getName))
                .toList();

    }
    
    @Override
    public boolean isDefaultArea(String area) {
        return defaultLoadRepository.findAllByIsDefaultIsTrue()
                .stream()
                .anyMatch(e -> e.getName().equalsIgnoreCase(area));
    }

    @Override
    public List<DefaultThermalTechnologyDTO> fetchAllThermalTechnologies() {
        List< ThermalTechnology> thermalTechnologyList = thermalTechnologyRepository.findAll();
        return DefaultThermalTechnologyMapper.toDefaultThermalTechnologyDTOs(thermalTechnologyList)
                .stream()
                .sorted(Comparator.comparing(DefaultThermalTechnologyDTO::getName))
                .toList();

    }

}
