package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

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

    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

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

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile", "2023-2024");

        verify(areaFileProcessorService, times(1)).processAreaFile(any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getLinkDirectory()).thenReturn("/links");

        trajectoryService.processTrajectory(TrajectoryType.LINK, "links_BP23_A_ref", "2023-2024");

        verify(linkFileProcessorService, times(1)).processLinkFile(any(), any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsThermalCapacity() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/thermal_capacity/thermal_BE_PEMMDB23_26avril.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("/tmp/mnt/nas");
        when(antaressDataManagerProperties.getThermalCapacityDirectory()).thenReturn("src/test/resources/thermal_capacity/");

        trajectoryService.processTrajectory(TrajectoryType.THERMAL_CAPACITY, "thermal_BE_PEMMDB23_26avril", "2023-2024");

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

        assertEquals("testFile.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromFS_throwsExceptionWhenDirectoryDoesNotExist() {
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/");
        when(antaressDataManagerProperties.getNasDirectory()).thenReturn("");
        assertThrows(UncheckedIOException.class, () -> trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType.AREA));
    }

    @Test
    void findTrajectoriesByTypeAndIds_returnsEmptyListForNonExistentType() {
        when(trajectoryRepository.findByTypeAndIdIn("nonExistentType", List.of(1, 2, 3))).thenReturn(List.of());
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndIds("nonExistentType", List.of(1, 2, 3));
        assertThat(result).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndIds_returnsEmptyListForNonExistentIds() {
        when(trajectoryRepository.findByTypeAndIdIn("AREA", List.of(999, 1000))).thenReturn(List.of());
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndIds("AREA", List.of(999, 1000));
        assertThat(result).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndIds_returnsNonEmptyListForExistentTypeAndIds() {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setType("AREA");
        entity.setId(1);
        when(trajectoryRepository.findByTypeAndIdIn("AREA", List.of(1, 2))).thenReturn(List.of(entity));
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndIds("AREA", List.of(1, 2));
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getType()).isEqualTo("AREA");
        assertThat(result.get(0).getId()).isEqualTo(1);
    }

    @Test
    void findTrajectoriesByTypeAndIds_returnsEmptyListForNullType() {
        when(trajectoryRepository.findByTypeAndIdIn(null, List.of(1, 2, 3))).thenReturn(List.of());
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndIds(null, List.of(1, 2, 3));
        assertThat(result).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndIds_returnsEmptyListForEmptyIds() {
        when(trajectoryRepository.findByTypeAndIdIn("AREA", List.of())).thenReturn(List.of());
        List<TrajectoryDTO> result = trajectoryService.findTrajectoriesByTypeAndIds("AREA", List.of());
        assertThat(result).isEmpty();
    }
}