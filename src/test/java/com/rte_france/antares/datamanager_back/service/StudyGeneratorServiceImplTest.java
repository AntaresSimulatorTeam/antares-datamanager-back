package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.misc.MiscGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.study.impl.*;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyGeneratorServiceImplTest {



    @Mock
    private LoadRepository loadRepository;

    @Mock
    private NasFileService nasFileService;

    @Mock
    private UserService userService;
    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private StudyGeneratorServiceImpl studyGeneratorService;

    @Mock
    private LoadToJsonService loadToJsonService;

    @Mock
    private LinksToJsonService linksToJsonService;

    @Mock
    private StsToJsonService stsToJsonService;

    @Mock
    private ThermalToJsonService thermalToJsonService;

    @Mock
    private DsrToJsonService drsToJsonService;

    @Mock
    private MiscToJsonService miscToJsonService;

    @Mock
    private ResToJsonService resToJsonService;

    @Mock
    private HydroToJsonService hydroToJsonService;

    @Mock
    private ThermalPropertiesAssemblerService thermalPropertiesAssemblerService;

    @Mock
    private StsGenerationAssemblerService stPropertiesAssemblerService;

    @Mock
    private DsrGenerationAssemblerService dsrGenerationAssemblerService;

    @Mock
    private MiscGenerationAssemblerService miscGenerationAssemblerService;

    @Mock
    private ResGenerationAssemblerService resGenerationAssemblerService;

    @Mock
    private HydroGenerationAssemblerService hydroGenerationAssemblerService;

    private final Set<TrajectoryEntity> trajectoryEntityList = new LinkedHashSet<>();

    private StudyEntity studyEntity;

    @BeforeEach
    void setUp() {

        // convert the injected instance into a spy so we can stub/verify its methods
        studyGeneratorService = spy(studyGeneratorService);

        studyEntity = StudyEntity.builder().id(1).name("studyTest").build();
        // Create LinkEntity with data
        LinkEntity linkEntity = LinkEntity.builder().name("FR-DE")
                .winterHpDirectMw(100.0)
                .winterHpIndirectMw(200.0)
                .winterHcDirectMw(300.0)
                .winterHcIndirectMw(400.0)
                .summerHpDirectMw(500.0)
                .summerHpIndirectMw(600.0)
                .summerHcDirectMw(700.0)
                .summerHcIndirectMw(800.0)
                .hurdleCost(0.1)
                .build();

        // Create TrajectoryEntity for links
        TrajectoryEntity trajectoryEntityLinks = TrajectoryEntity.builder().type("LINK").linkEntities(Collections.singletonList(linkEntity)).build();

        // Create AreaEntity and AreaConfigEntity
        AreaEntity areaEntity = AreaEntity.builder().name("DE").build();
        AreaConfigEntity areaConfigEntity = AreaConfigEntity.builder()
                .area(areaEntity)
                .unsuppliedEnergyCost(1000.0)
                .spilledEnergyCost(0.0)
                .build();

        // Create TrajectoryEntity for areas
        TrajectoryEntity trajectoryEntityAreas = TrajectoryEntity.builder().type("AREA").areaConfigEntities(Collections.singletonList(areaConfigEntity)).build();

        // Add trajectories to set
        trajectoryEntityList.add(trajectoryEntityLinks);
        trajectoryEntityList.add(trajectoryEntityAreas);

        // Create StudyEntity with trajectories
        StudyEntity studyEntity = StudyEntity.builder().name("studyTest").trajectories(trajectoryEntityList).build();

        // Mock studyRepository behavior
        lenient().when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));
        // Default STS assembler returns empty map to avoid NPE in tests not focused on STS
        lenient().when(stPropertiesAssemblerService.assembleStsProperties(any())).thenReturn(Collections.emptyMap());
        //Default DSR assembler returns empty map to avoid NPE in tests not focused on DSR
        lenient().when(dsrGenerationAssemblerService.assembleDsrProperties(any())).thenReturn(Collections.emptyMap());
        lenient().when(resGenerationAssemblerService.assembleResProperties(any())).thenReturn(Collections.emptyMap());
        lenient().when(hydroGenerationAssemblerService.assembleHydroProperties(any())).thenReturn(Collections.emptyMap());

        // Delegate links building to real implementation by default
        lenient().doAnswer(inv -> {
            new LinksToJsonService().buildLinksDataMap(inv.getArgument(0), inv.getArgument(1), studyEntity);
            return null;
        }).when(linksToJsonService).buildLinksDataMap(any(), any(), any());

        // Delegate STS building to real implementation by default
        lenient().doAnswer(inv -> new StsToJsonService().stsMapGenerator(inv.getArgument(0), inv.getArgument(1)))
                .when(stsToJsonService).stsMapGenerator(anyString(), anyMap());
        // Delegate Thermal building to real implementation by default
        lenient().doAnswer(inv -> new ThermalToJsonService().getClusterPropsForArea(inv.getArgument(0), inv.getArgument(1)))
                .when(thermalToJsonService).getClusterPropsForArea(anyMap(), anyString());
        lenient().doAnswer(inv -> new ThermalToJsonService().thermalsMapGenerator(inv.getArgument(0)))
                .when(thermalToJsonService).thermalsMapGenerator(anyMap());

        lenient().doAnswer(inv -> new DsrToJsonService().buildDsrDataMap(inv.getArgument(0), inv.getArgument(1)))
                .when(drsToJsonService).buildDsrDataMap(anyString(),anyMap());

        lenient().doAnswer(inv -> new ResToJsonService().buildResDataMap(inv.getArgument(0), inv.getArgument(1)))
                .when(resToJsonService).buildResDataMap(anyString(), anyMap());

        lenient().doAnswer(inv -> new ResToJsonService().buildResDataMap(inv.getArgument(0), inv.getArgument(1)))
                .when(hydroToJsonService).buildHydroDataMap(anyString(), anyMap());
    }

    @Test
    void testBuildJsonForStudyGenerationResultOK() throws IOException {
        //Given
        ObjectMapper objectMapper = new ObjectMapper();
        Integer studyId = 1;

        // When
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(any())).thenReturn(Collections.emptyMap());

        studyGeneratorService.buildJsonForStudyGeneration(studyId);

        // Then
        verify(nasFileService).saveFile(eq(studyId + ".json"), any(byte[].class), anyString());

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
        assertEquals("9.3", studyMap.get("version"));
        assertEquals("will be refactored so we'll put nothing for the moment", studyMap.get("settings"));


        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });
        assertNotNull(areasMap);
        assertTrue(areasMap.containsKey("DE"));

        Map<String, Object> deArea = objectMapper.convertValue(areasMap.get("DE"), new TypeReference<>() {});
        Map<String, Object> properties = objectMapper.convertValue(deArea.get("properties"), new TypeReference<>() {});
        assertEquals("1000.0", properties.get("energy_cost_unsupplied"));
        assertEquals("0.0", properties.get("energy_cost_spilled"));

        Map<String, Object> linksMap = objectMapper.convertValue(studyMap.get("links"), new TypeReference<>() {
        });
        assertNotNull(linksMap);
        assertTrue(linksMap.containsKey("FR/DE"));
    }

    @Test
    void testBuildJsonForStudyGenerationDoesNotThrowForPspTrajectories() {
        Integer studyId = 2;
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        TrajectoryEntity pspSeriesTrajectory = TrajectoryEntity.builder().type("HYDRO_PSP_SERIES").fileName("psp_series").build();
        TrajectoryEntity pspParametersTrajectory = TrajectoryEntity.builder().type("HYDRO_PSP_TECHNICAL_PARAMETERS").fileName("psp_params").build();
        StudyEntity pspStudy = StudyEntity.builder().id(studyId).name("studyPsp")
                .trajectories(Set.of(pspSeriesTrajectory, pspParametersTrajectory))
                .build();
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(pspStudy));

        assertDoesNotThrow(() -> studyGeneratorService.buildJsonForStudyGeneration(studyId));
    }

    @Test
    void testBuildLinksDataMap() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Integer studyId = 1;
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        // When
        studyGeneratorService.buildJsonForStudyGeneration(studyId);


        byte[] generatedJson = captureGeneratedJson(studyId);


        Map<String, Object> rootJsonMap = objectMapper.readValue(generatedJson, new TypeReference<>() {
        });


        Map<String, Object> studyData = objectMapper.convertValue(rootJsonMap.get("studyTest"), new TypeReference<>() {
        });
        assertThat(studyData).as("Check that json expected keys").containsKeys("areas", "links", "settings", "version");

        // Extract links data
        Map<String, Object> linksMap = objectMapper.convertValue(studyData.get("links"), new TypeReference<>() {
        });
        assertThat(linksMap).as("Check that the links map contains the link entry").hasSize(1).containsKey("FR/DE");

        // Validate the data for the "FR/DE" link.
        Map<String, Object> linkData = objectMapper.convertValue(linksMap.get("FR/DE"), new TypeReference<>() {
        });
        assertThat(linkData).as("Check that link data contains the correct winter and summer values")
                .containsEntry("winterHpDirectMw", 100.0).containsEntry("winterHpIndirectMw", 200.0)
                .containsEntry("winterHcDirectMw", 300.0).containsEntry("winterHcIndirectMw", 400.0)
                .containsEntry("summerHpDirectMw", 500.0).containsEntry("summerHpIndirectMw", 600.0)
                .containsEntry("summerHcDirectMw", 700.0).containsEntry("summerHcIndirectMw", 800.0)
                .containsEntry("hurdleCost", 0.1);
    }

    private byte[] captureGeneratedJson(Integer studyId) throws IOException {

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq(studyId + ".json"), captor.capture(), anyString());

        return captor.getValue();
    }

    @Test
    void testBuildJsonForStudyGenerationThrowsExceptionWhenIOExceptionOccurs() throws TechnicalException, IOException {
        // Given
        Integer studyId = 1;
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        doThrow(new IOException("IO error")).when(nasFileService).saveFile(eq(studyId + ".json"), any(byte[].class), anyString());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> studyGeneratorService.buildJsonForStudyGeneration(studyId));

        assertNotNull(exception);
        assertInstanceOf(TechnicalException.class, exception);
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeLoadFilesByArea_withOthersArea() throws Exception {
        // Prépare un TrajectoryEntity LOAD avec area OTHERS et deux fichiers
        LoadEntity load1 = LoadEntity.builder().outPutFileName("load_fr_2030-2031.txt").build();
        LoadEntity load2 = LoadEntity.builder().outPutFileName("load_de_2030-2031.txt").build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder().type("LOAD").area("OTHERS").build();
        loadTrajectory.addLoadEntity(load1);
        loadTrajectory.addLoadEntity(load2);

        AreaEntity areaEntityFR = AreaEntity.builder().name("FR").build();
        AreaEntity areaEntityDE = AreaEntity.builder().name("DE").build();
        AreaConfigEntity areaConfigFR = AreaConfigEntity.builder().area(areaEntityFR).build();
        AreaConfigEntity areaConfigDE = AreaConfigEntity.builder().area(areaEntityDE).build();

        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder().type("AREA").areaConfigEntities(Arrays.asList(areaConfigFR, areaConfigDE)).build();

        studyEntity.addTrajectoryEntity(loadTrajectory);
        studyEntity.addTrajectoryEntity(areaTrajectory);
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(any())).thenReturn(Map.of(
                "FR", List.of("load_fr_2030-2031.txt"),
                "DE", List.of("load_de_2030-2031.txt")
        ));

        studyGeneratorService.buildJsonForStudyGeneration(1);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq("1.json"), captor.capture(), anyString());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(captor.getValue(), new TypeReference<>() {
        });
        Map<String, Object> studyMap = objectMapper.convertValue(jsonMap.get("studyTest"), new TypeReference<>() {
        });
        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });

        // Vérifie que les fichiers LOAD sont bien associés à chaque area
        assertThat(areasMap.get("FR").toString()).contains("load_fr_2030-2031.txt");
        assertThat(areasMap.get("DE").toString()).contains("load_de_2030-2031.txt");
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeLoadFilesByArea_withExplicitArea() throws Exception {
        // Prépare un TrajectoryEntity LOAD avec area explicite
        LoadEntity load1 = LoadEntity.builder().outPutFileName("load_fr_2030-2031.txt").build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder().type("LOAD").area("FR").loadEntities(Set.of(load1)).build();

        AreaEntity areaEntityFR = AreaEntity.builder().name("FR").build();
        AreaConfigEntity areaConfigFR = AreaConfigEntity.builder().area(areaEntityFR).build();

        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder().type("AREA").areaConfigEntities(List.of(areaConfigFR)).build();

        studyEntity.setTrajectories(new HashSet<>(Arrays.asList(loadTrajectory, areaTrajectory)));
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        when(loadToJsonService.getListArrowLoadFilesByAreaFromStudy(any())).thenReturn(Map.of(
                "FR", List.of("load_fr_2030-2031.txt")
        ));

        studyGeneratorService.buildJsonForStudyGeneration(1);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(nasFileService).saveFile(eq("1.json"), captor.capture(), anyString());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> jsonMap = objectMapper.readValue(captor.getValue(), new TypeReference<>() {
        });
        Map<String, Object> studyMap = objectMapper.convertValue(jsonMap.get("studyTest"), new TypeReference<>() {
        });
        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });

        // Vérifie que le fichier LOAD est bien associé à l'area FR
        assertThat(areasMap.get("FR").toString()).contains("load_fr_2030-2031.txt");
    }

    @Test
    void buildJsonForStudyGeneration_shouldGenerateArrowFileIfOutPutFileNameIsNull() throws IOException {
        // Given
        var load = LoadEntity.builder().fileName("load_fr_2030-2031.txt").build(); // outputFileName == null

        var loadTrajectory = TrajectoryEntity.builder().type("LOAD").area("OTHERS").fileName("BP23_A_Ref").build();
        loadTrajectory.addLoadEntity(load);

        var areaEntityFR = AreaEntity.builder().name("FR").build();
        var areaConfigFR = AreaConfigEntity.builder().area(areaEntityFR).build();
        var areaTrajectory = TrajectoryEntity.builder().type("AREA").areaConfigEntities(List.of(areaConfigFR)).build();

        var study = StudyEntity.builder().id(1).name("studyTest").build();
        study.addTrajectoryEntity(loadTrajectory);
        study.addTrajectoryEntity(areaTrajectory);

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("load");
        when(antaresDataManagerProperties.getOutputLoadDirectory()).thenReturn("outload");
        when(loadRepository.save(any())).thenReturn(load);
        doReturn("generated.arrow").when(nasFileService).readAndSaveMatrixToNas(any(), any(), any(), anyBoolean());
        // Delegate the mocked service call to real logic to trigger arrow generation
        doAnswer(inv -> new LoadToJsonService(loadRepository, nasFileService, antaresDataManagerProperties)
                .getListArrowLoadFilesByAreaFromStudy(inv.getArgument(0)))
                .when(loadToJsonService).getListArrowLoadFilesByAreaFromStudy(any());

        // When
        studyGeneratorService.buildJsonForStudyGeneration(1);

        // Then
        verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), any(), any(), anyBoolean());
    }


    @Test
    void callGenerateStudyService_shouldThrowTechnicalExceptionOnRuntimeException() {
        // Arrange
        int studyId = 42;
        String url = "http://localhost/generate_study/?study_id=42";
        when(antaresDataManagerProperties.getGeneratorHostUrl()).thenReturn("http://localhost");
        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);

        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(url)).thenThrow(new RuntimeException("Connexion refusée"));

        // Act & Assert
        assertThatThrownBy(() -> studyGeneratorService.callGenerateStudyService(studyId)).isInstanceOf(TechnicalException.class).hasMessageContaining("Error while call Generate study from generator");
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeThermalsInAreas() throws Exception {
        // Given
        var areaEntity = AreaEntity.builder().name("FR").build();
        var areaConfig = AreaConfigEntity.builder()
                .area(areaEntity)
                .unsuppliedEnergyCost(3000.0)
                .spilledEnergyCost(0.0)
                .build();
        var areaTrajectory = TrajectoryEntity.builder().type("AREA").areaConfigEntities(List.of(areaConfig)).area("FR").build();

        var study = StudyEntity.builder().id(1).name("studyTest").trajectories(Set.of(areaTrajectory)).build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));

        var dto = ThermalClusterGenerationDto.builder().efficiency(100.0).build();
        var ref = ThermalClusterRef.builder().name("Gas1").build();
        when(thermalPropertiesAssemblerService.assembleForTrajectories(study)).thenReturn(Map.of(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", ref), dto));
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        // When
        studyGeneratorService.buildJsonForStudyGeneration(1);

        // Then
        var captorValue = captureGeneratedJson(1);
        var objectMapper = new ObjectMapper();

        Map<String, Object> jsonMap = objectMapper.readValue(captorValue, new TypeReference<>() {
        });
        Map<String, Object> studyMap = objectMapper.convertValue(jsonMap.get("studyTest"), new TypeReference<>() {
        });
        assertThat(studyMap).containsKey("enable_random_ts");
        Map<String, Object> areasMap = objectMapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });
        Map<String, Object> frArea = objectMapper.convertValue(areasMap.get("FR"), new TypeReference<>() {
        });

        assertThat(frArea).containsKey("thermals");

        Map<String, Object> thermals = objectMapper.convertValue(frArea.get("thermals"), new TypeReference<>() {
        });
        assertThat(thermals).containsKey("FR_Gas1");

        Map<String, Object> cluster = objectMapper.convertValue(thermals.get("FR_Gas1"), new TypeReference<>() {
        });
        assertThat(cluster).containsKeys("series", "fuel_cost", "co2_cost", "data", "modulation", "properties");

        Map<String, Object> properties = objectMapper.convertValue(cluster.get("properties"), new TypeReference<>() {
        });
        assertAll(() -> assertThat(properties).containsKey("efficiency"), () -> assertThat(properties).doesNotContainKey("enabled"), // will be set to default in antares craft
                () -> assertThat(properties).doesNotContainKey("nominal_capacity"));
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeThermalsDataCorrectly() throws Exception {
        // Given
        var areaEntity = AreaEntity.builder().name("FR").build();
        var areaConfig = AreaConfigEntity.builder()
                .area(areaEntity)
                .unsuppliedEnergyCost(3000.0)
                .spilledEnergyCost(0.0)
                .build();
        var areaTrajectory = TrajectoryEntity.builder().type("AREA").areaConfigEntities(List.of(areaConfig)).area("FR").build();

        var study = StudyEntity.builder().id(1).name("studyTest").trajectories(Set.of(areaTrajectory)).build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));

        var dto = ThermalClusterGenerationDto.builder()
                // PROPERTIES view fields
                .enabled(true).unitCount(5).nominalCapacity(150.0)

                // DATA view fields
                .foDuration(0.15).poDuration(0.20).npoMaxWinter(0.30).npoMaxSummer(0.25).nbUnit(3).foMonthlyRate(List.of(1.0, 2.0, 3.0)).poMonthlyRate(List.of(4.0, 5.0, 6.0)).build();

        var ref = ThermalClusterRef.builder().name("Gas1").build();

        when(thermalPropertiesAssemblerService.assembleForTrajectories(study)).thenReturn(Map.of(new ThermalPropertiesAssemblerService.AreaClusterRefKey("FR", ref), dto));
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        // When
        studyGeneratorService.buildJsonForStudyGeneration(1);

        // Then
        var jsonString = captureGeneratedJson(1);
        var mapper = new ObjectMapper();

        Map<String, Object> root = mapper.readValue(jsonString, new TypeReference<>() {
        });
        Map<String, Object> studyMap = mapper.convertValue(root.get("studyTest"), new TypeReference<>() {
        });
        Map<String, Object> areas = mapper.convertValue(studyMap.get("areas"), new TypeReference<>() {
        });
        Map<String, Object> fr = mapper.convertValue(areas.get("FR"), new TypeReference<>() {
        });

        assertThat(fr).containsKey("thermals");

        Map<String, Object> thermals = mapper.convertValue(fr.get("thermals"), new TypeReference<>() {
        });
        assertThat(thermals).containsKey("FR_Gas1");

        Map<String, Object> cluster = mapper.convertValue(thermals.get("FR_Gas1"), new TypeReference<>() {
        });

        // Structural keys must exist
        assertThat(cluster).containsKeys("series", "fuel_cost", "co2_cost", "modulation", "properties", "data");

        // --- PROPERTIES VIEW ---
        Map<String, Object> properties = mapper.convertValue(cluster.get("properties"), new TypeReference<>() {
        });

        assertThat(properties).containsKeys("enabled", "unit_count", "nominal_capacity").doesNotContainKeys("fo_duration", "po_duration", "po_monthly_rate", "fo_monthly_rate", "npo_max_winter", "npo_max_summer", "po_rate_default", "nb_unit", "forced_outage_monthly", "planned_outage_monthly");

        // --- DATA VIEW ---
        Map<String, Object> data = mapper.convertValue(cluster.get("data"), new TypeReference<>() {
        });

        assertThat(data).containsKeys("fo_duration", "po_duration", "npo_max_winter", "npo_max_summer", "nb_unit", "fo_monthly_rate", "po_monthly_rate").doesNotContainKeys("enabled", "unit_count", "nominal_capacity");

        // Check example values
        assertThat(data.get("fo_duration")).isEqualTo(0.15);
        assertThat(data.get("nb_unit")).isEqualTo(3);
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeStsInAreas() throws Exception {
        // Given: one area DE, and STS props for DE and another area to ensure filtering
        var deDto = StsGenerationDTO.builder()
                .enabled(true)
                .groupe("G1")
                .injection(10)
                .withdrawal(5.5)
                .storage(100.0)
                .efficiencyInjection(0.9)
                .efficiencyWithdrawal(80.0)
                .initialLevel(0.5)
                .initialLevelOptim(true)
                .build();
        var frDto = StsGenerationDTO.builder().enabled(false).groupe("IGN").build();
        Map<String, StsGenerationDTO> stsProps = new LinkedHashMap<>();
        stsProps.put("DE_Storage1", deDto);
        stsProps.put("FR_Ignore", frDto);
        when(stPropertiesAssemblerService.assembleStsProperties(any())).thenReturn(stsProps);
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        // When
        studyGeneratorService.buildJsonForStudyGeneration(1);

        // Then
        var json = captureGeneratedJson(1);
        var mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
        Map<String, Object> study = mapper.convertValue(root.get("studyTest"), new TypeReference<>() {});
        Map<String, Object> areas = mapper.convertValue(study.get("areas"), new TypeReference<>() {});
        Map<String, Object> de = mapper.convertValue(areas.get("DE"), new TypeReference<>() {});

        assertThat(de).containsKey("sts");
        Map<String, Object> sts = mapper.convertValue(de.get("sts"), new TypeReference<>() {});
        assertThat(sts)
                .containsKey("DE_Storage1")
                        .doesNotContainKey("FR_Ignore");

        Map<String, Object> cluster = mapper.convertValue(sts.get("DE_Storage1"), new TypeReference<>() {});
        assertThat(cluster).containsKey("properties");
        Map<String, Object> props = mapper.convertValue(cluster.get("properties"), new TypeReference<>() {});
        assertThat(props)
                .containsEntry("enabled", true)
                .containsEntry("group", "G1")
                .containsEntry("injection_nominal_capacity", 10)
                .containsEntry("withdrawal_nominal_capacity", 5.5)
                .containsEntry("reservoir_capacity", 100.0)
                .containsEntry("efficiency", 0.9)
                .containsEntry("efficiency_withdrawal", 80.0)
                .containsEntry("initial_level", 0.5)
                .containsEntry("initial_level_optim", true);

        // series placeholder must exist
        assertThat(cluster).containsKey("series");
    }

    @Test
    void buildJsonForStudyGeneration_shouldIncludeResInAreas() throws Exception {
        when(antaresDataManagerProperties.getStudyJsonOutputDirectory()).thenReturn("output");

        var cluster = new ResClusterGenerationDto(new ResClusterPropertiesDto(1200.0, "wind_offshore"), List.of("fr_wind.arrow"), null);
        when(resGenerationAssemblerService.assembleResProperties(any())).thenReturn(Map.of("DE", Map.of("wind_offshore", cluster)));

        studyGeneratorService.buildJsonForStudyGeneration(1);

        var json = captureGeneratedJson(1);
        var mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
        Map<String, Object> study = mapper.convertValue(root.get("studyTest"), new TypeReference<>() {});
        Map<String, Object> areas = mapper.convertValue(study.get("areas"), new TypeReference<>() {});
        Map<String, Object> deArea = mapper.convertValue(areas.get("DE"), new TypeReference<>() {});
        Map<String, Object> res = mapper.convertValue(deArea.get("res"), new TypeReference<>() {});

        assertThat(res).containsKey("wind_offshore");
    }

    @Test
    void buildJsonForStudyGeneration_shouldThrowTechnicalExceptionWhenStudyNotFound() {
        when(studyRepository.findById(777)).thenReturn(Optional.empty());

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> studyGeneratorService.buildJsonForStudyGeneration(777));

        assertTrue(exception.getMessage().contains("Study not found"));
    }

    @Test
    void buildJsonForStudyGeneration_shouldThrowBusinessExceptionWhenNoTrajectories() {
        StudyEntity emptyStudy = StudyEntity.builder().id(12).name("emptyStudy").trajectories(Collections.emptySet()).build();
        when(studyRepository.findById(12)).thenReturn(Optional.of(emptyStudy));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> studyGeneratorService.buildJsonForStudyGeneration(12));

        assertTrue(exception.getMessage().contains("No trajectories found"));
    }

    @Test
    void buildJsonForStudyGeneration_shouldThrowTechnicalExceptionForUnknownTrajectoryType() {
        StudyEntity study = StudyEntity.builder().id(13).name("studyUnknown").build();
        TrajectoryEntity unknownTrajectory = TrajectoryEntity.builder().type("UNKNOWN").fileName("unknown").build();
        study.setTrajectories(new LinkedHashSet<>(List.of(unknownTrajectory)));
        when(studyRepository.findById(13)).thenReturn(Optional.of(study));

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> studyGeneratorService.buildJsonForStudyGeneration(13));

        assertTrue(exception.getMessage().contains("Unhandled trajectory"));
    }

    @Test
    void callGenerateStudyService_shouldSucceedWhenGeneratorReturnsOk() {
        int studyId = 42;
        String url = "http://localhost/generate_study/?study_id=42";

        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);

        when(antaresDataManagerProperties.getGeneratorHostUrl()).thenReturn("http://localhost");
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(url)).thenReturn(bodySpec);
        when(bodySpec.exchangeToMono(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<ClientResponse, Mono<String>> handler = invocation.getArgument(0);
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.OK);
            when(response.bodyToMono(String.class)).thenReturn(Mono.just("ok"));
            return handler.apply(response);
        });

        assertDoesNotThrow(() -> studyGeneratorService.callGenerateStudyService(studyId));
    }

    @Test
    void callGenerateStudyService_shouldThrowTechnicalExceptionWhenGeneratorReturnsNonOk() {
        int studyId = 55;
        String url = "http://localhost/generate_study/?study_id=55";

        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);

        when(antaresDataManagerProperties.getGeneratorHostUrl()).thenReturn("http://localhost");
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(url)).thenReturn(bodySpec);
        when(bodySpec.exchangeToMono(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<ClientResponse, Mono<String>> handler = invocation.getArgument(0);
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);
            when(response.bodyToMono(String.class)).thenReturn(Mono.just("{\"detail\":\"Internal Error: bad payload\"}"));
            return handler.apply(response);
        });

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> studyGeneratorService.callGenerateStudyService(studyId));

        assertTrue(exception.getMessage().contains("bad payload"));
        assertFalse(exception.getMessage().contains("{\"detail\""));
    }

    @Test
    void callGenerateStudyService_shouldRethrowTechnicalExceptionWithoutWrapping() {
        int studyId = 66;
        String url = "http://localhost/generate_study/?study_id=66";

        TechnicalException technicalException = TechnicalException.builder().message("generator down").build();
        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);

        when(antaresDataManagerProperties.getGeneratorHostUrl()).thenReturn("http://localhost");
        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(url)).thenThrow(technicalException);

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> studyGeneratorService.callGenerateStudyService(studyId));

        assertEquals("generator down", exception.getMessage());
    }
}
