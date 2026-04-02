package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.impl.StsPropertiesAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;


import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StsPropertiesAssemblerServiceImplTest {

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;
    @Mock
    private NasFileService nasFileService;
    @TempDir
    Path tempDir;

    @InjectMocks
    private StsPropertiesAssemblerServiceImpl stsPropertiesAssemblerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        antaresDataManagerProperties = Mockito.mock(AntaresDataManagerProperties.class);
        nasFileService = Mockito.mock(NasFileService.class);
        ReflectionTestUtils.setField(stsPropertiesAssemblerService, "antaresDataManagerProperties", antaresDataManagerProperties);
        ReflectionTestUtils.setField(stsPropertiesAssemblerService, "nasFileService", nasFileService);
    }

    @Test
    void assembleStsProperties_ShouldReturnMappedProperties() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("FR")
                .name("Storage1")
                .groupe("Group1")
                .injection(new BigDecimal("10.5"))
                .withdrawal(new BigDecimal("5.2"))
                .storage(new BigDecimal("100.0"))
                .efficiencyInjection(new BigDecimal("0.9"))
                .efficiencyWithdrawal(new java.math.BigDecimal("0.2")) // StStorageEntity has Integer for efficiencyWithdrawal
                .initialLevel(new BigDecimal("0.5"))
                .initialLevelOptim(true)
                .enabled(true)
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_Storage1"));
        StsGenerationDTO dto = result.get("FR_Storage1");
        assertEquals(true, dto.getEnabled());
        assertEquals("Group1", dto.getGroupe());
        assertEquals(10, dto.getInjection());
        assertEquals(5.2, dto.getWithdrawal());
        assertEquals(100.0, dto.getStorage());
        assertEquals(0.9, dto.getEfficiencyInjection());
        assertEquals(0.2, dto.getEfficiencyWithdrawal());
        assertEquals(0.5, dto.getInitialLevel());
        assertEquals(true, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldHandleMultipleTrajectoriesAndAreas() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("fr")
                .name("S1")
                .enabled(true)
                .injection(BigDecimal.ONE)
                .tsPath("")
                .build();
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StStorageEntity stStorage2 = StStorageEntity.builder()
                .area("be")
                .name("S2")
                .enabled(false)
                .withdrawal(BigDecimal.ONE)
                .tsPath("")
                .build();
        TrajectoryEntity traj2 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1, traj2))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR_S1"));
        assertTrue(result.containsKey("BE_S2"));
        assertEquals(true, result.get("FR_S1").getEnabled());
        assertEquals(false, result.get("BE_S2").getEnabled());
    }

    @Test
    void assembleStsProperties_ShouldSkipNonStsTrajectories() {
        // Given
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .stStorageEntities(List.of(StStorageEntity.builder().area("FR").name("S1").build()))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleStsProperties_ShouldHandleNullFields() {
        // Given
        StStorageEntity stStorage = StStorageEntity.builder()
                .area("FR")
                .name("S1")
                .storage(BigDecimal.TEN)
                .tsPath(null)
                .build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage))
                .build();
        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        StsGenerationDTO dto = result.get("FR_S1");
        assertEquals(false, dto.getEnabled());
        assertEquals(0, dto.getInjection());
        assertEquals(0.0, dto.getWithdrawal());
        assertEquals(false, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldExcludeClustersWithZeroTotalCapacity() {
        // Given
        StStorageEntity stStorageZero = StStorageEntity.builder()
                .area("FR")
                .name("ZeroCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(BigDecimal.ZERO)
                .storage(BigDecimal.ZERO)
                .tsPath("")
                .build();

        StStorageEntity stStoragePartial = StStorageEntity.builder()
                .area("FR")
                .name("PartialCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(new BigDecimal("1.0"))
                .storage(BigDecimal.ZERO)
                .tsPath("")
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorageZero, stStoragePartial))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_PartialCapacity"));
        assertFalse(result.containsKey("FR_ZeroCapacity"));
    }

    @Test
    void shouldReturnEmptyListWhenEntityIsNull() {
        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(null, "2030");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenTsPathIsBlank() {
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath("   ");

        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSaveAllFilesWhenAllRequiredFilesExist() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());
        String horizon = "2030";

        when(antaresDataManagerProperties.getStsTsOutputDirectory())
                .thenReturn("/output");

        // create all required files
        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
            when(nasFileService.saveMatrixToNas(file.resolve(tempDir), "/output", horizon))
                    .thenReturn(file.fileName());
        }

        // when
        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, horizon);

        // then
        assertTrue((result.size() == 5));


    }

    @Test
    void shouldThrowBusinessExceptionWhenARequiredFileIsMissing() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());

        when(antaresDataManagerProperties.getStsTsOutputDirectory())
                .thenReturn("/output");

        // create all files except one
        StsTsFile missing = StsTsFile.INFLOWS;

        for (StsTsFile file : StsTsFile.values()) {
            if (file != missing) {
                Files.createFile(file.resolve(tempDir));
            }
        }

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030")
        );

        assertTrue(ex.getMessage().contains("Required STS series file not found"));
        assertTrue(ex.getErrorMessageArguments().contains(missing.resolve(tempDir).toString()));

        verifyNoInteractions(nasFileService);
    }

    @Test
    void shouldThrowTechnicalExceptionOnIOException() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());

        when(antaresDataManagerProperties.getStsTsOutputDirectory())
                .thenReturn("/output");

        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
        }

        when(nasFileService.saveMatrixToNas(any(Path.class), any(), any()))
                .thenThrow(new IOException("NAS error"));

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030")
        );

        assertTrue(ex.getMessage().contains("NAS error"));

    }

    @Test
    void shouldPropagateBusinessExceptionWhenSheetNotFound() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());
        String horizon = "2030";

        when(antaresDataManagerProperties.getStsTsOutputDirectory())
                .thenReturn("/output");

        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
        }

        BusinessException originalEx = BusinessException.builder()
                .message("Horizon {0} does not exist in file: {1}")
                .errorMessageArguments(List.of(horizon, "inflows.xlsx"))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();

        when(nasFileService.saveMatrixToNas(any(Path.class), any(), eq(horizon)))
                .thenThrow(originalEx);

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, horizon)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("Horizon {0} does not exist in file: {1}", ex.getMessage());
        assertEquals(2, ex.getErrorMessageArguments().size());
        assertEquals(horizon, ex.getErrorMessageArguments().get(0));
    }


}
