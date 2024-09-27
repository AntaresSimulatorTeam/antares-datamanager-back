package com.rte_france.antares.datamanager_back.controller;


import com.rte_france.antares.datamanager_back.configuration.SftpDownloadService;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 class TrajectoryControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    TrajectoryServiceImpl trajectoryServiceImpl;

    @MockBean
    SftpDownloadService sftpDownloadService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void uploadTrajectory_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processTrajectory(any(),any(), any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1)).processTrajectory(any(), any(),any());
    }

    @Test
    void findTrajectoriesByTypeFromDb_returnsTrajectories() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndFileNameStartWithFromDB(any(),any(),any())).thenReturn(List.of(TrajectoryEntity.builder().build()));

        this.mockMvc.perform(get("/v1/trajectory/db")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("fileNameStartsWith", "test")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1)).findTrajectoriesByTypeAndFileNameStartWithFromDB(any(), any(),any());
    }

    @Test
    void findTrajectoriesByTypeFromFileSystem_returnsFileNames() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndFileNameStartWithFromFS(any())).thenReturn(List.of("test"));
        this.mockMvc.perform(get("/v1/trajectory/fs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(sftpDownloadService, times(1)).listFsTrajectoryByType(any(), any());
    }

}
