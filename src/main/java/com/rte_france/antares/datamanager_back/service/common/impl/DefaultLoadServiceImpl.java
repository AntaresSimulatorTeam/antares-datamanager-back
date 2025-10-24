package com.rte_france.antares.datamanager_back.service.common.impl;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.mapper.DefaultLoadMapper;
import com.rte_france.antares.datamanager_back.repository.DefaultLoadRepository;
import com.rte_france.antares.datamanager_back.repository.model.DefaultLoadEntity;
import com.rte_france.antares.datamanager_back.service.common.DefaultLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLoadServiceImpl implements DefaultLoadService {

    private final DefaultLoadRepository defaultLoadRepository;

    @Override
    public List<DefaultLoadDTO> fetchAllDefaults() {
        List< DefaultLoadEntity> defaultLoadEntities = defaultLoadRepository.findAllByIsDefaultIsTrue();
        return DefaultLoadMapper.toLoadDefaultDTOs(defaultLoadEntities)
                .stream()
                .sorted(Comparator.comparing(DefaultLoadDTO::getName))
                .toList();

    }
}
