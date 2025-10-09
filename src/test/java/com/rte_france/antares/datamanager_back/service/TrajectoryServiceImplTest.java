package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private AreaFileProcessorService areaFileProcessorService;
    @Mock
    private LinkFileProcessorService linkFileProcessorService;
    @Mock
    private AntaressDataManagerProperties antaressDataManagerProperties;
    @Mock
    private ThermalFileProcessorService thermalFileProcessorService;
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
    private LoadFileProcessorServiceImpl loadFileProcessorService;
    @Mock
    private ThermalSpecificParametersRepository thermalSpecificParametersRepository;
    @Mock
    private LoadRepository loadRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaressDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");
    }


    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTYpeIsAREA() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/area/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getAreaDirectory()).thenReturn("/areas");

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile", "2023-2024", 1);

        verify(areaFileProcessorService, times(1)).processAreaFile(any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getLinkDirectory()).thenReturn("/links");

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

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaressDataManagerProperties.getThermalCapacityDirectory()).thenReturn("thermal_capacity");
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
    void findTrajectoriesByTypeAndFileNameStartWithFromFS_returnsFileNamesWhenDirectoryExists(@TempDir Path tempDir) throws IOException {
        // Given
        Path areaDir = tempDir.resolve("area");
        Files.createDirectories(areaDir);

        // When
        Path testFile = areaDir.resolve("areas_testFile.xlsx");
        Files.createFile(testFile);


        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaressDataManagerProperties.getAreaDirectory()).thenReturn("area");

        // Then
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null, "test");

        assertEquals(1, result.size());
        assertEquals("areas_testFile.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsSpecificFilesForThermalTechnicalSpecificParameter(@TempDir Path tempDir) throws IOException {
        Path thermalDir = tempDir.resolve("thermal");
        Files.createDirectories(thermalDir);

        Path specificFile = thermalDir.resolve("specific_param_file.xlsx");
        Path commonFile = thermalDir.resolve("common_param_file.xlsx");
        Files.createFile(specificFile);
        Files.createFile(commonFile);

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");

        java.util.List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, null, null);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().getFileName().startsWith("specific_param_"));
    }

    @Test
    void findTrajectoriesByType_returnsSpecificFilesForThermalTechnicalCommonParameter(@TempDir Path tempDir) throws IOException {
        Path thermalDir = tempDir.resolve("thermal");
        Files.createDirectories(thermalDir);

        Path specificFile = thermalDir.resolve("specific_param_test.xlsx");
        Path commonFile = thermalDir.resolve("common_param_test.xlsx");
        Files.createFile(specificFile);
        Files.createFile(commonFile);

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");

        java.util.List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null, null);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().getFileName().startsWith("common_param_"));
    }

    @Test
    void findTrajectoriesByType_throwsExceptionWhenDirectoryDoesNotExist() {
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaressDataManagerProperties.getAreaDirectory()).thenReturn("area"); // <-- Ajouté
        assertThrows(UncheckedIOException.class, () -> trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null, "area"));
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
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");

        assertThrows(TechnicalException.class, () -> trajectoryService.processTrajectory(TrajectoryType.MISC, "testFile", "2023-2024", 1));
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


        List<TrajectoryDataDTO> result = trajectoryService.getTrajectoryDataByTypeAndId(TrajectoryType.LINK, 1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.getFirst().toString().contains("DE-SU"));

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
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = List.of(new ThermalClusterCapacityEntity());

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .horizon(horizon)
                .thermalClusterCapacities(thermalClusterCapacities)
                .build();

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

        verify(thermalFileProcessorService, times(1)).verifyClustersInCommonParamTrajectory(studyId, horizon, thermalClusterCapacities);
        verify(thermalFileProcessorService, times(1)).verifyClustersInSpecificParamTrajectory(studyId, horizon, thermalClusterCapacities);
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

        verify(thermalFileProcessorService, times(1)).checkMissingClusters(studyId, horizon, clusterRefs, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    @Test
    void checkMissingClustersForSpecificParam_shouldCallCheckMissingClusters() throws IOException {
        Integer studyId = 1;
        String horizon = "2023-2024";
        Set<String> clusterRefs = Set.of("ClusterA", "ClusterB");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
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

        verify(thermalFileProcessorService, times(1)).checkMissingClusters(studyId, horizon, clusterRefs, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER);
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

        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaressDataManagerProperties.getAreaDirectory()).thenReturn("area");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("areas_test1.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        // Given
        Path linkDir = tempDir.resolve("link");
        Files.createDirectories(linkDir);

        Path testFile = linkDir.resolve("invalid_name.txt");
        Files.createFile(testFile);

        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaressDataManagerProperties.getLinkDirectory()).thenReturn("link"); // <-- Ajouté

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.LINK, null, "OTHER");

        // Then
        assertEquals(0, result.size());
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
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("/INPUT");
        when(antaressDataManagerProperties.getLoadDirectory())
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
        verify(loadFileProcessorService, never()).saveMatrixToNas(any());
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
        when(loadFileProcessorService.saveMatrixToNas(mockPath)).thenThrow(IOException.class);
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

        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("src/test/resources/load");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");

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

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("");
        when(areaRepository.findAllByStudyId(studyId)).thenReturn(List.of());
        when(loadFileProcessorService.checkForMissingLoadFiles(any(), any(), any(), any(), any()))
                .thenReturn(Set.of());

        Set<WarningMessageEntity> warningMessageEntities = loadFileProcessorService
                .checkForMissingLoadFiles(trajectoryPath, horizon, studyId, userNni, newTrajectory);

        verify(loadFileProcessorService, times(1))
                .checkForMissingLoadFiles(eq(trajectoryPath), eq(horizon), eq(studyId), eq(userNni), eq(newTrajectory));
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
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("");

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
    void getDirectoryByTrajectoryType_returnsLoadDirectory_whenTypeIsLoad() {
        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("loadDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.LOAD, null);
        assertEquals("loadDir", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsThermalCostDirectory_whenTypeIsThermalEconomicCostParameter() {
        when(antaressDataManagerProperties.getThermalCostDirectory()).thenReturn("thermalCostDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, null);
        assertEquals("thermalCostDir", result);
    }

    @Test
    void getDirectoryByTrajectoryType_throwsTechnicalException_whenTypeIsMisc() {
        TechnicalException exception = assertThrows(
                TechnicalException.class,
                () -> trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.MISC, null)
        );
        assertTrue(exception.getMessage().contains("No directory defined for TrajectoryType"));
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
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId));

        assertEquals("Missing modulation files: CM_modulation_trajectory_2025.csv, MR_modulation_trajectory_2025.csv", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processThermalModulationParameterTrajectory_mustThrowException_missing_Mr_clusters(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        Files.createFile(trajectoryPath.resolve("CM_modulation_trajectory_2025.csv"));
        Files.createFile(trajectoryPath.resolve("MR_modulation_trajectory_2025.csv"));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);
        when(thermalSpecificParametersRepository.findWithCmModulationByStudyIdAndHorizon(any(), any()))
                .thenReturn(List.of(ThermalSpecificParametersEntity.builder()
                        .id(1)
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("cluster1").build())
                        .build()));
        when(thermalSpecificParametersRepository.findWithMrModulationByStudyIdAndHorizon(any(), any()))
                .thenReturn(List.of(ThermalSpecificParametersEntity.builder()
                        .id(2)
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("cluster2").build())
                        .build()));

        // Simule l'absence du cluster attendu dans le fichier CM
        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(List.of("other_cluster","FR_cluster1")).when(spyService).extractClustersFromCsvHeader(any());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                spyService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId)
        );

        assertEquals("Les clusters suivants sont manquants dans le fichier MR : FR_cluster2", exception.getMessage());
    }

    @Test
    void processThermalModulationParameterTrajectory_mustThrowException_missing_clusters(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";
        String horizon = "2025";
        Integer studyId = 1;

        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        Files.createFile(trajectoryPath.resolve("CM_modulation_trajectory_2025.csv"));

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("nni").build());
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);
        when(thermalSpecificParametersRepository.findWithCmModulationByStudyIdAndHorizon(any(), any()))
                .thenReturn(List.of(ThermalSpecificParametersEntity.builder()
                        .id(1)
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("cluster1").build())
                        .build()));

        // Simule l'absence du cluster attendu dans le fichier CM
        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(List.of("other_cluster")).when(spyService).extractClustersFromCsvHeader(any());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                spyService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId)
        );

        assertEquals("Les clusters suivants sont manquants dans le fichier CM : FR_cluster1", exception.getMessage());
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
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(paramModulationDir);
        when(thermalSpecificParametersRepository.findWithCmModulationByStudyIdAndHorizon(any(), any()))
                .thenReturn(List.of(ThermalSpecificParametersEntity.builder()
                        .id(1)
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("cluster1").build())
                        .build()));
        when(thermalSpecificParametersRepository.findWithMrModulationByStudyIdAndHorizon(any(), any()))
                .thenReturn(List.of(ThermalSpecificParametersEntity.builder()
                        .id(2)
                        .area("FR")
                        .thermalClusterRef(ThermalClusterRef.builder().name("cluster2").build())
                        .build()));

        trajectoryService.processThermalModulationParameterTrajectory(trajectoryToUse, horizon, studyId);

        verify(thermalFileProcessorService, times(1)).processThermalModulationParameterFile(any(), any(), any(), any());
    }

}