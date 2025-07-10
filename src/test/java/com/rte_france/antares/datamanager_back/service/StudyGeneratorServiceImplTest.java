package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
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
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyGeneratorServiceImplTest {
    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private AntaressDataManagerProperties antaressDataManagerProperties;

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private NasFileService nasFileService;

    @InjectMocks
    private StudyGeneratorServiceImpl studyGeneratorService;


    private final Set<TrajectoryEntity> trajectoryEntityList = new LinkedHashSet<>();

    private StudyEntity studyEntity;

    @BeforeEach
    void setUp() {
        studyEntity = StudyEntity.builder()
                .id(1)
                .name("studyTest")
                .build();
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
        lenient().when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));
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


        Map<String, Object> rootJsonMap = objectMapper.readValue(generatedJson, new TypeReference<>() {});


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

        RuntimeException exception = assertThrows(RuntimeException.class, () -> studyGeneratorService.buildJsonForStudyGeneration(studyId));

        assertNotNull(exception);
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeLoadFilesByArea_withOthersArea() throws Exception {
        // Prépare un TrajectoryEntity LOAD avec area OTHERS et deux fichiers
        LoadEntity load1 = LoadEntity.builder()
                .outPutFileName("load_fr_2030-2031.txt")
                .build();
        LoadEntity load2 = LoadEntity.builder()
                .outPutFileName("load_de_2030-2031.txt")
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .type("LOAD")
                .loadArea("OTHERS")
                .build();
        loadTrajectory.addLoadEntity(load1);
        loadTrajectory.addLoadEntity(load2);

        AreaEntity areaEntityFR = AreaEntity.builder().name("FR").build();
        AreaEntity areaEntityDE = AreaEntity.builder().name("DE").build();
        AreaConfigEntity areaConfigFR = AreaConfigEntity.builder().area(areaEntityFR).build();
        AreaConfigEntity areaConfigDE = AreaConfigEntity.builder().area(areaEntityDE).build();

        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .type("AREA")
                .areaConfigEntities(Arrays.asList(areaConfigFR, areaConfigDE))
                .build();

        studyEntity.addTrajectoryEntity(loadTrajectory);
        studyEntity.addTrajectoryEntity(areaTrajectory);

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyGeneratorService.buildJsonForStudyGeneration(1);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq("1.json"), captor.capture());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(captor.getValue(), new TypeReference<>() {});
        Map<String, Object> studyMap = objectMapper.convertValue(jsonMap.get("studyTest"), new TypeReference<>() {});
        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {});

        // Vérifie que les fichiers LOAD sont bien associés à chaque area
        assertThat(areasMap.get("FR").toString()).contains("load_fr_2030-2031.txt");
        assertThat(areasMap.get("DE").toString()).contains("load_de_2030-2031.txt");
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeLoadFilesByArea_withExplicitArea() throws Exception {
        // Prépare un TrajectoryEntity LOAD avec area explicite
        LoadEntity load1 = LoadEntity.builder()
                .outPutFileName("load_fr_2030-2031.txt")
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .type("LOAD")
                .loadArea("FR")
                .loadEntities(Set.of(load1))
                .build();

        AreaEntity areaEntityFR = AreaEntity.builder().name("FR").build();
        AreaConfigEntity areaConfigFR = AreaConfigEntity.builder().area(areaEntityFR).build();

        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .type("AREA")
                .areaConfigEntities(List.of(areaConfigFR))
                .build();

        studyEntity.setTrajectories(new HashSet<>(Arrays.asList(loadTrajectory, areaTrajectory)));

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyGeneratorService.buildJsonForStudyGeneration(1);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq("1.json"), captor.capture());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(captor.getValue(), new TypeReference<>() {});
        Map<String, Object> studyMap = objectMapper.convertValue(jsonMap.get("studyTest"), new TypeReference<>() {});
        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {});

        // Vérifie que le fichier LOAD est bien associé à l'area FR
        assertThat(areasMap.get("FR").toString()).contains("load_fr_2030-2031.txt");
    }

    @Test
    void callGenerateStudyService_shouldThrowTechnicalExceptionOnRuntimeException() {
        // Arrange
        int studyId = 42;
        String url = "http://localhost/generate_study/?study_id=42";
        when(antaressDataManagerProperties.getGeneratorHostUrl()).thenReturn("http://localhost");
        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);

        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(url)).thenThrow(new RuntimeException("Connexion refusée"));

        // Act & Assert
        assertThatThrownBy(() -> studyGeneratorService.callGenerateStudyService(studyId))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("Error while call Generate study from generator");
    }
}
