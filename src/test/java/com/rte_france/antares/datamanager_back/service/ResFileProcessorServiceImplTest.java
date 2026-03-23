package com.rte_france.antares.datamanager_back.service.res;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.impl.ResFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class ResFileProcessorServiceImplTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class InstalledResFileProcessorTest {

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

        @Test
        void successfulProcessingWhenDefaultAreaAndTechnology(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR");  // Simule directoryPath pour "FR"
            Files.createDirectories(frDir);  // Crée "FR"

            Path nestedDir = frDir.resolve("BP_23_REF");  // Simule folderPath = directoryPath.resolve("BP_23_REF")
            Files.createDirectories(nestedDir);  // Crée "BP_23_REF" (fixe l'erreur de dossier inexistant)

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            List<String> areas = List.of("FR");
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "BP_23_REF", "2029-2030", 1, "FR", "Solar PV", false
            );

            // THEN
            assertNotNull(result);
            assertEquals("solar_pv_BP23_Aref", result.getFileName());  // Ajustez si votre prefix/nom diffère
            assertEquals(1, result.getResClusterCapacityEntities().size());  // OK, seulement 1 fichier filtré par technology
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenDefaultAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR");
            Files.createDirectories(frDir); 

            Path nestedDir = frDir.resolve("BP_23_REF"); 
            Files.createDirectories(nestedDir);
            List<String> areas = List.of("FR");
            
            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);
            
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "BP_23_REF", "2029-2030", 1, "FR", null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("BP_23_REF", result.getFileName()); 
            assertEquals(2, result.getResClusterCapacityEntities().size());  
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenOtherAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of("AT", "AT");
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", null, false
            );

            // THEN
            assertNotNull(result);
            assertEquals("solar_pv_BP23_Aref", result.getFileName());
            assertEquals(2, result.getResClusterCapacityEntities().size());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }

        @Test
        void successfulProcessingWhenOtherAreaWithTechnology(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of("AT", "AT"), "solar_pv", true);
            createMockResExcelFile(tempRoot, "installedRES_solar_thermo_BP23_Aref.xlsx",List.of("FR", "AT"), "solar_thermo", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // WHEN
            TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "solar_pv", false
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
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of("AT", "AT"), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);
            
            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});

            var trajectoryEntity = new TrajectoryEntity();
            trajectoryEntity.setType(TrajectoryType.RES_CAPACITY.name());
            trajectoryEntity.setArea("AT");
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
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "solar_pv", false
            );

            assertThat(trajectory).isNotNull();
            assertThat(trajectory.getVersion()).isEqualTo(2);
        }

        @Test
        void shouldThrowWhenAlreadyProcessedSameContent(@TempDir Path tempRoot) throws Exception {
            // On crée un fichier avec une seule ligne valide
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of("AT", "AT"), "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // stubs for repository/user
            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));
            when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
            when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            
            // Créer une première trajectoire
            // WHEN
            TrajectoryEntity firstResult = resFileProcessorServiceImpl.processInstalledResFile(
                    "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "solar_pv", false
            );

            assertThat(firstResult).isNotNull();
            assertThat(firstResult.getChecksum()).isNotNull();

            // Recréer le même fichier avec les mêmes données
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of("AT", "AT"), "solar_pv", true);

            // Mock pour retourner la première trajectoire
            when(trajectoryRepository
                    .findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                            anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Optional.of(firstResult));

            // Le deuxième appel avec le même contenu devrait lever une exception
            assertThatThrownBy(() ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "solar_pv", false
                    ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("File already processed");
        }

        @Test
        void throwsExceptionForDefaultAreaWhenHorizonIsWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR");
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);
            List<String> areas = List.of("FR");

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP_23_REF", "2026-2027", 1, "FR", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Horizon"));
        }

        @Test
        void throwsExceptionForDefaultAreaWithoutTechnologyAndNoFiles(@TempDir Path tempRoot) throws Exception {
            Path frDir = tempRoot.resolve("FR");
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF"); 
            Files.createDirectories(nestedDir); 

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(nestedDir);
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "invalid_res.xlsx", "2030", 1, "FR", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("No FR res capacity file found in directory"));
        }

        @Test
        void throwsExceptionWhenDefaultAreaAndTechnologyOffshoreFileColumnsAreWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR"); 
            Files.createDirectories(frDir);  

            Path nestedDir = frDir.resolve("BP_23_REF");  
            Files.createDirectories(nestedDir);  

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFileWithWrongColumns(nestedDir, "installedRES_wind_offshore_BP23_Aref.xlsx");

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP_23_REF", "2029-2030", 1, "FR", "Wind Offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionWhenDefaultAreaAndTechnologyOnshoreFileColumnsAreWrong(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR");
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFileWithWrongColumns(nestedDir, "installedRES_wind_onshore_BP23_Aref.xlsx");

            // Mock normalizeAndValidateDirectory pour renvoyer frDir (le code ajoutera .resolve("BP_23_REF") dessus)
            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("FR");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP_23_REF", "2029-2030", 1, "FR", "Wind Onshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Missing columns"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenNoAreaForArea(@TempDir Path tempRoot) throws Exception {
            // GIVEN : Créer la structure de dossiers temporaire
            Path frDir = tempRoot.resolve("FR");
            Files.createDirectories(frDir);

            Path nestedDir = frDir.resolve("BP_23_REF");
            Files.createDirectories(nestedDir);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx",List.of("AT", "AT"), "solar_pv", true);
            createMockResExcelFile(nestedDir, "installedRES_solar_thermo_BP23_Aref.xlsx",List.of("FR", "AT"), "solar_thermo", true);
            List<String> areas = List.of("FR");

            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(nestedDir, "installedRES_offshore_BP23_Aref.xlsx", null, true);
            createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(frDir);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("IT");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "BP_23_REF", "2029-2030", 1, "FR", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("None of the areas of trajectory AREA are present"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenAreaSelectedNotInAREA(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of("AT", "AT");
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("IT");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));
            
            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "IT", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected area {0} is not present in the 'node' column"));
        }

        @Test
        void throwsExceptionForDefaultAreaWhenTechnologySelectedNotInTrajectory(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of("AT", "AT");
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("IT");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "Wind offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Selected technology {0} is not present in the 'node' column of"));
        }

        @Test
        void throwsExceptionWhenDataOnshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of("AT", "AT");
            createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("IT");
            }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster AT/solar_pv/solar_pv are not numeric in Res trajectory installedRES_solar_pv_BP23_Aref.xlsx"));
        }

        @Test
        void throwsExceptionWhenDataOffshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            createMockOffshoreExcelFile(tempRoot, "installedRES_wind_offshore_BP23_Aref.xlsx", "AT", false);

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_wind_offshore_BP23_Aref", "2029-2030", 1, "AT", "Wind offshore", false
                    )
            );
            assertTrue(exception.getMessage().contains("Values for node/group/cluster AT/wind_offshore/wind_offshore are not numeric in Res trajectory installedRES_wind_offshore_BP23_Aref.xlsx"));
        }

        @Test
        void throwsExceptionWhenValuesInRequiredColumnsAreaAreaNull(@TempDir Path tempRoot) throws Exception {
            // Créer les fichiers mocks dans nestedDir
            List<String> areas = List.of("AT", "AT");
            createMockResExcelFileWithNull(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx", areas, "solar_pv");

            when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

            // Autres mocks
            when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
                setName("AT");
            }}));

            // WHEN & THEN
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    resFileProcessorServiceImpl.processInstalledResFile(
                            "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, "AT", "Solar PV", false
                    )
            );
            assertTrue(exception.getMessage().contains("values can't be empty in Res trajectory"));
        }

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
                String areaFile = (area == null) ? "FR" : area;
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
                dataRow.createCell(1).setCellValue("FR");
                dataRow.createCell(2).setCellValue("PECD_Zone_Value");
                dataRow.createCell(3).setCellValue("wind_offshore");
                dataRow.createCell(4).setCellValue("wind_offshore");
                dataRow.createCell(5).setCellValue(200.0);
                wb.write(out);
            }
            return file;
        }
    }
}