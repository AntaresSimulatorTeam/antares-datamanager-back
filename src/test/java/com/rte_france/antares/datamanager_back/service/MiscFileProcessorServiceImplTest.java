package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;

import java.math.BigDecimal;
import java.nio.file.attribute.PosixFilePermissions;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.GroupAreaMiscCapacity;
import com.rte_france.antares.datamanager_back.repository.MiscClusterCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
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

        when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any()))
                .thenReturn(file);

        return file;
    }

    private Path createInstalledWorkbookWithHeader(List<Object> headerCells, List<Object[]> rows) throws Exception {
        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("InstalledMisc");

            Row header = s.createRow(0);
            for (int i = 0; i < headerCells.size(); i++) {
                Object v = headerCells.get(i);
                Cell c = header.createCell(i);
                if (v instanceof Number n) c.setCellValue(n.doubleValue());
                else if (v != null) c.setCellValue(v.toString());
            }

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

    private Path createInstalledWorkbookMissingHeaderRow() throws Exception {
        Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("InstalledMisc"); // pas de row 0
            try (OutputStream os = Files.newOutputStream(file)) {
                wb.write(os);
            }
        }

        when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any()))
                .thenReturn(file);

        return file;
    }

    private Path createLoadFactorStructure(String horizon, String group, String cluster, String header) throws Exception {

        Path root = Files.createTempDirectory(tempDir, "misc_load_");

        Path dir = root.resolve(group).resolve(cluster);
        Files.createDirectories(dir);

        Path csv = dir.resolve("load_factor_" + cluster + "_" + horizon + ".csv");
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
                    new Object[]{1, "FR", "biomass", "c1", "cat", 100},
                    new Object[]{1, "DE", "waste", "c2", "cat", 200}
            ), 2030);

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
                    new Object[]{1, "FR", "biomass", "c1", "cat", 100},
                    new Object[]{1, "DE", "waste", "c2", "cat", 200}
            ), 2030);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", true);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
        }

        @Test
        void shouldThrowWhenHorizonMissing() throws Exception {
            createInstalledWorkbook(List.of(), 2025);

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
                    new Object[]{1, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test",
                            "2029-2030", 1, "FR", false);

            assertThat(result.getVersion()).isEqualTo(3);
        }

        @Test
        void shouldThrowWhenAlreadyProcessedSameContent() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            // On crée un fichier avec une seule ligne valide
            createInstalledWorkbook(Collections.singletonList(
                    new Object[]{true, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            // Créer une première trajectoire
            TrajectoryEntity firstResult =
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.getChecksum()).isNotNull();
            String firstChecksum = firstResult.getChecksum();

            // Recréer le même fichier avec les mêmes données
            createInstalledWorkbook(Collections.singletonList(
                    new Object[]{true, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            // Mock pour retourner la première trajectoire
            when(trajectoryRepository
                    .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                            anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(firstResult));

            // Le deuxième appel avec le même contenu devrait lever une exception
            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File already processed");
        }

        @Test
        void shouldThrowWhenHeaderMissing() throws Exception {
            createInstalledWorkbookMissingHeaderRow();

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Missing header");
        }

        @Test
        void shouldThrowWhenHeaderTooShort() throws Exception {
            // lastCol < 6 : on ne met pas la colonne année
            createInstalledWorkbookWithHeader(
                    List.of("ToUse", "Area", "Group", "Cluster", "Category"),
                    List.of()
            );

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("InstalledMisc header is invalid");
        }

        @Test
        void shouldThrowWhenRequiredColumnMissing() throws Exception {
            // "Cluster" manquant => checkMissingColumns(...) doit échouer
            createInstalledWorkbookWithHeader(
                    List.of("ToUse", "Area", "Group", "Clustr", "Category", 2030),
                    List.<Object[]>of(new Object[]{1, "FR", "biomass", "c", "cat", 100})
            );

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldThrowWhenToUseIsMissing() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{null, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("can't be empty");
        }

        @Test
        void shouldThrowWhenNoAreaFoundAfterFiltering() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            // areaParam = FR mais seules des lignes DE => filtrées => entities vide
            createInstalledWorkbook(
                        List.<Object[]>of(), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No area found");
        }

        @Test
        void shouldThrowWhenNonNumericValuesExist() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "biogas", "biogas", "cat", "abc"},
                    new Object[]{true, "FR", "biogas", "biogas", "cat", 400}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("are not numeric");
        }

        @Test
        void shouldThrowWhenSelectedAreaNotPresentInFile() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "DE", "g", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void shouldNotRequireSelectedAreaWhenAreaIsOthers() throws Exception {
            AreaEntity de = new AreaEntity();
            de.setName("DE");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(de));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "DE", "biomass", "c", "cat", 100}
            ), 2030);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "OTHERS", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getArea()).isEqualTo("OTHERS");
        }

        @Test
        void shouldThrowWhenGroupIsEmpty() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a valid group");
        }

        @Test
        void shouldThrowWhenGroupIsNotValid() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "invalidgroup", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a valid group")
                    .hasMessageContaining("Valid groups are");
        }

        @Test
        void shouldAcceptAllValidGroups() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            String[] validGroups = {"biomass", "biogas", "geothermal", "other", "waste", "wave", "hydrokinetic"};
            for (String validGroup : validGroups) {
                createInstalledWorkbook(List.<Object[]>of(
                        new Object[]{true, "FR", validGroup, "c", "cat", 100}
                ), 2030);

                TrajectoryEntity result =
                        service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

                assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
                assertThat(result.getMiscClusterCapacityEntities().get(0).getGroupe())
                        .isEqualToIgnoringCase(validGroup);
            }
        }

        @Test
        void shouldAcceptGroupsWithDifferentCaseValidation() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            // Test uppercase group name (should be accepted and converted to lowercase)
            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "BIOMASS", "c", "cat", 100}
            ), 2030);

            TrajectoryEntity result =
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getGroupe())
                    .isEqualTo("BIOMASS");
        }

        @Test
        void shouldThrowWhenGroupContainsNumbersInvalid() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            // "biomass1" is not in VALID_GROUPS
            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "biomass1", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a valid group")
                    .hasMessageContaining("Valid groups are");
        }

        @Test
        void shouldThrowWhenGroupContainsSpecialCharactersInvalid() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            // "biomass@" is not in VALID_GROUPS
            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "biomass@", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a valid group")
                    .hasMessageContaining("Valid groups are");
        }

        @Test
        void shouldThrowWhenGroupContainsSpacesInvalid() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            // "bio mass" is not in VALID_GROUPS
            createInstalledWorkbook(List.<Object[]>of(
                    new Object[]{true, "FR", "bio mass", "c", "cat", 100}
            ), 2030);

            assertThatThrownBy(() ->
                    service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not a valid group")
                    .hasMessageContaining("Valid groups are");
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

            createLoadFactorStructure(horizon, "biomass", "Small biomass", "FR;DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            TrajectoryEntity result =
                    service.processLoadFactorMiscFile("loadFactor",
                            horizon,
                            1,
                            "FR");

            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(TrajectoryType.MISC_LOAD.name());
            assertThat(result.getChecksum()).isNotNull();
        }

        @Test
        void shouldThrowWhenTsFileMissing() throws Exception {

            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            when(trajectoryService.buildTrajectoryPath(anyString(), any()))
                    .thenReturn(root);

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

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

            createLoadFactorStructure(horizon, "biomass", "Small biomass", "DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor",
                            horizon,
                            1,
                            "FR"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void processLoadFactorMiscFileThrowsWhenSpecificAreaMissingInHeader() throws Exception {
            String horizon = "2029-2030";
            String trajectoryToUse = "loadFactor";
            Integer studyId = 1;
            String area = "FR";

            Path root = Files.createTempDirectory(tempDir, "misc_load_missing_area_");

            when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Force listAreasByGroup.isEmpty() to exercise verifyLoadFactorTsFilesWithoutInstalledPower
            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                    .thenReturn(List.of());

            List<MiscFileProcessorServiceImpl.GroupClusterKey> groupClusterKeyList = List.of(
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("geothermal", "geothermal"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("other", "other"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("waste", "waste"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("wave", "wave"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "hydrokinetic")
            );

            // Create all expected CSV files but none contains the requested area 'FR'
            for (MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey : groupClusterKeyList) {
                Path dir = root.resolve(groupClusterKey.groupe()).resolve(groupClusterKey.cluster());
                Files.createDirectories(dir);
                Path csv = dir.resolve("load_factor_" + groupClusterKey.cluster() + "_" + horizon + ".csv");
                Files.writeString(csv, "DE;IT\n1;2");
            }

            assertThatThrownBy(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Load factor file {0} is missing area {1} for group {2}");
        }

        @Test
        void processLoadFactorThrowsBusinessExceptionWithMissingAreaMessage() throws Exception {
            String horizon = "2029-2030";
            String trajectoryToUse = "loadFactor";
            Integer studyId = 1;
            String area = "FR";

            Path root = Files.createTempDirectory(tempDir, "misc_load_missing_area2_");

            when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                    .thenReturn(List.of());

            List<MiscFileProcessorServiceImpl.GroupClusterKey> groupClusterKeyList = List.of(
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
            );

            for (MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey : groupClusterKeyList) {
                Path dir = root.resolve(groupClusterKey.groupe()).resolve(groupClusterKey.cluster());
                Files.createDirectories(dir);
                Path csv = dir.resolve("load_factor_" + groupClusterKey.cluster() + "_" + horizon + ".csv");
                Files.writeString(csv, "DE;IT\n1;2");
            }

            assertThatThrownBy(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Load factor file {0} is missing area {1} for group {2}");
        }

        @Test
        void processLoadFactorThrowsBusinessExceptionForMissingAreaTemplate() throws Exception {
            String horizon = "2029-2030";
            String trajectoryToUse = "loadFactor";
            Integer studyId = 1;
            String area = "FR";

            Path root = Files.createTempDirectory(tempDir, "misc_load_missing_area_template_");

            when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                    .thenReturn(List.of());

            List<MiscFileProcessorServiceImpl.GroupClusterKey> groupClusterKeyList = List.of(
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biogas", "biogas"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("geothermal", "geothermal"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("other", "other"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("waste", "waste"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("wave", "wave"),
                    new MiscFileProcessorServiceImpl.GroupClusterKey("hydrokinetic", "hydrokinetic")
            );

            for (MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey : groupClusterKeyList) {
                Path dir = root.resolve(groupClusterKey.groupe()).resolve(groupClusterKey.cluster());
                Files.createDirectories(dir);
                Path csv = dir.resolve("load_factor_" + groupClusterKey.cluster() + "_" + horizon + ".csv");
                Files.writeString(csv, "DE;IT\n1;2");
            }

            assertThatThrownBy(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Load factor file {0} is missing area {1} for group {2}");
        }

        @Test
        void processLoadFactorMiscFileThrowsTechnicalExceptionWhenLoadFactorFileCannotBeRead() throws Exception {
            String horizon = "2029-2030";
            String trajectoryToUse = "loadFactor";
            Integer studyId = 1;
            String area = "FR";

            Path root = Files.createTempDirectory(tempDir, "misc_load_io_");

            when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Force listAreasByGroup.isEmpty() to exercise verifyLoadFactorTsFilesWithoutInstalledPower
            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                    .thenReturn(List.of());

            List<MiscFileProcessorServiceImpl.GroupClusterKey> groupClusterKeyList = List.of(
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
            );

            for (MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey : groupClusterKeyList) {
                Path parent = root.resolve(groupClusterKey.groupe()).resolve(groupClusterKey.cluster());
                Files.createDirectories(parent);
                Path tsFilePath = parent.resolve("load_factor_" + groupClusterKey.cluster() + "_" + horizon + ".csv");
                Files.writeString(tsFilePath, "AREA;OTHER\n1;2\n");
                // remove read permissions to provoke IOException when opening the file
                try {
                    Files.setPosixFilePermissions(tsFilePath, PosixFilePermissions.fromString("---------"));
                } catch (UnsupportedOperationException ignored) {
                    // In environments without POSIX file attribute support, fallback by attempting to make file unreadable
                    tsFilePath.toFile().setReadable(false, false);
                }
            }

            assertThatThrownBy(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                    .isInstanceOf(TechnicalException.class)
                    .hasMessageContaining("Error while reading load factor file");
        }

        @Test
        void throwsBusinessExceptionWithMissingAreaTemplateForSingleGroupFile() throws Exception {
            String horizon = "2029-2030";
            String trajectoryToUse = "loadFactor";
            Integer studyId = 1;
            String area = "FR";

            Path root = Files.createTempDirectory(tempDir, "misc_load_missing_area_single_");

            when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area))
                    .thenReturn(List.of());

            MiscFileProcessorServiceImpl.GroupClusterKey key = new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass");
            Path dir = root.resolve(key.groupe()).resolve(key.cluster());
            Files.createDirectories(dir);
            Path csv = dir.resolve("load_factor_" + key.cluster() + "_" + horizon + ".csv");
            Files.writeString(csv, "DE;IT\n1;2");

            assertThatThrownBy(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, area))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Load factor file {0} is missing area {1} for group {2}");
        }
    }

    // ======================================================
    // REPOSITORY WRAPPERS
    // ======================================================

    @Nested
    class FindByStudyId {

        @Test
        void shouldReturnAllWhenAreaIsOthers() {
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "OTHERS"))
                    .thenReturn(List.of(
                            buildGroup("biomass", "Small biomass", "FR"),
                            buildGroup("waste", "waste", "DE")
                    ));

            List<GroupAreaMiscCapacity> result = miscClusterCapacityRepository.findByStudyIdAndArea(1, "OTHERS");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(GroupAreaMiscCapacity::getArea).containsExactly("FR", "DE");
        }

        @Test
        void shouldFilterBySpecificArea() {
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

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
    @Test
    void processLoadFactorMiscFileThrowsWhenMergedHeadersDoNotContainAllAreas(@TempDir Path tempDir) throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "OTHERS";

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
        String areaParam = "OTHERS";

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
        Files.writeString(groupDir.resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA1;AREA2;OTHER\n1;2;3\n");

        when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.processLoadFactorMiscFile(trajectoryToUse, horizon, studyId, areaParam));
        verify(trajectoryRepository, atLeastOnce()).save(any());
    }

    @Test
    void mergedAllHeadersSkipsDuplicateExistingPaths() throws Exception {
        String horizon = "2030-2031";
        String trajectoryToUse = "load_factor_test";
        Integer studyId = 1;
        String areaParam = "OTHERS";

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
        String areaParam = "OTHERS";

        when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, areaParam))
                .thenReturn(List.of(buildGroup("group1", "cluster1", "AREA1"), buildGroup("group1", "cluster1", "AREA2")));

        when(trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD)).thenReturn(tempDir);

        Path groupDir = tempDir.resolve("group1").resolve("cluster1");
        Files.createDirectories(groupDir);
        Files.writeString(groupDir.resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA1\n1\n");

        TrajectoryEntity tA = TrajectoryEntity.builder().fileName("tA").area("AREA2").build();
        TrajectoryEntity tB = TrajectoryEntity.builder().fileName("tB").area("AREA2_PART").build();
        when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                .thenReturn(List.of(tA, tB));

        Path baseA = tempDir.resolve("baseA");
        Files.createDirectories(baseA.resolve("group1").resolve("cluster1"));
        Files.writeString(baseA.resolve("group1").resolve("cluster1").resolve("load_factor_cluster1_" + horizon + ".csv"), "AREA2_PART;AREA2\n1;2\n");

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

    @Nested
    class LoadFactorWithInstalledPower {

        @Test
        void shouldVerifyLoadFactorTsFilesWithInstalledPowerAndSpecificArea() throws Exception {
            String horizon = "2029-2030";
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            createLoadFactorStructure(horizon, "biomass", "Small biomass", "FR;DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            TrajectoryEntity result = service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR");

            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(TrajectoryType.MISC_LOAD.name());
        }

        @Test
        void shouldVerifyLoadFactorTsFilesWithInstalledPowerAndOthersArea() throws Exception {
            String horizon = "2029-2030";
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createLoadFactorStructure(horizon, "biomass", "Small biomass", "FR;DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "OTHERS"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            TrajectoryEntity result = service.processLoadFactorMiscFile("loadFactor", horizon, 1, "OTHERS");

            assertThat(result).isNotNull();
            assertThat(result.getArea()).isEqualTo("OTHERS");
        }

        @Test
        void shouldThrowWhenLoadFactorMissingAreaForSpecificArea() throws Exception {
            String horizon = "2029-2030";
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createLoadFactorStructure(horizon, "biomass", "Small biomass", "DE");

            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missing area");
        }
    }

    @Nested
    class ReadHeaderAreas {

        @Test
        void shouldReadHeaderAreasSuccessfully() throws Exception {
            String horizon = "2029-2030";
            Path root = createLoadFactorStructure(horizon, "biomass", "Small biomass", "FR;DE;IT");

            List<String> header = MiscFileProcessorServiceImpl.readHeaderAreas(
                    horizon,
                    root,
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
            );

            assertThat(header).containsExactly("fr", "de", "it");
        }

        @Test
        void shouldThrowWhenTsFileNotFound() throws IOException {
            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            assertThatThrownBy(() ->
                    MiscFileProcessorServiceImpl.readHeaderAreas(
                            "2029-2030",
                            root,
                            new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Load factor file not found");
        }

        @Test
        void shouldThrowWhenTsFileIsEmpty() throws Exception {
            String horizon = "2029-2030";
            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);

            Path csvFile = groupDir.resolve("load_factor_Small biomass_2029-2030.csv");
            Files.createFile(csvFile);

            assertThatThrownBy(() ->
                    MiscFileProcessorServiceImpl.readHeaderAreas(
                            horizon,
                            root,
                            new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("empty");
        }
    }

    @Nested
    class GetLoadFactorByGroupPath {

        @Test
        void shouldGetLoadFactorPathWithClusterProvided() throws IOException {
            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            Path result = MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(
                    "2029-2030",
                    root,
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "Small biomass")
            );

            assertThat(result).isNotNull();
            assertThat(result.toString()).contains("biomass");
            assertThat(result.toString()).contains("Small biomass");
            assertThat(result.toString()).contains("load_factor_");
        }

        @Test
        void shouldGetLoadFactorPathWithoutClusterProvided() throws Exception {
            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            Path groupDir = root.resolve("biomass");
            Path clusterDir = groupDir.resolve("Small biomass");
            Files.createDirectories(clusterDir);

            Path result = MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(
                    "2029-2030",
                    root,
                    new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "")
            );

            assertThat(result).isNotNull();
            assertThat(result.toString()).contains("Small biomass");
        }

        @Test
        void shouldThrowWhenClusterDirectoryNotFound() throws IOException {
            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            Path groupDir = root.resolve("biomass");
            Files.createDirectories(groupDir);

            assertThatThrownBy(() ->
                    MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(
                            "2029-2030",
                            root,
                            new MiscFileProcessorServiceImpl.GroupClusterKey("biomass", "")
                    ))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class VerifyLoadFactorWithoutInstalledPower {

        @Test
        void shouldVerifyAllGroupFilesExist() throws Exception {
            String horizon = "2029-2030";
            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            List<String> groups = List.of("biomass", "biogas", "geothermal", "other", "waste", "wave", "hydrokinetic");
            List<String> clusters = List.of("Small biomass", "biogas", "geothermal", "other", "waste", "wave", "hydrokinetic");

            for (int i = 0; i < groups.size(); i++) {
                Path dir = root.resolve(groups.get(i)).resolve(clusters.get(i));
                Files.createDirectories(dir);
                Path csv = dir.resolve("load_factor_" + clusters.get(i) + "_" + horizon + ".csv");
                Files.writeString(csv, "FR;DE\n1;2");
            }

            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(root);
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR")).thenReturn(List.of());

            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR")
            );
        }

        @Test
        void shouldThrowWhenAreaNotFoundInSpecificGroup() throws Exception {
            String horizon = "2029-2030";
            Path root = Files.createTempDirectory(tempDir, "misc_load_");

            List<String> groups = List.of("biomass", "biogas", "geothermal", "other", "waste", "wave", "hydrokinetic");
            List<String> clusters = List.of("Small biomass", "biogas", "geothermal", "other", "waste", "wave", "hydrokinetic");

            for (int i = 0; i < groups.size(); i++) {
                Path dir = root.resolve(groups.get(i)).resolve(clusters.get(i));
                Files.createDirectories(dir);
                Path csv = dir.resolve("load_factor_" + clusters.get(i) + "_" + horizon + ".csv");
                // Omit area "FR" in the first group
                String header = i == 0 ? "DE\n1" : "FR;DE\n1;2";
                Files.writeString(csv, header);
            }

            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(root);
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR")).thenReturn(List.of());

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missing area");
        }
    }

    @Nested
    class ProcessMiscCapacityRow {

        @Test
        void shouldSkipRowWhenToUseIsFalse() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(Arrays.asList(
                    new Object[]{false, "FR", "biomass", "c", "cat", 100},
                    new Object[]{true, "FR", "biomass", "c", "cat", 200}
            ), 2030);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getCapacityByYear())
                    .isEqualByComparingTo(BigDecimal.valueOf(200));
        }

        @Test
        void shouldHandleStringNumericValue() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            Path file = Files.createTempFile(tempDir, "installedMisc_", ".xlsx");
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet s = wb.createSheet("InstalledMisc");

                Row header = s.createRow(0);
                header.createCell(0).setCellValue("ToUse");
                header.createCell(1).setCellValue("Area");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("Cluster");
                header.createCell(4).setCellValue("Category");
                header.createCell(5).setCellValue(2030);

                Row r = s.createRow(1);
                r.createCell(0).setCellValue(true);
                r.createCell(1).setCellValue("FR");
                r.createCell(2).setCellValue("biomass");
                r.createCell(3).setCellValue("c");
                r.createCell(4).setCellValue("cat");
                r.createCell(5).setCellValue("123.45");

                try (OutputStream os = Files.newOutputStream(file)) {
                    wb.write(os);
                }
            }

            when(trajectoryService.getTrajectoryFilePath(any(), anyString(), any())).thenReturn(file);
            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(tempDir);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getCapacityByYear())
                    .isEqualByComparingTo(BigDecimal.valueOf(123.45));
        }

        @Test
        void shouldSkipRowWhenAreaNotMatchingParam() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            AreaEntity de = new AreaEntity();
            de.setName("DE");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr, de));

            createInstalledWorkbook(Arrays.asList(
                    new Object[]{true, "DE", "biomass", "c", "cat", 100},
                    new Object[]{true, "FR", "biomass", "c", "cat", 200}
            ), 2030);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getArea()).isEqualTo("FR");
        }

        @Test
        void shouldSkipRowWhenAreaIsNotInStudyAreasAndOthersParam() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(Arrays.asList(
                    new Object[]{true, "XX", "biomass", "c", "cat", 100},
                    new Object[]{true, "FR", "biomass", "c", "cat", 200}
            ), 2030);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "OTHERS", false);

            assertThat(result.getMiscClusterCapacityEntities()).hasSize(1);
            assertThat(result.getMiscClusterCapacityEntities().get(0).getArea()).isEqualTo("FR");
        }
    }

    @Nested
    class VersionManagement {

        @Test
        void shouldSetVersionToOneForNewTrajectory() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            createInstalledWorkbook(Collections.singletonList(
                    new Object[]{true, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getVersion()).isEqualTo(1);
        }

        @Test
        void shouldIncrementVersionWhenChecksumDiffers() throws Exception {
            AreaEntity fr = new AreaEntity();
            fr.setName("FR");
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(fr));

            TrajectoryEntity existing = new TrajectoryEntity();
            existing.setChecksum("OLD_CHECKSUM");
            existing.setVersion(3);

            when(trajectoryRepository
                    .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                            anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(existing));

            createInstalledWorkbook(Collections.singletonList(
                    new Object[]{true, "FR", "biomass", "c", "cat", 100}
            ), 2030);

            TrajectoryEntity result = service.processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);

            assertThat(result.getVersion()).isEqualTo(4);
        }
    }

    @Nested
    class VerifySpecificAreaTsFile {

        @Test
        void shouldThrowWhenSelectedAreaNotInExpectedAreas() throws Exception {
            String horizon = "2029-2030";
            
            // Configurer pour que expectedAreas = [DE, IT] mais selectedArea = FR
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(Arrays.asList(
                            buildGroup("biomass", "Small biomass", "DE"),
                            buildGroup("biomass", "Small biomass", "IT")
                    ));

            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(root);

            // Créer le fichier TS avec la zone FR (qui est demandée mais pas attendue)
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE;IT\n1;2;3\n");

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not expected");
        }

        @Test
        void shouldThrowWhenAreaMissingInFile() throws Exception {
            String horizon = "2029-2030";
            
            // Configurer pour que expectedAreas = [FR] mais le fichier n'a pas FR
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(root);

            // Créer le fichier TS sans la zone FR
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "DE;IT\n1;2\n");

            assertThatThrownBy(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missing area");
        }

        @Test
        void shouldSucceedWhenAreaIsExpectedAndInHeader() throws Exception {
            String horizon = "2029-2030";
            
            when(miscClusterCapacityRepository.findByStudyIdAndArea(1, "FR"))
                    .thenReturn(List.of(buildGroup("biomass", "Small biomass", "FR")));

            Path root = Files.createTempDirectory(tempDir, "misc_load_");
            when(trajectoryService.buildTrajectoryPath(anyString(), any())).thenReturn(root);

            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE\n1;2\n");

            when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, 1, "FR")
            );
        }
    }

    @Nested
    class MergeSpecificTrajectoryWithActualOther {

        @Test
        void shouldLogWarningWhenExistingTsFileNotFoundButContinue() throws Exception {
            String horizon = "2029-2030";
            Integer studyId = 1;

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "OTHERS"))
                    .thenReturn(Arrays.asList(
                            buildGroup("biomass", "Small biomass", "FR"),
                            buildGroup("biomass", "Small biomass", "DE")
                    ));

            Path root = Files.createTempDirectory(tempDir, "misc_load_main_");
            when(trajectoryService.buildTrajectoryPath("loadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Créer le fichier TS principal avec FR et DE
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE\n1;2\n");

            // Créer une trajectoire existante sans fichier TS (exception sera attrapée et loggée)
            TrajectoryEntity existingTraj = TrajectoryEntity.builder()
                    .fileName("existingLoadFactor")
                    .area("IT")
                    .build();

            when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                    .thenReturn(List.of(existingTraj));

            Path existingRoot = Files.createTempDirectory(tempDir, "misc_load_existing_");
            when(trajectoryService.buildTrajectoryPath("existingLoadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(existingRoot);

            when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Le test doit réussir car l'exception est attrapée et loggée (pas levée)
            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, studyId, "OTHERS")
            );

            verify(trajectoryRepository, atLeastOnce()).save(any());
        }

        @Test
        void shouldContinueWhenExistingTsFileNotFoundAndCatchedException() throws Exception {
            String horizon = "2029-2030";
            Integer studyId = 1;

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "OTHERS"))
                    .thenReturn(Arrays.asList(
                            buildGroup("biomass", "Small biomass", "FR"),
                            buildGroup("biomass", "Small biomass", "DE")
                    ));

            Path root = Files.createTempDirectory(tempDir, "misc_load_main2_");
            when(trajectoryService.buildTrajectoryPath("loadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Créer le fichier TS principal
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE\n1;2\n");

            // Créer une trajectoire existante avec un fichier TS valide
            TrajectoryEntity existingTraj = TrajectoryEntity.builder()
                    .fileName("existingLoadFactor")
                    .area("IT")
                    .build();

            when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                    .thenReturn(List.of(existingTraj));

            Path existingRoot = Files.createTempDirectory(tempDir, "misc_load_existing2_");
            when(trajectoryService.buildTrajectoryPath("existingLoadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(existingRoot);

            // Créer le fichier TS existant
            Path existingGroupDir = existingRoot.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(existingGroupDir);
            Files.writeString(existingGroupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "IT;FR\n3;4\n");

            when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Le test doit réussir en fusionnant les headers
            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, studyId, "OTHERS")
            );

            verify(trajectoryRepository, atLeastOnce()).save(any());
        }

        @Test
        void shouldMergeMultipleExistingTrajectoryHeaders() throws Exception {
            String horizon = "2029-2030";
            Integer studyId = 1;

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "OTHERS"))
                    .thenReturn(Arrays.asList(
                            buildGroup("biomass", "Small biomass", "FR"),
                            buildGroup("biomass", "Small biomass", "DE")
                    ));

            Path root = Files.createTempDirectory(tempDir, "misc_load_main3_");
            when(trajectoryService.buildTrajectoryPath("loadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Créer le fichier TS principal
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE\n1;2\n");

            // Créer deux trajectoires existantes
            TrajectoryEntity traj1 = TrajectoryEntity.builder()
                    .fileName("traj1")
                    .area("IT")
                    .build();
            TrajectoryEntity traj2 = TrajectoryEntity.builder()
                    .fileName("traj2")
                    .area("ES")
                    .build();

            when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                    .thenReturn(Arrays.asList(traj1, traj2));

            Path root1 = Files.createTempDirectory(tempDir, "misc_load_traj1_");
            Path root2 = Files.createTempDirectory(tempDir, "misc_load_traj2_");

            when(trajectoryService.buildTrajectoryPath("traj1", TrajectoryType.MISC_LOAD))
                    .thenReturn(root1);
            when(trajectoryService.buildTrajectoryPath("traj2", TrajectoryType.MISC_LOAD))
                    .thenReturn(root2);

            // Créer les fichiers TS existants
            Path groupDir1 = root1.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir1);
            Files.writeString(groupDir1.resolve("load_factor_Small biomass_" + horizon + ".csv"), "IT;FR\n3;4\n");

            Path groupDir2 = root2.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir2);
            Files.writeString(groupDir2.resolve("load_factor_Small biomass_" + horizon + ".csv"), "ES;DE\n5;6\n");

            when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Le test doit réussir en fusionnant tous les headers
            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, studyId, "OTHERS")
            );

            verify(trajectoryRepository, atLeastOnce()).save(any());
        }

        @Test
        void shouldSkipOthersAreaTrajectories() throws Exception {
            String horizon = "2029-2030";
            Integer studyId = 1;

            when(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, "OTHERS"))
                    .thenReturn(Arrays.asList(
                            buildGroup("biomass", "Small biomass", "FR"),
                            buildGroup("biomass", "Small biomass", "DE")
                    ));

            Path root = Files.createTempDirectory(tempDir, "misc_load_main4_");
            when(trajectoryService.buildTrajectoryPath("loadFactor", TrajectoryType.MISC_LOAD))
                    .thenReturn(root);

            // Créer le fichier TS principal
            Path groupDir = root.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir);
            Files.writeString(groupDir.resolve("load_factor_Small biomass_" + horizon + ".csv"), "FR;DE\n1;2\n");

            // Créer trajectoires incluant une avec area=OTHERS (qui doit être ignorée)
            TrajectoryEntity traj1 = TrajectoryEntity.builder()
                    .fileName("traj1")
                    .area("OTHERS")
                    .build();
            TrajectoryEntity traj2 = TrajectoryEntity.builder()
                    .fileName("traj2")
                    .area("IT")
                    .build();

            when(trajectoryRepository.findAllByStudyIdAndHorizonAndTypeOrderByVersionDesc(studyId, horizon, TrajectoryType.MISC_LOAD.name()))
                    .thenReturn(Arrays.asList(traj1, traj2));

            Path root2 = Files.createTempDirectory(tempDir, "misc_load_traj2_");

            when(trajectoryService.buildTrajectoryPath("traj2", TrajectoryType.MISC_LOAD))
                    .thenReturn(root2);

            // Créer le fichier TS existant pour traj2
            Path groupDir2 = root2.resolve("biomass").resolve("Small biomass");
            Files.createDirectories(groupDir2);
            Files.writeString(groupDir2.resolve("load_factor_Small biomass_" + horizon + ".csv"), "IT;FR\n3;4\n");

            when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Le test doit réussir sans traiter la trajectoire OTHERS
            assertDoesNotThrow(() ->
                    service.processLoadFactorMiscFile("loadFactor", horizon, studyId, "OTHERS")
            );

            verify(trajectoryRepository, atLeastOnce()).save(any());
        }
    }
}
