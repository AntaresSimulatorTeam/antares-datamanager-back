package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.mapper.DefaultLoadMapper;
import com.rte_france.antares.datamanager_back.repository.DefaultLoadRepository;
import com.rte_france.antares.datamanager_back.service.DefaultLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLoadServiceImpl implements DefaultLoadService {

    private final DefaultLoadRepository defaultLoadRepository;

    @Override
    public List<DefaultLoadDTO> fetchAllDefaults() {
        return DefaultLoadMapper.toLoadDefaultDTOs(defaultLoadRepository.findAllByIsDefaultIsTrue());
    }
}
