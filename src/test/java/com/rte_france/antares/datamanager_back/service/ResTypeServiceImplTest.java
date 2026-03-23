package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.ResTypeRepository;
import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.service.res.impl.ResTypeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResTypeServiceImplTest {

    @Mock
    private ResTypeRepository resTypeRepository;

    @InjectMocks
    private ResTypeServiceImpl resTypeService;

    @Test
    void getAllResTypesReturnsAllEntities() {
        ResTypeEntity a = ResTypeEntity.builder().id(1).label("Offshore Wind").build();
        ResTypeEntity b = ResTypeEntity.builder().id(2).label("Onshore Wind").build();

        when(resTypeRepository.findAll()).thenReturn(List.of(a, b));

        List<ResTypeEntity> result = resTypeService.getAllResTypes();

        assertThat(result).hasSize(2).containsExactly(a, b);
    }
}

