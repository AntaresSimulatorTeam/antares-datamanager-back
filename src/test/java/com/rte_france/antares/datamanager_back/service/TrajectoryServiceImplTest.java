package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrajectoryServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;
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
    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private LoadFileProcessorServiceImpl loadFileProcessorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTYpeIsAREA() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/area/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getAreaDirectory()).thenReturn("/areas");

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile", "2023-2024",1);

        verify(areaFileProcessorService, times(1)).processAreaFile(any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getLinkDirectory()).thenReturn("/links");

        trajectoryService.processTrajectory(TrajectoryType.LINK, "links_BP23_A_ref", "2023-2024",1);

        verify(linkFileProcessorService, times(1)).processLinkFile(any(), any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsThermalCapacity() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/thermal_capacity/thermal_BE_PEMMDB23_26avril.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getThermalCapacityDirectory()).thenReturn("src/test/resources/thermal_capacity/");

        trajectoryService.processTrajectory(TrajectoryType.THERMAL_CAPACITY, "thermal_BE_PEMMDB23_26avril", "2023-2024",1);

        verify(thermalFileProcessorService, times(1)).processThermalFile(any(), any(), any(), any());
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromDB_returnsEntitiesWhenExist() {
        List<TrajectoryEntity> expectedEntities = List.of(new TrajectoryEntity());
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(TrajectoryType.AREA.name(), "2023-2024", "fileNameStartsWith")).thenReturn(expectedEntities);

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType.AREA, "2023-2024", "fileNameStartsWith");

        assertEquals(expectedEntities, result);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromDB_returnsEmptyWhenDoNotExist() {
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(TrajectoryType.AREA.name(), "2023-2024", "nonExistentFileNameStartsWith")).thenReturn(List.of());

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType.AREA, "2023-2024", "nonExistentFileNameStartsWith");

        assertEquals(List.of(), result);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromFS_returnsFileNamesWhenDirectoryExists() {
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");

        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType.AREA);

        assertEquals("testFile.xlsx", result.get(0).getFileName());
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromFS_throwsExceptionWhenDirectoryDoesNotExist() {
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        assertThrows(UncheckedIOException.class, () -> trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType.AREA));
    }

    @Test
    void linkTrajectoryToStudy_linksTrajectoryWhenStudyAndTrajectoryExist() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        StudyEntity study =  StudyEntity.builder().id(studyId).studyTrajectoryEntities(Collections.emptySet()).build();

        TrajectoryEntity trajectory =  TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type);

        assertEquals(trajectory, result);
        verify(studyTrajectoryRepository, times(1)).save(any());
    }

    @Test
    void linkTrajectoryToStudy_throwsExceptionWhenStudyNotFound() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        when(studyRepository.findById(studyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type));
    }

    @Test
    void linkTrajectoryToStudy_throwsExceptionWhenTrajectoryNotFound() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;

        StudyEntity study =  StudyEntity.builder().id(studyId).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type));
    }

    @Test
    void linkTrajectoryToStudy_replacesExistingLinkWhenSameTypeExists() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        TrajectoryType type = TrajectoryType.AREA;


        TrajectoryEntity trajectory =  TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();

        StudyTrajectoryEntity existingLink =  StudyTrajectoryEntity.builder().trajectory(trajectory).build();

        StudyEntity study =  StudyEntity.builder().id(studyId).build();
        study.setStudyTrajectoryEntities(Set.of(existingLink));

        TrajectoryEntity newTrajectory =  TrajectoryEntity.builder().id(trajectoryId).type(type.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(newTrajectory));
        when(studyTrajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, type);

        assertEquals(newTrajectory, result);
        verify(studyTrajectoryRepository, times(1)).delete(existingLink);
        verify(studyTrajectoryRepository, times(1)).save(any());
    }

    @Test
    void unlinkTrajectoryFromStudy_unlinksWhenLinkExists() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyTrajectoryKey key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();
        StudyTrajectoryEntity entity = StudyTrajectoryEntity.builder().id(key).build();

        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(entity));

        trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId);

        verify(studyTrajectoryRepository, times(1)).delete(entity);
    }

    @Test
    void unlinkTrajectoryFromStudy_throwsExceptionWhenLinkDoesNotExist() {
        Integer trajectoryId = 1;
        Integer studyId = 1;
        StudyTrajectoryKey key = StudyTrajectoryKey.builder().trajectoryId(trajectoryId).scenarioId(studyId).build();

        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trajectoryService.unlinkTrajectoryFromStudy(trajectoryId, studyId));
    }
  
    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLOAD() throws IOException {
        var path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/load/testFile.txt");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("/load");

        trajectoryService.processTrajectory(TrajectoryType.LOAD, "testFile", "2030-2031",1);

        verify(loadFileProcessorService, times(1)).processLoadFile(any(), any());
    }
  
    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsTHERMAL_PARAMETER() throws IOException {
        var path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/thermal_parameter/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getThermalParameterDirectory()).thenReturn("/thermal_parameters");

        trajectoryService.processTrajectory(TrajectoryType.THERMAL_PARAMETER, "testFile", "2023-2024",1);

        verify(thermalFileProcessorService, times(1)).processThermalFile(any(), any(), any(), eq(TrajectoryType.THERMAL_PARAMETER));
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsTHERMAL_COST() throws IOException {
        var path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/thermal_cost/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getThermalCostDirectory()).thenReturn("/thermal_costs");

        trajectoryService.processTrajectory(TrajectoryType.THERMAL_COST, "testFile", "2023-2024",1);

        verify(thermalFileProcessorService, times(1)).processThermalFile(any(), any(), any(), eq(TrajectoryType.THERMAL_COST));
    }

    @Test
    void processTrajectory_throwsExceptionWhenTrajectoryTypeIsUnsupported() {
        var path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/unsupported/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");

        assertThrows(IllegalArgumentException.class, () -> trajectoryService.processTrajectory(TrajectoryType.MISC, "testFile", "2023-2024",1));
    }
}