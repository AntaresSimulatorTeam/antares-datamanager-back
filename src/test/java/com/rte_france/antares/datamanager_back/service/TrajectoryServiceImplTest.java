package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.area_link.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.area_link.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.area_link.impl.LinkMeProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.dsr.DsrCapacityModulationFileProcessorService;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroCoherenceCheckService;
import com.rte_france.antares.datamanager_back.service.res.impl.ResCoherenceCheckService;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import static org.mockito.ArgumentMatchers.*;
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
    private LinkMeProcessorServiceImpl linkMeProcessorServiceImpl;
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
    private DsrCapacityModulationFileProcessorService dsrCapacityModulationFileProcessorService;
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

    @Mock
    private DefaultConfigService defaultConfigService;

    @TempDir
    Path tempDir;

    @Mock
    private ResCoherenceCheckService resCoherenceCheckService;

    @Mock
    private HydroCoherenceCheckService hydroCoherenceCheckService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");

        // Mock default config service to return FR as a default area
        DefaultLoadDTO frDefault = new DefaultLoadDTO();
        frDefault.setName("FR");
        when(defaultConfigService.fetchAllDefaults()).thenReturn(List.of(frDefault));
    }


    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTYpeIsAREA() throws IOException {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("src/test/resources/area/testFile.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getAreaDirectory()).thenReturn("/areas");

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile", "2023-2024", 1);

        verify(areaFileProcessorService, times(1)).processAreaFile(any(), any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getLinkDirectory()).thenReturn("/links");

        trajectoryService.processTrajectory(TrajectoryType.LINK, "links_BP23_A_ref", "2023-2024", 1);

        verify(linkFileProcessorService, times(1)).processLinkFile(any(), any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK_ME() throws IOException {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("src/test/resources/link_me/linkme_ref.xlsx");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaresDataManagerProperties.getLinkMeDirectory()).thenReturn("/link_me");

        trajectoryService.processTrajectory(TrajectoryType.LINK_ME, "linkme_ref", "2023-2024", 1);

        verify(linkMeProcessorServiceImpl, times(1)).processLinkMeFile(any(), any(), any());
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
    void linkTrajectoryToStudy_validatesDsrCapacityModulationTrajectoryBeforeLinking() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.DSR_CAPACITY_MODULATION;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(type.name())
                .fileName("capacity_test")
                .horizon("2029-2030")
                .warningMessages(new HashSet<>())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        doNothing().when(dsrCapacityModulationFileProcessorService)
                .validateDsrCapacityModulationCoherence(trajectory, studyId);
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type);

        verify(dsrCapacityModulationFileProcessorService)
                .validateDsrCapacityModulationCoherence(trajectory, studyId);
        verify(studyTrajectoryRepository, times(1)).save(any());
        assertEquals(trajectory.getId(), result.getId());
    }

    @Test
    void linkTrajectoryToStudy_rejectsInvalidDsrCapacityModulationTrajectory() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.DSR_CAPACITY_MODULATION;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(type.name())
                .fileName("capacity_test")
                .horizon("2029-2030")
                .warningMessages(new HashSet<>())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        doThrow(BusinessException.builder()
                .message("Missing Areas/Clusters")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build())
                .when(dsrCapacityModulationFileProcessorService)
                .validateDsrCapacityModulationCoherence(trajectory, studyId);

        assertThrows(BusinessException.class, () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type));
        verify(studyTrajectoryRepository, never()).save(any());
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

    @ParameterizedTest
    @EnumSource(value = TrajectoryType.class, names = {
            "NUCLEAR_FR_MODULATION", "NUCLEAR_FR_TALON", "NUCLEAR_FR_TS_ERP",
            "NUCLEAR_FR_TS_LONG_TERM", "NUCLEAR_FR_TS_SMR"
    })
    void linkTrajectoryToStudy_replacesExistingLinkForNuclearTypes(TrajectoryType type) throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 1;

        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();
        StudyTrajectoryEntity existingLink = StudyTrajectoryEntity.builder().trajectory(trajectory).build();

        StudyEntity study = StudyEntity.builder().id(studyId).build();
        study.setStudyTrajectoryEntities(Set.of(existingLink));

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder().id(trajectoryId).type(type.name())
                .warningMessages(new HashSet<>())
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
    void linkTrajectoryToStudy_callsValidateHydroSeriesCoherenceForHydroSeries() throws IOException {
        Integer trajectoryId = 10;
        Integer studyId = 5;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_SERIES);

        assertEquals(trajectoryId, result.getId());
        verify(hydroCoherenceCheckService, times(1)).validateHydroSeriesCoherence(studyId, trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_callsValidateHydroSeriesCoherenceForHydroPspSeries() throws IOException {
        Integer trajectoryId = 12;
        Integer studyId = 8;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_PSP_SERIES.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_PSP_SERIES);

        assertEquals(trajectoryId, result.getId());
        verify(hydroCoherenceCheckService, times(1)).validateHydroSeriesCoherence(studyId, trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_throwsWhenHydroSeriesCoherenceCheckFails() throws BusinessException {
        Integer trajectoryId = 10;
        Integer studyId = 5;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        doThrow(BusinessException.builder()
                .message("Missing areas hydroAllocation in Hydro TechnicalParameters trajectory")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build())
                .when(hydroCoherenceCheckService).validateHydroSeriesCoherence(studyId, trajectory);

        assertThrows(BusinessException.class,
                () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_SERIES));
        verify(studyTrajectoryRepository, never()).save(any());
    }

    @Test
    void linkTrajectoryToStudy_callsValidateHydroTechnicalParametersCoherenceForBothHydroTypes() throws IOException {
        Integer trajectoryId = 11;
        Integer studyId = 6;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS);

        assertEquals(trajectoryId, result.getId());
        verify(hydroCoherenceCheckService, times(1)).validateHydroTechnicalParametersCoherence(studyId, trajectory, TrajectoryType.HYDRO_ALLOCATION);
        verify(hydroCoherenceCheckService, times(1)).validateHydroTechnicalParametersCoherence(studyId, trajectory, TrajectoryType.HYDRO_PARAMETERS);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_callsValidateHydroTechnicalParametersCoherenceForPspType() throws IOException {
        Integer trajectoryId = 13;
        Integer studyId = 9;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS);

        assertEquals(trajectoryId, result.getId());
        verify(hydroCoherenceCheckService, times(1)).validateHydroTechnicalParametersCoherence(studyId, trajectory, TrajectoryType.HYDRO_ALLOCATION);
        verify(hydroCoherenceCheckService, times(1)).validateHydroTechnicalParametersCoherence(studyId, trajectory, TrajectoryType.HYDRO_PARAMETERS);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_throwsWhenHydroTechnicalParametersCoherenceCheckFails() throws BusinessException {
        Integer trajectoryId = 11;
        Integer studyId = 6;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .warningMessages(new HashSet<>())
                .build();
        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        doThrow(BusinessException.builder()
                .message("Missing areas hydroAllocation in Hydro TechnicalParameters trajectory")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build())
                .when(hydroCoherenceCheckService).validateHydroTechnicalParametersCoherence(eq(studyId), eq(trajectory), any(TrajectoryType.class));

        assertThrows(BusinessException.class,
                () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS));
        verify(studyTrajectoryRepository, never()).save(any());
    }

    @Test
    void unlinkTrajectoryFromStudy_unlinksWhenLinkExists() {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyTrajectoryKey key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type("LINK").scenarioEntities(new HashSet<>()).build();
        StudyEntity study = StudyEntity.builder().id(studyId).trajectories(new HashSet<>(Set.of(trajectory))).build();
        trajectory.getScenarioEntities().add(study);

        StudyTrajectoryEntity entity = StudyTrajectoryEntity.builder()
                .id(key)
                .studyEntity(study)
                .trajectory(trajectory)
                .build();

        // When
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(entity));

        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);

        // Then
        verify(studyTrajectoryRepository, times(1)).delete(entity);
        assertFalse(study.getTrajectories().contains(trajectory));
        assertFalse(trajectory.getScenarioEntities().contains(study));
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

        // When
        var areaTrajectory = TrajectoryEntity.builder().id(trajectoryId).type("AREA").scenarioEntities(new HashSet<>()).build();
        var study = StudyEntity.builder().id(studyId).trajectories(new HashSet<>(Set.of(areaTrajectory))).build();
        areaTrajectory.getScenarioEntities().add(study);

        var entity = StudyTrajectoryEntity.builder()
                .id(key)
                .studyEntity(study)
                .trajectory(areaTrajectory)
                .build();

        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(areaTrajectory));
        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(entity));

        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);

        // Then
        verify(studyTrajectoryRepository, times(1)).delete(entity);
        assertFalse(study.getTrajectories().contains(areaTrajectory));
        assertFalse(areaTrajectory.getScenarioEntities().contains(study));
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


        List<TrajectoryDataDTO> result = trajectoryService.getTrajectoryDataByTypeAndId(TrajectoryType.LINK, 1);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.getFirst().toString().contains("DE-SU"));

    }

    @Test
    void getTrajectoryDataByTypeAndId_returnAreaDTOForSTSType() {
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
        verify(thermalControlService, times(1)).verifyClustersInSpecificParamTrajectory(eq(studyId), eq(horizon), eq(thermalClusterCapacities), any());
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
                                .cluster("ClusterA")
                                .build(),
                        ThermalSpecificParametersEntity.builder()
                                .cluster("ClusterB")
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
    void checkTrajectoryCoherence_throwsTechnicalException_whenTypeIsUnsupported() {
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder().type("UNSUPPORTED_TYPE").build();
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, "user"));

        assertEquals("Trajectory type {0} is not supported", exception.getMessage());
        assertEquals(List.of("UNSUPPORTED_TYPE"), exception.getErrorMessageArguments());
        verify(warningRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RES_CAPACITY", "RES_LOAD", "RES_ZONAL_DISTRIBUTION", "RES_TECHNOLOGY_DISTRIBUTION"})
    void checkTrajectoryCoherence_shouldHandleResTypesWithoutAdditionalChecks(String resType) throws IOException {
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder().type(resType).build();
        WarningMessageEntity warning = WarningMessageEntity.builder().build();
        Set<WarningMessageEntity> warningMessages = new HashSet<>(Set.of(warning));

        trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, "user");

        assertSame(trajectory, warning.getTrajectory());
        verify(warningRepository).saveAll(warningMessages);
    }

    @Test
    void linkTrajectoryToStudy_shouldLinkResTrajectoryAndRunCoherence() throws IOException {
        Integer trajectoryId = 99;
        Integer studyId = 7;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(TrajectoryType.RES_LOAD.name()).build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user").build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_LOAD);

        assertEquals(trajectoryId, result.getId());
        verify(warningRepository).saveAll(anySet());
        verify(studyTrajectoryRepository).save(any(StudyTrajectoryEntity.class));
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
    void findTrajectoriesByType_throwsTechnicalException_whenCannotReadLastModifiedDateForThermalModulation(@TempDir Path tempDir) throws IOException {
        Path modulationDir = tempDir.resolve("modulation");
        Files.createDirectories(modulationDir);
        Path trajectoryDirectory = modulationDir.resolve("traj_mod");
        Files.createDirectories(trajectoryDirectory);
        // Add content to make directory non-empty (required by isDirectoryEmpty check)
        Files.createFile(trajectoryDirectory.resolve(".placeholder"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn("modulation");

        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            filesMock.when(() -> Files.getLastModifiedTime(trajectoryDirectory))
                    .thenThrow(new IOException("cannot read timestamp"));

            TechnicalException exception = assertThrows(TechnicalException.class,
                    () -> trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER, null, null, null));

            assertEquals("Can't read trajectory file", exception.getMessage());
            assertNotNull(exception.getCause());
            assertInstanceOf(IOException.class, exception.getCause());
        }
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

    @ParameterizedTest
    @ValueSource(strings = {
            "onshore",
            "ONSHORE",
    })
    void findTrajectoriesByType_returnsRESFilesWithTechnologyForFR(String technology,@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/installed power/FR/");
        Files.createDirectories(thermalDir);

        // Create directories with content (not empty)
        Path dir1 = thermalDir.resolve("BP23_Aref");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder")); // Add content to make directory non-empty

        Path dir2 = thermalDir.resolve("BP23_Aref_v2");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder")); // Add content to make directory non-empty

        Files.createFile(thermalDir.resolve("installedRES_offshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("installedRES_onshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("installedRES_onshore_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResCapacityDirectory()).thenReturn("RES/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_CAPACITY, "FR", technology, null);

        // Then
        assertEquals(2, result.size());
        List<String> expected = List.of("BP23_Aref", "BP23_Aref_v2");

        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();

        assertTrue(actual.containsAll(expected));
        assertEquals(expected.size(), actual.size());
    }

    @Test
    void findTrajectoriesByType_returnsRESFilesWithoutTechnologyForFR(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/installed power/FR");
        Files.createDirectories(thermalDir);

        // Create directories with content (not empty)
        Path dir1 = thermalDir.resolve("BP23_Aref");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder")); // Add content to make directory non-empty

        Path dir2 = thermalDir.resolve("BP23_Aref_v2");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder")); // Add content to make directory non-empty

        Files.createFile(thermalDir.resolve("installedRES_onshore_BP23_Aref.xlsx"));
        Files.createFile(thermalDir.resolve("other_installedRES_BP23_Aref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResCapacityDirectory()).thenReturn("RES/installed power/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_CAPACITY, "FR", null, null);

        // Then
        assertEquals(2, result.size());
        List<String> expected = List.of("BP23_Aref", "BP23_Aref_v2");

        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();

        assertTrue(actual.containsAll(expected));
        assertEquals(expected.size(), actual.size());
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
    void findTrajectoriesByType_returnsResZonalDistributionWithoutTechnologyFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/technicalParameters/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("repartition_zonale_offshore_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_zonale_onshore_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("REPARTITION_TECHNO_BP23_A_ref_onshore.xlsx"));
        Files.createFile(thermalDir.resolve("REPARTITION_ZONALE_BP23_A_ref_onshore.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_zonale_OFFSHORE_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_zonale_offshore_BP23_A_ref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResDistributionDirectory()).thenReturn("RES/technicalParameters/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_ZONAL_DISTRIBUTION, null, null, null);

        // Then
        assertEquals(4, result.size());
        List<String> expected = List.of("repartition_zonale_offshore_BP23_A_ref.xlsx", "repartition_zonale_onshore_BP23_A_ref.xlsx", "REPARTITION_ZONALE_BP23_A_ref_onshore.xlsx", "repartition_zonale_OFFSHORE_BP23_A_ref.xlsx");

        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();

        assertTrue(actual.containsAll(expected));
        assertEquals(expected.size(), actual.size());

    }

    @ParameterizedTest
    @ValueSource(strings = {
            "offshore",
            "OFFSHORE",
    })
    void findTrajectoriesByType_returnsResTechnologyDistributionWithTechnologyFiles(String technology, @TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/technicalParameters/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("repartition_techno_offshore_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_techno_onshore_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_techno_BP23_A_ref_onshore.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_techno_OFFSHORE_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_techno_offshore_BP23_A_ref.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResDistributionDirectory()).thenReturn("RES/technicalParameters/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, null, technology, null);

        // Then
        assertEquals(2, result.size());
        List<String> expected = List.of("repartition_techno_offshore_BP23_A_ref.xlsx", "repartition_techno_OFFSHORE_BP23_A_ref.xlsx");

        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();

        assertTrue(actual.containsAll(expected));
        assertEquals(expected.size(), actual.size());
    }

    @Test
    void findTrajectoriesByType_returnsResTechnologyDistributionFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/technicalParameters/");
        Files.createDirectories(thermalDir);

        Files.createFile(thermalDir.resolve("repartition_techno_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_TECHNO_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_zonale_BP23_A_ref_onshore.xlsx"));
        Files.createFile(thermalDir.resolve("REPARTITION_TECHNO_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_zonale_OFFSHORE_BP23_A_ref.xlsx"));
        Files.createFile(thermalDir.resolve("repartition_techno_BP23_A_ref.xlsx.txt"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResDistributionDirectory()).thenReturn("RES/technicalParameters/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION, null, null, null);

        // Then
        assertEquals(3, result.size());
        List<String> expected = List.of("repartition_techno_BP23_A_ref.xlsx", "repartition_TECHNO_BP23_A_ref.xlsx", "REPARTITION_TECHNO_BP23_A_ref.xlsx");

        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();

        assertTrue(actual.containsAll(expected));
        assertEquals(expected.size(), actual.size());

    }

    @Test
    void findTrajectoriesByType_returnsResLoadFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path thermalDir = tempDir.resolve("RES/load factor/");
        Files.createDirectories(thermalDir);

        Path dir1 = thermalDir.resolve("BP23_A_ref_FR");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = thermalDir.resolve("BP23_A_ref_FR_v2");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        Path dir3 = thermalDir.resolve("BP23_A_ref_FR_v3");
        Files.createDirectory(dir3);
        Files.createFile(dir3.resolve(".placeholder"));
        Files.createFile(thermalDir.resolve("BP23_A_ref_FR_v3.xlsx"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getResLoadDirectory()).thenReturn("RES/load factor/");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.RES_LOAD, null, null, null);

        // Then
        assertEquals(3, result.size());
        Set<String> expected = Set.of(
                "BP23_A_ref_FR",
                "BP23_A_ref_FR_v2",
                "BP23_A_ref_FR_v3"
        );

        Set<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
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
    void findTrajectoriesByType_throwsTechnicalException_whenDirectoryIsMissing() throws TechnicalException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("STS");
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn("modulation");

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER, null, null, null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Could not find directory"));
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
        verify(nasFileService, never()).readAndSaveMatrixToNas(any(), any(), any(), anyBoolean());
    }

    @Test
    void findTrajectoriesByType_returnsHydroSeriesTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path hydroDir = tempDir.resolve("hydro/series/");
        Files.createDirectories(hydroDir);

        Path dir1 = hydroDir.resolve("BP_23_ref");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = hydroDir.resolve("BP_50_ref");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getHydroSeriesDirectory()).thenReturn(hydroDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.HYDRO_SERIES, "FR", null, null);

        // Then
        List<String> expected = List.of("BP_23_ref", "BP_50_ref");
        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();
        assertEquals(2, result.size());
        assertTrue(actual.containsAll(expected));
    }

    @Test
    void findTrajectoriesByType_returnsHydroTechnicalParametersTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path hydroTechnicalParametersDir = tempDir.resolve("hydro/technical_parameters");
        Files.createDirectories(hydroTechnicalParametersDir);

        Path dir1 = hydroTechnicalParametersDir.resolve("BP_23_ref");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = hydroTechnicalParametersDir.resolve("BP_50_ref");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getHydroParametersDirectory()).thenReturn(hydroTechnicalParametersDir.toString());
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, "FR", null, null);

        // Then
        List<String> expected = List.of("BP_23_ref", "BP_50_ref");
        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();
        assertEquals(2, result.size());
        assertTrue(actual.containsAll(expected));
    }

    @Test
    void findTrajectoriesByType_returnsHydroPspSeriesTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path hydroDir = tempDir.resolve("PSP_virtual/series/");
        Files.createDirectories(hydroDir);

        Path dir1 = hydroDir.resolve("BP_23_ref_psp");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = hydroDir.resolve("BP_50_ref_psp");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getPspSeriesDirectory()).thenReturn(hydroDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.HYDRO_PSP_SERIES, "FR", null, null);

        // Then
        List<String> expected = List.of("BP_23_ref_psp", "BP_50_ref_psp");
        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();
        assertEquals(2, result.size());
        assertTrue(actual.containsAll(expected));
    }

    @Test
    void findTrajectoriesByType_returnsHydroPspTechnicalParametersTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path hydroTechnicalParametersDir = tempDir.resolve("PSP_virtual/technical_parameters");
        Files.createDirectories(hydroTechnicalParametersDir);

        Path dir1 = hydroTechnicalParametersDir.resolve("BP_23_ref_psp");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = hydroTechnicalParametersDir.resolve("BP_50_ref_psp");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getPspParametersDirectory()).thenReturn(hydroTechnicalParametersDir.toString());
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS, "FR", null, null);

        // Then
        List<String> expected = List.of("BP_23_ref_psp", "BP_50_ref_psp");
        List<String> actual = result.stream()
                .map(FsTrajectoryDTO::getFileName)
                .toList();
        assertEquals(2, result.size());
        assertTrue(actual.containsAll(expected));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearTalonTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear");
        Files.createDirectories(nuclearDir);

        Files.createFile(nuclearDir.resolve("talon_nuc_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("talon_nuc_2030.xlsx"));
        Files.createFile(nuclearDir.resolve("talon_nuc_test.txt"));

        when(antaresDataManagerProperties.getNuclearTalonDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TALON, null, null, null);

        // Then
        assertEquals(2, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("talon_nuc_2025.xlsx"));
        assertTrue(fileNames.contains("talon_nuc_2030.xlsx"));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearEprTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/epr");
        Files.createDirectories(nuclearDir);

        Files.createFile(nuclearDir.resolve("ts_epr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("ts_epr_2030.xlsx"));

        when(antaresDataManagerProperties.getNuclearEprDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_ERP, null, null, null);

        // Then
        assertEquals(2, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("ts_epr_2025.xlsx"));
        assertTrue(fileNames.contains("ts_epr_2030.xlsx"));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearSmrTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/smr");
        Files.createDirectories(nuclearDir);

        Files.createFile(nuclearDir.resolve("ts_smr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("ts_smr_projection.xlsx"));

        when(antaresDataManagerProperties.getNuclearSmrDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_SMR, null, null, null);

        // Then
        assertEquals(2, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("ts_smr_2025.xlsx"));
        assertTrue(fileNames.contains("ts_smr_projection.xlsx"));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearModulationTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);

        Path dir1 = nuclearDir.resolve("modulation_2025");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = nuclearDir.resolve("modulation_2030");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null);

        // Then
        assertEquals(2, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("modulation_2025"));
        assertTrue(fileNames.contains("modulation_2030"));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearLongTermTrajectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/long_term");
        Files.createDirectories(nuclearDir);

        Path dir1 = nuclearDir.resolve("lt_2025");
        Files.createDirectory(dir1);
        Files.createFile(dir1.resolve(".placeholder"));

        Path dir2 = nuclearDir.resolve("lt_2030");
        Files.createDirectory(dir2);
        Files.createFile(dir2.resolve(".placeholder"));

        Path dir3 = nuclearDir.resolve("EPR");
        Files.createDirectory(dir3);
        Files.createFile(dir3.resolve(".epr_placeholder"));

        Path dir4 = nuclearDir.resolve("SMR");
        Files.createDirectory(dir4);
        Files.createFile(dir4.resolve(".smr_placeholder"));

        when(antaresDataManagerProperties.getNuclearLtDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null, null);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void findTrajectoriesByType_shouldIgnoreInvalidNuclearTalonFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear");
        Files.createDirectories(nuclearDir);

        // Valid files
        Files.createFile(nuclearDir.resolve("talon_nuc_2025.xlsx"));

        // Invalid files - wrong prefix
        Files.createFile(nuclearDir.resolve("talon_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("nuc_talon_2025.xlsx"));

        // Invalid files - wrong extension
        Files.createFile(nuclearDir.resolve("talon_nuc_2030.txt"));
        Files.createFile(nuclearDir.resolve("talon_nuc_2035.csv"));

        when(antaresDataManagerProperties.getNuclearTalonDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TALON, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("talon_nuc_2025.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_shouldIgnoreInvalidNuclearEprFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/epr");
        Files.createDirectories(nuclearDir);

        // Valid files
        Files.createFile(nuclearDir.resolve("ts_epr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("ts_epr_projection.xlsx"));

        // Invalid files - wrong prefix
        Files.createFile(nuclearDir.resolve("epr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("ts_smr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("te_epr_2025.xlsx"));

        // Invalid files - wrong extension
        Files.createFile(nuclearDir.resolve("ts_epr_2030.txt"));
        Files.createFile(nuclearDir.resolve("ts_epr_2035.docx"));

        when(antaresDataManagerProperties.getNuclearEprDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_ERP, null, null, null);

        // Then
        assertEquals(2, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("ts_epr_2025.xlsx"));
        assertTrue(fileNames.contains("ts_epr_projection.xlsx"));
        assertFalse(fileNames.contains("ts_smr_2025.xlsx"));
    }

    @Test
    void findTrajectoriesByType_shouldIgnoreInvalidNuclearSmrFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/smr");
        Files.createDirectories(nuclearDir);

        // Valid files
        Files.createFile(nuclearDir.resolve("ts_smr_2025.xlsx"));

        // Invalid files - wrong prefix
        Files.createFile(nuclearDir.resolve("smr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("ts_epr_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("t_smr_2025.xlsx"));

        // Invalid files - wrong extension
        Files.createFile(nuclearDir.resolve("ts_smr_2030.txt"));
        Files.createFile(nuclearDir.resolve("ts_smr_2035.json"));

        when(antaresDataManagerProperties.getNuclearSmrDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_SMR, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("ts_smr_2025.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_shouldIgnoreAllNonXlsxFilesForNuclearTypes(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/test");
        Files.createDirectories(nuclearDir);

        // Valid file
        Files.createFile(nuclearDir.resolve("talon_nuc_valid.xlsx"));

        // Invalid extensions - none should be accepted for nuclear types
        Files.createFile(nuclearDir.resolve("talon_nuc_invalid.xls"));
        Files.createFile(nuclearDir.resolve("talon_nuc_invalid.pdf"));
        Files.createFile(nuclearDir.resolve("talon_nuc_invalid"));

        when(antaresDataManagerProperties.getNuclearTalonDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TALON, null, null, null);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.getFirst().getFileName().endsWith(".xlsx"));
    }

    @Test
    void findTrajectoriesByType_shouldHandleNuclearFilesWithMixedCase(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/mixed");
        Files.createDirectories(nuclearDir);

        // Files with different cases - filenames are made lowercase in validation
        Files.createFile(nuclearDir.resolve("TALON_NUC_2025.xlsx"));
        Files.createFile(nuclearDir.resolve("Talon_Nuc_2030.xlsx"));
        Files.createFile(nuclearDir.resolve("talon_nuc_2035.xlsx"));

        when(antaresDataManagerProperties.getNuclearTalonDirectory()).thenReturn(nuclearDir.toString());

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TALON, null, null, null);

        // Then
        assertEquals(3, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains("TALON_NUC_2025.xlsx"));
        assertTrue(fileNames.contains("Talon_Nuc_2030.xlsx"));
        assertTrue(fileNames.contains("talon_nuc_2035.xlsx"));
    }

    @Test
    void findTrajectoriesByType_shouldHandleFlowbasedCase(@TempDir Path tempDir) throws IOException {
        // Given
        String flowbasedDirectory = "flowbased";
        Path basePath = tempDir.resolve(flowbasedDirectory);
        
        String trajName1 = "Porygon_CNEC_interne_2022";
        Path traj1 = basePath.resolve(trajName1);
        String yearDir1 = "2021";
        Path traj1Dir = traj1.resolve(yearDir1);
        
        String trajName2 = "Porygon_CNEC_externe_2022";
        Path traj2 = basePath.resolve(trajName2);
        String yearDir2 = "2023";
        Path traj2Dir = traj2.resolve(yearDir2);
        
        String trajName3 = "Porygon_BPPP_interne_2040";
        Path traj3 = basePath.resolve(trajName3);
        String yearDir3 = "2023";
        Path traj3Dir = traj3.resolve(yearDir3);
        Files.createDirectories(traj1Dir);
        Files.createDirectories(traj2Dir);
        Files.createDirectories(traj3Dir);

        // Files with different cases - filenames are made lowercase in validation
        Files.createFile(traj1Dir.resolve("correspondance_links_weights.csv"));
        Files.createFile(traj1Dir.resolve("Flowbased_nodes_links.xlsx"));
        Files.createFile(traj1Dir.resolve("IdTypDays.csv"));
        Files.createFile(traj1Dir.resolve("second_member.txt"));
        Files.createFile(traj1Dir.resolve("weight.txt"));

        when(antaresDataManagerProperties.getFlowbasedDirectory()).thenReturn(basePath.toString());
        
        TrajectoryServiceImpl spyService = spy(trajectoryService);
        doReturn(basePath)
                .when(spyService)
                .normalizeAndValidateDirectory(
                        eq(TrajectoryType.FLOWBASED),
                        any(),
                        any()
                );
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.FLOWBASED, null, null, null);

        // Then
        assertEquals(1, result.size());
        List<String> fileNames = result.stream().map(FsTrajectoryDTO::getFileName).toList();
        assertTrue(fileNames.contains(trajName1+"/"+yearDir1));
        assertFalse(fileNames.contains(trajName2+"/"+yearDir2));
        assertFalse(fileNames.contains(trajName3+"/"+yearDir3));
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
        when(nasFileService.readAndSaveMatrixToNas(mockPath, "outputDir", null, true)).thenThrow(IOException.class);
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
    void getDirectoryByTrajectoryType_returnsLinkDirectory_whenTypeIsLink() throws IOException {
        when(antaresDataManagerProperties.getLinkDirectory()).thenReturn("linkDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.LINK, null, null);
        assertEquals("linkDir", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsLinkMeDirectory_whenTypeIsLinkMe() throws IOException {
        when(antaresDataManagerProperties.getLinkMeDirectory()).thenReturn("linkMeDir");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.LINK_ME, null, null);
        assertEquals("linkMeDir", result);
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

    // Tests for Nuclear Trajectories
    @Test
    void getDirectoryByTrajectoryType_returnsNuclearModulationDirectory_whenTypeIsNuclearFrModulation() throws IOException {
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("/nuclear/modulation");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.NUCLEAR_FR_MODULATION, null, null);
        assertEquals("/nuclear/modulation", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearTalonDirectory_whenTypeIsNuclearFrTalon() throws IOException {
        when(antaresDataManagerProperties.getNuclearTalonDirectory()).thenReturn("/nuclear/talon");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.NUCLEAR_FR_TALON, null, null);
        assertEquals("/nuclear/talon", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearEprDirectory_whenTypeIsNuclearFrTsErp() throws IOException {
        when(antaresDataManagerProperties.getNuclearEprDirectory()).thenReturn("/nuclear/epr");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.NUCLEAR_FR_TS_ERP, null, null);
        assertEquals("/nuclear/epr", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearLtDirectory_whenTypeIsNuclearFrTsLongTerm() throws IOException {
        when(antaresDataManagerProperties.getNuclearLtDirectory()).thenReturn("/nuclear/long_term");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null);
        assertEquals("/nuclear/long_term", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsNuclearSmrDirectory_whenTypeIsNuclearFrTsSmr() throws IOException {
        when(antaresDataManagerProperties.getNuclearSmrDirectory()).thenReturn("/nuclear/smr");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.NUCLEAR_FR_TS_SMR, null, null);
        assertEquals("/nuclear/smr", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsAdequacyPatchDirectory_whenTypeIsAdequacyPatch() throws IOException {
        when(antaresDataManagerProperties.getAdequacyDirectory()).thenReturn("/adequacy");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.ADEQUACY_PATCH, null, null);
        assertEquals("/adequacy", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsSettingsDirectory_whenTypeIsAdequacyPatch() throws IOException {
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("/settings");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.SETTINGS, null, null);
        assertEquals("/settings", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsFlowbasedDirectory_whenTypeIsFlowbased() throws IOException {
        when(antaresDataManagerProperties.getFlowbasedDirectory()).thenReturn("/flowbased");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.FLOWBASED, null, null);
        assertEquals("/flowbased", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsP2GCapacityDirectory_whenTypeIsP2GCapacity() throws IOException {
        when(antaresDataManagerProperties.getP2gDirectory()).thenReturn("/P2G");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.P2G_CAPACITY_COST, null, null);
        assertEquals("/P2G", result);
    }

    @Test
    void getDirectoryByTrajectoryType_returnsP2GModulationDirectory_whenTypeIsP2GModulation() throws IOException {
        when(antaresDataManagerProperties.getP2gMarketModulationDirectory()).thenReturn("/thermal/economic parameters/market_bid_marg_cost_modulation");
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.P2G_MARKET_MODULATION, null, null);
        assertEquals("/thermal/economic parameters/market_bid_marg_cost_modulation", result);
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

        String mrFileName = "MR_" + trajectoryToUse + "_" + horizon + ".csv";
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

        String mrFileName = "CM_" + trajectoryToUse + "_" + horizon + ".csv";
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
        cMlines.addFirst("DATE_HEURE;HEURE;FR_cluster1;FR_cluster2");
        Files.write(csvCmPath, cMlines);

        Files.createFile(trajectoryPath.resolve("MR_modulation_trajectory_2025.csv"));
        Path csvMrPath = trajectoryPath.resolve("MR_modulation_trajectory_2025.csv");
        List<String> mRlines = Files.readAllLines(csvMrPath);
        mRlines.addFirst("DATE_HEURE;HEURE;FR_cluster1;FR_cluster2");
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

        trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), trajectory, "user");

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
         when(miscClusterCapacityRepository.findByStudyIdAndArea(eq(studyId), eq("FR"))).thenReturn(List.of(add1));

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
         verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);
     }

    @Test
    void verifyLoadFactorAreaHeaders_throwsBusinessExceptionWhenAreaNotInHeaders() throws Exception {
        TrajectoryEntity loadFactoryTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_factor_file")
                .area("FR")
                .horizon("2030-2031")
                .build();

        String expectedArea = "DE";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("group1", "cluster1");
        List<String> headers = List.of("fr", "it");

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), 1))
                .thenReturn(List.of(loadFactoryTrajectory));

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_factor_file", TrajectoryType.MISC_LOAD);

        try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
            miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster))
                    .thenReturn(headers);

            try {
                var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
                method.setAccessible(true);
                method.invoke(spyService, loadFactoryTrajectory, expectedArea, groupCluster, "2030-2031");
                fail("Expected BusinessException to be thrown");
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertInstanceOf(BusinessException.class, e.getCause());
                BusinessException exception = (BusinessException) e.getCause();
                assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
                assertTrue(exception.getMessage().contains("does not contain the expected area"));
            }
        }
    }

    @Test
    void verifyLoadFactorAreaHeaders_succedsWhenAreaFoundInHeadersRegardlessOfCase() throws Exception {
        TrajectoryEntity loadFactorTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_factor_file")
                .area("FR")
                .horizon("2030-2031")
                .build();

        String expectedArea = "DE";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("group1", "cluster1");
        List<String> headers = List.of("fr", "de", "it");

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_factor_file", TrajectoryType.MISC_LOAD);

        try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
            miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster))
                    .thenReturn(headers);

            assertDoesNotThrow(() -> {
                var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
                method.setAccessible(true);
                method.invoke(spyService, loadFactorTrajectory, expectedArea, groupCluster, "2030-2031");
            });
        }
    }

    @Test
    void verifyLoadFactorAreaHeaders_usesTrajectoryHorizonWhenHorizonParameterIsNull() throws Exception {
        TrajectoryEntity loadFactorTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_factor_file")
                .area("FR")
                .horizon("2030-2031")
                .build();

        String expectedArea = "FR";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("group1", "cluster1");
        List<String> headers = List.of("fr");

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_factor_file", TrajectoryType.MISC_LOAD);

        try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
            miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster))
                    .thenReturn(headers);

            assertDoesNotThrow(() -> {
                var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
                method.setAccessible(true);
                method.invoke(spyService, loadFactorTrajectory, expectedArea, groupCluster, null);
            });

            miscMock.verify(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster));
        }
    }

    @Test
    void verifyLoadFactorAreaHeaders_throwsBusinessExceptionWithCorrectMessageAndArguments() throws Exception {
        TrajectoryEntity loadFactorTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_test")
                .area("FR")
                .horizon("2025-2026")
                .build();

        String expectedArea = "ES";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
        List<String> headers = List.of("fr", "it");

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_test", TrajectoryType.MISC_LOAD);

         try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
             miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2025-2026", mockPath, groupCluster))
                     .thenReturn(headers);

             try {
                 var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
                 method.setAccessible(true);
                 method.invoke(spyService, loadFactorTrajectory, expectedArea, groupCluster, "2025-2026");
                 fail("Expected BusinessException to be thrown");
             } catch (java.lang.reflect.InvocationTargetException e) {
                 assertInstanceOf(BusinessException.class, e.getCause());
                 BusinessException exception = (BusinessException) e.getCause();

                 List<String> args = exception.getErrorMessageArguments();
                 assertEquals("load_test", args.get(0));
                 assertEquals("FR", args.get(1));
                 assertEquals("ES", args.get(2));
                 assertEquals("biogas", args.get(3));
             }
         }
    }

    @Test
    void verifyLoadFactorAreaHeaders_throwsBusinessExceptionWhenHeadersListIsEmpty() throws Exception {
        TrajectoryEntity loadFactorTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_factor_file")
                .area("FR")
                .horizon("2030-2031")
                .build();

        String expectedArea = "DE";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("group1", "cluster1");
        List<String> headers = Collections.emptyList();

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_factor_file", TrajectoryType.MISC_LOAD);

         try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
             miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster))
                     .thenReturn(headers);

             try {
                 var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
                 method.setAccessible(true);
                 method.invoke(spyService, loadFactorTrajectory, expectedArea, groupCluster, "2030-2031");
                 fail("Expected BusinessException to be thrown");
             } catch (java.lang.reflect.InvocationTargetException e) {
                 assertInstanceOf(BusinessException.class, e.getCause());
                 BusinessException exception = (BusinessException) e.getCause();
                 assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
             }
         }
    }

    @Test
    void verifyLoadFactorAreaHeaders_buildsCorrectPathForMiscLoadTrajectory() throws Exception {
        TrajectoryEntity loadFactorTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("load_specific_trajectory")
                .area("FR")
                .horizon("2030-2031")
                .build();

        String expectedArea = "FR";
        MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("group1", "cluster1");
        List<String> headers = List.of("fr");

        TrajectoryServiceImpl spyService = spy(trajectoryService);
        Path mockPath = mock(Path.class);
        doReturn(mockPath).when(spyService).buildTrajectoryPath("load_specific_trajectory", TrajectoryType.MISC_LOAD);

        try (MockedStatic<MiscFileProcessorServiceImpl> miscMock = mockStatic(MiscFileProcessorServiceImpl.class)) {
            miscMock.when(() -> MiscFileProcessorServiceImpl.readHeaderAreas("2030-2031", mockPath, groupCluster))
                    .thenReturn(headers);

            var method = TrajectoryServiceImpl.class.getDeclaredMethod("verifyLoadFactorAreaHeaders", TrajectoryEntity.class, String.class, MiscFileProcessorServiceImpl.GroupClusterKey.class, String.class);
            method.setAccessible(true);
            method.invoke(spyService, loadFactorTrajectory, expectedArea, groupCluster, "2030-2031");

            verify(spyService).buildTrajectoryPath("load_specific_trajectory", TrajectoryType.MISC_LOAD);
        }
    }


        @Test
        void shouldReturn_whenNoInstalledPower_forLoadFactor() {
            Integer studyId = 1;

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of());

            TrajectoryEntity load = TrajectoryEntity.builder()
                    .type(TrajectoryType.MISC_LOAD.name())
                    .horizon("2030")
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), load, "user"));
        }

        @Test
        void shouldHandleEmptyCapacities_forLoadFactor() {
            Integer studyId = 1;

            TrajectoryEntity installed = TrajectoryEntity.builder()
                    .id(10)
                    .area("FR")
                    .horizon("2030")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installed));

            when(miscClusterCapacityRepository.findByTrajectoryId(10))
                    .thenReturn(List.of());

            TrajectoryEntity load = TrajectoryEntity.builder()
                    .type(TrajectoryType.MISC_LOAD.name())
                    .horizon("2030")
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), load, "user"));
        }

        // =========================
        // INSTALLED POWER TESTS
        // =========================

        @Test
        void shouldReturn_whenNoLoadFactor_forInstalledPower() {
            Integer studyId = 1;

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of());

            when(miscClusterCapacityRepository.findByTrajectoryId(1))
                    .thenReturn(List.of());

            TrajectoryEntity installed = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .horizon("2030")
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), installed, "user"));
        }

        @Test
        void shouldReturn_whenNoCapacities_forInstalledPower() {
            Integer studyId = 1;

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(new TrajectoryEntity()));

            when(miscClusterCapacityRepository.findByTrajectoryId(1))
                    .thenReturn(List.of());

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR"))
                    .thenReturn(List.of());

            TrajectoryEntity installed = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .horizon("2030")
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(studyId, new HashSet<>(), installed, "user"));
        }

        // =========================
        // FULL FLOW TEST
        // =========================

        @Test
        void shouldPassFullFlow_whenHeadersAreValid() throws Exception {
            Integer studyId = 1;

            GroupAreaMiscCapacity cap = mock(GroupAreaMiscCapacity.class);
            when(cap.getGroupe()).thenReturn("g1");
            when(cap.getCluster()).thenReturn("c1");
            when(cap.getArea()).thenReturn("FR");

            when(miscClusterCapacityRepository.findByTrajectoryId(1))
                    .thenReturn(List.of(cap));

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR"))
                    .thenReturn(List.of());

            TrajectoryEntity loadFactor = TrajectoryEntity.builder()
                    .fileName("lf")
                    .area("FR")
                    .horizon("2030")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(loadFactor));

            TrajectoryServiceImpl spy = spy(trajectoryService);

            Path path = mock(Path.class);
            doReturn(path).when(spy).buildTrajectoryPath(any(), any());

            try (MockedStatic<MiscFileProcessorServiceImpl> mocked =
                         mockStatic(MiscFileProcessorServiceImpl.class)) {

                mocked.when(() ->
                                MiscFileProcessorServiceImpl.readHeaderAreas(any(), any(), any()))
                        .thenReturn(List.of("fr"));

                TrajectoryEntity installed = TrajectoryEntity.builder()
                        .id(1)
                        .type(TrajectoryType.MISC_CAPACITY.name())
                        .area("FR")
                        .horizon("2030")
                        .build();

                assertDoesNotThrow(() ->
                        spy.checkTrajectoryCoherence(studyId, new HashSet<>(), installed, "user"));
            }
        }

        // =========================
        // ERROR CASE
        // =========================

        @Test
        void shouldThrowException_whenHeaderMissing() throws Exception {
            Integer studyId = 1;

            GroupAreaMiscCapacity cap = mock(GroupAreaMiscCapacity.class);
            when(cap.getGroupe()).thenReturn("g1");
            when(cap.getCluster()).thenReturn("c1");
            when(cap.getArea()).thenReturn("FR");

            when(miscClusterCapacityRepository.findByTrajectoryId(1))
                    .thenReturn(List.of(cap));

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "FR"))
                    .thenReturn(List.of());

            TrajectoryEntity loadFactor = TrajectoryEntity.builder()
                    .fileName("lf")
                    .area("FR")
                    .horizon("2030")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(loadFactor));

            TrajectoryServiceImpl spy = spy(trajectoryService);

            Path path = mock(Path.class);
            doReturn(path).when(spy).buildTrajectoryPath(any(), any());

            try (MockedStatic<MiscFileProcessorServiceImpl> mocked =
                         mockStatic(MiscFileProcessorServiceImpl.class)) {

                mocked.when(() ->
                                MiscFileProcessorServiceImpl.readHeaderAreas(any(), any(), any()))
                        .thenReturn(List.of("de")); // mismatch

                TrajectoryEntity installed = TrajectoryEntity.builder()
                        .id(1)
                        .type(TrajectoryType.MISC_CAPACITY.name())
                        .area("FR")
                        .horizon("2030")
                        .build();

                assertThrows(BusinessException.class, () ->
                        spy.checkTrajectoryCoherence(studyId, new HashSet<>(), installed, "user"));
            }
        }


        @Test
        void shouldSkipOtherAreas_whenNoMatchingLoadFactor() {
            GroupAreaMiscCapacity cap = mock(GroupAreaMiscCapacity.class);
            when(cap.getArea()).thenReturn("DE");

            when(miscClusterCapacityRepository.findByTrajectoryId(1))
                    .thenReturn(List.of(cap));

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of());

            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), 1))
                    .thenReturn(List.of());

            TrajectoryEntity installed = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .horizon("2030")
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(1, new HashSet<>(), installed, "user"));
        }



        // =====================================================
        // IMPORT CASES
        // =====================================================

        @Test
        void shouldPassImportLoadFactor_whenNoInstalledPower() {
            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_CAPACITY.name(), 1))
                    .thenReturn(List.of());

            TrajectoryEntity load = TrajectoryEntity.builder()
                    .type(TrajectoryType.MISC_LOAD.name())
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(1, new HashSet<>(), load, "user"));
        }

        @Test
        void shouldPassImportInstalledPower_whenNoLoadFactor() {
            when(trajectoryRepository.findByTypeAndStudyId(
                    TrajectoryType.MISC_LOAD.name(), 1))
                    .thenReturn(List.of());

            TrajectoryEntity installed = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .build();

            assertDoesNotThrow(() ->
                    trajectoryService.checkTrajectoryCoherence(1, new HashSet<>(), installed, "user"));
        }
        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_verifiesLoadFactorWhenSpecificAreaFound() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon);

            verify(spyService, times(1)).verifyLoadFactorAreaHeaders(loadFactorFR, "fr", groupCluster, horizon);
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_fallsBackToOthersWhenSpecificAreaNotFound() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("es");

            TrajectoryEntity loadFactorOthers = TrajectoryEntity.builder()
                    .fileName("load_others")
                    .area("OTHERS")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorOthers);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon);

            verify(spyService, times(1)).verifyLoadFactorAreaHeaders(loadFactorOthers, "es", groupCluster, horizon);
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_skipWhenNoLoadFactorFound() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("it");

            List<TrajectoryEntity> loadFactorTrajectories = List.of();

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            assertDoesNotThrow(() -> spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon));

            verify(spyService, never()).verifyLoadFactorAreaHeaders(any(), any(), any(), any());
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_handlesMultipleAreas() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr", "de", "it");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon(horizon)
                    .build();

            TrajectoryEntity loadFactorOthers = TrajectoryEntity.builder()
                    .fileName("load_others")
                    .area("OTHERS")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR, loadFactorOthers);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon);

            verify(spyService, atLeastOnce()).verifyLoadFactorAreaHeaders(any(), any(), eq(groupCluster), eq(horizon));
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_handlesEmptyAreasSet() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of();

            TrajectoryEntity loadFactorOthers = TrajectoryEntity.builder()
                    .fileName("load_others")
                    .area("OTHERS")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorOthers);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            assertDoesNotThrow(() -> spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon));

            verify(spyService, never()).verifyLoadFactorAreaHeaders(any(), any(), any(), any());
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_verifiesHeadersWithNullHorizon() throws IOException {
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon("2030-2031")
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, null);

            verify(spyService, times(1)).verifyLoadFactorAreaHeaders(loadFactorFR, "fr", groupCluster, null);
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_prioritizesSpecificAreaOverOthers() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon(horizon)
                    .build();

            TrajectoryEntity loadFactorOthers = TrajectoryEntity.builder()
                    .fileName("load_others")
                    .area("OTHERS")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR, loadFactorOthers);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(eq(loadFactorFR), eq("fr"), eq(groupCluster), eq(horizon));
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(eq(loadFactorOthers), any(), any(), any());

            spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon);

            verify(spyService, times(1)).verifyLoadFactorAreaHeaders(loadFactorFR, "fr", groupCluster, horizon);
            verify(spyService, never()).verifyLoadFactorAreaHeaders(eq(loadFactorOthers), any(), any(), any());
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_throwsExceptionWhenVerifyLoadFactorHeadersFails() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doThrow(BusinessException.class).when(spyService).verifyLoadFactorAreaHeaders(any(), any(), any(), any());

            assertThrows(BusinessException.class, () ->
                spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon)
            );
        }

        @Test
        void validateOthersInstalledPowerAreasAgainstLoadFactors_continueProcessingWhenOneAreaFails() throws IOException {
            String horizon = "2030-2031";
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas");
            Set<String> areasInInstalledPower = Set.of("fr", "de");

            TrajectoryEntity loadFactorFR = TrajectoryEntity.builder()
                    .fileName("load_fr")
                    .area("fr")
                    .horizon(horizon)
                    .build();

            List<TrajectoryEntity> loadFactorTrajectories = List.of(loadFactorFR);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doThrow(BusinessException.class).when(spyService).verifyLoadFactorAreaHeaders(loadFactorFR, "fr", groupCluster, horizon);
            doNothing().when(spyService).verifyLoadFactorAreaHeaders(any(), eq("de"), any(), any());

            assertThrows(BusinessException.class, () ->
                spyService.validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon)
            );
        }
        @Test
        void controlesMiscOnImportLoadFactor_returnsEarlyWhenNoInstalledPowerTrajectories() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            String horizon = "2030-2031";

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of());

            trajectoryService.controlesMiscOnImportLoadFactor(studyId, area, horizon);

            verify(trajectoryRepository, times(1)).findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId);
            verify(trajectoryRepository, never()).findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);
        }

        @Test
        void controlesMiscOnImportLoadFactor_validatesInstalledPowerWhenLoadFactorTrajectoriesExist() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            String horizon = "2030-2031";

            TrajectoryEntity installedPowerTraj = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .horizon(horizon)
                    .build();

            TrajectoryEntity loadFactorTraj = TrajectoryEntity.builder()
                    .id(2)
                    .type(TrajectoryType.MISC_LOAD.name())
                    .area("FR")
                    .horizon(horizon)
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installedPowerTraj));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(loadFactorTraj));

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());

            spyService.controlesMiscOnImportLoadFactor(studyId, area, horizon);

            verify(spyService, times(1)).validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj, List.of(loadFactorTraj), horizon);
        }

        @Test
        void controlesMiscOnImportLoadFactor_validatesAllInstalledPowerTrajectories() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            String horizon = "2030-2031";

            TrajectoryEntity installedPowerTraj1 = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .build();

            TrajectoryEntity installedPowerTraj2 = TrajectoryEntity.builder()
                    .id(2)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("DE")
                    .build();

            TrajectoryEntity loadFactorTraj = TrajectoryEntity.builder()
                    .id(3)
                    .type(TrajectoryType.MISC_LOAD.name())
                    .area("FR")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installedPowerTraj1, installedPowerTraj2));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(loadFactorTraj));

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());

            spyService.controlesMiscOnImportLoadFactor(studyId, area, horizon);

            verify(spyService, times(1)).validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj1, List.of(loadFactorTraj), horizon);
            verify(spyService, times(1)).validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj2, List.of(loadFactorTraj), horizon);
            verify(spyService, times(2)).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());
        }

        @Test
        void controlesMiscOnImportLoadFactor_handlesEmptyLoadFactorTrajectories() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            String horizon = "2030-2031";

            TrajectoryEntity installedPowerTraj = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installedPowerTraj));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of());

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());

            spyService.controlesMiscOnImportLoadFactor(studyId, area, horizon);

            verify(spyService, times(1)).validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj, List.of(), horizon);
        }

        @Test
        void controlesMiscOnImportLoadFactor_throwsIOExceptionWhenValidationFails() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            String horizon = "2030-2031";

            TrajectoryEntity installedPowerTraj = TrajectoryEntity.builder()
                    .id(1)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("FR")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installedPowerTraj));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of());

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doThrow(new IOException("File not found")).when(spyService).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());

            assertThrows(IOException.class, () ->
                spyService.controlesMiscOnImportLoadFactor(studyId, area, horizon)
            );
        }

        @Test
        void controlesMiscOnImportLoadFactor_passesCorrectParametersToValidation() throws IOException {
            Integer studyId = 2;
            String area = "DE";
            String horizon = "2025-2026";

            TrajectoryEntity installedPowerTraj = TrajectoryEntity.builder()
                    .id(5)
                    .type(TrajectoryType.MISC_CAPACITY.name())
                    .area("DE")
                    .build();

            TrajectoryEntity loadFactorTraj = TrajectoryEntity.builder()
                    .id(6)
                    .type(TrajectoryType.MISC_LOAD.name())
                    .area("DE")
                    .build();

            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId))
                    .thenReturn(List.of(installedPowerTraj));
            when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId))
                    .thenReturn(List.of(loadFactorTraj));

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).validateInstalledPowerAgainstLoadFactors(anyInt(), any(), anyList(), anyString());

            spyService.controlesMiscOnImportLoadFactor(studyId, area, horizon);

            verify(spyService).validateInstalledPowerAgainstLoadFactors(eq(studyId), eq(installedPowerTraj), eq(List.of(loadFactorTraj)), eq(horizon));
        }

        @Test
        void controlesMiscOnImportInstalledPower_callsControlsMethodWhenValidInputProvided() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            MiscClusterCapacityEntity capacity1 = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("FR")
                    .build();
            MiscClusterCapacityEntity capacity2 = MiscClusterCapacityEntity.builder()
                    .groupe("group2")
                    .cluster("cluster2")
                    .area("FR")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity1, capacity2);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            verify(spyService, times(1)).controlesMiscInstalledPower(eq(studyId), any(), eq(area), isNull());
        }

        @Test
        void controlesMiscOnImportInstalledPower_passesNullHorizonToControlesMethod() throws IOException {
            Integer studyId = 1;
            String area = "IT";
            MiscClusterCapacityEntity capacity = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("IT")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            ArgumentCaptor<String> horizonCaptor = ArgumentCaptor.forClass(String.class);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), horizonCaptor.capture());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            assertNull(horizonCaptor.getValue());
            verify(spyService).controlesMiscInstalledPower(eq(studyId), any(), eq(area), isNull());
        }

        @Test
        void controlesMiscOnImportInstalledPower_handlesEmptyCapacityList() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            List<MiscClusterCapacityEntity> capacities = List.of();

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            verify(spyService, times(1)).controlesMiscInstalledPower(eq(studyId), any(), eq(area), isNull());
        }

        @Test
        void controlesMiscOnImportInstalledPower_propagatesIOExceptionFromControlsMethod() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            MiscClusterCapacityEntity capacity = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("FR")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doThrow(new IOException("File error")).when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), isNull());

            assertThrows(IOException.class, () ->
                    spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area)
            );
        }

        @Test
        void controlesMiscOnImportInstalledPower_passesStudyIdCorrectly() throws IOException {
            Integer studyId = 99;
            String area = "FR";
            MiscClusterCapacityEntity capacity = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("FR")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            ArgumentCaptor<Integer> studyIdCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(spyService).controlesMiscInstalledPower(studyIdCaptor.capture(), any(), eq(area), isNull());
            assertEquals(99, studyIdCaptor.getValue());
        }

        @Test
        void controlesMiscOnImportInstalledPower_passesAreaCorrectly() throws IOException {
            Integer studyId = 1;
            String area = "OTHERS";
            MiscClusterCapacityEntity capacity = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("OTHERS")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            ArgumentCaptor<String> areaCaptor = ArgumentCaptor.forClass(String.class);
            verify(spyService).controlesMiscInstalledPower(eq(studyId), any(), areaCaptor.capture(), isNull());
            assertEquals("OTHERS", areaCaptor.getValue());
        }

        @Test
        void controlesMiscOnImportInstalledPower_handlesMultipleCapacitiesWithDifferentGroups() throws IOException {
            Integer studyId = 1;
            String area = "FR";
            List<MiscClusterCapacityEntity> capacities = List.of(
                    MiscClusterCapacityEntity.builder().groupe("group1").cluster("cluster1").area("FR").build(),
                    MiscClusterCapacityEntity.builder().groupe("group2").cluster("cluster2").area("FR").build(),
                    MiscClusterCapacityEntity.builder().groupe("group3").cluster("cluster3").area("FR").build()
            );

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), anyString(), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, area);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<GroupAreaMiscCapacity>> captor = ArgumentCaptor.forClass(List.class);
            verify(spyService).controlesMiscInstalledPower(eq(studyId), captor.capture(), eq(area), isNull());
            List<GroupAreaMiscCapacity> convertedCapacities = captor.getValue();
            assertEquals(3, convertedCapacities.size());
        }

        @Test
        void controlesMiscOnImportInstalledPower_handlesNullArea() throws IOException {
            Integer studyId = 1;
            MiscClusterCapacityEntity capacity = MiscClusterCapacityEntity.builder()
                    .groupe("group1")
                    .cluster("cluster1")
                    .area("FR")
                    .build();
            List<MiscClusterCapacityEntity> capacities = List.of(capacity);

            TrajectoryServiceImpl spyService = spy(trajectoryService);
            doNothing().when(spyService).controlesMiscInstalledPower(anyInt(), anyList(), nullable(String.class), anyString());

            spyService.controlesMiscOnImportInstalledPower(studyId, capacities, null);

            verify(spyService, times(1)).controlesMiscInstalledPower(eq(studyId), any(), isNull(), isNull());
        }

    @Test
    void unlinkTrajectoryFromStudy_whenLastDsrWithTimeSeries_unlinksCmTrajectories() {
        // Arrange
        var studyId = 1;
        var dsrTrajectoryId = 10;
        var cmTrajectoryId = 20;

        var dsrTrajectory = TrajectoryEntity.builder()
                .id(dsrTrajectoryId)
                .type(TrajectoryType.DSR.name())
                .hasTimeSeries(true)
                .build();

        var cmTrajectory = TrajectoryEntity.builder()
                .id(cmTrajectoryId)
                .type(TrajectoryType.DSR_CAPACITY_MODULATION.name())
                .fileName("cm_test.xlsx")
                .build();

        var study = StudyEntity.builder().id(studyId).trajectories(new HashSet<>(Set.of(dsrTrajectory))).build();
        dsrTrajectory.setScenarioEntities(new HashSet<>(Set.of(study)));
        var cmStudy = StudyEntity.builder().id(studyId).trajectories(new HashSet<>(Set.of(cmTrajectory))).build();
        cmTrajectory.setScenarioEntities(new HashSet<>(Set.of(cmStudy)));

        var dsrStudyTrajectoryKey = StudyTrajectoryKey.builder()
                .trajectoryId(dsrTrajectoryId)
                .scenarioId(studyId)
                .build();
        var dsrStudyTrajectory = StudyTrajectoryEntity.builder()
                .id(dsrStudyTrajectoryKey)
                .studyEntity(study)
                .trajectory(dsrTrajectory)
                .build();

        var cmStudyTrajectoryKey = StudyTrajectoryKey.builder()
                .trajectoryId(cmTrajectoryId)
                .scenarioId(studyId)
                .build();
        var cmStudyTrajectory = StudyTrajectoryEntity.builder()
                .id(cmStudyTrajectoryKey)
                .studyEntity(cmStudy)
                .trajectory(cmTrajectory)
                .build();

        when(trajectoryRepository.findById(dsrTrajectoryId)).thenReturn(Optional.of(dsrTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR.name(), studyId))
                .thenReturn(List.of(dsrTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR_CAPACITY_MODULATION.name(), studyId))
                .thenReturn(List.of(cmTrajectory));

        when(studyTrajectoryRepository.findById(dsrStudyTrajectoryKey)).thenReturn(Optional.of(dsrStudyTrajectory));
        when(studyTrajectoryRepository.findById(cmStudyTrajectoryKey)).thenReturn(Optional.of(cmStudyTrajectory));

        // Act
        trajectoryService.unlinkTrajectoryFromStudy(dsrTrajectoryId, studyId);

        // Assert
        verify(studyTrajectoryRepository).delete(dsrStudyTrajectory);
        verify(studyTrajectoryRepository).delete(cmStudyTrajectory); // CM Link is deleted
        verify(trajectoryRepository, never()).delete(any()); // No physical TrajectoryEntity is deleted
    }

    @Test
    void unlinkTrajectoryFromStudy_whenNotLastDsrWithTimeSeries_doesNotTriggerCmUnlinking() {
        // Arrange
        var studyId = 1;
        var dsrTrajectoryId = 10;
        var otherDsrTrajectoryId = 11;

        var dsrTrajectory = TrajectoryEntity.builder()
                .id(dsrTrajectoryId)
                .type(TrajectoryType.DSR.name())
                .hasTimeSeries(true)
                .build();

        var otherDsrTrajectory = TrajectoryEntity.builder()
                .id(otherDsrTrajectoryId)
                .type(TrajectoryType.DSR.name())
                .hasTimeSeries(true)
                .build();

        var study = StudyEntity.builder().id(studyId).trajectories(new HashSet<>(Set.of(dsrTrajectory))).build();
        dsrTrajectory.setScenarioEntities(new HashSet<>(Set.of(study)));

        var dsrStudyTrajectoryKey = StudyTrajectoryKey.builder()
                .trajectoryId(dsrTrajectoryId)
                .scenarioId(studyId)
                .build();
        var dsrStudyTrajectory = StudyTrajectoryEntity.builder()
                .id(dsrStudyTrajectoryKey)
                .studyEntity(study)
                .trajectory(dsrTrajectory)
                .build();

        when(trajectoryRepository.findById(dsrTrajectoryId)).thenReturn(Optional.of(dsrTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR.name(), studyId))
                .thenReturn(List.of(dsrTrajectory, otherDsrTrajectory));

        when(studyTrajectoryRepository.findById(dsrStudyTrajectoryKey)).thenReturn(Optional.of(dsrStudyTrajectory));

        // Act
        trajectoryService.unlinkTrajectoryFromStudy(dsrTrajectoryId, studyId);

        // Assert
        verify(studyTrajectoryRepository).delete(dsrStudyTrajectory);
        verify(trajectoryRepository, never()).findByTypeAndStudyId(eq(TrajectoryType.DSR_CAPACITY_MODULATION.name()), any());
    }

        @Test
        void shouldCreateNewTrajectoryWhenNoExistingTrajectory() throws Exception {
            Path base = tempDir.resolve("hydro");
            Path traj = base.resolve("BP_23");
            Files.createDirectories(traj);
            // GIVEN
            String type = "TYPE";
            String fileName = "BP_23";
            String horizon = "2030";
            String area = "AREA";
            String tech = "TECH";

            when(userService.getCurrentUserDetails())
                    .thenReturn(UserInfoDto.builder().nni("NNI123").build());

            try (var mockedStatic = mockStatic(Utils.class)) {
                mockedStatic.when(() -> Utils.calculateDirectoryChecksum(traj)).thenReturn("checksum123");
                when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        fileName, type, horizon, area, tech
                )).thenReturn(Optional.empty());

                // WHEN
                TrajectoryEntity result = trajectoryService.buildDirectoryTrajectory(
                        type, fileName, traj, horizon, area, tech
                );

                // THEN
                assertEquals(1, result.getVersion());
                assertEquals("checksum123", result.getChecksum());
                assertEquals("NNI123", result.getCreatedBy());
                assertEquals(fileName, result.getFileName());
                assertTrue(result.getFileSize() > 0);
                assertTrue(result.getHasTimeSeries());
            }

        }

        @Test
        void shouldIncrementVersionWhenExistingTrajectoryWithDifferentChecksum() throws Exception {
            Path base = tempDir.resolve("hydro");
            Path traj = base.resolve("BP_23");
            Files.createDirectories(traj);
            // GIVEN
            String type = "TYPE";
            String fileName = "file.csv";
            String horizon = "2030";
            String area = "AREA";
            String tech = "TECH";

            TrajectoryEntity existing = TrajectoryEntity.builder()
                    .version(3)
                    .checksum("oldChecksum")
                    .build();

            when(userService.getCurrentUserDetails())
                    .thenReturn(UserInfoDto.builder().nni("NNI123").build());
            try (var mockedStatic = mockStatic(Utils.class)) {
                mockedStatic.when(() -> Utils.calculateDirectoryChecksum(traj)).thenReturn("newChecksum");
                when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        fileName, type, horizon, area, tech
                )).thenReturn(Optional.of(existing));

                // WHEN
                TrajectoryEntity result = trajectoryService.buildDirectoryTrajectory(
                        type, fileName, traj, horizon, area, tech
                );

                // THEN
                assertEquals(4, result.getVersion());
                assertEquals("newChecksum", result.getChecksum());
            }
        }

    @Test
    void shouldThrowExceptionWhenChecksumIsSame() throws Exception {
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve("BP_23");
        Files.createDirectories(traj);

        String type = "TYPE";
        String fileName = "BP_23";
        String horizon = "2030";
        String area = "AREA";
        String tech = "TECH";

        String checkSum = "checksum123";

        TrajectoryEntity existing = TrajectoryEntity.builder()
                .version(2)
                .checksum(checkSum)
                .fileName(fileName)
                .horizon(horizon)
                .build();

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("NNI123").build());

        try (var mockedStatic = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            mockedStatic.when(() -> Utils.calculateDirectoryChecksum(traj))
                    .thenReturn(checkSum);

            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    fileName,
                    type,
                    horizon,
                    area,
                    tech
            )).thenReturn(Optional.of(existing));

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    trajectoryService.buildDirectoryTrajectory(
                            type, fileName, traj, horizon, area, tech
                    )
            );

            assertNotNull(exception);
            verify(trajectoryRepository).findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    fileName,
                    type,
                    horizon,
                    area,
                    tech
            );
            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
            assertEquals("File already processed with same content {0}", exception.getMessage());
            assertEquals(List.of(traj.getFileName().toString()), exception.getErrorMessageArguments());
        }
    }

    @Test
    void shouldCreateNewTrajectoryWhenP2GCapacityCostType() throws Exception {
        Path base = tempDir.resolve("p2g");
        Path traj = base.resolve("p2g_traj");
        Files.createDirectories(traj);

        String type = TrajectoryType.P2G_CAPACITY_COST.name();
        String fileName = "p2g_traj";
        String horizon = "2030";
        String area = null;
        String tech = null;

        Map<String, List<String>> expectedFilesWithSheets = Map.of(
                "P2G_capacity.xlsx", List.of("parameters", horizon),
                "P2G_costs.xlsx", List.of("costs")
        );

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("NNI123").build());

        try (var mockedStatic = mockStatic(Utils.class)) {
            mockedStatic.when(() -> Utils.calculateDirectoryChecksumWithSpecificSheets(traj, expectedFilesWithSheets))
                    .thenReturn("p2gChecksum123");
            mockedStatic.when(() -> Utils.civilToChevalHorizon(horizon)).thenReturn("2029-2030");
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    fileName, type, horizon, area, tech
            )).thenReturn(Optional.empty());

            TrajectoryEntity result = trajectoryService.buildDirectoryTrajectory(
                    type, fileName, traj, horizon, area, tech
            );

            assertEquals(1, result.getVersion());
            assertEquals("p2gChecksum123", result.getChecksum());
            assertEquals("NNI123", result.getCreatedBy());
            assertEquals(fileName, result.getFileName());
            mockedStatic.verify(() -> Utils.calculateDirectoryChecksumWithSpecificSheets(traj, expectedFilesWithSheets));
            mockedStatic.verify(() -> Utils.calculateDirectoryChecksum(any()), never());
        }
    }

    @Test
    void shouldThrowExceptionWhenChecksumIsSameForP2GCapacityCost() throws Exception {
        Path base = tempDir.resolve("p2g");
        Path traj = base.resolve("p2g_traj");
        Files.createDirectories(traj);

        String type = TrajectoryType.P2G_CAPACITY_COST.name();
        String fileName = "p2g_traj";
        String horizon = "2030";
        String area = null;
        String tech = null;
        String checkSum = "p2gChecksum123";

        Map<String, List<String>> expectedFilesWithSheets = Map.of(
                "P2G_capacity.xlsx", List.of("parameters", horizon),
                "P2G_costs.xlsx", List.of("costs")
        );

        TrajectoryEntity existing = TrajectoryEntity.builder()
                .version(2)
                .checksum(checkSum)
                .fileName(fileName)
                .horizon(horizon)
                .build();

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("NNI123").build());

        try (var mockedStatic = mockStatic(Utils.class)) {
            mockedStatic.when(() -> Utils.calculateDirectoryChecksumWithSpecificSheets(traj, expectedFilesWithSheets))
                    .thenReturn(checkSum);
            mockedStatic.when(() -> Utils.civilToChevalHorizon(horizon)).thenReturn("2029-2030");

            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    fileName, type, horizon, area, tech
            )).thenReturn(Optional.of(existing));

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    trajectoryService.buildDirectoryTrajectory(
                            type, fileName, traj, horizon, area, tech
                    )
            );

            assertNotNull(exception);
            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
            assertEquals("File already processed with same content {0}", exception.getMessage());
            assertEquals(List.of(traj.getFileName().toString()), exception.getErrorMessageArguments());
            mockedStatic.verify(() -> Utils.calculateDirectoryChecksumWithSpecificSheets(traj, expectedFilesWithSheets));
            mockedStatic.verify(() -> Utils.calculateDirectoryChecksum(any()), never());
        }
    }

    @Test
        void shouldUseUnknownUserWhenUserIsNull() throws Exception {
            Path base = tempDir.resolve("hydro");
            Path traj = base.resolve("BP_23");
            Files.createDirectories(traj);
            // GIVEN
            when(userService.getCurrentUserDetails()).thenReturn(null);

            try (var mockedStatic = mockStatic(Utils.class)) {
                mockedStatic.when(() -> Utils.calculateDirectoryChecksum(traj)).thenReturn("checksum123");
                when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        any(), any(), any(), any(), any()
                )).thenReturn(Optional.empty());

                // WHEN
                TrajectoryEntity result = trajectoryService.buildDirectoryTrajectory(
                        "TYPE", "file.csv", traj, "2030", "AREA", "TECH"
                );

                // THEN
                assertEquals("UNKNOWN_USER", result.getCreatedBy());
            }
        }

    // ==================== RES Coherence Validation Tests ====================

    @Test
    void linkTrajectoryToStudy_shouldValidateIPTDCoherence_whenTrajectoryTypeIsRES_CAPACITY() throws IOException {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_CAPACITY.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_CAPACITY);

        // Then
        verify(resCoherenceCheckService, times(1)).validateIPTDCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, times(1)).validateIPLoadFactorCoherence(studyId, trajectory);
    }

    @Test
    void linkTrajectoryToStudy_shouldValidateIPTDCoherence_whenTrajectoryTypeIsRES_TECHNOLOGY_DISTRIBUTION() throws IOException {
        // Given
        Integer trajectoryId = 2;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION);

        // Then
        verify(resCoherenceCheckService, times(1)).validateIPTDCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, never()).validateIPLoadFactorCoherence(studyId, trajectory);
    }

    @Test
    void linkTrajectoryToStudy_shouldValidateIPLoadFactorCoherence_whenTrajectoryTypeIsRES_LOAD() throws IOException {
        // Given
        Integer trajectoryId = 3;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_LOAD.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_LOAD);

        // Then
        verify(resCoherenceCheckService, never()).validateIPTDCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, times(1)).validateIPLoadFactorCoherence(studyId, trajectory);
    }

    @Test
    void linkTrajectoryToStudy_shouldNotValidateRESCoherence_whenTrajectoryTypeIsRES_ZONAL_DISTRIBUTION() throws IOException {
        // Given
        Integer trajectoryId = 4;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_ZONAL_DISTRIBUTION.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_ZONAL_DISTRIBUTION);

        // Then
        verify(resCoherenceCheckService, never()).validateIPTDCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, never()).validateIPLoadFactorCoherence(studyId, trajectory);
    }

    @Test
    void linkTrajectoryToStudy_shouldNotValidateRESCoherence_whenTrajectoryTypeIsNonRES() throws IOException {
        // Given
        Integer trajectoryId = 5;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .thermalClusterCapacities(List.of())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.THERMAL_CAPACITY);

        // Then
        verify(resCoherenceCheckService, never()).validateIPTDCoherence(any(), any());
        verify(resCoherenceCheckService, never()).validateIPLoadFactorCoherence(any(), any());
    }

    @Test
    void linkTrajectoryToStudy_shouldValidateLFDTCoherence_whenTrajectoryTypeIsRES_TECHNOLOGY_DISTRIBUTION() throws IOException {
        // Given
        Integer trajectoryId = 6;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION);

        // Then
        verify(resCoherenceCheckService, times(1)).validateIPTDCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, times(1)).validateLFDTCoherence(studyId, trajectory);
    }

    @Test
    void linkTrajectoryToStudy_shouldValidateLFDTCoherence_whenTrajectoryTypeIsRES_LOAD_AndImportedWithoutTechnology() throws IOException {
        // Given
        Integer trajectoryId = 7;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.RES_LOAD.name())
                .technology(null) // LF without technology triggers LF/DT validation
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.RES_LOAD);

        // Then
        verify(resCoherenceCheckService, times(1)).validateIPLoadFactorCoherence(studyId, trajectory);
        verify(resCoherenceCheckService, times(1)).validateLFDTCoherence(studyId, trajectory);
    }

    @Test
    void findTrajectoriesByType_returnsScenarioBuilderFilesWithCorrectPrefix(@TempDir Path tempDir) throws IOException {
        // Given
        Files.createDirectories(tempDir);
        String trajectoryFilePath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        
        Path scenarioBuilderPath = tempDir.resolve(trajectoryFilePath).resolve(scenarioBuilderDir);
        Files.createDirectories(scenarioBuilderPath);
        
        Path file1 = scenarioBuilderPath.resolve("scenario_builder_test1.xlsx");
        Path file2 = scenarioBuilderPath.resolve("scenario_builder_test2.xlsx");
        Path invalidFile = scenarioBuilderPath.resolve("invalid_file.xlsx");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(invalidFile);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryFilePath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.SCENARIO_BUILDER, null, null, null);
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(dto -> dto.getFileName().startsWith("scenario_builder_")));
        assertTrue(result.stream().allMatch(dto -> dto.getFileName().endsWith(".xlsx")));
    }

    @Test
    void findTrajectoriesByType_returnsScenarioBuilderFilesFilteredByFileName(@TempDir Path tempDir) throws IOException {
        // Given
        Files.createDirectories(tempDir);
        String trajectoryFilePath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        
        Path scenarioBuilderPath = tempDir.resolve(trajectoryFilePath).resolve(scenarioBuilderDir);
        Files.createDirectories(scenarioBuilderPath);
        
        Path file1 = scenarioBuilderPath.resolve("scenario_builder_production.xlsx");
        Path file2 = scenarioBuilderPath.resolve("scenario_builder_test.xlsx");
        
        Files.createFile(file1);
        Files.createFile(file2);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryFilePath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.SCENARIO_BUILDER, null, null, "production");
        
        // Then
        assertEquals(1, result.size());
        assertEquals("scenario_builder_production.xlsx", result.get(0).getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsEmptyList_whenNoScenarioBuilderFilesExist(@TempDir Path tempDir) throws IOException {
        // Given
        Files.createDirectories(tempDir);
        String trajectoryFilePath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        
        Path scenarioBuilderPath = tempDir.resolve(trajectoryFilePath).resolve(scenarioBuilderDir);
        Files.createDirectories(scenarioBuilderPath);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryFilePath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.SCENARIO_BUILDER, null, null, null);
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void isDirectoryTrajectory_returnsFalse_forScenarioBuilderType() {
        // Given
        Path dirPath = Paths.get("/some/path");
        
        // When & Then
        assertFalse(trajectoryService.isDirectoryTrajectory(dirPath, TrajectoryType.SCENARIO_BUILDER, null));
    }

    @Test
    void linkTrajectoryToStudy_shouldLinkScenarioBuilderTrajectory_andReplaceExisting() throws IOException {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        String userNni = "user";

        TrajectoryEntity oldTrajectory = TrajectoryEntity.builder()
                .id(2)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();

        StudyTrajectoryEntity oldLink = StudyTrajectoryEntity.builder()
                .trajectory(oldTrajectory)
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(studyId)
                .studyTrajectoryEntities(new HashSet<>(List.of(oldLink)))
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(newTrajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.SCENARIO_BUILDER);

        // Then
        assertEquals(trajectoryId, result.getId());
        verify(studyTrajectoryRepository).delete(oldLink);
        verify(studyTrajectoryRepository).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void checkTrajectoryCoherence_shouldNotThrowException_forScenarioBuilderType() throws IOException {
        // Given
        Integer studyId = 1;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();
        Set<WarningMessageEntity> warningMessages = new HashSet<>();

        when(warningRepository.saveAll(any())).thenReturn(Collections.emptyList());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> trajectoryService.checkTrajectoryCoherence(studyId, warningMessages, trajectory, "user"));
    }

    @Test
    void getDirectoryByTrajectoryType_returnScenarioBuilderDirectory() throws IOException {
        // Given
        String expectedDir = "settings/scenario_builder";
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(expectedDir);

        // When
        String result = trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.SCENARIO_BUILDER, null, null);

        // Then
        assertEquals(expectedDir, result);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameContainsFromDB_returnsScenarioBuilderTrajectories() {
        // Given
        List<TrajectoryEntity> expectedEntities = List.of(
                TrajectoryEntity.builder()
                        .id(1)
                        .type(TrajectoryType.SCENARIO_BUILDER.name())
                        .fileName("scenario_builder_test1.xlsx")
                        .build(),
                TrajectoryEntity.builder()
                        .id(2)
                        .type(TrajectoryType.SCENARIO_BUILDER.name())
                        .fileName("scenario_builder_test2.xlsx")
                        .build()
        );
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(
                TrajectoryType.SCENARIO_BUILDER.name(), "2023-2024", "test", null, null))
                .thenReturn(expectedEntities);

        // When
        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(
                TrajectoryType.SCENARIO_BUILDER, "2023-2024", "test", null, null);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getType().equals(TrajectoryType.SCENARIO_BUILDER.name())));
        verify(trajectoryRepository).findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(
                TrajectoryType.SCENARIO_BUILDER.name(), "2023-2024", "test", null, null);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameContainsFromDB_returnsEmptyForScenarioBuilder_whenNoMatches() {
        // Given
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(
                TrajectoryType.SCENARIO_BUILDER.name(), "2023-2024", "nonexistent", null, null))
                .thenReturn(Collections.emptyList());

        // When
        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameContainsFromDB(
                TrajectoryType.SCENARIO_BUILDER, "2023-2024", "nonexistent", null, null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void normalizeAndValidateDirectory_returnsCorrectPathForScenarioBuilder() throws IOException {
        // Given
        String nasDir = "/nas/data";
        String trajectoryPath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        String expectedPath = "/nas/data/trajectories/settings/scenario_builder";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir);
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryPath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);

        // When
        Path result = trajectoryService.normalizeAndValidateDirectory(TrajectoryType.SCENARIO_BUILDER, null, null);

        // Then
        assertTrue(result.toString().contains("settings"));
        assertTrue(result.toString().contains("scenario_builder"));
    }

    @Test
    void findTrajectoriesByType_ignoresInvalidScenarioBuilderFiles(@TempDir Path tempDir) throws IOException {
        // Given
        Files.createDirectories(tempDir);
        String trajectoryFilePath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        
        Path scenarioBuilderPath = tempDir.resolve(trajectoryFilePath).resolve(scenarioBuilderDir);
        Files.createDirectories(scenarioBuilderPath);
        
        Path validFile = scenarioBuilderPath.resolve("scenario_builder_valid.xlsx");
        Path invalidPrefix = scenarioBuilderPath.resolve("test_invalid.xlsx");
        Path noPrefix = scenarioBuilderPath.resolve("noscenariobuilder.xlsx");
        
        Files.createFile(validFile);
        Files.createFile(invalidPrefix);
        Files.createFile(noPrefix);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryFilePath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.SCENARIO_BUILDER, null, null, null);
        
        // Then
        assertEquals(1, result.size());
        assertEquals("scenario_builder_valid.xlsx", result.get(0).getFileName());
    }

    @Test
    void linkTrajectoryToStudy_shouldNotCallCoherenceCheckForScenarioBuilder() throws IOException {
        // Given
        Integer trajectoryId = 1;
        Integer studyId = 1;
        String userNni = "user";

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.SCENARIO_BUILDER);

        // Then - No coherence checks should be called for SCENARIO_BUILDER
        verify(resCoherenceCheckService, never()).validateIPTDCoherence(any(), any());
        verify(resCoherenceCheckService, never()).validateIPLoadFactorCoherence(any(), any());
    }

    @Test
    void findTrajectoriesByType_handlesScenarioBuilderWithSpecialCharactersInFileName(@TempDir Path tempDir) throws IOException {
        // Given
        Files.createDirectories(tempDir);
        String trajectoryFilePath = "trajectories";
        String scenarioBuilderDir = "settings/scenario_builder";
        
        Path scenarioBuilderPath = tempDir.resolve(trajectoryFilePath).resolve(scenarioBuilderDir);
        Files.createDirectories(scenarioBuilderPath);
        
        Path fileWithSpecialChars = scenarioBuilderPath.resolve("scenario_builder_test-2024_v2.xlsx");
        Files.createFile(fileWithSpecialChars);
        
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(trajectoryFilePath);
        when(antaresDataManagerProperties.getScenarioBuilderDirectory()).thenReturn(scenarioBuilderDir);
        
        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.SCENARIO_BUILDER, null, null, null);
        
        // Then
        assertEquals(1, result.size());
        assertEquals("scenario_builder_test-2024_v2.xlsx", result.get(0).getFileName());
    }

    @Test
    void linkTrajectoryToStudy_shouldPreserveScenarioBuilderAsOnlyOne() throws IOException {
        // Given - existing link
        Integer oldTrajectoryId = 1;
        Integer newTrajectoryId = 2;
        Integer studyId = 1;
        String userNni = "user";

        TrajectoryEntity oldTrajectory = TrajectoryEntity.builder()
                .id(oldTrajectoryId)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();

        StudyTrajectoryEntity oldLink = StudyTrajectoryEntity.builder()
                .trajectory(oldTrajectory)
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(studyId)
                .studyTrajectoryEntities(new HashSet<>(List.of(oldLink)))
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(newTrajectoryId)
                .type(TrajectoryType.SCENARIO_BUILDER.name())
                .build();

        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userNni).build());
        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(newTrajectoryId)).thenReturn(Optional.of(newTrajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When - link new scenario builder
        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(newTrajectoryId, studyId, TrajectoryType.SCENARIO_BUILDER);

        // Then
        assertEquals(newTrajectoryId, result.getId());
        // Verify old link was deleted and new one was created
        verify(studyTrajectoryRepository, times(1)).delete(oldLink);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void matchesPrefix_returnsTrue_whenTrajectoryTypeIsLINK_andFileNameStartsWithLinksPrefix() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("links_test.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.LINK, null, null);

        // Then
        assertTrue(result);
    }

    @Test
    void matchesPrefix_returnsFalse_whenTrajectoryTypeIsLINK_andFileNameDoesNotStartWithLinksPrefix() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("areas_test.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.LINK, null, null);

        // Then
        assertFalse(result);
    }

    @Test
    void matchesPrefix_returnsTrue_whenTrajectoryTypeIsAREA_ME() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("any_file.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.AREA_ME, null, null);

        // Then
        assertTrue(result);
    }

    @Test
    void matchesPrefix_returnsTrueForAnyFileName_whenTrajectoryTypeIsAREA_ME() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("random_name_12345.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.AREA_ME, null, null);

        // Then
        assertTrue(result);
    }

    @Test
    void matchesPrefix_returnsTrue_whenTrajectoryTypeIsLINK_ME() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("any_file.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.LINK_ME, null, null);

        // Then
        assertTrue(result);
    }

    @Test
    void matchesPrefix_returnsTrueForAnyFileName_whenTrajectoryTypeIsLINK_ME() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Given
        Path tempFile = tempDir.resolve("random_name_12345.xlsx");
        Files.createFile(tempFile);

        // When - call private method using reflection
        var method = TrajectoryServiceImpl.class.getDeclaredMethod("matchesPrefix", Path.class, TrajectoryType.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(trajectoryService, tempFile, TrajectoryType.LINK_ME, null, null);

        // Then
        assertTrue(result);
    }

    @Test
    void unlinkTrajectoryFromStudy_whenTrajectoryTypeIsNotAreaMe_doesNotCascadeDeleteLinkMe() {
        // Given - Testing that when type is DSR (not AREA_ME), LINK_ME trajectories are NOT deleted
        Integer dsrTrajectoryId = 5;
        Integer linkMeTrajectory1 = 2;
        Integer studyId = 100;

        // Create DSR trajectory (the one being deleted - NOT AREA_ME)
        TrajectoryEntity dsrTrajectory = TrajectoryEntity.builder()
                .id(dsrTrajectoryId)
                .type(TrajectoryType.DSR.name())
                .scenarioEntities(new HashSet<>())
                .build();

        // Create LINK_ME trajectory that should NOT be deleted
        TrajectoryEntity linkMeT1 = TrajectoryEntity.builder()
                .id(linkMeTrajectory1)
                .type(TrajectoryType.LINK_ME.name())
                .build();

        // Create study
        StudyEntity study = StudyEntity.builder()
                .id(studyId)
                .trajectories(new HashSet<>(Set.of(dsrTrajectory, linkMeT1)))
                .build();
        dsrTrajectory.getScenarioEntities().add(study);

        // Create StudyTrajectoryEntity for the DSR trajectory being deleted
        StudyTrajectoryKey dsrKey = StudyTrajectoryKey.builder()
                .trajectoryId(dsrTrajectoryId)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity dsrEntity = StudyTrajectoryEntity.builder()
                .id(dsrKey)
                .studyEntity(study)
                .trajectory(dsrTrajectory)
                .build();

        // When
        when(trajectoryRepository.findById(dsrTrajectoryId)).thenReturn(Optional.of(dsrTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(dsrTrajectory, linkMeT1));
        when(studyTrajectoryRepository.findById(dsrKey)).thenReturn(Optional.of(dsrEntity));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR.name(), studyId))
                .thenReturn(List.of(dsrTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR_CAPACITY_MODULATION.name(), studyId))
                .thenReturn(List.of());

        trajectoryService.unlinkTrajectoryFromStudy(dsrTrajectoryId, studyId);

        // Then
        // Verify that ONLY the DSR trajectory is deleted
        verify(studyTrajectoryRepository, times(1)).delete(dsrEntity);

        // Verify delete was called only 1 time (DSR only, NOT LINK_ME)
        verify(studyTrajectoryRepository, times(1)).delete(any());

        // Verify findById_ScenarioId was NOT called (because type != AREA_ME, so unlinkLinkMeTrajectoriesToAreaMe not called)
        verify(studyTrajectoryRepository, never()).findById_ScenarioId(studyId);
    }

    @Test
    void unlinkTrajectoryFromStudy_whenAreaMeHasNoLinkMe_onlyDeletesAreaMe() {
        // Given - Testing AREA_ME deletion when there are NO LINK_ME trajectories
        Integer areaTrajectoryId = 1;
        Integer dsrTrajectory = 4;
        Integer studyId = 100;

        // Create AREA_ME trajectory (the one being deleted)
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .id(areaTrajectoryId)
                .type(TrajectoryType.AREA_ME.name())
                .scenarioEntities(new HashSet<>())
                .build();

        // Create DSR trajectory (should NOT be unlinked)
        TrajectoryEntity dsrT = TrajectoryEntity.builder()
                .id(dsrTrajectory)
                .type(TrajectoryType.DSR.name())
                .build();

        // Create study
        StudyEntity study = StudyEntity.builder()
                .id(studyId)
                .trajectories(new HashSet<>(Set.of(areaTrajectory, dsrT)))
                .build();
        areaTrajectory.getScenarioEntities().add(study);

        // Create StudyTrajectoryEntity for AREA_ME
        StudyTrajectoryKey areaKey = StudyTrajectoryKey.builder()
                .trajectoryId(areaTrajectoryId)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity areaEntity = StudyTrajectoryEntity.builder()
                .id(areaKey)
                .studyEntity(study)
                .trajectory(areaTrajectory)
                .build();

        // Create StudyTrajectoryEntity for DSR
        StudyTrajectoryKey dsrKey = StudyTrajectoryKey.builder()
                .trajectoryId(dsrTrajectory)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity dsrEntity = StudyTrajectoryEntity.builder()
                .id(dsrKey)
                .studyEntity(study)
                .trajectory(dsrT)
                .build();

        // When
        when(trajectoryRepository.findById(areaTrajectoryId)).thenReturn(Optional.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(areaTrajectory, dsrT));
        when(studyTrajectoryRepository.findById(areaKey)).thenReturn(Optional.of(areaEntity));
        when(studyTrajectoryRepository.findById_ScenarioId(studyId))
                .thenReturn(List.of(areaEntity, dsrEntity));  // No LINK_ME trajectories
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR.name(), studyId))
                .thenReturn(List.of(dsrT));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR_CAPACITY_MODULATION.name(), studyId))
                .thenReturn(List.of());

        trajectoryService.unlinkTrajectoryFromStudy(areaTrajectoryId, studyId);

        // Then
        // Verify findById_ScenarioId was called once (for unlinkLinkMeTrajectoriesToAreaMe)
        verify(studyTrajectoryRepository, times(1)).findById_ScenarioId(studyId);

        // Verify only AREA_ME is deleted (no LINK_ME to delete)
        verify(studyTrajectoryRepository, times(1)).delete(areaEntity);

        // Verify DSR is NOT deleted
        verify(studyTrajectoryRepository, never()).delete(dsrEntity);

        // Verify delete was called only 1 time (AREA_ME only)
        verify(studyTrajectoryRepository, times(1)).delete(any());
    }

    @Test
    void unlinkTrajectoryFromStudy_whenAreaMeHasMultipleLinkMe_deletesAllLinkMe() {
        // Given - Testing AREA_ME deletion with multiple LINK_ME trajectories
        Integer areaTrajectoryId = 1;
        Integer linkMeTrajectory1 = 2;
        Integer linkMeTrajectory2 = 3;
        Integer linkMeTrajectory3 = 6;
        Integer studyId = 100;

        // Create AREA_ME trajectory
        TrajectoryEntity areaTrajectory = TrajectoryEntity.builder()
                .id(areaTrajectoryId)
                .type(TrajectoryType.AREA_ME.name())
                .scenarioEntities(new HashSet<>())
                .build();

        // Create multiple LINK_ME trajectories
        TrajectoryEntity linkMeT1 = TrajectoryEntity.builder()
                .id(linkMeTrajectory1)
                .type(TrajectoryType.LINK_ME.name())
                .build();
        TrajectoryEntity linkMeT2 = TrajectoryEntity.builder()
                .id(linkMeTrajectory2)
                .type(TrajectoryType.LINK_ME.name())
                .build();
        TrajectoryEntity linkMeT3 = TrajectoryEntity.builder()
                .id(linkMeTrajectory3)
                .type(TrajectoryType.LINK_ME.name())
                .build();

        // Create study
        StudyEntity study = StudyEntity.builder()
                .id(studyId)
                .trajectories(new HashSet<>(Set.of(areaTrajectory, linkMeT1, linkMeT2, linkMeT3)))
                .build();
        areaTrajectory.getScenarioEntities().add(study);

        // Create StudyTrajectoryEntity for AREA_ME
        StudyTrajectoryKey areaKey = StudyTrajectoryKey.builder()
                .trajectoryId(areaTrajectoryId)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity areaEntity = StudyTrajectoryEntity.builder()
                .id(areaKey)
                .studyEntity(study)
                .trajectory(areaTrajectory)
                .build();

        // Create StudyTrajectoryEntities for LINK_ME trajectories
        StudyTrajectoryKey linkMeKey1 = StudyTrajectoryKey.builder()
                .trajectoryId(linkMeTrajectory1)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity linkMeEntity1 = StudyTrajectoryEntity.builder()
                .id(linkMeKey1)
                .studyEntity(study)
                .trajectory(linkMeT1)
                .build();

        StudyTrajectoryKey linkMeKey2 = StudyTrajectoryKey.builder()
                .trajectoryId(linkMeTrajectory2)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity linkMeEntity2 = StudyTrajectoryEntity.builder()
                .id(linkMeKey2)
                .studyEntity(study)
                .trajectory(linkMeT2)
                .build();

        StudyTrajectoryKey linkMeKey3 = StudyTrajectoryKey.builder()
                .trajectoryId(linkMeTrajectory3)
                .scenarioId(studyId)
                .build();
        StudyTrajectoryEntity linkMeEntity3 = StudyTrajectoryEntity.builder()
                .id(linkMeKey3)
                .studyEntity(study)
                .trajectory(linkMeT3)
                .build();

        // When
        when(trajectoryRepository.findById(areaTrajectoryId)).thenReturn(Optional.of(areaTrajectory));
        when(trajectoryRepository.findByTypeAndStudyId(null, studyId)).thenReturn(List.of(areaTrajectory, linkMeT1, linkMeT2, linkMeT3));
        when(studyTrajectoryRepository.findById(areaKey)).thenReturn(Optional.of(areaEntity));
        when(studyTrajectoryRepository.findById_ScenarioId(studyId))
               .thenReturn(List.of(areaEntity, linkMeEntity1, linkMeEntity2, linkMeEntity3));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR.name(), studyId))
                .thenReturn(List.of());
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.DSR_CAPACITY_MODULATION.name(), studyId))
               .thenReturn(List.of());

        trajectoryService.unlinkTrajectoryFromStudy(areaTrajectoryId, studyId);

        // Then
        // Verify all LINK_ME trajectories are deleted
        verify(studyTrajectoryRepository, times(1)).delete(linkMeEntity1);
        verify(studyTrajectoryRepository, times(1)).delete(linkMeEntity2);
        verify(studyTrajectoryRepository, times(1)).delete(linkMeEntity3);

        // Verify AREA_ME is deleted
        verify(studyTrajectoryRepository, times(1)).delete(areaEntity);

        // Verify delete was called 4 times (3 LINK_ME + 1 AREA_ME)
        verify(studyTrajectoryRepository, times(4)).delete(any());

        // Verify findById_ScenarioId was called once for cascade delete
        verify(studyTrajectoryRepository, times(1)).findById_ScenarioId(studyId);
    }
}


