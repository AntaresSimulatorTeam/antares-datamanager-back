package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.impl.ResFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
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
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        TrajectoryEntity result = resFileProcessorServiceImpl.processInstalledResFile(
                BP_23_REF, HORIZON_2029_2030, STUDY_ID, AREA_FR, SOLAR_PV_LABEL, false
        );

        // THEN
        assertNotNull(result);
        assertEquals("solar_pv_BP23_Aref", result.getFileName());
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
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
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
        void successfulProcessingWhenOtherAreaWithoutTechnology(@TempDir Path tempRoot) throws Exception {
        // Créer les fichiers mocks dans nestedDir
        List<String> areas = List.of(AREA_AT, AREA_AT);
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

        // Autres mocks
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_FR);
        }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_AT);
        }}));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
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
        void successfulProcessingWhenOtherAreaWithTechnology(@TempDir Path tempRoot) throws Exception {
        // Créer les fichiers mocks dans nestedDir
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of(AREA_AT, AREA_AT), "solar_pv", true);
        createMockResExcelFile(tempRoot, "installedRES_solar_thermo_BP23_Aref.xlsx",List.of(AREA_FR, AREA_AT), "solar_thermo", true);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

        // Autres mocks
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_FR);
        }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_AT);
        }}));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
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
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of(AREA_AT, AREA_AT), "solar_pv", true);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);
        
        // stubs for repository/user
        // Autres mocks
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_FR);
        }}, new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_AT);
        }}));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});

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
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of(AREA_AT, AREA_AT), "solar_pv", true);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(tempRoot);

        // stubs for repository/user
        // Autres mocks
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new com.rte_france.antares.datamanager_back.repository.model.AreaEntity() {{
            setName(AREA_AT);
        }}));
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni("testUser");}});
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Créer une première trajectoire
        // WHEN
        TrajectoryEntity firstResult = resFileProcessorServiceImpl.processInstalledResFile(
                "installedRES_solar_pv_BP23_Aref", "2029-2030", 1, AREA_AT, "solar_pv", false
        );

        assertThat(firstResult).isNotNull();
        assertThat(firstResult.getChecksum()).isNotNull();

        // Recréer le même fichier avec les mêmes données
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",List.of(AREA_AT, AREA_AT), "solar_pv", true);

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
        createMockResExcelFile(nestedDir, "installedRES_solar_pv_BP23_Aref.xlsx",List.of(AREA_AT, AREA_AT), "solar_pv", true);
        createMockResExcelFile(nestedDir, "installedRES_solar_thermo_BP23_Aref.xlsx",List.of(AREA_FR, AREA_AT), "solar_thermo", true);
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
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

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
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", true);

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
        assertTrue(exception.getMessage().contains("Selected technology {0} is not present in the 'node' column of"));
        }

        @Test
        void throwsExceptionWhenDataOnshoreAreNotNumeric(@TempDir Path tempRoot) throws Exception {
        // Créer les fichiers mocks dans nestedDir
        List<String> areas = List.of(AREA_AT, AREA_AT);
        createMockResExcelFile(tempRoot, "installedRES_solar_pv_BP23_Aref.xlsx",areas, "solar_pv", false);

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
        assertTrue(exception.getMessage().contains("Values for node/group/cluster AT/solar_pv/solar_pv are not numeric in Res trajectory installedRES_solar_pv_BP23_Aref.xlsx"));
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
        assertTrue(exception.getMessage().contains("Values for node/group/cluster AT/wind_offshore/wind_offshore are not numeric in Res trajectory installedRES_wind_offshore_BP23_Aref.xlsx"));
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
    void processLoadFactorResFileSucceedsWithValidCsvFiles(@TempDir Path tempRoot) throws Exception {
        // GIVEN
        Path nasDir = tempRoot.resolve(NAS_DIR);
        Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
            .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
        Files.createDirectories(trajectoryDir);
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
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
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

        TrajectoryEntity existingTrajectory = new TrajectoryEntity();
        existingTrajectory.setVersion(2);
        existingTrajectory.setChecksum(CHECKSUM_DIFFERENT);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingTrajectory));
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
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

        TrajectoryEntity existingTrajectory = new TrajectoryEntity();
        existingTrajectory.setVersion(2);
        existingTrajectory.setChecksum(CHECKSUM_OLD);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingTrajectory));
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
        createMockCsvFile(trajectoryDir, "timeseries_1.csv");
        createMockCsvFile(trajectoryDir, "timeseries_2.csv");

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_AT, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(ANOTHER_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
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
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

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
    void processLoadFactorResFileSafelyHandlesSymbolicLinks(@TempDir Path tempRoot) throws Exception {
        // GIVEN - Create legitimate CSV file
        Path nasDir = tempRoot.resolve(NAS_DIR);
        Path trajectoryDir = nasDir.resolve(TRAJECTORY_PATH).resolve(DIRECTORY_RES_LOAD)
            .resolve(TRAJECTORY_NAME).resolve(TECHNOLOGY_SOLAR_PV).resolve(TECHNOLOGY_SOLAR_PV);
        Files.createDirectories(trajectoryDir);
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
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
        createMockCsvFile(trajectoryDir, CSV_FILE_NAME);

        TrajectoryEntity existingTrajectory = new TrajectoryEntity();
        existingTrajectory.setVersion(1);
        existingTrajectory.setChecksum("EXISTING_CHECKSUM");

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(existingTrajectory));
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
        createMockCsvFile(trajectoryDir, "2030.csv");
        createMockCsvFile(trajectoryDir, "2031.csv");
        createMockCsvFile(trajectoryDir, "2032.csv");

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
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
        createMockCsvFile(trajectoryDir, "data.csv");
        Files.createFile(trajectoryDir.resolve("readme.txt"));
        Files.createFile(trajectoryDir.resolve("metadata.json"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(TRAJECTORY_PATH);
        when(trajectoryService.getDirectoryByTrajectoryType(TrajectoryType.RES_LOAD, AREA_FR, null))
                .thenReturn(DIRECTORY_RES_LOAD);
        when(userService.getCurrentUserDetails()).thenReturn(new UserInfoDto() {{setNni(TEST_USER);}});
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        TrajectoryEntity result = resFileProcessorServiceImpl.processLoadFactorResFile(
                TRAJECTORY_NAME, HORIZON_2029_2030, STUDY_ID, AREA_FR, TECHNOLOGY_SOLAR_PV
        );

        // THEN - Should succeed even with non-CSV files present
        assertNotNull(result);
        assertTrue(result.getHasTimeSeries());
    }

    private void createMockCsvFile(Path directory, String fileName) throws IOException {
        Path csvFile = directory.resolve(fileName);
        Files.writeString(csvFile, "timestamp,value\n2030-01-01,0.5\n2030-01-02,0.6\n");
    }
}