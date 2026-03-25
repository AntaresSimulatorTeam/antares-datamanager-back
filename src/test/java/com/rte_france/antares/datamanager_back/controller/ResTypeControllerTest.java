package com.rte_france.antares.datamanager_back.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.repository.model.ResTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.res.ResFileProcessorService;
import com.rte_france.antares.datamanager_back.service.res.ResTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    private MockMvc mockMvc;

    @MockBean
    private ResTypeService resTypeService;
    
    @MockBean
    private ResFileProcessorService resFileProcessorService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void getAllResTypesReturnsJsonArray() throws Exception {
        ResTypeEntity a = ResTypeEntity.builder().id(1).label("Wind Offshore").build();
        ResTypeEntity b = ResTypeEntity.builder().id(2).label("Wind Onshore").build();

        when(resTypeService.getAllResTypes()).thenReturn(List.of(a, b));

        mockMvc.perform(get("/v1/trajectory/res-types"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(List.of(a, b))));
    }

    @Test
    void uploadInstalledResTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(123);
        entity.setFileName("installedRES_test");
        entity.setType("RES_CAPACITY");
        entity.setVersion(1);
        entity.setArea("FR");
        entity.setTechnology("solar");
        entity.setHasTimeSeries(false);

        when(resFileProcessorService.processInstalledResFile(
                "installedRES_test",
                "2029-2030",
                1,
                "FR",
                "solar",
                false
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/installed-power-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "solar")
                        .param("trajectoryToUse", "installedRES_test")
                        .param("horizon", "2029-2030")
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.trajectoryName").value("installedRES_test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.area").value("FR"))
                .andExpect(jsonPath("$.technology").value("solar"));

        verify(resFileProcessorService, times(1))
                .processInstalledResFile("installedRES_test", "2029-2030", 1, "FR", "solar", false);
        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(456);
        entity.setFileName("loadFactorRES_test");
        entity.setType("RES_LOAD_FACTOR");
        entity.setVersion(1);
        entity.setArea("FR");
        entity.setTechnology("wind");
        entity.setHasTimeSeries(true);

        when(resFileProcessorService.processLoadFactorResFile(
                "loadFactorRES_test",
                "2029-2030",
                1,
                "FR",
                "wind"
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", "loadFactorRES_test")
                        .param("horizon", "2029-2030")
                        .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(456))
                .andExpect(jsonPath("$.trajectoryName").value("loadFactorRES_test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.area").value("FR"))
                .andExpect(jsonPath("$.technology").value("wind"));

        verify(resFileProcessorService, times(1))
                .processLoadFactorResFile("loadFactorRES_test", "2029-2030", 1, "FR", "wind");
        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_returnsBadRequest_whenTrajectoryNameExceeds40Characters() throws Exception {
        String longTrajectoryName = "a".repeat(41);

        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", longTrajectoryName)
                        .param("horizon", "2029-2030")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_returnsBadRequest_whenHorizonFormatIsInvalid() throws Exception {
        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", "loadFactorRES_test")
                        .param("horizon", "2029")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_returnsBadRequest_whenHorizonMissingDash() throws Exception {
        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", "loadFactorRES_test")
                        .param("horizon", "20292030")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_returnsBadRequest_whenHorizonContainsNonDigits() throws Exception {
        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", "loadFactorRES_test")
                        .param("horizon", "abcd-efgh")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(resFileProcessorService);
    }

    @Test
    void uploadLoadFactorResTrajectory_callsServiceWithCorrectParameters() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(789);
        entity.setFileName("loadFactorRES_param");
        entity.setType("RES_LOAD_FACTOR");
        entity.setVersion(1);
        entity.setArea("DE");
        entity.setTechnology("solar");

        when(resFileProcessorService.processLoadFactorResFile(
                "loadFactorRES_param",
                "2026-2027",
                42,
                "DE",
                "solar"
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "DE")
                        .param("technology", "solar")
                        .param("trajectoryToUse", "loadFactorRES_param")
                        .param("horizon", "2026-2027")
                        .param("studyId", "42"))
                .andExpect(status().isCreated());

        verify(resFileProcessorService, times(1))
                .processLoadFactorResFile("loadFactorRES_param", "2026-2027", 42, "DE", "solar");
    }

    @Test
    void uploadLoadFactorResTrajectory_acceptsTrajectoryNameWithExactly40Characters() throws Exception {
        String trajectoryName40Chars = "a".repeat(40);
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(999);
        entity.setFileName(trajectoryName40Chars);
        entity.setType("RES_LOAD_FACTOR");

        when(resFileProcessorService.processLoadFactorResFile(
                trajectoryName40Chars,
                "2029-2030",
                1,
                "FR",
                "wind"
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", trajectoryName40Chars)
                        .param("horizon", "2029-2030")
                        .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(999));

        verify(resFileProcessorService, times(1))
                .processLoadFactorResFile(trajectoryName40Chars, "2029-2030", 1, "FR", "wind");
    }

    @Test
    void uploadLoadFactorResTrajectory_throwsException_whenServiceFails() throws Exception {
        when(resFileProcessorService.processLoadFactorResFile(
                "loadFactorRES_test",
                "2029-2030",
                1,
                "FR",
                "wind"
        )).thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(post("/v1/trajectory/load-factor-res")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("technology", "wind")
                        .param("trajectoryToUse", "loadFactorRES_test")
                        .param("horizon", "2029-2030")
                        .param("studyId", "1"))
                .andExpect(status().isInternalServerError());

        verify(resFileProcessorService, times(1))
                .processLoadFactorResFile("loadFactorRES_test", "2029-2030", 1, "FR", "wind");
    }
}
