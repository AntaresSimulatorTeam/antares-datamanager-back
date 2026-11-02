package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
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
import java.util.HashMap;
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
        Path stsRoot = tempDir.resolve("trajectory").resolve("sts");
        Files.createDirectories(stsRoot);
        Path constraintsDir = stsRoot.resolve("EV").resolve("constraints");
        Files.createDirectories(constraintsDir);
        Files.createFile(constraintsDir.resolve("Additional-constraints.xlsx"));
        Files.createFile(constraintsDir.resolve("trajectory.xlsx"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectory");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("sts");
    }

    @Test
    void shouldNotOverwriteSpecificAreaWhenSubmittingOthers() throws IOException {

        // GIVEN
        Integer studyId = 1;

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
        storage.setTrajectory(com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity.builder().id(1).build());

        // New parameters from the "OTHERS" file: only DE (FR params excluded for OTHERS concept)
        StConstraintsHoursEntity deHour = StConstraintsHoursEntity.builder().occurrence(1).startHour(1).endHour(2).build();
        StConstraintsParameterEntity deParam = StConstraintsParameterEntity.builder()
                    .name("param_de")
                    .zone("DE")
                    .cluster("cluster1")
                    .enabled(true)
                    .hours(new ArrayList<>(List.of(deHour)))
                    .build();
        deHour.setParameter(deParam);

        when(trajectoryRepository.findAreasByStudyIdAndType(studyId, "STS"))
                    .thenReturn(List.of("FR"));

        // Pre-populate cache with OTHERS containing only DE param
        HashMap<String, List<StConstraintsParameterEntity>> cache = new HashMap<>();
        cache.put("OTHERS", List.of(deParam));

            // WHEN
        service.handleStsConstraints(Path.of("dummy"), "FR", "EV", "cluster1",
                    "trajectory.xlsx", "OTHERS", List.of("FR", "DE"),
                    storage, studyId, cache);

            // THEN
            // When filtering DE params by zone="FR", no matches found
            // Existing FR parameters remain unchanged
        assertThat(storage.getParameters()).hasSize(1);
        assertThat(storage.getParameters().get(0)).isEqualTo(existingFrParam);
        }
}
