package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadToJsonServiceTest {

    @Mock
    private NasFileService nasFileService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @InjectMocks
    private LoadToJsonService loadToJsonService;

    @BeforeEach
    void setUp() {
        lenient().when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");
        lenient().when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        lenient().when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("load");
        lenient().when(antaresDataManagerProperties.getLoadMeDirectory()).thenReturn("load_me");
        lenient().when(antaresDataManagerProperties.getOutputLoadDirectory()).thenReturn("output_load");
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectory_shouldReturnGroupedByArea() throws IOException {
        // Given
        LoadEntity loadEntity1 = LoadEntity.builder()
                .id(1)
                .fileName("load_v_me_h2_short_fr_2026-2027.csv")
                .area("v_me_h2_short_fr")
                .build();

        LoadEntity loadEntity2 = LoadEntity.builder()
                .id(2)
                .fileName("load_v_me_gaz_short_fr_2026-2027.csv")
                .area("V_ME_GAZ_SHORT_FR")
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity1, loadEntity2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(eq(Path.of("/nas/trajectories/load_me/load_me_dataset/load_v_me_h2_short_fr_2026-2027.csv")), eq("output_load"), isNull(), eq(false)))
                .thenReturn("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow");
        when(nasFileService.readAndSaveMatrixToNas(eq(Path.of("/nas/trajectories/load_me/load_me_dataset/load_v_me_gaz_short_fr_2026-2027.csv")), eq("output_load"), isNull(), eq(false)))
                .thenReturn("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(2)
                .containsEntry("V_ME_H2_SHORT_FR", List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"))
                .containsEntry("V_ME_GAZ_SHORT_FR", List.of("load_v_me_gaz_short_fr_2026-2027.csv.uuid2.arrow"));
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withLoadMeTrajectory_shouldIgnoreLoadMe() {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_v_me_h2_short_fr_2026-2027.csv")
                .area("v_me_h2_short_fr")
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectoryNullArea_shouldExtractFromFileName() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.csv")
                .area(null)
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("load_fr_2026-2027.csv.uuid1.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsKey("FR");
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectoryNullAreaAndMultiUnderscoreArea_shouldExtractFullAreaFromFileName() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_v_me_h2_short_fr_2026-2027.csv")
                .area(null)
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("V_ME_H2_SHORT_FR", List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"));
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withStandardLoadTrajectory_shouldUseLoadDirectory() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.txt")
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_fr")
                .area("FR")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(eq(Path.of("/nas/trajectories/load/load_fr/load_fr_2026-2027.txt")), eq("output_load"), isNull(), eq(false)))
                .thenReturn("load_fr_2026-2027.txt.uuid.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("FR", List.of("load_fr_2026-2027.txt.uuid.arrow"));
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withOthersAreaLoadTrajectory_shouldFilterByStudy() throws IOException {
        // Given
        StudyEntity study = StudyEntity.builder().id(100).build();

        TrajectoryEntity trajLinked = TrajectoryEntity.builder()
                .scenarioEntities(Set.of(study))
                .build();

        LoadEntity loadLinked = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.txt")
                .trajectoryEntities(Set.of(trajLinked))
                .build();

        LoadEntity loadUnlinked = LoadEntity.builder()
                .id(2)
                .fileName("load_de_2026-2027.txt")
                .trajectoryEntities(Collections.emptySet())
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_others")
                .area("OTHERS")
                .loadEntities(Set.of(loadLinked, loadUnlinked))
                .build();

        study.setTrajectories(Set.of(loadTrajectory));

        when(nasFileService.readAndSaveMatrixToNas(eq(Path.of("/nas/trajectories/load/load_others/load_fr_2026-2027.txt")), eq("output_load"), isNull(), eq(false)))
                .thenReturn("load_fr_2026-2027.txt.uuid.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("FR", List.of("load_fr_2026-2027.txt.uuid.arrow"));
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withEmptyTrajectories_shouldReturnEmptyMap() {
        // Given
        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Collections.emptySet())
                .build();

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withNonLoadTrajectories_shouldReturnEmptyMap() {
        // Given
        TrajectoryEntity thermalTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.THERMAL_CAPACITY.name())
                .fileName("thermal_cap")
                .loadEntities(Set.of(LoadEntity.builder().id(1).fileName("sample.txt").build()))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(thermalTrajectory))
                .build();

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withNullOrEmptyLoadEntities_shouldReturnEmptyMap() {
        // Given
        TrajectoryEntity loadTrajectoryWithNullEntities = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_null")
                .loadEntities(null)
                .build();

        TrajectoryEntity loadMeTrajectoryWithEmptyEntities = TrajectoryEntity.builder()
                .id(11)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_empty")
                .loadEntities(Collections.emptySet())
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadTrajectoryWithNullEntities, loadMeTrajectoryWithEmptyEntities))
                .build();

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectoryBlankArea_shouldExtractFromFileName() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_be_2026-2027.csv")
                .area("   ")
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("load_be_2026-2027.csv.uuid.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsKey("BE");
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectoryBlankAreaAndMultiUnderscoreArea_shouldExtractFullAreaFromFileName() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_v_me_h2_short_fr_2026-2027.csv")
                .area("   ")
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("V_ME_H2_SHORT_FR", List.of("load_v_me_h2_short_fr_2026-2027.csv.uuid1.arrow"));
    }

    @Test
    void getListArrowLoadMeFilesFromStudy_withLoadMeTrajectoryFileNameWithoutPattern_shouldFallbackToOthers() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("unmatchedfilename")
                .area(null)
                .build();

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD_ME.name())
                .fileName("load_me_dataset")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadMeTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("unmatchedfilename.uuid.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadMeFilesFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsKey("OTHERS");
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withOthersAreaOutputFileNameWithoutPattern_shouldFallbackToOthers() throws IOException {
        // Given
        StudyEntity study = StudyEntity.builder().id(100).build();

        TrajectoryEntity trajLinked = TrajectoryEntity.builder()
                .scenarioEntities(Set.of(study))
                .build();

        LoadEntity loadLinked = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.txt")
                .trajectoryEntities(Set.of(trajLinked))
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_others")
                .area("OTHERS")
                .loadEntities(Set.of(loadLinked))
                .build();

        study.setTrajectories(Set.of(loadTrajectory));

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("outputwithoutpattern");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("OTHERS", List.of("outputwithoutpattern"));
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_whenNasFileServiceThrowsIOException_shouldThrowTechnicalException() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.txt")
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_fr")
                .area("FR")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(loadTrajectory))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenThrow(new IOException("Disk read error"));

        // When & Then
        assertThatThrownBy(() -> loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study))
                .isInstanceOf(TechnicalException.class)
                .hasMessage("Disk read error")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withSameLoadEntityIdMultipleTimes_shouldUseCache() throws IOException {
        // Given
        LoadEntity loadEntity = LoadEntity.builder()
                .id(42)
                .fileName("load_fr_2026-2027.txt")
                .build();

        TrajectoryEntity trajectory1 = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_fr_1")
                .area("FR")
                .loadEntities(Set.of(loadEntity))
                .build();

        TrajectoryEntity trajectory2 = TrajectoryEntity.builder()
                .id(11)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_fr_2")
                .area("FR")
                .loadEntities(Set.of(loadEntity))
                .build();

        StudyEntity study = StudyEntity.builder()
                .id(1)
                .trajectories(Set.of(trajectory1, trajectory2))
                .build();

        when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean()))
                .thenReturn("load_fr_cached.arrow");

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).hasSize(1)
                .containsEntry("FR", List.of("load_fr_cached.arrow", "load_fr_cached.arrow"));
        verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), any(), any(), anyBoolean());
    }

    @Test
    void getListArrowLoadFilesByAreaFromStudy_withOthersAreaTrajectoryEntitiesWithNullOrEmptyScenarios_shouldFilterOut() {
        // Given
        StudyEntity study = StudyEntity.builder().id(100).build();

        TrajectoryEntity trajWithNullScenarios = TrajectoryEntity.builder()
                .scenarioEntities(Collections.emptySet())
                .build();

        LoadEntity loadEntity = LoadEntity.builder()
                .id(1)
                .fileName("load_fr_2026-2027.txt")
                .trajectoryEntities(Set.of(trajWithNullScenarios))
                .build();

        TrajectoryEntity loadTrajectory = TrajectoryEntity.builder()
                .id(10)
                .type(TrajectoryType.LOAD.name())
                .fileName("load_others")
                .area("OTHERS")
                .loadEntities(Set.of(loadEntity))
                .build();

        study.setTrajectories(Set.of(loadTrajectory));

        // When
        Map<String, List<String>> result = loadToJsonService.getListArrowLoadFilesByAreaFromStudy(study);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(nasFileService);
    }
}
