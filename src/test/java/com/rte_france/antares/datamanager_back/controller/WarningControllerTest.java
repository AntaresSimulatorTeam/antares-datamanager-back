package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.WarningDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.service.WarningService;
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

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WarningControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    WarningService warningService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void acknowledgeWarningEndpointReturnsOkWhenWarningExists() throws Exception {
        var id = 1;

        doNothing().when(warningService).acknowledgeWarning(id);

        mockMvc.perform(put("/v1/warnings/{id}/ack", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        verify(warningService, times(1)).acknowledgeWarning(id);
    }

    @Test
    void acknowledgeWarningEndpointReturnsNotFoundWhenWarningDoesNotExist() throws Exception {
        var id = 1;

        doThrow(BusinessException.builder().message("Warning message not found with id: " + id).httpStatus(HttpStatus.NOT_FOUND).build())
                .when(warningService).acknowledgeWarning(id);

        mockMvc.perform(put("/v1/warnings/{id}/ack", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andDo(print());

        verify(warningService, times(1)).acknowledgeWarning(id);
    }

    @Test
    void fetchWarningByTrajectoryIdAndStudyId_shouldReturnWarnings_whenTrajectoryExists() throws Exception {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        Set<WarningDTO> warnings = Set.of(
                WarningDTO.builder()
                        .id(1)
                        .content("Test warning")
                        .level("ERROR")
                        .build()
        );

        when(warningService.getWarningsForTrajectory(studyId, TrajectoryType.AREA)).thenReturn(warnings);

        // When & Then
        mockMvc.perform(get("/v1/warnings")
                        .param("trajectoryType", TrajectoryType.AREA.name())
                        .param("studyId", studyId.toString())
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andReturn();

        verify(warningService).getWarningsForTrajectory(trajectoryId, TrajectoryType.AREA);
    }


}
