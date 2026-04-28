package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.impl.ResFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResFileProcessorServiceImplTest {

    // Constants for test data
    private static final String AREA_FR = "FR";
    private static final String AREA_AT = "AT";
    private static final String AREA_IT = "IT";
    private static final String HORIZON_2029_2030 = "2029-2030";
    private static final String HORIZON_2026_2027 = "2026-2027";
    private static final String HORIZON_2030_2035 = "2030-2035";
    private static final String TECHNOLOGY_SOLAR_PV = "solar_pv";

    private static final String TECHNOLOGY_WIND_ONSHORE = "wind_onshore";
    private static final String TRAJECTORY_NAME = "loadFactorTrajectory";
    private static final String TRAJECTORY_WIND_NAME = "windLoadFactorTrajectory";
    private static final String DIRECTORY_RES_LOAD = "RES_LOAD";
    private static final String TRAJECTORY_PATH = "trajectory";
    private static final String NAS_DIR = "nas";
    private static final String TEST_USER = "testUser";
    private static final String ANOTHER_USER = "anotherUser";
    private static final String CSV_FILE_NAME = "load_factor.csv";
    private static final String CHECKSUM_DIFFERENT = "DIFFERENT_CHECKSUM";
    private static final String CHECKSUM_OLD = "OLD_CHECKSUM";
    private static final String BP_23_REF = "BP_23_REF";
    private static final String SOLAR_PV_LABEL = "Solar PV";
    private static final String WIND_OFFSHORE_LABEL = "Wind Offshore";
    private static final String WIND_ONSHORE_LABEL = "Wind Onshore";
    private static final int STUDY_ID = 1;
    protected static final String FILE_NOT_FOUND = "File not found: ";
    private static final String ZONAL_REPARTITION_FILE_NAME = "repartition_zonal_BP23_Aref";

    @InjectMocks
    private ResFileProcessorServiceImpl resFileProcessorServiceImpl;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private PathSecurityUtil pathSecurityUtil;

    // ======================================================
    // INSTALLED POWER RES
    // ======================================================

    @Nested
    class processInstalledResFile {
        private Path createMockResExcelFile(Path tempDir, String fileName, List<String> areas, String technology, boolean isNumericValues) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet = wb.createSheet("Sheet1");

                // Créer le header (ligne 0)
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("ToUse");
                header.createCell(1).setCellValue("Area");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("Cluster");
                header.createCell(4).setCellValue("Category");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                // Créer une row par area fournie
                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet.createRow(i + 1);  // Commence à ligne 1
                    var value = isNumericValues ? (100.0 + (i * 10)) : "truc";
                    dataRow.createCell(0).setCellValue(true);  // ToUse : fixe à true (modifiable si needed)
                    dataRow.createCell(1).setCellValue(currentArea);  // Area : varie selon le tableau
                    dataRow.createCell(2).setCellValue(technology);  // Group : mock
                    dataRow.createCell(3).setCellValue(technology);  // Cluster : varie légèrement pour unicité
                    dataRow.createCell(4).setCellValue("PV");  // Category : mock
                    if (value instanceof Number n) {
                        dataRow.createCell(5).setCellValue(n.doubleValue());
                    } else {
                        dataRow.createCell(5).setCellValue(String.valueOf(value));
                    }
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithNull(Path tempDir, String fileName, List<String> areas, String technology) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet = wb.createSheet("Sheet1");

                // Créer le header (ligne 0)
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("ToUse");
                header.createCell(1).setCellValue("Area");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("Cluster");
                header.createCell(4).setCellValue("Category");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                // Créer une row par area fournie
                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet.createRow(i + 1);  // Commence à ligne 1
                    dataRow.createCell(0).setCellValue(true);
                    dataRow.createCell(1).setCellValue(currentArea);
                    dataRow.createCell(2).setCellValue(technology);
                    dataRow.createCell(3);
                    dataRow.createCell(4).setCellValue("PV");
                    dataRow.createCell(5).setCellValue(200);
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockOffshoreExcelFile(Path tempDir, String fileName, String area, boolean isNumericalValue) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet = wb.createSheet("Sheet1");
                Row header = sheet.createRow(0);
                String areaFile = (area == null) ? AREA_FR : area;
                header.createCell(0).setCellValue("ToUse");
                header.createCell(1).setCellValue("Area");
                header.createCell(2).setCellValue("PECD_Zone");
                header.createCell(3).setCellValue("Group");
                header.createCell(4).setCellValue("Cluster");
                header.createCell(5).setCellValue("2030");
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue(true);
                dataRow.createCell(1).setCellValue(areaFile);
                dataRow.createCell(2).setCellValue("PECD_Zone_Value");
                dataRow.createCell(3).setCellValue("wind_offshore");
                dataRow.createCell(4).setCellValue("wind_offshore");
                dataRow.createCell(5).setCellValue(200.0);
                var value = isNumericalValue ? 200.0 : "truc";
                if (value instanceof Number n) {
                    dataRow.createCell(5).setCellValue(n.doubleValue());
                } else {
                    dataRow.createCell(5).setCellValue(String.valueOf(value));
                }
                wb.write(out);
            }
            return file;
        }

        private Path createMockOffshoreExcelFileWithWrongColumns(Path tempDir, String fileName) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet = wb.createSheet("Sheet1");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("ToUse");
                header.createCell(1).setCellValue("Area");
                header.createCell(2).setCellValue("TRUC");
                header.createCell(3).setCellValue("Group");
                header.createCell(4).setCellValue("CHOSE");
                header.createCell(5).setCellValue("2030");
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue(true);
                dataRow.createCell(1).setCellValue(AREA_FR);
                dataRow.createCell(2).setCellValue("PECD_Zone_Value");
                dataRow.createCell(3).setCellValue("wind_offshore");
                dataRow.createCell(4).setCellValue("wind_offshore");
                dataRow.createCell(5).setCellValue(200.0);
                wb.write(out);
            }
            return file;
        }

        @Test
        void successfulProcessingWhenDefaultAreaAndTechnology(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve(BP_23_REF);
            Files.createDirectories(nestedDir);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, TECHNOLOGY_SOLAR_PV, true);

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(STUDY_ID)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni(TEST_USER);
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    BP_23_REF, HORIZON_2029_2030, STUDY_ID, AREA_FR, SOLAR_PV_LABEL, false
            );

            // THEN
            assertNotNull(result);
            assertEquals(BP_23_REF, result.getFileName());
            assertEquals(1, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenDefaultAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);
            List<String> areas = List.of(AREA_FR);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "BP_23_REF", "2029-2030", 1, AREA_FR, null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("BP_23_REF", result.getFileName());
            assertEquals(2, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenSpecificAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("solar_pv_BP23_Aref", result.getFileName());
            assertEquals(2, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenOtherAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_IT, AREA_FR);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "OTHERS", null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("solar_pv_BP23_Aref", result.getFileName());
            assertEquals(2, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenSpecificAreaWithTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);
            createMockResExcelFile(tempRoot, "installedRES_solar_thermo_BP23_Aref.xlsx", List.of(AREA_FR, AREA_AT), "solar_thermo", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
            );

            // THEN
            assertNotNull(result);
            assertEquals("solar_pv_BP23_Aref", result.getFileName());
            assertEquals(2, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_CAPACITY.name());
            trajectoryEntity.setArea(AREA_AT);
            trajectoryEntity.setFileName("test");
            trajectoryEntity.setVersion(1);
            trajectoryEntity.setHorizon("2029-2030");
            trajectoryEntity.setChecksum("ABC123");
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(trajectoryEntity));
            when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

            // WHEN
            TrajectoryEntity trajectory = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
            );

            assertThat(trajectory).isNotNull();
            assertThat(trajectory.getVersion()).isEqualTo(2);
        }

        @Test
        void shouldThrowWhenAlreadyProcessedSameContent(@TempDir Path tempRoot) throws Exception {
            // On crée un fichier avec une seule ligne valide
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // Créer une première trajectoire
            // WHEN
            TrajectoryEntity firstResult = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
            );

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.getChecksum()).isNotNull();

            // Recréer le même fichier avec les mêmes données
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);

            // Mock pour retourner la première trajectoire
            when(trajectoryRepository
                    .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                            anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(firstResult));

            // Le deuxième appel avec le même contenu devrait lever une exception
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File already processed");
        }

        @Test
        void shouldThrowWhenNoFileFoundInFRDirectory(@TempDir Path tempRoot) throws Exception {
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve(BP_23_REF);
            Files.createDirectories(nestedDir);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No FR res capacity file found in directory:");
        }

        @Test
        void shouldThrowWhenNoFileFoundInIPRootDirectory(@TempDir Path tempRoot) throws Exception {
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(FILE_NOT_FOUND);
        }

        @Test
        void throwsExceptionForDefaultAreaWhenHorizonIsWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);
            List<String> areas = List.of(AREA_FR);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            BP_23_REF, HORIZON_2026_2027, STUDY_ID, AREA_FR, SOLAR_PV_LABEL, false
                    )
            );
            assertTrue(exception.getMessage().contains("Horizon"));
        }

        @Test
        void throwsExceptionForDefaultAreaWithoutTechnologyAndNoFiles(@TempDir Path tempRoot) throws Exception {
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(nestedDir);
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "invalid_res.xlsx", "2030", 1, AREA_FR, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("No FR res capacity file found in directory"));
        }

        @Test
        void throwsExceptionWhenDefaultAreaAndTechnologyOffshoreFileColumnsAreWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFileWithWrongColumns(nestedDir, "installedRES_wind_offshore_BP23_Aref.xlsx");

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            BP_23_REF, HORIZON_2029_2030, STUDY_ID, AREA_FR, WIND_OFFSHORE_LABEL, false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionWhenDefaultAreaAndTechnologyOnshoreFileColumnsAreWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFileWithWrongColumns(nestedDir, "installedRES_wind_onshore_BP23_Aref.xlsx");

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            BP_23_REF, HORIZON_2029_2030, STUDY_ID, AREA_FR, WIND_ONSHORE_LABEL, false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenNoAreaForArea(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve(AREA_FR);
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);
            createMockResExcelFile(nestedDir, "installedRES_solar_thermo_BP23_Aref.xlsx", List.of(AREA_FR, AREA_AT), "solar_thermo", true);
            List<String> areas = List.of(AREA_FR);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP_23_REF", "2029-2030", 1, AREA_FR, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("None of the areas of trajectory AREA are present"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenAreaSelectedNotInAREA(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_IT, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected area {0} is not present in the 'node' column"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenTechnologySelectedNotInTrajectory(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "Wind offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected technology {1}/{0} is not present in the 'node' column of"));
        }

        @Test
        void throwsExceptionWhenDataOnshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster"));
        }

        @Test
        void throwsExceptionWhenDataOffshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(tempRoot, "installedRES_wind_offshore_BP23_Aref.xlsx", AREA_AT, false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_wind_offshore_BP23_Aref", "2029-2030", 1, AREA_AT, "Wind offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster"));
        }

        @Test
        void throwsExceptionWhenValuesInRequiredColumnsAreaAreaNull(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFileWithNull(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("values can't be empty"));
        }

        @Test
        void shouldThrowBusinessExceptionWhenProcessingFails(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);
            // Given
            ResFileProcessorServiceImpl spy = spy(resFileProcessorServiceImpl);
            // On mock la méthode interne pour forcer une IOException
            doThrow(new IOException("boom"))
                    .when(spy)
                    .processResCapacityFile(
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyList(),
                            Mockito.anyBoolean(),
                            Mockito.any()
                    );

            // When + Then
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> spy.processInstalledResFile("installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "solar", false)
            );

            assertEquals("Could not process RES installed power trajectory", ex.getMessage());
        }
    }

    @Nested
    class processLoadFactorResFile {
        @Test
        void processLoadFactorResFileSucceedsWithValidCsvFiles(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_LOAD.name());
            trajectoryEntity.setArea(AREA_FR);
            trajectoryEntity.setFileName(TRAJECTORY_NAME);
            trajectoryEntity.setVersion(1);
            trajectoryEntity.setHorizon(HORIZON_2029_2030);
            trajectoryEntity.setTechnology(TECHNOLOGY_SOLAR_PV);
            trajectoryEntity.setHasTimeSeries(true);
            
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);     
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN
            assertNotNull(result);
            assertEquals(TRAJECTORY_NAME, result.getFileName());
            assertEquals(TrajectoryType.RES_LOAD.name(), result.getType());
            assertEquals(AREA_FR, result.getArea());
            assertEquals(TECHNOLOGY_SOLAR_PV, result.getTechnology());
            assertEquals(1, result.getVersion());
            assertTrue(result.getHasTimeSeries());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenNoCsvFiles(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No csv file found in technology folder");
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenTechnologyFolderNotFound(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD);
            Files.createDirectories(trajectoryDir);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No technology folder found");
        }

        @Test
        void processLoadFactorResFileIncrementsVersionWhenTrajectoryExists(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            TrajectoryEntity existingTrajectory = new TrajectoryEntity();
            existingTrajectory.setVersion(2);
            existingTrajectory.setChecksum(CHECKSUM_DIFFERENT);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setVersion(existingTrajectory.getVersion() + 1);

            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN
            assertNotNull(result);
            assertEquals(3, result.getVersion());
        }

        @Test
        void processLoadFactorResFileUsesExistingChecksumWhenDifferent(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            TrajectoryEntity existingTrajectory = new TrajectoryEntity();
            existingTrajectory.setVersion(2);
            existingTrajectory.setChecksum(CHECKSUM_OLD);

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setVersion(existingTrajectory.getVersion() + 1);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN - Version should be incremented since checksum is different
            assertNotNull(result);
            assertEquals(3, result.getVersion());
            assertNotEquals(existingTrajectory.getChecksum(), result.getChecksum());
        }

        @Test
        void processLoadFactorResFileCreatesTrajectoryWithTimeSeries(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_WIND_NAME).resolve(TECHNOLOGY_WIND_ONSHORE).resolve(TECHNOLOGY_WIND_ONSHORE);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "timeseries_1.csv");
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "timeseries_2.csv");

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_AT, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_LOAD.name());
            trajectoryEntity.setFileName(TRAJECTORY_WIND_NAME);
            trajectoryEntity.setCreatedBy(ANOTHER_USER);
            trajectoryEntity.setHasTimeSeries(true);
            trajectoryEntity.setChecksum("checksum");
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_WIND_NAME, HORIZON_2030_2035, STUDY_ID, AREA_AT, TECHNOLOGY_WIND_ONSHORE
            );

            // THEN
            assertNotNull(result);
            assertEquals(TRAJECTORY_WIND_NAME, result.getFileName());
            assertTrue(result.getHasTimeSeries());
            assertEquals(ANOTHER_USER, result.getCreatedBy());
            assertNotNull(result.getChecksum());
        }

        @Test
        void processLoadFactorResFileRejectsPathTraversalAttempts(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Setup with valid path
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN - Path traversal with .. should be blocked or result in invalid path
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            "../../../etc/passwd", HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
                    ))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void processLoadFactorResFileRejectsInvalidTechnologyPathBeforeFilesystemAccess() throws Exception {
            when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp/nas");
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            doAnswer(invocation -> {
                String value = invocation.getArgument(0, String.class);
                if (value.contains("..")) {
                    throw new IOException("Entry is outside of the allowed directory");
                }
                return null;
            }).when(pathSecurityUtil).validatePathFromBaseDir(anyString(), any(Function.class));

            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, "../../../etc/passwd"
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid trajectory path");
        }

        @Test
        void processLoadFactorResFileSafelyHandlesSymbolicLinks(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Create legitimate CSV file
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setFileName(TRAJECTORY_NAME);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN - Process with valid path
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN
            assertNotNull(result);
            assertEquals(TRAJECTORY_NAME, result.getFileName());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenChecksumNotChanged(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            TrajectoryEntity existingTrajectory = new TrajectoryEntity();
            existingTrajectory.setVersion(1);
            existingTrajectory.setChecksum("EXISTING_CHECKSUM");
            existingTrajectory.setFileName(TRAJECTORY_NAME);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(existingTrajectory);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> {
                TrajectoryEntity saved = invocation.getArgument(0);
                saved.setId(999);
                return saved;
            });

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN - Verify that when checksum is the same, no new version is created
            assertNotNull(result);
            assertEquals(TRAJECTORY_NAME, result.getFileName());
        }

        @Test
        void processLoadFactorResFileHandlesMultipleCsvFiles(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_WIND_ONSHORE).resolve(TECHNOLOGY_WIND_ONSHORE);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "2030.csv");
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "2031.csv");
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "2032.csv");

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setTechnology(TECHNOLOGY_WIND_ONSHORE);
            trajectoryEntity.setHasTimeSeries(true);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_WIND_ONSHORE
            );

            // THEN
            assertNotNull(result);
            assertTrue(result.getHasTimeSeries());
            assertEquals(TECHNOLOGY_WIND_ONSHORE, result.getTechnology());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void processLoadFactorResFileHandlesNonCsvFilesInDirectory(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, "data.csv");
            Files.createFile(trajectoryDir.resolve("readme.txt"));
            Files.createFile(trajectoryDir.resolve("metadata.json"));

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setHasTimeSeries(true);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN - Should succeed even with non-CSV files present
            assertNotNull(result);
            assertTrue(result.getHasTimeSeries());
        }

        // ======================================================
        // Tests for new behavior when technology is not specified
        // ======================================================

        @Test
        void processLoadFactorResFileSucceedsWhenTechnologyNotSpecifiedAndAllFourTechnologiesExist(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Create all 4 required technologies
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryBaseDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME);

            // Create all 4 required technologies with CSV files
            createTechnologyWithCsv(trajectoryBaseDir, "wind onshore");
            createTechnologyWithCsv(trajectoryBaseDir, "wind offshore");
            createTechnologyWithCsv(trajectoryBaseDir, "solar pv");
            createTechnologyWithCsv(trajectoryBaseDir, "solar thermo");

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setHasTimeSeries(true);
            trajectoryEntity.setTechnology(null);
            trajectoryEntity.setFileName(TRAJECTORY_NAME);
            trajectoryEntity.setVersion(1);
            trajectoryEntity.setArea(AREA_FR);
            trajectoryEntity.setType(TrajectoryType.RES_LOAD.name());
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), isNull()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN - Call without technology parameter (null)
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null
            );

            // THEN
            assertNotNull(result);
            assertEquals(TRAJECTORY_NAME, result.getFileName());
            assertEquals(TrajectoryType.RES_LOAD.name(), result.getType());
            assertEquals(AREA_FR, result.getArea());
            assertNull(result.getTechnology());
            assertEquals(1, result.getVersion());
            assertTrue(result.getHasTimeSeries());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenTechnologyNotSpecifiedAndMissingTechnologies(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Create only 2 technologies (missing wind offshore and solar thermo)
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryBaseDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME);

            createTechnologyWithCsv(trajectoryBaseDir, "wind onshore");
            createTechnologyWithCsv(trajectoryBaseDir, "solar pv");

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Missing required technologies")
                    .hasMessageContaining("wind offshore")
                    .hasMessageContaining("solar thermo");
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenTechnologyNotSpecifiedAndTechnologyFolderNotFound(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Trajectory folder doesn't exist
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD);
            Files.createDirectories(trajectoryDir);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Trajectory folder not found");
        }

        @Test
        void processLoadFactorResFileThrowsExceptionWhenTechnologyNotSpecifiedAndMissingCsvInOneTechnology(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Create all 4 technologies but one is missing CSV files
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryBaseDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME);

            createTechnologyWithCsv(trajectoryBaseDir, "wind onshore");
            createTechnologyWithCsv(trajectoryBaseDir, "wind offshore");
            createTechnologyWithCsv(trajectoryBaseDir, "solar pv");
            // Create solar thermo folder but without CSV
            Path solarThermoDir = trajectoryBaseDir.resolve("solar thermo").resolve("solar thermo");
            Files.createDirectories(solarThermoDir);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);

            // WHEN & THEN
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processLoadFactorResFile(
                            TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Missing required technologies")
                    .hasMessageContaining("solar thermo");
        }

        @Test
        void processLoadFactorResFileIncrementsVersionWhenTechnologyNotSpecifiedAndTrajectoryExists(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryBaseDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME);

            createTechnologyWithCsv(trajectoryBaseDir, "wind onshore");
            createTechnologyWithCsv(trajectoryBaseDir, "wind offshore");
            createTechnologyWithCsv(trajectoryBaseDir, "solar pv");
            createTechnologyWithCsv(trajectoryBaseDir, "solar thermo");

            TrajectoryEntity existingTrajectory = new TrajectoryEntity();
            existingTrajectory.setVersion(2);
            existingTrajectory.setChecksum(CHECKSUM_DIFFERENT);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setHasTimeSeries(true);
            trajectoryEntity.setVersion(3);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), isNull()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null
            );

            // THEN
            assertNotNull(result);
            assertEquals(3, result.getVersion());
            assertNull(result.getTechnology());
        }

        @Test
        void processLoadFactorResFileWhenTechnologySpecifiedIgnoresOtherTechnologies(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Only one technology with CSV
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
            Files.createDirectories(trajectoryDir);
            CreateExcelTestUtil.createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setHasTimeSeries(true);
            trajectoryEntity.setTechnology(TECHNOLOGY_SOLAR_PV);
            trajectoryEntity.setVersion(1);
            when(trajectoryService.buildDirectoryTrajectory(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(trajectoryEntity);
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN - Call WITH technology parameter (should use original behavior)
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN - Should succeed with specific technology, ignoring the rule about 4 technologies
            assertNotNull(result);
            assertEquals(TECHNOLOGY_SOLAR_PV, result.getTechnology());
            assertEquals(1, result.getVersion());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void processLoadFactorResFileResolvesNormalizedParentAndDisplayLeafFolders(@TempDir Path tempRoot) throws Exception {
            // GIVEN - Folder structure like solar_pv/"solar pv"
            Path nasDir = tempRoot.resolve(NAS_DIR);
            Path trajectoryBaseDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
                    .resolve(TRAJECTORY_NAME);
            createTechnologyWithCsv(trajectoryBaseDir, "solar_pv", "solar pv");

            when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
            when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
            when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                    .thenReturn(DIRECTORY_RES_LOAD);
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni(TEST_USER);
            }});
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                    TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
            );

            // THEN
            assertNotNull(result);
            assertEquals(TECHNOLOGY_SOLAR_PV, result.getTechnology());
            assertEquals(1, result.getVersion());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        private void createTechnologyWithCsv(Path trajectoryBaseDir, String technology) throws IOException {
            createTechnologyWithCsv(trajectoryBaseDir, technology, technology);
        }

        private void createTechnologyWithCsv(Path trajectoryBaseDir, String technologyFolder, String leafFolder) throws IOException {
            Path technologyDir = trajectoryBaseDir.resolve(technologyFolder).resolve(leafFolder);
            Files.createDirectories(technologyDir);
            CreateExcelTestUtil.createMockCsvFile(technologyDir, leafFolder + "_data.csv");
        }
    }

    @Nested
    class processTechnologyDistributionResFile {
        private Path createMockResExcelFile(Path tempDir, String fileName, List<String> areas, String technology, boolean isNumericValues) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet0 = wb.createSheet("Sheet0");

                // Créer le header (ligne 0)
                Row header = sheet0.createRow(0);
                header.createCell(0).setCellValue("Group");
                header.createCell(1).setCellValue("Cluster");
                header.createCell(2).setCellValue("Area");
                header.createCell(3).setCellValue("PECD_Zone");
                header.createCell(4).setCellValue("Techno_PECD");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                // Créer une row par area fournie
                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet0.createRow(i + 1);  // Commence à ligne 1
                    var value = isNumericValues ? (100.0 + (i * 10)) : "truc";
                    dataRow.createCell(0).setCellValue(technology);
                    dataRow.createCell(1).setCellValue(technology);
                    dataRow.createCell(2).setCellValue(currentArea);
                    dataRow.createCell(3).setCellValue(currentArea+"0"+i);
                    dataRow.createCell(4).setCellValue("SP199_HH200");
                    if (value instanceof Number n) {
                        dataRow.createCell(5).setCellValue(n.doubleValue());
                    } else {
                        dataRow.createCell(5).setCellValue(String.valueOf(value));
                    }
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithNull(Path tempDir, String fileName, List<String> areas, String technology) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet1 = wb.createSheet("Sheet1");

                // Créer le header (ligne 0)
                Row header = sheet1.createRow(0);
                header.createCell(0).setCellValue("Group");
                header.createCell(1).setCellValue("Cluster");
                header.createCell(2).setCellValue("Area");
                header.createCell(3).setCellValue("PECD_Zone");
                header.createCell(4).setCellValue("Techno_PECD");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet1.createRow(i + 1);  // Commence à ligne 1
                    dataRow.createCell(0).setCellValue(technology);
                    dataRow.createCell(1).setCellValue(technology);
                    dataRow.createCell(2).setCellValue(currentArea);
                    dataRow.createCell(3);
                    dataRow.createCell(4);
                    dataRow.createCell(5).setCellValue(400);
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithoutDataRow(Path tempDir, String fileName) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet1 = wb.createSheet("Sheet1");

                // Créer le header (ligne 0)
                Row header = sheet1.createRow(0);
                header.createCell(0).setCellValue("Group");
                header.createCell(1).setCellValue("Cluster");
                header.createCell(2).setCellValue("Area");
                header.createCell(3).setCellValue("PECD_Zone");
                header.createCell(4).setCellValue("Techno_PECD");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithMissingColumns(Path tempDir, String fileName, List<String> areas, String technology) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet1 = wb.createSheet("Sheet1");

                // Créer le header (ligne 0)
                Row header = sheet1.createRow(0);
                header.createCell(0).setCellValue("Group");
                header.createCell(1).setCellValue("machin");
                header.createCell(2).setCellValue("Area");
                header.createCell(3).setCellValue("truc");
                header.createCell(4).setCellValue("Techno_PECD");
                header.createCell(5).setCellValue("2030");  // Exemple d'année

                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet1.createRow(i + 1);  // Commence à ligne 1
                    dataRow.createCell(0).setCellValue(technology);
                    dataRow.createCell(1).setCellValue(technology);
                    dataRow.createCell(2).setCellValue(currentArea);
                    dataRow.createCell(3);
                    dataRow.createCell(4);
                    dataRow.createCell(5).setCellValue(400);
                }

                wb.write(out);
            }
            return file;
        }

        @Test
        void successfulProcessingWhenAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                    "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("BP23_Aref", result.getFileName());
            assertEquals(1, result.getResTechnologyDistributionCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenAreaWithTechnology(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_FR, AREA_AT);
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                    "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
            );

            // THEN
            assertNotNull(result);
            assertEquals("BP23_Aref", result.getFileName());
            assertEquals("solar_pv", result.getTechnology());
            assertEquals(1, result.getResTechnologyDistributionCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", List.of(AREA_FR), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name());
            trajectoryEntity.setArea(AREA_FR);
            trajectoryEntity.setFileName("test");
            trajectoryEntity.setVersion(1);
            trajectoryEntity.setHorizon("2029-2030");
            trajectoryEntity.setChecksum("ABC123");
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(trajectoryEntity));
            when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

            // WHEN
            TrajectoryEntity trajectory = resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                    "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
            );

            assertThat(trajectory).isNotNull();
            assertThat(trajectory.getVersion()).isEqualTo(2);
        }

        @Test
        void shouldThrowWhenAlreadyProcessedSameContent(@TempDir Path tempRoot) throws Exception {
            // On crée un fichier avec une seule ligne valide
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", List.of(AREA_FR), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.empty());

            // Créer une première trajectoire
            // WHEN
            TrajectoryEntity firstResult = resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                    "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
            );

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.getChecksum()).isNotNull();

            // Recréer le même fichier avec les mêmes données
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", List.of(AREA_FR), "solar_pv", true);

            // Mock pour retourner la première trajectoire au deuxième appel
            Mockito.reset(trajectoryRepository);
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(firstResult));

            // Le deuxième appel avec le même contenu devrait lever une exception
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File already processed");
        }

        @Test
        void throwsExceptionForAreaWhenHorizonIsWrong(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_FR);

            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", HORIZON_2026_2027, STUDY_ID, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("Horizon"));
        }

        @Test
        void throwsExceptionWhenValuesInRequiredColumnsAreaAreaNull(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFileWithNull(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("values can't be empty in"));
        }

        @Test
        void throwsExceptionForAreaWhenNoAreaForArea(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", List.of(AREA_AT, AREA_AT), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("None of the areas of trajectory AREA are present"));
        }

        @Test
        void throwsExceptionForAreaWhenAreaSelectedNotInAREA(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT);
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_IT, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected area {0} is not present in the 'node' column"));
        }

        @Test
        void throwsExceptionForAreaWhenTechnologySelectedNotInTrajectory(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT, AREA_AT);
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_AT, "Wind offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected technology {1}/{0} is not present in the 'node' column of"));
        }

        @Test
        void throwsExceptionWhenDataOnshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFile(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv", false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", "2029-2030", 1, AREA_FR, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster"));
        }

        @Test
        void throwsExceptionWhenMissingColumns(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_FR);
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFileWithMissingColumns(tempRoot, "repartition_techno_BP23_Aref.xlsx", areas, "solar_pv");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", HORIZON_2029_2030, STUDY_ID, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionWhenOnlyEmptyRows(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFileWithoutDataRow(tempRoot, "repartition_techno_BP23_Aref.xlsx");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_BP23_Aref", HORIZON_2029_2030, STUDY_ID, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("No area found in"));
        }

        @Test
        void shouldThrowWhenNoFileFoundInTechnologyDistributionRootDirectory(@TempDir Path tempRoot) throws Exception {
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processTechnologyDistributionResFile(
                            "repartition_techno_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(FILE_NOT_FOUND);
        }

        @Test
        void shouldThrowBusinessExceptionWhenProcessingFails(@TempDir Path tempRoot) throws IOException {
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);
            // Given
            ResFileProcessorServiceImpl spy = spy(resFileProcessorServiceImpl);
            // On mock la méthode interne pour forcer une IOException
            doThrow(new IOException("boom"))
                    .when(spy)
                    .processResCapacityFile(
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyList(),
                            Mockito.anyBoolean(),
                            Mockito.any()
                    );

            // When + Then
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> spy.processTechnologyDistributionResFile("repartition_techno_pv_BP23_Aref", "2029-2030", 1, "FR", "solar", false)
            );

            assertEquals("Could not import RES technology distribution trajectory", ex.getMessage());
        }

        @Test
        void shouldThrowBusinessExceptionWhenResFileCannotBeImported(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            String trajectory = "repartition_techno_pv_BP23_Aref";
            String horizon = "2029-2030";
            Integer studyId = 1;
            String area = "AT";
            String tech = "solar_pv";

            // Le fichier attendu par la méthode
            Path fakeFile = tempRoot.resolve("repartition_techno_pv_BP23_Aref.xlsx");
            createMockResExcelFile(tempRoot, "repartition_techno_pv_BP23_Aref.xlsx", List.of(area), "solar_pv", true);

            // Mock du répertoire (IMPORTANT : renvoyer le dossier, pas le fichier)
            when(trajectoryService.normalizeAndValidateDirectory(
                    eq(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION),
                    eq(area),
                    isNull()
            )).thenReturn(tempRoot);

            // Mock de loadStudyAreas
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(area);
            }}));

            ResFileProcessorServiceImpl spy = spy(resFileProcessorServiceImpl);

            // Mock du processResCapacityFile qui doit lancer IOException
            doThrow(new IOException("boom"))
                    .when(spy)
                    .processResCapacityFile(
                            eq(fakeFile),
                            anyString(),
                            eq(horizon),
                            eq(area),
                            any(),
                            anyList(),
                            eq(false),
                            eq(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION)
                    );

            // WHEN + THEN
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> spy.processTechnologyDistributionResFile(
                            trajectory, horizon, studyId, area, tech, false
                    )
            );

            assertEquals("Could not import RES technology distribution trajectory", ex.getMessage());
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
    }

    @Nested
    class processZonalDistributionResFile {
        private Path createMockResExcelFile(Path tempDir, String fileName, List<String> areas, boolean isNumericValues) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet0 = wb.createSheet("Sheet0");
                Row header = sheet0.createRow(0);
                header.createCell(0).setCellValue("Area");
                header.createCell(1).setCellValue("PECD_zone");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("2030");

                // Créer une row par area fournie
                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet0.createRow(i + 1);  // Commence à ligne 1
                    var value = isNumericValues ? (100.0 + (i * 10)) : "truc";
                    dataRow.createCell(0).setCellValue(currentArea);
                    dataRow.createCell(1).setCellValue(currentArea+"0"+i);
                    dataRow.createCell(2).setCellValue("wind offshore");
                    if (value instanceof Number n) {
                        dataRow.createCell(3).setCellValue(n.doubleValue());
                    } else {
                        dataRow.createCell(3).setCellValue(String.valueOf(value));
                    }
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithNull(Path tempDir, String fileName, List<String> areas) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet0 = wb.createSheet("Sheet0");
                Row header = sheet0.createRow(0);
                header.createCell(0).setCellValue("Area");
                header.createCell(1).setCellValue("PECD_zone");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("2030");

                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet0.createRow(i + 1);  // Commence à ligne 1
                    dataRow.createCell(0).setCellValue(currentArea);
                    dataRow.createCell(1).setCellValue(currentArea+"0"+i);
                    dataRow.createCell(2);
                    dataRow.createCell(3).setCellValue(0.05);
                }

                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithoutDataRow(Path tempDir, String fileName) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet0 = wb.createSheet("Sheet0");
                Row header = sheet0.createRow(0);
                header.createCell(0).setCellValue("Area");
                header.createCell(1).setCellValue("PECD_zone");
                header.createCell(2).setCellValue("Group");
                header.createCell(3).setCellValue("2030");
                wb.write(out);
            }
            return file;
        }

        private Path createMockResExcelFileWithMissingColumns(Path tempDir, String fileName, List<String> areas) throws Exception {
            Path file = tempDir.resolve(fileName);
            try (var wb = new XSSFWorkbook(); var out = Files.newOutputStream(file)) {
                Sheet sheet0 = wb.createSheet("Sheet0");
                Row header = sheet0.createRow(0);
                header.createCell(0).setCellValue("Area");
                header.createCell(1).setCellValue("PECD_zone");
                header.createCell(3).setCellValue("2030");

                for (int i = 0; i < areas.size(); i++) {
                    String currentArea = areas.get(i);
                    Row dataRow = sheet0.createRow(i + 1);  // Commence à ligne 1
                    dataRow.createCell(0).setCellValue(currentArea);
                    dataRow.createCell(1).setCellValue(currentArea+"0"+i);
                    dataRow.createCell(3).setCellValue(0.67);
                }

                wb.write(out);
            }
            return file;
        }

        @Test
        void successfulProcessingWhenAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas, true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processZonalDistributionResFile(
                    ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals(ZONAL_REPARTITION_FILE_NAME, result.getFileName());
            assertEquals(1, result.getResZonalDistributionCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void shouldCreateTrajectoryWithIncrementVersionWhenTrajectoryExists(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", List.of(AREA_FR), true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name());
            trajectoryEntity.setArea(AREA_FR);
            trajectoryEntity.setFileName("test");
            trajectoryEntity.setVersion(1);
            trajectoryEntity.setHorizon("2029-2030");
            trajectoryEntity.setChecksum("ABC123");
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(trajectoryEntity));
            when(trajectoryRepository.save(any())).thenAnswer((Answer<TrajectoryEntity>) inv -> inv.getArgument(0));

            // WHEN
            TrajectoryEntity trajectory = resFileProcessorServiceImpl.processZonalDistributionResFile(
                    ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, "solar_pv", false
            );

            assertThat(trajectory).isNotNull();
            assertThat(trajectory.getVersion()).isEqualTo(2);
        }

        @Test
        void shouldThrowWhenAlreadyProcessedSameContent(@TempDir Path tempRoot) throws Exception {
            // On crée un fichier avec une seule ligne valide
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", List.of(AREA_FR),  true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{
                setNni("testUser");
            }});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.empty());

            // Créer une première trajectoire
            // WHEN
            TrajectoryEntity firstResult = resFileProcessorServiceImpl.processZonalDistributionResFile(
                    ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, null, false
            );

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.getChecksum()).isNotNull();

            // Recréer le même fichier avec les mêmes données
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", List.of(AREA_FR),  true);

            // Mock pour retourner la première trajectoire au deuxième appel
            Mockito.reset(trajectoryRepository);
            when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                    anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(firstResult));

            // Le deuxième appel avec le même contenu devrait lever une exception
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, null, false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File already processed");
        }

        @Test
        void throwsExceptionForAreaWhenHorizonIsWrong(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_FR);

            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas, true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, HORIZON_2026_2027, STUDY_ID, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("Horizon"));
        }

        @Test
        void throwsExceptionWhenValuesInRequiredColumnsAreaAreaNull(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFileWithNull(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("values can't be empty in"));
        }

        @Test
        void throwsExceptionForAreaWhenNoAreaForArea(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", List.of(AREA_AT, AREA_AT), true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("None of the areas of trajectory AREA are present"));
        }

        @Test
        void throwsExceptionForAreaWhenAreaSelectedNotInAREA(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_AT);
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas, true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_IT);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_IT, "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected area {0} is not present in the 'node' column"));
        }

        @Test
        void throwsExceptionWhenDataOnshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of(AREA_FR);
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas,  false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, null, false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster"));
        }

        @Test
        void throwsExceptionWhenMissingColumns(@TempDir Path tempRoot) throws Exception {
            List<String> areas = List.of(AREA_FR);
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFileWithMissingColumns(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", areas);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, "solar_pv", false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionWhenOnlyEmptyRows(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFileWithoutDataRow(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_FR);
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, null, false
                    )
            );
            assertTrue(exception.getMessage().contains("No area found in"));
        }

        @Test
        void shouldThrowWhenNoFileFoundInTechnologyDistributionRootDirectory(@TempDir Path tempRoot) throws Exception {
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(AREA_AT);
            }}));

            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_AT, null, false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(FILE_NOT_FOUND);
        }

        @Test
        void shouldThrowBusinessExceptionWhenProcessingFails(@TempDir Path tempRoot) throws IOException {
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);
            // Given
            ResFileProcessorServiceImpl servicespy = spy(resFileProcessorServiceImpl);
            // On mock la méthode interne pour forcer une IOException
            Mockito.lenient().doThrow(new IOException("boom"))
                    .when(servicespy)
                    .processResCapacityFile(
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.any(),
                            Mockito.anyList(),
                            Mockito.anyBoolean(),
                            Mockito.any()
                    );

            // When + Then
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> servicespy.processZonalDistributionResFile(ZONAL_REPARTITION_FILE_NAME, "2029-2030", 1, AREA_FR, null, false)
            );

            assertEquals("Could not import RES zonal distribution trajectory", ex.getMessage());
        }

        @Test
        void shouldThrowBusinessExceptionWhenResFileCannotBeImported(@TempDir Path tempRoot) throws Exception {
            // GIVEN
            String horizon = "2029-2030";
            Integer studyId = 1;
            String area = AREA_AT;

            // Le fichier attendu par la méthode
            createMockResExcelFile(tempRoot, ZONAL_REPARTITION_FILE_NAME+".xlsx", List.of(area), true);

            // Mock du répertoire (IMPORTANT : renvoyer le dossier, pas le fichier)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Mock de loadStudyAreas
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName(area);
            }}));

            ResFileProcessorServiceImpl spy = spy(resFileProcessorServiceImpl);

            // Mock du processResCapacityFile qui doit lancer IOException
            Mockito.lenient().doThrow(new IOException("boom"))
                    .when(spy)
                    .processResCapacityFile(
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.any(),
                            Mockito.anyList(),
                            Mockito.anyBoolean(),
                            Mockito.any()
                    );

            // WHEN + THEN
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> spy.processZonalDistributionResFile(
                            ZONAL_REPARTITION_FILE_NAME, horizon, studyId, area, null, false
                    )
            );

            assertEquals("Could not import RES zonal distribution trajectory", ex.getMessage());
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        }
    }
}