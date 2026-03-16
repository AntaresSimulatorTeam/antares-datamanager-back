package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiscFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private MiscClusterCapacityRepository miscClusterCapacityRepository;

    @Mock
    private UserService userService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @InjectMocks
    private MiscFileProcessorServiceImpl miscFileProcessorService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ======================================================
    // Helpers
    // ======================================================

    private Path createInstalledWorkbook(List<Object[]> rows, int year) throws Exception {
        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("InstalledMisc");

            Row header = s.createRow(0);
            header.createCell(0).setCellValue("ToUse");
            header.createCell(1).setCellValue("Area");
            header.createCell(2).setCellValue("Group");
            header.createCell(3).setCellValue("Cluster");
            header.createCell(4).setCellValue("Category");
            header.createCell(5).setCellValue(year);

            int rowIndex = 1;
            for (Object[] values : rows) {
                Row r = s.createRow(rowIndex++);
                for (int i = 0; i < values.length; i++) {
                    Cell c = r.createCell(i);
                    Object v = values[i];
                    if (v instanceof Boolean b) c.setCellValue(b);
                    else if (v instanceof Number n) c.setCellValue(n.doubleValue());
                    else if (v != null) c.setCellValue(v.toString());
                }
            }

            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        when(trajectoryService.getTrajectoryFilePath(eq(TrajectoryType.MISC_CAPACITY), anyString(), any()))
                .thenReturn(file);

        return file;
    }

    private GroupAreaMiscCapacity createMockGroupArea(String groupe, String area, String cluster) {
        GroupAreaMiscCapacity mockObj = mock(GroupAreaMiscCapacity.class);
        when(mockObj.getGroupe()).thenReturn(groupe);
        when(mockObj.getArea()).thenReturn(area);
        when(mockObj.getCluster()).thenReturn(cluster);
        return mockObj;
    }

    // ======================================================
    // INSTALLED MISC
    // ======================================================

    @Nested
    class InstalledMisc {

        @Test
        void shouldRejectInvalidTrajectoryName() {
            assertThatThrownBy(() ->
                    miscFileProcessorService.processInstalledMiscFile("bad", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("The trajectory file name must start with");
        }

        @Test
        void shouldFilterByArea() throws Exception {
            AreaEntity frEntity = new AreaEntity();
            frEntity.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(frEntity));

            createInstalledWorkbook(List.of(
                    new Object[]{true, "FR", "g1", "c1", "cat", 100},
                    new Object[]{true, "DE", "g2", "c2", "cat", 200}
            ), 2030);

            when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(i -> i.getArguments()[0]);

            TrajectoryEntity result = miscFileProcessorService.processInstalledMiscFile("installedMisc_test",
                    "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getArea()).isEqualTo("FR");
        }

        @Test
        void shouldThrowWhenHorizonMissing() throws Exception {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{true, "FR", "g1", "c1", "cat", 100});
            createInstalledWorkbook(rows, 2050); // Wrong year

            assertThatThrownBy(() ->
                    miscFileProcessorService.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Horizon '2029-2030' does not exist");
        }
    }

    // ======================================================
    // LOAD FACTOR
    // ======================================================

    @Nested
    class LoadFactor {
        @Test
        void shouldProcessSuccessfully() throws Exception {
            GroupAreaMiscCapacity m1 = mock(GroupAreaMiscCapacity.class);
            when(m1.getGroupe()).thenReturn("biomass");
            when(m1.getCluster()).thenReturn("small_biomass");
            when(m1.getArea()).thenReturn("FR");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(m1));

            Path root = tempDir.resolve("trajectories");
            Files.createDirectories(root.resolve("biomass").resolve("small_biomass"));
            Path csv = root.resolve("biomass").resolve("small_biomass").resolve("load_factor_small_biomass_2029-2030.csv");
            Files.writeString(csv, "date;FR\n2029-01-01;0.5");

            when(trajectoryService.buildTrajectoryPath(anyString(), eq(TrajectoryType.MISC_LOAD)))
                    .thenReturn(root);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
            when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(i -> i.getArguments()[0]);

            TrajectoryEntity result = miscFileProcessorService.processLoadFactorMiscFile("load_factor_test", "2029-2030", 1, "FR");

            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(TrajectoryType.MISC_LOAD.name());
        }

        @Test
        void shouldThrowWhenAreasMissingInLoadFactor() throws Exception {
            GroupAreaMiscCapacity m1 = mock(GroupAreaMiscCapacity.class);
            when(m1.getGroupe()).thenReturn("biomass");
            when(m1.getCluster()).thenReturn("small_biomass");
            when(m1.getArea()).thenReturn("FR");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(m1));

            Path root = tempDir.resolve("trajectories_missing");
            Files.createDirectories(root.resolve("biomass").resolve("small_biomass"));
            Path csv = root.resolve("biomass").resolve("small_biomass").resolve("load_factor_small_biomass_2029-2030.csv");
            Files.writeString(csv, "date;DE\n2029-01-01;0.5"); // Missing FR

            when(trajectoryService.buildTrajectoryPath(anyString(), eq(TrajectoryType.MISC_LOAD)))
                    .thenReturn(root);

            assertThatThrownBy(() ->
                    miscFileProcessorService.processLoadFactorMiscFile("load_factor_test", "2029-2030", 1, "FR"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("is missing areas");
        }
    }

    // ======================================================
    // REPOSITORY WRAPPERS
    // ======================================================

    @Nested
    class RepositoryWrappers {

        @Test
        void shouldGroupByGroupClusterKey() {
            GroupAreaMiscCapacity m1 = mock(GroupAreaMiscCapacity.class);
            when(m1.getGroupe()).thenReturn("g1");
            when(m1.getArea()).thenReturn("FR");
            when(m1.getCluster()).thenReturn("c1");

            GroupAreaMiscCapacity m2 = mock(GroupAreaMiscCapacity.class);
            when(m2.getGroupe()).thenReturn("g1");
            when(m2.getArea()).thenReturn("FR");
            when(m2.getCluster()).thenReturn("c2");

            GroupAreaMiscCapacity m3 = mock(GroupAreaMiscCapacity.class);
            when(m3.getGroupe()).thenReturn("g2");
            when(m3.getArea()).thenReturn("FR");
            when(m3.getCluster()).thenReturn("c3");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(m1, m2, m3));

            var result = miscFileProcessorService.getAreasByGroupClusterByStudyId(1, "FR");

            assertThat(result).hasSize(3); // 3 distinct group/cluster keys
            assertThat(result.keySet()).extracting("groupe").containsExactlyInAnyOrder("g1", "g1", "g2");
        }
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
        Path csv = groupDir.resolve("load_factor_cluster1_" + horizon + ".csv");
        Files.writeString(csv, "\"area3\";\"other\"\nvalue1;value2\n");

        // mock save to avoid interacting with DB
        when(trajectoryRepository.save(any())).thenReturn(TrajectoryEntity.builder().fileName("f").build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam)
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
        Path csv = groupDir.resolve("load_factor_cluster1_" + horizon + ".csv");
        Files.writeString(csv, "AREA1;AREA2;OTHER\n1;2;3\n");

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void mergedAllHeadersSkipsDuplicateExistingPaths() throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam))
                .thenReturn(List.of(buildGroup("group1", "cluster1", "AREA1")));

        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Files.writeString(groupDir.resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA1\n1\n");

        TrajectoryEntity traj1 = TrajectoryEntity.builder().fileName("existA").build();
        TrajectoryEntity traj2 = TrajectoryEntity.builder().fileName("existB").build();
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(traj1, traj2));

        Path existingBase = tempDir.resolve("existingBase");
        Files.createDirectories(existingBase.resolve("group1").resolve("cluster1"));
        Files.writeString(existingBase.resolve("group1").resolve("cluster1").resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA1\n1\n");

        when(trajectoryService.buildTrajectoryPath(eq("existA"), eq(TrajectoryType.MISC_LOAD))).thenReturn(existingBase);
        when(trajectoryService.buildTrajectoryPath(eq("existB"), eq(TrajectoryType.MISC_LOAD))).thenReturn(existingBase);

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void mergedAllHeadersMergesHeadersFromDistinctExistingTrajectories() throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "";

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam))
                .thenReturn(List.of(buildGroup("group1", "cluster1", "AREA1"), buildGroup("group1", "cluster1", "AREA2")));

        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Files.writeString(groupDir.resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA1\n1\n");

        TrajectoryEntity tA = TrajectoryEntity.builder().fileName("tA").build();
        TrajectoryEntity tB = TrajectoryEntity.builder().fileName("tB").build();
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(tA, tB));

        Path baseA = tempDir.resolve("baseA");
        Files.createDirectories(baseA.resolve("group1").resolve("cluster1"));
        Files.writeString(baseA.resolve("group1").resolve("cluster1").resolve("load_factor_group1_" + horizon + ".csv"), "AREA2_PART;AREA2\n1;2\n");

        Path baseB = tempDir.resolve("baseB");
        Files.createDirectories(baseB.resolve("group1").resolve("cluster1"));
        Files.writeString(baseB.resolve("group1").resolve("cluster1").resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA2;AREA3\n3;4\n");

        when(trajectoryService.buildTrajectoryPath(eq("tA"), eq(TrajectoryType.MISC_LOAD))).thenReturn(baseA);
        when(trajectoryService.buildTrajectoryPath(eq("tB"), eq(TrajectoryType.MISC_LOAD))).thenReturn(baseB);

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void shouldThrowWhenDefaultGroupFileMissing() throws Exception {

        String horizon = "2029-2030";
        String trajectoryToUse = "loadFactor";
        Integer studyId = 1;
        String area = "FR";

        Path root = Files.createTempDirectory(tempDir, "misc_load_");

        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                .thenReturn(root);

        // Force listAreasByGroup.isEmpty()
        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                .thenReturn(List.of());

        // Create ALL folders but omit one CSV to trigger exception
        List<MiscFileProcessorServiceImpl.GroupClusterKey> keys = List.of(
                new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "small biomass"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("geothermal", "geothermal"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("other", "other"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("waste", "waste"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("wave", "wave"),
                new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "hydrokinetic")
        );

        int index = 0;

        for (MiscFileProcessorServiceImpl.GroupClusterKey key : keys) {

            Path dir = root.resolve(key.groupe()).resolve(key.cluster());
            Files.createDirectories(dir);

            // Skip one file to trigger error
            if (index++ == 3) continue;

            Path csv = dir.resolve("load_factor_" + key.cluster() + "_" + horizon + ".csv");

            Files.writeString(csv, "FR;DE\n1;2");
        }

        assertThatThrownBy(() ->
                service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Load factor file not found");
    }

}

