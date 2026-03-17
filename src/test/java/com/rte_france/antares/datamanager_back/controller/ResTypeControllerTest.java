package com.rte_france.antares.datamanager_back.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.service.res.ResTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResTypeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ResTypeService resTypeService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ResController controller = new ResController(resTypeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllResTypesReturnsJsonArray() throws Exception {
        ResTypeEntity a = ResTypeEntity.builder().id(1).label("Offshore Wind").build();
        ResTypeEntity b = ResTypeEntity.builder().id(2).label("Onshore Wind").build();

        when(resTypeService.getAllResTypes()).thenReturn(List.of(a, b));

        mockMvc.perform(get("/v1/trajectory/res-types"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(List.of(a, b))));
    }
}

