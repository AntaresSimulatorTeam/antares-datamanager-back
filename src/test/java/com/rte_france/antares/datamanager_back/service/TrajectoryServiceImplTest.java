package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.area_link.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.area_link.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicServiceImpl.SHEET_CO2;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicServiceImpl.SHEET_ENR;
import static com.rte_france.antares.datamanager_back.util.Utils.OTHERS_AREA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrajectoryServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;
    @Mock
    private AreaConfigRepository areaConfigRepository;
    @Mock
    private LinkRepository linkRepository;
    @Mock
    private StStorageRepository stStorageRepository;
    @Mock
    private AreaFileProcessorService areaFileProcessorService;
    @Mock
    private LinkFileProcessorService linkFileProcessorService;
    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;
    @Mock
    private ThermalFileProcessorService thermalFileProcessorService;
    @Mock
    private ThermalParamModulationService thermalParamModulationService;
    @Mock
    private ThermalControlService thermalControlService;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyTrajectoryRepository studyTrajectoryRepository;
    @Mock
    private WarningRepository warningRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;
    @Mock
    private NasFileService nasFileService;
    @Mock
    private LoadFileProcessorServiceImpl loadFileProcessorService;
    @Mock
    private ThermalSpecificFileProcessorService thermalSpecificProcessorService;
    @Mock
    private LoadRepository loadRepository;
    @Mock
    private ThermalEconomicService thermalEconomicService;

    @Mock
    private MiscClusterCapacityRepository miscClusterCapacityRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");
    }


    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTYpeIsAREA() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/area/testFile.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getAreaDirectory()).thenReturn("/areas");

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile", "2023-2024", 1);

        verify(areaFileProcessorService, times(1)).processAreaFile(any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getLinkDirectory()).thenReturn("/links");

        trajectoryService.processTrajectory(TrajectoryType.LINK, "links_BP23_A_ref", "2023-2024", 1);

        verify(linkFileProcessorService, times(1)).processLinkFile(any(), any(), any());
    }

    @Test
    void processThermalCapacityTrajectory_returnsEntityWhenValidDataProvided() throws IOException {
        String trajectoryToUse = "thermal_BE_PEMMDB23_26avril";
        String horizon = "2023-2024";
        Integer studyId = 1;
        boolean isCivilYear = true;
        String area = "BE";
        String technology = "CCGT";

        Path mockPath = mock(Path.class);
        List<ThermalClusterCapacityEntity> mockEntities = List.of(new ThermalClusterCapacityEntity());
        ThermalClusterCapacityDto thermalClusterCapacityDto = ThermalClusterCapacityDto.builder().thermalClusterCapacities(mockEntities).build();

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(mockPath).when(spyService).getTrajectoryFilePath(TrajectoryType.THERMAL_CAPACITY, trajectoryToUse, area);

        when(thermalFileProcessorService.buildThermalClusterCapacityValuesList(mockPath, horizon, isCivilYear, area, technology, studyId)).thenReturn(thermalClusterCapacityDto);
        when(thermalFileProcessorService.processThermalCapacityFile(mockPath, horizon, thermalClusterCapacityDto, TrajectoryType.THERMAL_CAPACITY, area, technology))
                .thenReturn(new TrajectoryEntity());

        TrajectoryEntity result = spyService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear, area, technology);

        assertNotNull(result);
    }

    @Test
    void processThermalCapacityTrajectory_throwsExceptionWhenThermalClusterCapacityListIsEmpty() throws IOException {
        String trajectoryToUse = "thermal_BE_PEMMDB23_26avril";
        String horizon = "2023-2024";
        Integer studyId = 1;
        boolean isCivilYear = true;
        String area = "BE";
        String technology = "CCGT";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getThermalCapacityDirectory()).thenReturn("thermal_capacity");
        when(thermalFileProcessorService.buildThermalClusterCapacityValuesList(
                any(Path.class), eq(horizon), eq(isCivilYear), eq(area), eq(technology), eq(studyId))
        ).thenReturn(ThermalClusterCapacityDto.builder().thermalClusterCapacities(Collections.emptyList()).build());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear, area, technology));

        assertEquals("No valid thermal cluster capacity found in the trajectory {0} for area: {1} and horizon: {2}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse, area, horizon), exception.getErrorMessageArguments());
    }

    @Test
    void throwsExceptionWhenTrajectoryToUseIsNull() {
        String trajectoryToUse = null;
        String horizon = "2023-2024";
        Integer studyId = 1;
        boolean isCivilYear = true;
        String area = "BE";
        String technology = "CCGT";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalCapacityTrajectory(trajectoryToUse, horizon, studyId, isCivilYear, area, technology));

        assertEquals("The trajectory file name must start with 'thermal_'", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void findTrajectoriesByTypeAndFileNameContainsFromDB_returnsEntitiesWhenExist() {
        List<TrajectoryEntity> expectedEntities = List.of(new TrajectoryEntity());
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(TrajectoryType.AREA.name(), "2023-2024", "fileNameStartsWith", "FR", "tech")).thenReturn(expectedEntities);

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType.AREA, "2023-2024", "fileNameStartsWith", "FR", "tech");

        assertEquals(expectedEntities, result);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameContainsFromDB_returnsEmptyWhenDoNotExist() {
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(TrajectoryType.AREA.name(), "2023-2024", "nonExistentFileNameStartsWith", "FR", "tech")).thenReturn(List.of());

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType.AREA, "2023-2024", "nonExistentFileNameStartsWith", "FR", "tech");

        assertEquals(List.of(), result);
    }

    @Test
    void linkTrajectoryToStudy_linksTrajectoryWhenStudyAndTrajectoryExist() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name())
                .areaConfigEntities(List.of(AreaConfigEntity.builder().area(AreaEntity.builder().name("are1").build()).build()))
                .warningMessages(new HashSet<>()) // <-- Ajouté pour éviter le NPE
                .build();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type);

        assertEquals(trajectory.getId(), result.getId());
        verify(studyTrajectoryRepository, times(1)).save(any());
    }

    @Test
    void linkTrajectoryToStudy_throwsExceptionWhenStudyNotFound() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        when(studyRepository.findById(studyId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type));
    }

    @Test
    void linkTrajectoryToStudy_throwsExceptionWhenTrajectoryNotFound() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        StudyEntity study = StudyEntity.builder().id(studyId).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type));
    }

    @Test
    void linkTrajectoryToStudy_replacesExistingLinkWhenSameTypeExists() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;


        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();

        StudyTrajectoryEntity existingLink = StudyTrajectoryEntity.builder().trajectory(trajectory).build();

        StudyEntity study = StudyEntity.builder().id(studyId).build();
        study.setStudyTrajectoryEntities(Set.of(existingLink));

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name())
                .warningMessages(new HashSet<>())
                .areaConfigEntities(List.of(AreaConfigEntity.builder().area(AreaEntity.builder().name("are1").build()).build()))
                .build();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(newTrajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type);

        assertEquals(newTrajectory.getId(), result.getId());
        verify(studyTrajectoryRepository, times(1)).delete(existingLink);
        verify(studyTrajectoryRepository, times(1)).save(any());
    }

    @Test
    void linkTrajectoryToStudy_doesNotFindExistingLinkWhenTypeIsLOAD() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.LOAD;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));

        Optional<StudyTrajectoryEntity> existingLink = study.getStudyTrajectoryEntities().stream()
                .filter(studyTrajectory -> studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                .findFirst();

        assertTrue(existingLink.isEmpty());
    }

    @Test
    void linkTrajectoryToStudy_findsExistingLinkWhenTypeIsNotLOAD() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();
        StudyTrajectoryEntity existingLink = StudyTrajectoryEntity.builder().trajectory(trajectory).build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Set.of(existingLink)).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));

        Optional<StudyTrajectoryEntity> foundLink = study.getStudyTrajectoryEntities().stream()
                .filter(studyTrajectory -> studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                .findFirst();

        assertTrue(foundLink.isPresent());
        assertEquals(existingLink, foundLink.get());
    }

    @Test
    void unlinkTrajectoryFromStudy_unlinksWhenLinkExists() {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyTrajectoryKey key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();
        StudyTrajectoryEntity entity = StudyTrajectoryEntity.builder().id(key).build();

        // When
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type("LINK").build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(entity));

        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);

        // Then
        verify(studyTrajectoryRepository, times(1)).delete(entity);
    }


    @Test
    void unlinkTrajectoryFromStudy_throwsConflictWhenAreaAndOtherTrajectoriesLinked() {
        // Given
        var trajectoryId = 1;
        var studyId = 1;

        // When
        var areaTrajectory = TrajectoryEntity.builder().id(trajectoryId).type("AREA").build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(areaTrajectory));

        var otherTrajectory = TrajectoryEntity.builder().id(2).type("LINK").build();
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(areaTrajectory, otherTrajectory));

        // Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Other trajectories are linked"));
    }

    @Test
    void unlinkTrajectoryFromStudy_unlinksWhenAreaAndNoOtherTrajectoriesLinked() {
        // Given
        var trajectoryId = 1;
        var studyId = 1;
        var key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();
        var entity = StudyTrajectoryEntity.builder().id(key).build();

        // When
        var areaTrajectory = TrajectoryEntity.builder().id(trajectoryId).type("AREA").build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(areaTrajectory));
        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(entity));

        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);

        // Then
        verify(studyTrajectoryRepository, times(1)).delete(entity);
    }

    @Test
    void unlinkTrajectoryFromStudy_throwsExceptionWhenLinkDoesNotExist() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyTrajectoryKey key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();

        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId));
    }

    @Test
    void processTrajectory_throwsExceptionWhenTrajectoryTypeIsUnsupported() {
        var path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/unsupported/testFile.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");

        assertThrows(TechnicalException.class, () -> trajectoryService.processTrajectory(TrajectoryType.UNKNOWN, "testFile", "2023-2024", 1));
    }

    @Test
    void getTrajectoryDataByTypeAndId_ShouldReturnAreaData_WhenTypeIsAREA() {
        Object[] mockedAreaConfigData = {"Germany", "true", "false"};

        when(areaConfigRepository.findAreaConfigByTrajectoryId(any())).thenReturn(Collections.singletonList(mockedAreaConfigData));

        List<TrajectoryDataDTO> result = trajectoryService.getTrajectoryDataByTypeAndId(TrajectoryType.AREA, 1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.getFirst().toString().contains("Germany"));

    }

    @Test
    void getTrajectoryDataByTypeAndId_ShouldReturnLinkData_WhenTypeIsLINK() {
        LinkEntity mockLinkEntity = LinkEntity.builder().name("DE-SU").build();

        when(linkRepository.findLinkEntitiesByTrajectoryIdIs(any())).thenReturn(Collections.singletonList(mockLinkEntity));


        List<TrajectoryDataDTO> result = (List<TrajectoryDataDTO>) trajectoryService.getTrajectoryDataByTypeAndId(TrajectoryType.LINK, 1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.getFirst().toString().contains("DE-SU"));

    }

    @Test
    void getTrajectoryDataByTypeAndId_returnAreaDTOForSTSType() throws Exception {
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setId(10);
        StStorageEntity ststorageEntity = StStorageEntity.builder()
                .area("AT")
                .name("battery_residential")
                .groupe("battery")
                .series(true)
                .trajectory(trajectoryEntity)
                .build();

        when(stStorageRepository.findStStorageEntitiesByTrajectoryId(10)).thenReturn(List.of(ststorageEntity));

        List<TrajectoryDataDTO> result = trajectoryService.getTrajectoryDataByTypeAndId(TrajectoryType.STS, 10);
        assertEquals(1, result.size());
        assertTrue(result.getFirst().toString().contains("AT - battery - battery_residential"));
        assertTrue(result.getFirst().toString().contains("TRUE"));
    }

    @Test
    void getTrajectoryDataByTypeAndId_ShouldThrowException_WhenTypeIsUnsupported() {

        TrajectoryType unsupportedType = TrajectoryType.LOAD;


        TechnicalException exception = assertThrows(
                TechnicalException.class,
                () -> trajectoryService.getTrajectoryDataByTypeAndId(unsupportedType, 1)
        );


        assertEquals("TrajectoryType {0} is not supported.", exception.getMessage());
        assertEquals(Collections.singletonList("LOAD"), exception.getErrorMessageArguments());
    }


    @Test
    void checkLinkAreaCoherence_whenTrajectoryTypeIsLink() throws IOException {
        Integer studyId = 1;
        String userNni = "me0000";
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setType(TrajectoryType.LINK.name());
        trajectory.setLinkEntities(List.of(LinkEntity.builder().name("CH-IT").build()));
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        when(linkFileProcessorService.findListArea(studyId)).thenReturn(List.of("FR", "CH", "IT"));

        trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, userNni);

        verify(linkFileProcessorService, times(1)).checkConsistencyTrajectoryLinkAndArea(any(), any(), any(), any(), any(), any(), any());
        verify(warningRepository, times(1)).saveAll(warningMessages);
    }

    @Test
    void verifyClustersInCommonAndSpecificParamTrajectory_shouldCallVerificationMethodsForThermalCapacity() throws IOException {
        Integer studyId = 1;
        String horizon = "2023-2024";
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = List.of(ThermalClusterCapacityEntity.builder()
                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterX").thermalTechnology(ThermalTechnology.builder().name("GAS").build()).build())
                .build());

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .horizon(horizon)
                .thermalClusterCapacities(thermalClusterCapacities)
                .thermalEconomicCo2s(List.of(ThermalEconomicCo2Entity.builder().fuel("Gas").build()))
                .thermalCosts(List.of(ThermalCostEntity.builder().thermalType(ThermalCostTypeEntity.builder().fuel("GAS").build()).build()))
                .build();

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(thermalControlService, times(1)).verifyClustersInCommonParamTrajectory(studyId, horizon, thermalClusterCapacities);
        verify(thermalControlService, times(1)).verifyClustersInSpecificParamTrajectory(studyId, horizon, thermalClusterCapacities);
    }

    @Test
    void checkMissingClustersForCommonParam_shouldCallCheckMissingClusters() throws IOException {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> clusterRefs = Set.of("Cluster1", "Cluster2");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER.name())
                .horizon(horizon)
                .thermalCommonParameters(List.of(
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("Cluster1").build())
                                .build(),
                        ThermalCommonParameterEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("Cluster2").build())
                                .build()
                ))
                .build();

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(thermalControlService, times(1)).checkMissingClusters(studyId, horizon, clusterRefs, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null);
    }

    @Test
    void checkMissingClustersForSpecificParam_shouldCallCheckMissingClusters() throws IOException {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> clusterRefs = Set.of("ClusterA/", "ClusterB/");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .area(OTHERS_AREA)
                .type(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name())
                .horizon(horizon)
                .thermalSpecificParameters(List.of(
                        ThermalSpecificParametersEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterA").build())
                                .build(),
                        ThermalSpecificParametersEntity.builder()
                                .thermalClusterRef(ThermalClusterRef.builder().name("ClusterB").build())
                                .build()
                ))
                .build();

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(thermalControlService, times(1)).checkMissingClusters(studyId, horizon, clusterRefs, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, OTHERS_AREA);
    }

    @Test
    void checkTrajectory() throws IOException {
        Integer studyId = 1;
        String userNni = "me0000";

        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setType(TrajectoryType.AREA.name());
        trajectory.setAreaConfigEntities(List.of(AreaConfigEntity.builder().area(AreaEntity.builder().name("FR").build()).build(),
                AreaConfigEntity.builder().area(AreaEntity.builder().name("CH").build()).build(),
                AreaConfigEntity.builder().area(AreaEntity.builder().name("IT").build()).build()
        ));
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        when(linkFileProcessorService.findListLink(studyId)).thenReturn(List.of(LinkEntity.builder().name("FR-CH").build(), LinkEntity.builder().name("FR-IT").build()));

        trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, userNni);

        verify(linkFileProcessorService, times(1)).validateLinkAreas("FR-CH", List.of("FR", "CH", "IT"));
        verify(linkFileProcessorService, times(1)).validateLinkAreas("FR-IT", List.of("FR", "CH", "IT"));
        verify(linkFileProcessorService, times(1)).checkConsistencyTrajectoryLinkAndArea(any(), any(), any(), any(), any(), any(), any());
        verify(warningRepository, times(1)).saveAll(warningMessages);
    }


    @Test
    void findTrajectoriesByType_returnsFilesStartingByAreas_(@TempDir Path tempDir) throws IOException {
        // Given
        Path areaDir = tempDir.resolve("area");
        Files.createDirectories(areaDir);

        Path testFile = areaDir.resolve("areas_test1.xlsx");
        Files.createFile(testFile);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getAreaDirectory()).thenReturn("area");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("areas_test1.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsFilesStartingByCosts_(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("thermal/economic_parameter/costs");
        Files.createDirectories(thermalDir);

        Path testFile = thermalDir.resolve("costs_test1.xlsx");
        Files.createFile(testFile);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getThermalCostDirectory()).thenReturn("thermal/economic_parameter/costs");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("costs_test1.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsFilesStartingByClusterAndTechnology(@TempDir Path tempDir) throws IOException {
        // Given
        String technology = "Battery";
        Path thermalDir = tempDir.resolve("STS/" + technology + "/clusters");
        Files.createDirectories(thermalDir);

        Path testFile = thermalDir.resolve("cluster_battery_trajectorysts.xlsx");
        Files.createFile(testFile);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("STS");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.STS, null, technology, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("cluster_battery_trajectorysts.xlsx", result.getFirst().getFileName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clusterbattery_trajectorysts.xlsx",
            "_battery_trajectorysts.xlsx",
            "battery_trajectorysts.xlsx",
            "_trajectorysts.xlsx",
    })
    void findTrajectoriesByType_notReturnFilesWithWrongPrefix(String fileName, @TempDir Path tempDir) throws IOException {

        // Given
        String technology = "Battery";
        Path thermalDir = tempDir.resolve("STS/" + technology + "/clusters");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve(fileName));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("STS");

        // When
        List<FsTrajectoryDTO> result =
                trajectoryService.findTrajectoriesByType(TrajectoryType.STS, null,technology, null);

        // Then
        assertEquals(0, result.size());
    }

    @Test
    void findTrajectoriesByType_returnsRESFilesContainingTechnologyForFR(@TempDir Path tempDir) throws IOException {
        // Given
        String technology = "onshore";
        Path thermalDir = tempDir.resolve("RES/installed power/FR/BP_23_FR/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("installedRES_offshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("installedRES_onshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("installedRES_onshore_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResCapacityDirectory()).thenReturn("RES/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_CAPACITY, "FR", technology, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("installedRES_onshore_BP23_Aref.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsRESFilesContainingTechnologyForOther(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/installed power/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("installedRES_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("onshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("other_installedRES_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResCapacityDirectory()).thenReturn("RES/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_CAPACITY, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("installedRES_BP23_Aref.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsThermalCapacityFilesForfR(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("thermal/installed power/FR/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("thermal_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("other_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("thermal_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getThermalCapacityDirectory()).thenReturn("thermal/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_CAPACITY, "FR", null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("thermal_BP23_Aref.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsThermalEconomicFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("thermal_economic/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("economic_param_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("other_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("param_economic_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getThermalEconomicDirectory()).thenReturn("thermal_economic/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_ECONOMIC_PARAMETER, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("economic_param_BP23_A_ref.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsDSRFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("DSR/cluster/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("cluster_DSR_PEMMDB25.xlsx"));
        Files.createFile(thermalDir.resolve("other_DSR_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("DSR_cluster_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getDsrDirectory()).thenReturn("DSR/cluster/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.DSR, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("cluster_DSR_PEMMDB25.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsDSRModulationFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("DSR/capacity modulation/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("CM_BP25_A_ref_2031.xlsx"));
        Files.createFile(thermalDir.resolve("other_CM_BP25_A_ref_2031.xlsx"));
        Files.createFile(thermalDir.resolve("DSR_cluster_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getDsrCapacityDirectory()).thenReturn("DSR/capacity modulation/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.DSR_CAPACITY_MODULATION, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("CM_BP25_A_ref_2031.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsMiscCapacityFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("MISC/installed power/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("installedMisc_BP23_A_ref_FR_v2.xlsx"));
        Files.createFile(thermalDir.resolve("other_installedMisc_BP23_A_ref_FR_v2.xlsx"));
        Files.createFile(thermalDir.resolve("installedMisc_BP23_A_ref_FR_v2.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getMiscCapacityDirectory()).thenReturn("MISC/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.MISC_CAPACITY, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("installedMisc_BP23_A_ref_FR_v2.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsMiscLoadFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("MISC/load factor/BP23_A_Ref/biogas/biogas/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("load_factor_biogas_2030-2031.csv"));
        Files.createFile(thermalDir.resolve("load_factor_biogas_2030-2031.xlsx"));
        Files.createFile(thermalDir.resolve("other_load_factor_biogas_2030-2031.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getMiscLoadDirectory()).thenReturn("MISC/load factor/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.MISC_LOAD, "FR", null, null);

        // Then
        assertEquals("BP23_A_Ref", result.getFirst().getFileName());
    }

    @Test
    void processLoadTrajectory_savesTrajectoryAndProcessesLoadFiles() throws IOException {
        String area = "FR";
        String trajectoryToUse = "testTrajectory";
        String horizon = "2030-2031";
        Integer studyId = 1;

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .loadEntities(Set.of(LoadEntity.builder().fileName("load1").build()))
                .build();

        when(areaRepository.findAllByStudyId(any())).thenReturn(Collections.singletonList(AreaEntity.builder().name("FR").build()));
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("/INPUT");
        when(antaresDataManagerProperties.getLoadDirectory())
                .thenReturn(Paths.get("src/test/resources/load").toAbsolutePath().toString());

        when(trajectoryRepository.save(any())).thenReturn(mockTrajectory);
        when(areaRepository.findAreaByNameAndStudyId(area, studyId)).thenReturn(Optional.of(new AreaEntity()));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(loadRepository.findByFileNameAndTrajectoryFileName(anyString(), anyString()))
                .thenReturn(Optional.empty());
        TrajectoryEntity result = trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId);

        assertNotNull(result);
        assertEquals(1, result.getLoadEntities().size());
        assertNull(result.getLoadEntities().iterator().next().getOutPutFileName(),
                "Should be null because .arrow is generated later"
        );
        verify(nasFileService, never()).saveMatrixToNas(any(), any());
    }

    @Test
    void processLoadTrajectory_throwsExceptionWhenAreaNotFound() {
        String area = "invalidArea";
        String trajectoryToUse = "testTrajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;

        when(areaRepository.findAreaByNameAndStudyId(area, studyId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId));
    }

    @Test
    void processLoadTrajectory_throwsExceptionWhenIOExceptionOccurs() throws IOException {
        String area = "testArea";
        String trajectoryToUse = "testTrajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;

        TrajectoryEntity mockTrajectory = TrajectoryEntity.builder()
                .loadEntities(Set.of(LoadEntity.builder().fileName("load1").build()))
                .build();

        Path mockPath = mock(Path.class);
        when(mockPath.resolve(anyString())).thenReturn(mockPath);
        when(nasFileService.saveMatrixToNas(mockPath, "outputDir")).thenThrow(IOException.class);
        when(trajectoryRepository.save(any())).thenReturn(mockTrajectory);
        when(areaRepository.findAreaByNameAndStudyId(area, studyId)).thenReturn(Optional.of(new AreaEntity()));

        assertThrows(RuntimeException.class, () -> trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId));
    }

    @Test
    void processLoadTrajectory_shouldThrowBusinessExceptionWhenAllLoadFilesMissing() {
        String area = "OTHERS";
        String trajectoryToUse = "testTrajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;

        when(areaRepository.findAllByStudyId(studyId)).thenReturn(List.of(
                AreaEntity.builder().name("AREA1").build(),
                AreaEntity.builder().name("AREA2").build()
        ));

        when(areaRepository.findAreaByNameAndStudyId(area, studyId)).thenReturn(Optional.of(new AreaEntity()));

        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("src/test/resources/load");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");

        doThrow(BusinessException.class).when(loadFileProcessorService)
                .checkForMissingLoadFiles(any(), any(), any(), any(), any());

        assertThrows(BusinessException.class, () ->
                trajectoryService.processLoadTrajectory(area, trajectoryToUse, horizon, studyId));
    }

    @Test
    void shouldCallCheckForMissingLoadFilesWhenOtherArea() {
        String horizon = "2023-2024";
        String trajectoryToUse = "testTrajectory";
        Integer studyId = 1;
        String userNni = "testUser";
        Path trajectoryPath = Path.of("/tmp/testTrajectory");
        TrajectoryEntity newTrajectory = TrajectoryEntity.builder().fileName(trajectoryToUse).build();

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("");
        when(areaRepository.findAllByStudyId(studyId)).thenReturn(List.of());
        when(loadFileProcessorService.checkForMissingLoadFiles(any(), any(), any(), any(), any()))
                .thenReturn(Set.of());

        Set<WarningMessageEntity> warningMessageEntities = loadFileProcessorService
                .checkForMissingLoadFiles(trajectoryPath, horizon, studyId, userNni, newTrajectory);

        verify(loadFileProcessorService, times(1))
                .checkForMissingLoadFiles(trajectoryPath, horizon, studyId, userNni, newTrajectory);
        assertNotNull(warningMessageEntities);
    }

    @Test
    void findTrajectoriesByTypeAndStudyId_returnsSortedWarningsByAckAndDate() {
        Integer studyId = 1;
        String trajectoryType = "AREA";

        WarningMessageEntity warning1 = WarningMessageEntity.builder()
                .isAck(false)
                .creationDate(LocalDateTime.of(2023, 10, 1, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .secondTrajectory(TrajectoryEntity.builder().id(1).build())
                .build();

        WarningMessageEntity warning2 = WarningMessageEntity.builder()
                .isAck(true)
                .creationDate(LocalDateTime.of(2023, 10, 2, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .secondTrajectory(TrajectoryEntity.builder().id(1).build())
                .build();

        WarningMessageEntity warning3 = WarningMessageEntity.builder()
                .isAck(false)
                .creationDate(LocalDateTime.of(2023, 10, 3, 10, 0))
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .study(StudyEntity.builder().id(studyId).build())
                .secondTrajectory(TrajectoryEntity.builder().id(1).build())
                .build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(trajectoryType)
                .warningMessages(Set.of(warning1, warning2, warning3))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId)).thenReturn(List.of(trajectory));
        when(studyTrajectoryRepository.findById(any())).thenReturn(Optional.of(StudyTrajectoryEntity.builder().build()));

        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndStudyId(trajectoryType, studyId);

        assertEquals(1, result.size());

    }


    @Test
    void findTrajectoriesByTypeAndStudyId_filtersWarningsByStudyId() {
        Integer studyId = 1;
        String trajectoryType = "LINK";

        WarningMessageEntity warning1 = WarningMessageEntity.builder()
                .isAck(true)
                .creationDate(LocalDateTime.of(2023, 10, 1, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .secondTrajectory(TrajectoryEntity.builder().id(1).build())
                .build();

        WarningMessageEntity warning2 = WarningMessageEntity.builder()
                .isAck(false)
                .creationDate(LocalDateTime.of(2023, 10, 2, 10, 0))
                .study(StudyEntity.builder().id(2).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .secondTrajectory(TrajectoryEntity.builder().id(1).build())
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(trajectoryType)
                .warningMessages(Set.of(warning1, warning2))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId)).thenReturn(List.of(trajectory));
        when(studyTrajectoryRepository.findById(any())).thenReturn(Optional.of(StudyTrajectoryEntity.builder().build()));
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndStudyId(trajectoryType, studyId);

        assertEquals(1, result.size());

    }

    @Test
    void checkLinkCoherence_whenAreasAreMissing_shouldThrowBusinessException() {
        //Given
        Integer studyId = 1;
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>();
        String userNni = "testUser";

        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<LinkEntity> links = Arrays.asList(
                LinkEntity.builder().trajectory(trajectory).name("BE-FR").build(),
                LinkEntity.builder().trajectory(trajectory).name("RE-ZE").build()
        );
        trajectory.setLinkEntities(links);

        List<String> savedAreas = Arrays.asList("BE", "ZE");
        when(linkFileProcessorService.findListArea(studyId)).thenReturn(savedAreas);

        //When
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> trajectoryService.checkLinkCoherence(studyId, warningMessageEntities, trajectory, userNni)
        );

        //Then
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Areas {0} in LINKS file is not present in AREA trajectory", exception.getMessage());
        assertEquals(List.of("RE, FR"), exception.getErrorMessageArguments());
    }

    @Test
    void countWarningMessage() {
        Integer studyId = 1;
        String trajectoryTypeAREA = "AREA";

        WarningMessageEntity warning1 = WarningMessageEntity.builder()
                .isAck(true)
                .creationDate(LocalDateTime.of(2023, 10, 1, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .trajectory(TrajectoryEntity.builder().id(1).build())
                .secondTrajectory(null)
                .build();

        WarningMessageEntity warning2 = WarningMessageEntity.builder()
                .isAck(false)
                .creationDate(LocalDateTime.of(2023, 10, 2, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .trajectory(TrajectoryEntity.builder().id(1).build())
                .secondTrajectory(null)
                .build();

        TrajectoryEntity trajectoryOne = TrajectoryEntity.builder()
                .id(1)
                .type(trajectoryTypeAREA)
                .warningMessages(Set.of(warning1, warning2))
                .build();

        String trajectoryTypeLINK = "LINK";

        WarningMessageEntity warning3 = WarningMessageEntity.builder()
                .isAck(false)
                .creationDate(LocalDateTime.of(2023, 10, 2, 10, 0))
                .study(StudyEntity.builder().id(studyId).build())
                .warningLevel(WarningLevel.WARNING_LEVEL)
                .warningCode(WarningCode.DATA_NOT_FOUND)
                .trajectory(TrajectoryEntity.builder().id(1).build())
                .secondTrajectory(null)
                .build();

        TrajectoryEntity trajectoryTwo = TrajectoryEntity.builder()
                .id(2)
                .type(trajectoryTypeLINK)
                .warningMessages(Set.of(warning3))
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(trajectoryOne, trajectoryTwo));

        Map<String, Integer> result = trajectoryService.countWarningMessage(studyId);

        assertTrue(result.containsKey(trajectoryTypeAREA));
        assertTrue(result.containsKey(trajectoryTypeLINK));
        assertEquals(2, result.get(trajectoryTypeAREA));
        assertEquals(1, result.get(trajectoryTypeLINK));

        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of());

        Map<String, Integer> resultEmpty = trajectoryService.countWarningMessage(studyId);

        assertTrue(resultEmpty.isEmpty());
    }

    @Test
    void saveLoadTrajectoriesInDb_shouldAddMissingAreasWhenSameLoadTrajectoryAndOtherArea(@TempDir Path tempDir) throws IOException {
        var area = "OTHERS";
        var trajectoryToUse = "testTrajectory";
        var horizon = "2030-2031";
        var studyId = 1;

        Path trajectoryPath = tempDir.resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        Files.createFile(trajectoryPath.resolve("load_fr_2030-2031.txt"));
        Files.createFile(trajectoryPath.resolve("load_de_2030-2031.txt"));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("");

        var area1 = AreaEntity.builder().name("FR").build();
        var area2 = AreaEntity.builder().name("DE").build();
        when(areaRepository.findAllByStudyId(studyId)).thenReturn(List.of(area1, area2));

        var existingLoad = LoadEntity.builder().fileName("load_fr_2030-2031.txt").build();
        var existingTrajectory = TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .loadEntities(new HashSet<>(Set.of(existingLoad)))
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndAreaOrderByVersionDesc(trajectoryToUse, horizon, area))
                .thenReturn(Optional.of(existingTrajectory));

        try (var mockedStatic = org.mockito.Mockito.mockStatic(
                com.rte_france.antares.datamanager_back.util.Utils.class)) {
            mockedStatic.when(() -> Utils.isSameTrajectory(any(), any())).thenReturn(true);
            mockedStatic.when(() -> Utils.getValidLoadFileNamesWithHorizon(
                    any(Path.class),
                    eq("OTHERS"),
                    eq("2030-2031"),
                    anyList(),
                    anyList()
            )).thenReturn(List.of("load_fr_2030-2031.txt", "load_de_2030-2031.txt"));


            var service = spy(trajectoryService);
            doReturn(trajectoryPath).when(service).buildTrajectoryPath(any(), any());

            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var result = service.saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId);

            assertNotNull(result);
            var fileNames = result.getLoadEntities().stream().map(LoadEntity::getFileName).collect(Collectors.toSet());
            assertTrue(fileNames.contains("load_fr_2030-2031.txt"));
            assertTrue(fileNames.contains("load_de_2030-2031.txt"));
        }
    }

    @Test
    void linkTrajectoryToStudy_shouldComputeMissingLoadFromDbAndSaveWarnings_whenLoadOtherArea() throws IOException {
        var trajectoryId = 1;
        var studyId = 2;
        var userNni = "user";
        var fileName = "traj";
        var horizon = "2023-2024";

        var study = StudyEntity.builder().id(studyId).build();
        var trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.LOAD.name())
                .area(TrajectoryServiceImpl.OTHER_AREA)
                .fileName(fileName)
                .horizon(horizon)
                .build();

        var warning1 = new WarningMessageEntity();
        var warning2 = new WarningMessageEntity();
        var warnings = new HashSet<>(List.of(warning1, warning2));

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyTrajectoryRepository.save(any()))
                .thenReturn(StudyTrajectoryEntity.builder().trajectory(trajectory).build());

        when(loadFileProcessorService.checkForMissingLoadByAreaFromDb(horizon, studyId, userNni, trajectory))
                .thenReturn(warnings);

        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.LOAD);

        assertTrue(warnings.stream().allMatch(w -> w.getTrajectory() == trajectory));
        verify(warningRepository).saveAll(warnings);
        verify(loadFileProcessorService).checkForMissingLoadByAreaFromDb(horizon, studyId, userNni, trajectory);
        verify(loadFileProcessorService, never()).checkForMissingLoadFiles(any(), any(), any(), any(), any());
    }

    @Test
    void getDirectoryByTrajectoryType_returnsLoadDirectory_whenTypeIsLoad() throws IOException {
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("loadDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.LOAD, null, null);
        assertEquals("loadDir", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsThermalCostDirectory_whenTypeIsThermalEconomicCostParameter() throws IOException {
        when(antaresDataManagerProperties.getThermalCostDirectory()).thenReturn("thermalCostDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, null, null);
        assertEquals("thermalCostDir", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsSTSDirectory_whenTypeIsSTS(@TempDir Path tempDir) throws IOException {
        // créer l'arborescence attendue : STS/DRS/clusters
        Path stsDir = tempDir.resolve("STS").resolve("DRS").resolve("clusters");
        Files.createDirectories(stsDir);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("STS");

        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.STS, null, "DRS");
        String expected = stsDir.toString();
        assertEquals(expected, result);
    }



    @Test
    void getDirectoryByTrajectoryType_returnsMiscCapacityDirector_whenTypeIsMisc() throws IOException {
        when(antaresDataManagerProperties.getMiscCapacityDirectory()).thenReturn("MISC/installed power");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.MISC_CAPACITY, null, null);
        assertEquals("MISC/installed power", result);

    }

    @Test
    void processThermalCommonParameterTrajectory_shouldReturnTrajectoryEntityWhenValidParameters() throws IOException {
        String trajectoryToUse = "thermal_common_parameters";
        String horizon = "2025";
        Integer studyId = 1;
        List<ThermalCommonParameterEntity> params = List.of(ThermalCommonParameterEntity.builder().id(1).build());

        when(thermalFileProcessorService.buildThermalCommonParameterValuesList(any(Path.class), eq(horizon), any())).thenReturn(params);
        when(thermalFileProcessorService.processThermalCommonParameterFile(any(Path.class), eq(horizon), eq(params), eq(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER)))
                .thenReturn(new TrajectoryEntity());

        TrajectoryEntity result = trajectoryService.processThermalCommonParameterTrajectory(trajectoryToUse, horizon, studyId);

        assertNotNull(result);
        verify(thermalFileProcessorService, times(1)).processThermalCommonParameterFile(any(Path.class), eq(horizon), eq(params), eq(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER));
    }


    @Test
    void processThermalCommonParameterTrajectory_shouldThrowBusinessExceptionWhenParamsAreEmpty() throws IOException {
        String trajectoryToUse = "thermal_common_parameters";
        String horizon = "2025";
        Integer studyId = 1;
        Path mockPath = mock(Path.class);

        when(thermalFileProcessorService.buildThermalCommonParameterValuesList(mockPath, horizon, 1)).thenReturn(Collections.emptyList());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalCommonParameterTrajectory(trajectoryToUse, horizon, studyId)
        );

        assertTrue(exception.getMessage().contains("No valid thermal common parameter found"));
        verify(thermalFileProcessorService, never()).processThermalCommonParameterFile(any(), any(), any(), any());
    }


    @Test
    void processThermalModulationParameterTrajectory_throwsExceptionWhenBothFilesAreMissing(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertEquals("No CM and MR trajectories found in trajectory {0} for horizon {1}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse, horizon), exception.getErrorMessageArguments());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processThermalModulationParameterTrajectory_throwsExceptionWhenCMFileIsMissing(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);

        String targetYear = horizon;
        String mrFileName = "MR_" + trajectoryToUse + "_" + targetYear + ".csv";
        Files.createFile(trajectoryPath.resolve(mrFileName));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertEquals("Missing Cost Modulation file in trajectory {0} for horizon {1}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse, horizon), exception.getErrorMessageArguments());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processThermalModulationParameterTrajectory_throwsExceptionWhenMRFileIsMissing(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);

        String targetYear = horizon;
        String mrFileName = "CM_" + trajectoryToUse + "_" + targetYear + ".csv";
        Files.createFile(trajectoryPath.resolve(mrFileName));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertEquals("Missing Must Run file in trajectory {0} for horizon {1}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse, horizon), exception.getErrorMessageArguments());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processThermalModulationParameterTrajectory_must_CheckCmFilesAndCreateTrajectory(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);

        Files.createFile(trajectoryPath.resolve("CM_modulation_trajectory_2025.csv"));
        Path csvCmPath = trajectoryPath.resolve("CM_modulation_trajectory_2025.csv");

        List<String> cMlines = Files.readAllLines(csvCmPath);
        cMlines.add(0, "DATE_HEURE;HEURE;FR_cluster1;FR_cluster2");
        Files.write(csvCmPath, cMlines);

        Files.createFile(trajectoryPath.resolve("MR_modulation_trajectory_2025.csv"));
        Path csvMrPath = trajectoryPath.resolve("MR_modulation_trajectory_2025.csv");
        List<String> mRlines = Files.readAllLines(csvMrPath);
        mRlines.add(0, "DATE_HEURE;HEURE;FR_cluster1;FR_cluster2");
        Files.write(csvMrPath, mRlines);


        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);

        when(thermalSpecificProcessorService.getListClusterByAreaForSpecificParam(any(), any(), eq(false))).thenReturn(Set.of("fr_cluster1", "fr_cluster2"));

        trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId);

        verify(thermalParamModulationService, times(1)).processThermalModulationParameterFile(any(), any(), any(), any());
    }

    @Test
    void processThermalEconomicParameterTrajectory_shouldThrowExceptionWhenCo2ParametersAreEmpty(@TempDir Path tempDir) throws IOException {

        String trajectoryToUse = "economic_trajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalEconomicDirectory()).thenReturn("economic");
        when(thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Création du fichier .xlsx minimal attendu par la méthode sous test
        generateExcelFile(tempDir, trajectoryToUse);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalEconomicParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertTrue(exception.getMessage().contains("No data in THERMAL Economic trajectory {0} in ener_content tab"));
    }

    @Test
    void processThermalEconomicParameterTrajectory_shouldThrowExceptionWhenEnerContentParametersAreEmpty(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "economic_trajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalEconomicDirectory()).thenReturn("economic");

        when(thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(any(), any(), any(), any()))
                .thenReturn(List.of(ThermalEconomicCo2Entity.builder().id(1).build()));
        when(thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Création du fichier .xlsx minimal attendu par la méthode sous test
        generateExcelFile(tempDir, trajectoryToUse);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalEconomicParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertTrue(exception.getMessage().contains("No data in THERMAL Economic trajectory {0} in ener_content tab "));
    }

    // java
    @Test
    void processThermalEconomicParameterTrajectory_shouldReturnTrajectoryEntityWhenValidParameters(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "economic_trajectory";
        String horizon = "2023-2024";
        Integer studyId = 1;
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalEconomicDirectory()).thenReturn("economic");

        // Création du fichier .xlsx minimal attendu par la méthode sous test
        generateExcelFile(tempDir, trajectoryToUse);

        when(thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(any(), any(), any(), any()))
                .thenReturn(List.of(new ThermalEconomicCo2Entity()));
        when(thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(any(), any(), any(), any()))
                .thenReturn(List.of(new ThermalEconomicEnerContentEntity()));
        when(thermalEconomicService.processThermalEconomicParameterFile(any(), any(), anyList(), anyList(), any()))
                .thenReturn(new TrajectoryEntity());

        TrajectoryEntity result = trajectoryService.processThermalEconomicParameterTrajectory(trajectoryToUse, horizon, studyId);

        assertNotNull(result);
    }

    private static void generateExcelFile(Path tempDir, String trajectoryToUse) throws IOException {
        Path economicDir = tempDir.resolve("economic");
        Files.createDirectories(economicDir);
        Path xlsxPath = economicDir.resolve(trajectoryToUse + ".xlsx");
        try (Workbook wb = new XSSFWorkbook();
             java.io.OutputStream os = Files.newOutputStream(xlsxPath)) {
            wb.createSheet(SHEET_CO2);
            wb.createSheet(SHEET_ENR);
            wb.write(os);
        }
    }

    @Test
    void checkTrajectoryCoherence_shouldCallVerifyThermalEconomicCostParameter() throws IOException {

        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER.name())
                .thermalCosts(List.of(ThermalCostEntity.builder().thermalType(ThermalCostTypeEntity.builder().fuel("gas").build()).build()))
                .build();

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "userNni");

    }

    // java
    @Test
    void verifyParamModulation_callsServiceForValidFiles() throws IOException {
        Integer studyId = 1;
        String horizon = "2025";
        String trajectoryFileName = "trajectory";
        String fileName = "CM_validFile.csv";

        Path basePath = Paths.get("/tmp/param_modulation", trajectoryFileName);
        Path expectedPath = basePath.resolve(fileName);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryFileName)
                .type(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.name())
                .horizon(horizon)
                .thermalModulationParameters(List.of(ThermalModulationParameterEntity.builder().tsName(fileName).build()))
                .build();

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(basePath).when(spyService).buildTrajectoryPath(trajectoryFileName, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        spyService.verifyParamModulation(studyId, trajectory);

        verify(thermalParamModulationService, times(1)).verifyExistingSpecificClustersOfParamModulation(
                eq(horizon), eq(studyId), eq(expectedPath), eq(trajectoryFileName), eq("CM"));
    }

    @Test
    void verifyParamModulation_throwsExceptionForInvalidFiles() throws IOException {
        Integer studyId = 1;
        String horizon = "2025";
        String trajectoryFileName = "trajectory";
        String fileName = "CM_invalidFile.csv";

        Path basePath = Paths.get("/tmp/param_modulation", trajectoryFileName);
        Path expectedPath = basePath.resolve(fileName);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryFileName)
                .horizon(horizon)
                .thermalModulationParameters(List.of(ThermalModulationParameterEntity.builder().tsName(fileName).build()))
                .build();

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(basePath).when(spyService).buildTrajectoryPath(trajectoryFileName, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        doThrow(new IOException("File not found")).when(thermalParamModulationService)
                .verifyExistingSpecificClustersOfParamModulation(any(), any(), any(), any(), any());

        TechnicalException exception = assertThrows(TechnicalException.class, () ->
                spyService.verifyParamModulation(studyId, trajectory));

        assertTrue(exception.getMessage().contains("could not verify param modulation trajectory"));
        verify(thermalParamModulationService, times(1)).verifyExistingSpecificClustersOfParamModulation(
                eq(horizon), eq(studyId), eq(expectedPath), eq(trajectoryFileName), eq("CM"));
    }
    @Test
    void verifyExistingEconomicSheet_throwsExceptionWhenBothSheetsAreMissing() {
        String trajectoryToUse = "trajectory_missing_sheets";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                TrajectoryServiceImpl.verifyExistingEconomicSheet(trajectoryToUse, null, null));

        assertEquals("Missing CO2_emissions /ener_content data in trajectory {0}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse), exception.getErrorMessageArguments());
    }

    @Test
    void verifyExistingEconomicSheet_throwsExceptionWhenCo2SheetIsMissing() {
        String trajectoryToUse = "trajectory_missing_co2";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                TrajectoryServiceImpl.verifyExistingEconomicSheet(trajectoryToUse, null, mock(Sheet.class)));

        assertEquals("Missing CO2_emissions data in trajectory {0}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse), exception.getErrorMessageArguments());
    }

    @Test
    void verifyExistingEconomicSheet_throwsExceptionWhenEnerContentSheetIsMissing() {
        String trajectoryToUse = "trajectory_missing_ener_content";

        BusinessException exception = assertThrows(BusinessException.class, () ->
                TrajectoryServiceImpl.verifyExistingEconomicSheet(trajectoryToUse, mock(Sheet.class), null));

        assertEquals("Missing ener_content data in trajectory {0}", exception.getMessage());
        assertEquals(List.of(trajectoryToUse), exception.getErrorMessageArguments());
    }

    @Test
    void verifyExistingEconomicSheet_doesNotThrowExceptionWhenBothSheetsArePresent() {
        String trajectoryToUse = "trajectory_valid";
        Sheet sheetCo2 = mock(Sheet.class);
        Sheet sheetEnr = mock(Sheet.class);

        assertDoesNotThrow(() ->
                TrajectoryServiceImpl.verifyExistingEconomicSheet(trajectoryToUse, sheetCo2, sheetEnr));
    }

    @Test
    void controlesMiscOnSelectInstalledPowerTrajectorySucceedsWhenLoadFactorCoversAllInstalledAreas(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;
        Integer trajectoryId = 10;

        GroupAreaMiscCapacity capacity1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        GroupAreaMiscCapacity capacity2 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area2";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(List.of(capacity1, capacity2));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of());

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(selectingTrajectory));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;area2\n1;2\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));

        assertDoesNotThrow(() -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
    }

    @Test
    void controlesMiscOnSelectInstalledPowerTrajectoryThrowsWhenLoadFactorMissingInstalledAreas(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;
        Integer trajectoryId = 10;

        GroupAreaMiscCapacity capacity1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        GroupAreaMiscCapacity capacity2 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area2";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(List.of(capacity1, capacity2));

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of());

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(selectingTrajectory));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;other\n1;2\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));

        BusinessException exception = assertThrows(BusinessException.class, () -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
        assertTrue(exception.getMessage().toLowerCase().contains("missing"));
    }

    @Test
    void controlesMiscOnSelectLoadFactorTrajectory_handlesEmptyCapacities() throws IOException {
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR"))
                .thenReturn(Collections.emptyList());

        when(trajectoryRepository.findById(1)).thenReturn(Optional.of(trajectory));

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);

        spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(miscClusterCapacityRepository, times(1)).findByStudyIdAndArea(studyId, "FR");
        verifyNoMoreInteractions(miscClusterCapacityRepository);
    }

    @Test
    void controlesMiscOnSelectLoadFactorTrajectory_mergesHeadersAndValidatesAreas() throws IOException {
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        List<GroupAreaMiscCapacity> capacities = List.of(
                new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} },
                new GroupAreaMiscCapacity() { public String getGroupe(){return "group2";} public String getArea(){return "area2";} public String getCluster(){return "cluster2";} }
        );

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR"))
                .thenReturn(capacities);

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName("file2")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                .thenReturn(List.of(existingTrajectory));

        when(trajectoryRepository.findById(1)).thenReturn(Optional.of(trajectory));

        Path root = Files.createTempDirectory("traj");
        Path dir1 = root.resolve("group1").resolve("cluster1");
        Path dir2 = root.resolve("group2").resolve("cluster2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);
        Files.writeString(dir1.resolve("load_factor_cluster1_2030-2031.csv"), "area1;area2\n1;2\n");
        Files.writeString(dir2.resolve("load_factor_cluster2_2030-2031.csv"), "area2\n1\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file2"), eq(TrajectoryType.MISC_LOAD));
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));

        spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(miscClusterCapacityRepository, times(1)).findByStudyIdAndArea(studyId, "FR");
        verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);
        verify(trajectoryRepository, times(1)).findById(1);
    }

    @Test
    void controlesMiscOnSelectLoadFactorTrajectory_ignoresDuplicateExistingTrajectories(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;
        Integer trajectoryId = 1;

        GroupAreaMiscCapacity capacity1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(List.of(capacity1));

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(selectingTrajectory));

        // two existing trajectories with same filename -> duplicate path
        TrajectoryEntity existing1 = TrajectoryEntity.builder().id(2).fileName("file2").horizon("2030-2031").type(TrajectoryType.MISC_LOAD.name()).area("FR").build();
        TrajectoryEntity existing2 = TrajectoryEntity.builder().id(3).fileName("file2").horizon("2030-2031").type(TrajectoryType.MISC_LOAD.name()).area("FR").build();
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existing1));
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, "2030-2031", TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(existing1, existing2));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        // current trajectory file
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1\n1\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file2"), eq(TrajectoryType.MISC_LOAD));

        // should not throw and merged header contains unique area1
        assertDoesNotThrow(() -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
    }

    @Test
    void controlesMiscOnSelectLoadFactorTrajectory_skipsMissingExistingTrajectoryFiles(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;
        Integer trajectoryId = 1;

        GroupAreaMiscCapacity capacity1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(List.of(capacity1));

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(selectingTrajectory));

        // existing trajectory present but file will be missing -> readHeaderAreas will throw BusinessException
        TrajectoryEntity existing = TrajectoryEntity.builder().id(2).fileName("file2").horizon("2030-2031").type(TrajectoryType.MISC_LOAD.name()).area("FR").build();
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existing));
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, "2030-2031", TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(existing));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        // create only current file
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1\n1\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));
        doReturn(root.resolve("nonexistent")).when(spyService).buildTrajectoryPath(eq("file2"), eq(TrajectoryType.MISC_LOAD));

        // expect BusinessException because missing existing file results in readHeaderAreas throwing
        assertThrows(BusinessException.class, () -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
    }

    @Test
    void controlesMiscOnSelectLoadFactorTrajectory_logsWarningOnExistingTrajectoryReadError(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;
        Integer trajectoryId = 1;

        GroupAreaMiscCapacity capacity1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(List.of(capacity1));

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("file1")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(selectingTrajectory));

        TrajectoryEntity existing = TrajectoryEntity.builder().id(2).fileName("file2").horizon("2030-2031").type(TrajectoryType.MISC_LOAD.name()).area("FR").build();
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existing));
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, "2030-2031", TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(existing));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        // create only current file
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1\n1\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        // buildTrajectoryPath for existing trajectory will throw to simulate read error
        doReturn(root).when(spyService).buildTrajectoryPath(eq("file1"), eq(TrajectoryType.MISC_LOAD));
        doThrow(new IOException("boom reading")) .when(spyService).buildTrajectoryPath(eq("file2"), eq(TrajectoryType.MISC_LOAD));

        // expect IOException because buildMergedLoadFactorHeaders does not swallow IOExceptions from buildTrajectoryPath
        assertThrows(IOException.class, () -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
    }

    @Test
    void controlesMiscOnSelectInstalledPowerTrajectorySucceedsWhenLoadFactorCoversAllInstalledAreas_select(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;

        // additional capacities returned when selecting installed power trajectory
        GroupAreaMiscCapacity add1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        GroupAreaMiscCapacity add2 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area2";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByTrajectoryId(10)).thenReturn(List.of(add1, add2));

        // one existing load factor trajectory present for the study
        TrajectoryEntity existingLf = TrajectoryEntity.builder()
                .id(2)
                .fileName("fileLF")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existingLf));

        // build temp files for readHeaderAreas
        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;area2\n1;2\n");

        // selecting trajectory (the one being selected is of type MISC_CAPACITY so checkTrajectoryCoherence will call the right private method)
        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(10)
                .fileName("installedFile")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_CAPACITY.name())
                .area("FR")
                .build();

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("fileLF"), eq(TrajectoryType.MISC_LOAD));

        assertDoesNotThrow(() -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
    }

    @Test
    void controlesMiscOnSelectInstalledPowerTrajectoryThrowsWhenLoadFactorMissingInstalledAreas_select(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;

        GroupAreaMiscCapacity add1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        GroupAreaMiscCapacity add2 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area2";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByTrajectoryId(10)).thenReturn(List.of(add1, add2));

        TrajectoryEntity existingLf = TrajectoryEntity.builder()
                .id(2)
                .fileName("fileLF")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existingLf));

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        // missing area2 in header
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;other\n1;2\n");

        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(10)
                .fileName("installedFile")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_CAPACITY.name())
                .area("FR")
                .build();

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("fileLF"), eq(TrajectoryType.MISC_LOAD));

        BusinessException exception = assertThrows(BusinessException.class, () -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));
        assertTrue(exception.getMessage().toLowerCase().contains("missing") || exception.getMessage().toLowerCase().contains("manqu"));
    }

    @Test
    void controlesMiscInstalledPowerReturnsWhenNoRelevantLoadFactorTrajectories() throws IOException {
        Integer studyId = 1;

        // additional capacities returned when selecting installed power trajectory
        GroupAreaMiscCapacity add1 = new GroupAreaMiscCapacity() { public String getGroupe(){return "group1";} public String getArea(){return "area1";} public String getCluster(){return "cluster1";} };
        when(miscClusterCapacityRepository.findByTrajectoryId(10)).thenReturn(List.of(add1));

        // existing load factor trajectory present but for a different area (neither the selected area nor OTHERS)
        TrajectoryEntity existingLf = TrajectoryEntity.builder()
                .id(2)
                .fileName("fileLF")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("DE")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existingLf));

        // selecting trajectory (the one being selected is of type MISC_CAPACITY so checkTrajectoryCoherence will call the right private method)
        TrajectoryEntity selectingTrajectory = TrajectoryEntity.builder()
                .id(10)
                .fileName("installedFile")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_CAPACITY.name())
                .area("FR")
                .build();

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);

        // Should return early (no exception) because after filtering by area there is no relevant load factor trajectory
        assertDoesNotThrow(() -> spyService.checkTrajectoryCoherence(studyId, new HashSet<>(), selectingTrajectory, "user"));

        verify(miscClusterCapacityRepository, times(1)).findByTrajectoryId(10);
        // ensure we did not call the study+area lookup because method should have returned early
        verify(miscClusterCapacityRepository, never()).findByStudyIdAndArea(anyInt(), anyString());
        verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);
    }

    @Test
    void controlesMiscOnImportInstalledPowerSucceedsWhenLoadFactorCoversAllInstalledAreas_import(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;

        // prepare imported MiscClusterCapacityEntity list
        MiscClusterCapacityEntity imported1 = MiscClusterCapacityEntity.builder().groupe("group1").area("area1").cluster("cluster1").build();
        MiscClusterCapacityEntity imported2 = MiscClusterCapacityEntity.builder().groupe("group1").area("area2").cluster("cluster1").build();

        // existing load factor trajectory
        TrajectoryEntity existingLf = TrajectoryEntity.builder()
                .id(2)
                .fileName("fileLF")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existingLf));

        // no previously stored installed capacity for study+area
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(Collections.emptyList());

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;area2\n1;2\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("fileLF"), eq(TrajectoryType.MISC_LOAD));

        // should not throw
        assertDoesNotThrow(() -> spyService.controlesMiscOnImportInstalledPower(studyId, List.of(imported1, imported2), "FR"));
    }

    @Test
    void controlesMiscOnImportInstalledPowerThrowsWhenLoadFactorMissingInstalledAreas_import(@TempDir Path tempDir) throws IOException {
        Integer studyId = 1;

        MiscClusterCapacityEntity imported1 = MiscClusterCapacityEntity.builder().groupe("group1").area("area1").cluster("cluster1").build();
        MiscClusterCapacityEntity imported2 = MiscClusterCapacityEntity.builder().groupe("group1").area("area2").cluster("cluster1").build();

        TrajectoryEntity existingLf = TrajectoryEntity.builder()
                .id(2)
                .fileName("fileLF")
                .horizon("2030-2031")
                .type(TrajectoryType.MISC_LOAD.name())
                .area("FR")
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)).thenReturn(List.of(existingLf));
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR")).thenReturn(Collections.emptyList());

        Path root = Files.createTempDirectory(tempDir, "traj");
        Path dir = root.resolve("group1").resolve("cluster1");
        Files.createDirectories(dir);
        // missing area2
        Files.writeString(dir.resolve("load_factor_cluster1_2030-2031.csv"), "area1;other\n1;2\n");

        TrajectoryServiceImpl spyService = Mockito.spy(trajectoryService);
        doReturn(root).when(spyService).buildTrajectoryPath(eq("fileLF"), eq(TrajectoryType.MISC_LOAD));

        BusinessException exception = assertThrows(BusinessException.class, () -> spyService.controlesMiscOnImportInstalledPower(studyId, List.of(imported1, imported2), "FR"));
        assertTrue(exception.getMessage().toLowerCase().contains("missing") || exception.getMessage().toLowerCase().contains("manqu"));
    }
}
