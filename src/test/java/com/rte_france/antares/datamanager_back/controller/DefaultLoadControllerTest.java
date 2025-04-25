package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.DefaultLoadDTO;
import com.rte_france.antares.datamanager_back.service.impl.DefaultLoadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DefaultLoadControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private DefaultLoadServiceImpl defaultLoadService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void fetchDefaultLoadConfigReturnsListOfDefaults() throws Exception {
        // Given
        DefaultLoadDTO default1 = DefaultLoadDTO.builder().name("FR").build();
        DefaultLoadDTO default2 = DefaultLoadDTO.builder().name("OTHERS").build();
        List<DefaultLoadDTO> defaults = List.of(default1, default2);

        when(defaultLoadService.fetchAllDefaults()).thenReturn(defaults);

        // When
        mockMvc.perform(get("/v1/default_config/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                // Then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("FR"))
                .andExpect(jsonPath("$[1].name").value("OTHERS"))
                .andDo(MockMvcResultHandlers.print());

        verify(defaultLoadService, times(1)).fetchAllDefaults();
    }
}

