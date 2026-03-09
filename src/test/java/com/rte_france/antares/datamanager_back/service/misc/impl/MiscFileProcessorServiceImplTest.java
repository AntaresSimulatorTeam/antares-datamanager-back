package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiscFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private MiscClusterCapacityRepository miscClusterCapacityRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private UserService userService;

    @InjectMocks
    private MiscFileProcessorServiceImpl miscFileProcessorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processLoadFactorMiscFileThrowsWhenMergedHeadersDoNotContainAllAreas(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        // Prepare DB projection results: two areas for the same group/cluster
        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        GroupAreaMiscCapacity e2 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA2"; }
            public String getCluster() { return "cluster1"; }
        };

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1, e2));

        // build base trajectory path (temp dir)
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        // create the csv file that will be read: only AREA3 present -> should fail
        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Path csv = groupDir.resolve("load_factor_group1_" + horizon + ".csv");
        Files.writeString(csv, "\"area3\";\"other\"\nvalue1;value2\n");

        // mock save to avoid interacting with DB
        when(trajectoryRepository.save(any())).thenReturn(TrajectoryEntity.builder().fileName("f").build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("missing areas") || ex.getMessage().toLowerCase().contains("is missing"));
    }

    @Test
    void processLoadFactorMiscFileSucceedsWhenMergedHeadersContainAllAreas(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        GroupAreaMiscCapacity e2 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA2"; }
            public String getCluster() { return "cluster1"; }
        };

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1, e2));
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Path csv = groupDir.resolve("load_factor_group1_" + horizon + ".csv");
        Files.writeString(csv, "AREA1;AREA2;OTHER\n1;2;3\n");

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void processLoadFactorMiscFileThrowsWhenCurrentFileIsMissing(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1));
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        when(trajectoryRepository.save(any())).thenReturn(TrajectoryEntity.builder().fileName("f").build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("not found") || ex.getMessage().toLowerCase().contains("missing"));
    }

    @Test
    void processLoadFactorMiscFileSucceedsWhenNoExistingTrajectories(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1));
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        // create current csv with header AREA1
        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Path csv = groupDir.resolve("load_factor_group1_" + horizon + ".csv");
        Files.writeString(csv, "AREA1;OTHER\n1;2\n");

        // no existing trajectories in DB
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name())).thenReturn(List.of());

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void processLoadFactorMiscFileSkipsMissingExistingTrajectoryFiles(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1));
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        // create current csv with header AREA1
        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Path csv = groupDir.resolve("load_factor_group1_" + horizon + ".csv");
        Files.writeString(csv, "AREA1;OTHER\n1;2\n");

        // one existing trajectory in DB but its load_factor file does NOT exist -> should be skipped
        TrajectoryEntity traj = TrajectoryEntity.builder().fileName("otherTraj").build();
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name())).thenReturn(List.of(traj));
        // trajectoryService will build a base path, but we won't create the expected load_factor file there
        when(trajectoryService.buildTrajectoryPath(traj.getFileName(), TrajectoryType.MISC_LOAD)).thenReturn(tempDir.resolve("other"));

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void processLoadFactorMiscFileContinuesWhenExistingHeaderIsEmpty(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        GroupAreaMiscCapacity e1 = new GroupAreaMiscCapacity() {
            public String getGroupe() { return "group1"; }
            public String getArea() { return "AREA1"; }
            public String getCluster() { return "cluster1"; }
        };
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam)).thenReturn(List.of(e1));
        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        // create current csv with header AREA1
        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Path csv = groupDir.resolve("load_factor_group1_" + horizon + ".csv");
        Files.writeString(csv, "AREA1;OTHER\n1;2\n");

        // existing trajectory with empty file -> readHeaderAreas will throw for that file but should be caught
        TrajectoryEntity traj = TrajectoryEntity.builder().fileName("otherTraj").build();
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name())).thenReturn(List.of(traj));
        Path otherBase = tempDir.resolve("other");
        Files.createDirectories(otherBase.resolve("group1")).toFile();
        Files.createDirectories(otherBase.resolve("group1").resolve("cluster1"));
        Path otherCsv = otherBase.resolve("group1").resolve("cluster1").resolve("load_factor_group1_" + horizon + ".csv");
        // create empty file to trigger "is empty" BusinessException when readHeaderAreas is called
        Files.writeString(otherCsv, "");
        when(trajectoryService.buildTrajectoryPath(traj.getFileName(), TrajectoryType.MISC_LOAD)).thenReturn(otherBase);

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> miscFileProcessorService.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }
}
