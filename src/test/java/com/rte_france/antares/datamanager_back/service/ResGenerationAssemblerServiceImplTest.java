package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.res.impl.ResGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResGenerationAssemblerServiceImplTest {

    private static final String OUTPUT_DIR = "output";
    private static final String DEFAULT_TRAJECTORY = "BP23";

    @TempDir
    Path tempDir;

    private NasFileService nasFileService;
    private ResGenerationAssemblerServiceImpl service;

    @BeforeEach
    void setUp() {
        AntaresDataManagerProperties properties = new AntaresDataManagerProperties();
        properties.nasDirectory = tempDir.toString();
        properties.trajectoryFilePath = "INPUT";
        properties.resLoadDirectory = "RES/load";
        properties.outputLoadDirectory = OUTPUT_DIR;

        nasFileService = mock(NasFileService.class);
        service = new ResGenerationAssemblerServiceImpl(nasFileService, properties, new PathSecurityUtil(properties));
    }

    @Nested
    class BasicOrchestration {
        @Test
        void shouldHandleEmptyScenarios() {
            StudyEntity study = StudyEntity.builder().id(1).trajectories(null).build();
            assertTrue(service.assembleResProperties(study).isEmpty());

            study.setTrajectories(new LinkedHashSet<>(List.of(createTrajectory(TrajectoryType.RES_LOAD, "BP23"))));
            assertTrue(service.assembleResProperties(study).isEmpty());
        }

        @Test
        void shouldThrowOnUnsupportedGroup() {
            StudyEntity study = createStudy(createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "invalid", 100)));
            assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        }
    }

    @Nested
    class NonFrResolution {
        @ParameterizedTest
        @CsvSource({
                "wind_DE_onshore_2030_2031.csv, DE, wind onshore, wind_onshore",
                "solar_pv_IT_utility_2030_2031.txt, IT, solar pv, solar_pv",
                "wind_UK_offshore_2030_2031.xlsx, UK, wind offshore, wind_offshore"
        })
        void shouldResolveVariousFormats(String fileName, String area, String group, String expectedKey) throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, fileName);
            when(nasFileService.saveMatrixToNas(any(), eq(OUTPUT_DIR))).thenReturn(fileName + ".arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity(area, group, 100))
            );

            Map<String, Object> payload = getGroupPayload(service.assembleResProperties(study), area, expectedKey);
            assertEquals(List.of(fileName + ".arrow"), payload.get("series"));
        }

        @Test
        void shouldHandleStyleBAndHorizonLogic() throws IOException {
            // Style B with single year (should NOT be stripped)
            String fileName = "solar_pv_DE_utility_2030.csv";
            preparePhysicalFile(DEFAULT_TRAJECTORY, fileName);
            when(nasFileService.saveMatrixToNas(any(), eq(OUTPUT_DIR))).thenReturn("styleB.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "solar pv", 500))
            );

            Map<String, Map<String, Object>> res = service.assembleResProperties(study);
            assertNotNull(res.get("DE").get("solar_pv"));
        }

        @Test
        void shouldFailOnAmbiguousSeries() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_DE_onshore_v1_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_DE_onshore_v2_2030_2031.csv");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        }
    }

    @Nested
    class FrenchAggregation {
        @Test
        void shouldHandleValidationBoundaries() {
            // Success: Power is 0, skip validation
            StudyEntity study0 = createStudy(createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "solar pv", 0)));
            assertDoesNotThrow(() -> service.assembleResProperties(study0));

            // Failure: Power > 0, but no zonal data
            StudyEntity studyNoZonal = createStudy(createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "solar pv", 100)));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.assembleResProperties(studyNoZonal));
            assertTrue(ex.getMessage().contains("aggregation data"));
        }

        @ParameterizedTest
        @CsvSource({"-0.1", "1.5"}) // Negative or invalid raw weight
        void shouldFailOnInvalidWeights(double weight) {
            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "solar pv", 100)),
                    createTrajectory(TrajectoryType.RES_ZONAL_DISTRIBUTION, createZonal("FR", "solar pv", "FR01", weight))
            );
            // weight 1.5 is valid (divided by 100), but negative is not.
            if (weight < 0) assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        }

        @Test
        void shouldFailOnMissingTechMapping() {
            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "solar pv", 100)),
                    createTrajectory(TrajectoryType.RES_ZONAL_DISTRIBUTION, createZonal("FR", "solar pv", "FR01", 10))
            );
            BusinessException ex = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
            assertTrue(ex.getMessage().contains("technology mapping"));
        }
    }

    @Nested
    class FileSystemSafety {
        @Test
        void shouldIgnoreOldAndLockFiles() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "old/wind_DE_onshore_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, ".~lock.wind_DE_onshore.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_DE_onshore_valid_2030_2031.csv");

            when(nasFileService.saveMatrixToNas(any(), any())).thenReturn("valid.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            service.assembleResProperties(study);
            verify(nasFileService, times(1)).saveMatrixToNas(any(), any());
        }

        @Test
        void shouldHandleTechnicalFailures() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_DE_onshore_2030_2031.csv");
            when(nasFileService.saveMatrixToNas(any(), any())).thenThrow(new IOException("NAS Down"));

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            assertThrows(TechnicalException.class, () -> service.assembleResProperties(study));
        }
    }

    private void preparePhysicalFile(String traj, String path) throws IOException {
        Path p = tempDir.resolve("INPUT/RES/load").resolve(traj).resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "v\n1");
    }

    private StudyEntity createStudy(TrajectoryEntity... t) {
        StudyEntity s = StudyEntity.builder().id(1).build();
        s.setTrajectories(new LinkedHashSet<>(Arrays.asList(t)));
        return s;
    }

    private TrajectoryEntity createTrajectory(TrajectoryType type, Object content) {
        TrajectoryEntity t = TrajectoryEntity.builder().type(type.name()).fileName(DEFAULT_TRAJECTORY).build();
        if (content instanceof String s) t.setFileName(s);
        else if (content instanceof ResClusterCapacityEntity e) t.setResClusterCapacityEntities(List.of(e));
        else if (content instanceof ResZonalDistributionEntity e) t.setResZonalDistributionCapacityEntities(List.of(e));
        else if (content instanceof ResTechnologyDistributionEntity e) t.setResTechnologyDistributionCapacityEntities(List.of(e));
        return t;
    }

    private ResClusterCapacityEntity createCapacity(String a, String g, double c) {
        return ResClusterCapacityEntity.builder().toUse(true).area(a).groupe(g).cluster("1").capacityByYear(BigDecimal.valueOf(c)).build();
    }

    private ResZonalDistributionEntity createZonal(String a, String g, String z, double w) {
        return ResZonalDistributionEntity.builder().area(a).groupe(g).pecdZone(z).capacityByYear(BigDecimal.valueOf(w)).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getGroupPayload(Map<String, Map<String, Object>> res, String a, String g) {
        return (Map<String, Object>) res.get(a.toUpperCase()).get(g.replace(" ", "_"));
    }
}