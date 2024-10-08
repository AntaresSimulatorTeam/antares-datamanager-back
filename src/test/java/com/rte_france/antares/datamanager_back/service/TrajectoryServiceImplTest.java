package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.service.impl.SftpDownloadService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

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
    private SftpDownloadService sftpDownloadService;


    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTYpeIsAREA() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/area/testFile.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");

        trajectoryService.processTrajectory(TrajectoryType.AREA, "testFile","2023-2024");

        verify(areaFileProcessorService, times(1)).processAreaFile(any(),any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsLINK() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");

        trajectoryService.processTrajectory(TrajectoryType.LINK, "links_BP23_A_ref","2023-2024");

        verify(linkFileProcessorService, times(1)).processLinkFile(any(),any());
    }

    @Test
    void processTrajectory_returnsEntityWhenTrajectoryTypeIsThermalCapacity() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/thermal_capacity/thermal_BE_PEMMDB23_26avril.xlsx");
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");

        trajectoryService.processTrajectory(TrajectoryType.THERMAL_CAPACITY, "thermal_BE_PEMMDB23_26avril","2023-2024");

        verify(thermalFileProcessorService, times(1)).processThermalCapacityFile(any(),any());
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromDB_returnsEntitiesWhenExist() {
        List<TrajectoryEntity> expectedEntities = List.of(new TrajectoryEntity());
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(TrajectoryType.AREA.name(), "2023-2024","fileNameStartsWith")).thenReturn(expectedEntities);

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType.AREA, "2023-2024","fileNameStartsWith");

        assertEquals(expectedEntities, result);
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromDB_returnsEmptyWhenDoNotExist() {
        when(trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(TrajectoryType.AREA.name(), "2023-2024", "nonExistentFileNameStartsWith")).thenReturn(List.of());

        List<TrajectoryEntity> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType.AREA,"2023-2024", "nonExistentFileNameStartsWith");

        assertEquals(List.of(), result);
    }

        @Test
        void findTrajectoriesByTypeAndFileNameStartWithFromFS_returnsFileNamesWhenDirectoryExists() {
            when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/resources/");

            List<String> result = trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType.AREA);

            assertEquals(List.of("testFile.xlsx"), result);
        }

        @Test
        void findTrajectoriesByTypeAndFileNameStartWithFromFS_throwsExceptionWhenDirectoryDoesNotExist() {
            when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("src/test/");


            assertThrows(IllegalArgumentException.class, () -> trajectoryService.findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType.AREA));
        }
}