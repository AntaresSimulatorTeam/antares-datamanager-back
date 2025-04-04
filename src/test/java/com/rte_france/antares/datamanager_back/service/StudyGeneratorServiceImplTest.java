package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import static org.assertj.core.api.Assertions.assertThat;
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


    private Set<TrajectoryEntity> trajectoryEntityList = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        // Create LinkEntity with data
        LinkEntity linkEntity = LinkEntity.builder()
                .name("FR-DE")
                .winterHpDirectMw(100.0)
                .winterHpIndirectMw(200.0)
                .winterHcDirectMw(300.0)
                .winterHcIndirectMw(400.0)
                .summerHpDirectMw(500.0)
                .summerHpIndirectMw(600.0)
                .summerHcDirectMw(700.0)
                .summerHcIndirectMw(800.0)
                .build();

        // Create TrajectoryEntity for links
        TrajectoryEntity trajectoryEntityLinks = TrajectoryEntity.builder()
                .type("LINK")
                .linkEntities(Collections.singletonList(linkEntity))
                .build();

        // Create AreaEntity and AreaConfigEntity
        AreaEntity areaEntity = AreaEntity.builder().name("DE").build();
        AreaConfigEntity areaConfigEntity = AreaConfigEntity.builder().area(areaEntity).build();

        // Create TrajectoryEntity for areas
        TrajectoryEntity trajectoryEntityAreas = TrajectoryEntity.builder()
                .type("AREA")
                .areaConfigEntities(Collections.singletonList(areaConfigEntity))
                .build();

        // Add trajectories to set
        trajectoryEntityList.add(trajectoryEntityLinks);
        trajectoryEntityList.add(trajectoryEntityAreas);

        // Create StudyEntity with trajectories
        StudyEntity studyEntity = StudyEntity.builder()
                .name("studyTest")
                .trajectories(trajectoryEntityList)
                .build();

        // Mock studyRepository behavior
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
    @Test
    void testBuildLinksDataMap() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Integer studyId = 1;

        // When
        studyGeneratorService.buildJsonForStudyGeneration(studyId);


        byte[] generatedJson = captureGeneratedJson(studyId);


        Map<String, Object> rootJsonMap = objectMapper.readValue(generatedJson, new TypeReference<Map<String, Object>>() {});


        Map<String, Object> studyData = objectMapper.convertValue(rootJsonMap.get("studyTest"), new TypeReference<>() {});
        assertThat(studyData)
                .as("Check that json expected keys")
                .containsKeys("areas", "links", "settings", "version");

        // Extract links data
        Map<String, Object> linksMap = objectMapper.convertValue(studyData.get("links"), new TypeReference<>() {});
        assertThat(linksMap)
                .as("Check that the links map contains the link entry")
                .hasSize(1)
                .containsKey("FR/DE");

        // Validate the data for the "FR/DE" link.
        Map<String, Object> linkData = objectMapper.convertValue(linksMap.get("FR/DE"), new TypeReference<>() {});
        assertThat(linkData)
                .as("Check that link data contains the correct winter and summer values")
                .containsEntry("winterHpDirectMw", 100.0)
                .containsEntry("winterHpIndirectMw", 200.0)
                .containsEntry("winterHcDirectMw", 300.0)
                .containsEntry("winterHcIndirectMw", 400.0)
                .containsEntry("summerHpDirectMw", 500.0)
                .containsEntry("summerHpIndirectMw", 600.0)
                .containsEntry("summerHcDirectMw", 700.0)
                .containsEntry("summerHcIndirectMw", 800.0);
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
