package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.impl.StudyGeneratorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyGeneratorServiceImplTest {

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private NasFileService nasFileService;

    @InjectMocks
    private StudyGeneratorServiceImpl studyGeneratorService;


    @BeforeEach
    void setUp() {

        LinkEntity linkEntity = LinkEntity.builder().name("FR-DE").build();


        TrajectoryEntity trajectoryEntityLinks = TrajectoryEntity.builder().type("LINK").
                linkEntities(Collections.singletonList(linkEntity)).build();

        AreaEntity areaEntity = AreaEntity.builder().name("DE").build();

        AreaConfigEntity areaConfigEntity = AreaConfigEntity.builder().area(areaEntity).build();

        TrajectoryEntity trajectoryEntityAreas = TrajectoryEntity.builder().type("AREA").
                areaConfigEntities(Collections.singletonList(areaConfigEntity)).build();

        Set<TrajectoryEntity> trajectoryEntityList = new LinkedHashSet<>();
        trajectoryEntityList.add(trajectoryEntityLinks);
        trajectoryEntityList.add(trajectoryEntityAreas);

        StudyEntity studyEntity = StudyEntity.builder().
                name("studyTest").trajectories(trajectoryEntityList).build();

        when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));
    }

    @Test
    void testBuildJsonForStudyGenerationResultOK() throws IOException {
        //Given
        ObjectMapper objectMapper = new ObjectMapper();
        Integer studyId = 1;

        // When
        studyGeneratorService.buildJsonForStudyGeneration(studyId);

        // Then
        verify(nasFileService).saveFile(eq(studyId + ".json"), any(byte[].class));

        byte[] generatedJson = captureGeneratedJson(studyId);


        Map<String, Object> jsonMap = objectMapper.readValue(generatedJson, new TypeReference<>() {
        });


        assertNotNull(jsonMap);
        assertTrue(jsonMap.containsKey("studyTest"));

        Map<String, Object> studyMap = objectMapper.readValue(objectMapper.writeValueAsString(jsonMap.get("studyTest")), new TypeReference<>() {
        });

        assertNotNull(studyMap);
        assertTrue(studyMap.containsKey("version"));
        assertTrue(studyMap.containsKey("areas"));
        assertTrue(studyMap.containsKey("links"));
        assertEquals("880", studyMap.get("version"));
        assertEquals("will be refactored so we'll put nothing for the moment", studyMap.get("settings"));


        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });
        assertNotNull(areasMap);
        assertTrue(areasMap.containsKey("DE"));

        Map<String, Object> linksMap = objectMapper.convertValue(studyMap.get("links"), new TypeReference<>() {
        });
        assertNotNull(linksMap);
        assertTrue(linksMap.containsKey("FR/DE"));
    }


    private byte[] captureGeneratedJson(Integer studyId) throws IOException {

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq(studyId + ".json"), captor.capture());

        return captor.getValue();
    }

    @Test
    void testBuildJsonForStudyGenerationThrowsExceptionWhenIOExceptionOccurs() throws IOException {
        // Given
        Integer studyId = 1;

        doThrow(IOException.class).when(nasFileService).saveFile(eq(studyId + ".json"), any(byte[].class));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            studyGeneratorService.buildJsonForStudyGeneration(studyId);
        });

        assertNotNull(exception);
        assertInstanceOf(IOException.class, exception.getCause());
    }

}
