package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.service.WarningMessageService;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WarningMessageControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    WarningMessageService warningMessageService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void acknowledgeWarningEndpointReturnsOkWhenWarningExists() throws Exception {
        var id = 1;

        doNothing().when(warningMessageService).acknowledgeWarning(id);

        mockMvc.perform(put("/v1/warnings/{id}/ack", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print());

        verify(warningMessageService, times(1)).acknowledgeWarning(id);
    }

    @Test
    void acknowledgeWarningEndpointReturnsNotFoundWhenWarningDoesNotExist() throws Exception {
        var id = 1;

        doThrow(BusinessException.builder().message("Warning message not found with id: " + id).httpStatus(HttpStatus.NOT_FOUND).build())
                .when(warningMessageService).acknowledgeWarning(id);

        mockMvc.perform(put("/v1/warnings/{id}/ack", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        verify(warningMessageService, times(1)).acknowledgeWarning(id);
    }
}
