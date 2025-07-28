package com.rte_france.antares.datamanager_back.controller;


import com.rte_france.antares.datamanager_back.dto.AreaTrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrajectoryControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    TrajectoryServiceImpl trajectoryServiceImpl;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void uploadTrajectory_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processTrajectory(any(), any(), any(), any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1)).processTrajectory(any(), any(), any(), any());
    }

    @Test
    void uploadTrajectory_withSpacesInFileName_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processTrajectory(any(), any(), any(), any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("trajectoryToUse", "test file with spaces")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1)).processTrajectory(any(), any(), any(), any());
    }

    @Test
    void findTrajectoriesByTypeFromDb_returnsTrajectories() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndFileNameContainsFromDB(any(), any(), any(), any())).thenReturn(List.of(TrajectoryEntity.builder().build()));

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
        verify(trajectoryServiceImpl, times(1)).findTrajectoriesByTypeAndFileNameContainsFromDB(any(), any(), any(), any());
    }

    @Test
    void findTrajectoriesByTypeFromFileSystem_returnsFileNames() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByType(any(), any())).thenReturn(List.of(FsTrajectoryDTO.builder().build()));
        this.mockMvc.perform(get("/v1/trajectory/fs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryType", "AREA")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
    }

    @Test
    void getTrajectoriesByStudyIdAndType_returnListForNullType() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndStudyId("nonExistentType", 1)).thenReturn(List.of());
        this.mockMvc.perform(get("/v1/trajectory")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getTrajectoriesByStudyIdAndType_returnsEmptyListForNonExistentId() throws Exception {
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndStudyId("AREA", 999)).thenReturn(List.of());
        this.mockMvc.perform(get("/v1/trajectory")
                        .param("trajectoryType", "AREA")
                        .param("studyId", "999")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getTrajectoriesByStudyIdAndType_returnsNonEmptyListForExistentTypeAndId() throws Exception {
        TrajectoryDTO dto = new TrajectoryDTO();
        dto.setType("AREA");
        dto.setId(1);
        when(trajectoryServiceImpl.findTrajectoriesByTypeAndStudyId("AREA", 1)).thenReturn(List.of(dto));
        this.mockMvc.perform(get("/v1/trajectory")
                        .param("trajectoryType", "AREA")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$[0].type").value("AREA"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void attachTrajectoryToStudy_returnsLinkedTrajectory() throws Exception {
        TrajectoryEntity dto = TrajectoryEntity.builder().id(1).type("AREA").build();
        dto.setType("AREA");
        dto.setId(1);
        when(trajectoryServiceImpl.linkTrajectoryToStudy(1, 1, TrajectoryType.AREA)).thenReturn(dto);

        this.mockMvc.perform(put("/v1/trajectory/attach")
                        .param("type", "AREA")
                        .param("trajectoryId", "1")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AREA"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void attachTrajectoryToStudy_returnsBadRequestForMissingParams() throws Exception {
        this.mockMvc.perform(put("/v1/trajectory/attach")
                        .param("type", "AREA")
                        .param("trajectoryId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unlinkTrajectoryFromStudy_returnsNoContentWhenLinkExists() throws Exception {
        doNothing().when(trajectoryServiceImpl).unlinkTrajectoryFromStudy(1, 1);

        this.mockMvc.perform(delete("/v1/trajectory/detach")
                        .param("trajectoryId", "1")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void unlinkTrajectoryFromStudy_returnsNotFoundWhenLinkDoesNotExist() throws Exception {
        doThrow(BusinessException.builder().message("Link between trajectory and study not found").httpStatus(HttpStatus.NOT_FOUND).build())
                .when(trajectoryServiceImpl).unlinkTrajectoryFromStudy(1, 1);

        this.mockMvc.perform(delete("/v1/trajectory/detach")
                        .param("trajectoryId", "1")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());
    }


    @Test
    void getTrajectoryDataByTypeAndId() throws Exception {
        AreaTrajectoryDataDTO trajectoryDataDTO = AreaTrajectoryDataDTO.builder()
                .areaName("AT")
                .powerToGas("true")
                .shortTermStorage("false")
                .build();

        when(trajectoryServiceImpl.getTrajectoryDataByTypeAndId(TrajectoryType.AREA, 1))
                .thenReturn(List.of(trajectoryDataDTO));

        this.mockMvc.perform(get("/v1/trajectory/trajectoryData")
                        .param("trajectoryType", "AREA")
                        .param("trajectoryId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].areaName").value("AT"))
                .andExpect(jsonPath("$[0].powerToGas").value("true"))
                .andExpect(jsonPath("$[0].shortTermStorage").value("false"));
    }

    @Test
    void uploadTrajectoryLoad_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processLoadTrajectory(any(), any(), any(), any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/load")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("area", "testArea")
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1)).processLoadTrajectory(any(), any(), any(), any());
    }

    @Test
    void uploadTrajectoryLoad_returnsBadRequestForInvalidHorizon() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/load")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("area", "testArea")
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadTrajectoryLoad_returnsBadRequestForMissingParams() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/load")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countWarningMessage() throws Exception {
        Integer studyId = 1;

        when(trajectoryServiceImpl.countWarningMessage(studyId)).thenReturn(any());

        this.mockMvc.perform(get("/v1/trajectory/count/warning/{id}",studyId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk());
    }
}
