package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.service.sts.StStorageConstraintsFileProcessorService;
import com.rte_france.antares.datamanager_back.service.sts.impl.StStorageFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class StStorageConstraintsMergeTest {

    @InjectMocks
    private StStorageFileProcessorServiceImpl service;

    @Mock
    private StStorageConstraintsFileProcessorService stStorageConstraintsFileProcessorService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        Path stsRoot = tempDir.resolve("traj").resolve("sts");
        Files.createDirectories(stsRoot);
        Path constraintsDir = stsRoot.resolve("EV").resolve("constraints");
        Files.createDirectories(constraintsDir);
        Files.createFile(constraintsDir.resolve("Additional-constraints.xlsx"));
        Files.createFile(constraintsDir.resolve("traj.xlsx"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("traj");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("sts");
    }

    @Test
    void shouldNotOverwriteSpecificAreaWhenSubmittingOthers() throws IOException {

        // GIVEN
        Integer studyId = 1;
        String horizon = "2025";


        // Storage already has a preferred FR parameter
        StStorageEntity storage = new StStorageEntity();
        storage.setArea("FR");
        storage.setName("cluster1");
        StConstraintsParameterEntity existingFrParam = StConstraintsParameterEntity.builder()
                    .name("param1")
                    .zone("FR")
                    .cluster("cluster1")
                    .enabled(true)
                    .build();
        storage.setParameters(new ArrayList<>(List.of(existingFrParam)));

        // New parameters from "OTHERS" file: FR + DE
        List<StConstraintsParameterEntity> newParamsFromFile = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            newParamsFromFile.add(StConstraintsParameterEntity.builder()
                        .name("param" + i)
                        .zone("FR")
                        .cluster("cluster1")
                        .enabled(false)
                        .build());
        }
        StConstraintsParameterEntity deParam = StConstraintsParameterEntity.builder()
                    .name("param_de")
                    .zone("DE")
                    .cluster("cluster1")
                    .enabled(true)
                    .build();
        newParamsFromFile.add(deParam);

        when(stStorageConstraintsFileProcessorService.processConstraintsParametersAnHoursFile(any(), eq("OTHERS"), any()))
                    .thenReturn(newParamsFromFile);


        when(trajectoryRepository.findAreasByStudyIdAndType(studyId, "STS"))
                    .thenReturn(List.of("FR"));

            // WHEN
        service.handleStsConstraints(Path.of("dummy"), "FR", "EV", "cluster1",
                    "traj.xlsx", "OTHERS", List.of("FR", "DE"),
                    storage, horizon, studyId);

            // THEN
            // Only existing FR param + new DE param should be present
        assertThat(storage.getParameters()).hasSize(2);

            // Existing FR parameter is preserved
        assertThat(storage.getParameters().stream()
                    .anyMatch(p -> p.getZone().equals("FR") && p.getName().equals("param1") && p.getEnabled()))
                    .isTrue();

            // DE parameter from the "OTHERS" file is included
        assertThat(storage.getParameters().stream()
                    .anyMatch(p -> p.getZone().equals("DE") && p.getName().equals("param_de") && p.getEnabled()))
                    .isTrue();
        }
}
