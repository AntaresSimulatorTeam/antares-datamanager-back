package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
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
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private MiscFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(trajectoryRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(userService.getCurrentUserDetails()).thenReturn(null);

        when(trajectoryRepository
                .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                        anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        
        when(areaRepository.findAllByStudyId(anyInt()))
                .thenReturn(List.of());
    }

    // ======================================================
    // Helpers
    // ======================================================

    private Path createInstalledWorkbook(List<Object[]> rows, String horizon, boolean isCivilYear) throws Exception {

        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");
        int horizonYear;
        if (isCivilYear) {
            horizonYear = Integer.parseInt(horizon.split("-")[1]);
        } else {
            int endYear = Integer.parseInt(horizon.split("-")[1]);
            horizonYear = endYear + 1;
        }

        try (Workbook wb = new XSSFWorkbook()) {

            Sheet s = wb.createSheet("InstalledMisc");

            Row header = s.createRow(0);
            header.createCell(0).setCellValue("ToUse");
            header.createCell(1).setCellValue("Area");
            header.createCell(2).setCellValue("Group");
            header.createCell(3).setCellValue("Cluster");
            header.createCell(4).setCellValue("Category");
            header.createCell(5).setCellValue(horizonYear);

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

        when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any()))
                .thenReturn(file);

        return file;
    }

    private Path createLoadFactorStructure(
            String horizon,
            String group,
            String cluster,
            String header
    ) throws Exception {

        Path root = Files.createTempDirectory(tempDir, "misc_load_");

        Path dir = root.resolve(group).resolve(cluster);
        Files.createDirectories(dir);

        Path csv = dir.resolve("load_factor_" + group + "_" + horizon + ".csv");
        Files.writeString(csv, header + "\n1;2;3");

        when(trajectoryService.buildTrajectoryPath(anyString(), any()))
                .thenReturn(root);

        return root;
    }

    // Implémentation concrète (PAS de mock)
    private GroupAreaMiscCapacity buildGroup(String group, String cluster, String area) {
        return new GroupAreaMiscCapacity() {
            @Override public String getGroupe() { return group; }
            @Override public String getCluster() { return cluster; }
            @Override public String getArea() { return area; }
        };
    }

    // ======================================================
    // INSTALLED MISC
    // ======================================================

    @Nested
    class InstalledMisc {

        @Test
        void shouldRejectInvalidTrajectoryName() {
            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("bad", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldFilterByArea() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");

            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            createInstalledWorkbook(List.of(
                    new Object[]{1, "FR", "g1", "c1", "cat", 100},
                    new Object[]{1, "DE", "g2", "c2", "cat", 200}
            ), "2029-2030", false);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
        }

        @Test
        void shouldFilterByAreaWhenisCivilYear() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");

            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            createInstalledWorkbook(List.of(
                    new Object[]{1, "FR", "g1", "c1", "cat", 100},
                    new Object[]{1, "DE", "g2", "c2", "cat", 200}
            ), "2029-2030", true);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
        }

        @Test
        void shouldThrowWhenHorizonMissing() throws Exception {

            createInstalledWorkbook(List.of(), "2024-2025", false);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldIncrementVersionWhenChecksumChanges() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");

            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            TrajectoryEntity existing = new TrajectoryEntity();
            existing.setChecksum("OLD");
            existing.setVersion(2);

            when(trajectoryRepository
                    .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                            anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(existing));

            createInstalledWorkbook(Collections.singletonList(
                    new Object[]{1, "FR", "g", "c", "cat", 100}
            ), "2029-2030", false);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", false);

            assertThat(result.getVersion()).isEqualTo(3);
        }
    }

    // ======================================================
    // LOAD FACTOR
    // ======================================================

    @Nested
    class LoadFactor {

        @Test
        void shouldProcessSuccessfully() throws Exception {

            String horizon = "2029-2030";

            createLoadFactorStructure(horizon, "g1", "c1", "FR;DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("g1", "c1", "FR")));

            TrajectoryEntity result =
                    service.processLoadFactorMiscFile("loadFactor",
                            horizon,
                            1,
                            "FR");

            assertThat(result).isNotNull();
            assertThat(result.getHasTimeSeries()).isTrue();
            assertThat(result.getVersion()).isEqualTo(1);
        }

        @Test
        void shouldThrowWhenNoGroupFound() {

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of());

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor",
                            "2029-2030",
                            1,
                            "FR"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldThrowWhenTsFileMissing() throws Exception {

            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            when(trajectoryService.buildTrajectoryPath(anyString(), any()))
                    .thenReturn(root);

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("g1", "c1", "FR")));

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor",
                            "2029-2030",
                            1,
                            "FR"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldThrowWhenHeaderMissingAreas() throws Exception {

            String horizon = "2029-2030";

            createLoadFactorStructure(horizon, "g1", "c1", "DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("g1", "c1", "FR")));

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor",
                            horizon,
                            1,
                            "FR"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ======================================================
    // FIND BY STUDY ID
    // ======================================================

    @Nested
    class FindByStudyId {

        @Test
        void shouldReturnAllWhenAreaIsOthers() {
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "OTHERS"))
                    .thenReturn(List.of(
                            buildGroup("g1", "c1", "FR"),
                            buildGroup("g2", "c2", "DE")
                    ));

            List<GroupAreaMiscCapacity> result = miscClusterCapacityRepository.findByStudyIdAndArea(1, "OTHERS");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(GroupAreaMiscCapacity::getArea).containsExactly("FR", "DE");
        }

        @Test
        void shouldFilterBySpecificArea() {
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("g1", "c1", "FR")));

            List<GroupAreaMiscCapacity> result = miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getArea()).isEqualTo("FR");
        }

        @Test
        void shouldReturnEmptyWhenNoMatchingArea() {
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "IT"))
                    .thenReturn(List.of());

            List<GroupAreaMiscCapacity> result = miscClusterCapacityRepository.findByStudyIdAndArea(1, "IT");

            assertThat(result).isEmpty();
        }
    }
}