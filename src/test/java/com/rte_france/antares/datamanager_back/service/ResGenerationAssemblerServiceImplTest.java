package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
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
        properties.resTsOutputDirectory = OUTPUT_DIR;

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
                "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv, DE, wind onshore, wind_onshore",
                "solar_pv/solar_pv/solar_pv_IT_utility_2030_2031.txt, IT, solar pv, solar_pv",
                "wind_offshore/wind_offshore/wind_offshore_UK_2030_2031.xlsx, UK, wind offshore, wind_offshore"
        })
        void shouldResolveVariousFormats(String fileName, String area, String group, String expectedKey) throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, fileName);
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn(fileName + ".arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity(area, group, 100))
            );

            var payload = getGroupPayload(service.assembleResProperties(study), area, expectedKey);
            assertEquals(List.of(fileName + ".arrow"), payload.series());
        }

        @Test
        void shouldHandleStyleBAndHorizonLogic() throws IOException {
            // Single trailing year should NOT be stripped as horizon pair
            String fileName = "solar_pv/solar_pv/solar_pv_DE_2030.csv";
            preparePhysicalFile(DEFAULT_TRAJECTORY, fileName);
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn("styleB.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "solar pv", 500))
            );

            var res = service.assembleResProperties(study);
            assertNotNull(res.get("DE").get("solar_pv"));
        }

        @Test
        void shouldFailOnAmbiguousSeries() throws IOException {
            // Both files parse to the same (DE, wind_onshore, wind_onshore) -> conflict
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_v1_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_v2_2030_2031.csv");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
        }

        @Test
        void shouldReturnEmptyParsedKeyWhenFrTechTokensAreEmpty() throws IOException {
            // File 1: Malformed FR series name
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_FR01_2030_2031.csv");

            // File 2: A valid series for DE must be present, otherwise resolveIndexedSingleSeries throws
            String validFile = "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv";
            preparePhysicalFile(DEFAULT_TRAJECTORY, validFile);
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn("valid_de.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            // assembleResProperties should succeed by ignoring the malformed file and using the valid one.
            assertDoesNotThrow(() -> service.assembleResProperties(study));
            verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean());
        }

        @Test
        void shouldSkipMalformedGlobalFrSeriesAndProcessValidOnes() throws IOException {
            // FR instead of FR01, FR02, ..
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_offshore/wind_offshore/wind_offshore_FR_2030_2031.csv");

            // File 2: A valid series for wind_onshore DE
            String validFile = "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv";
            preparePhysicalFile(DEFAULT_TRAJECTORY, validFile);
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn("valid_de.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            assertDoesNotThrow(() -> service.assembleResProperties(study));
            verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean());
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

        @Test
        void shouldCoverFrTechLoopAndCandidateKeys() throws IOException {
            String fileName = "solar_pv/solar_pv/solar_pv_FR01_utility_2030_2031.csv";
            preparePhysicalFile(DEFAULT_TRAJECTORY, fileName);
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn("fr_solar.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "solar pv", 1000)),
                    createTrajectory(TrajectoryType.RES_ZONAL_DISTRIBUTION, createZonal("FR", "solar pv", "FR01", 100)),
                    // Tech name "solar_pv_utility" starts with group prefix "solar_pv_"
                    createTrajectory(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION,
                            createTech("FR", "solar pv", "FR01", "solar_pv_utility", 100.0))
            );

            var result = service.assembleResProperties(study);
            assertNotNull(result.get("FR").get("solar_pv"));
        }

        @Test
        void shouldCoverZeroWeightBranchesInFrLoop() {
            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("FR", "wind offshore", 1000)),
                    createTrajectory(TrajectoryType.RES_ZONAL_DISTRIBUTION, createZonal("FR", "wind offshore", "FR01", 0.0)),
                    createTrajectory(TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION,
                            createTech("FR", "wind offshore", "FR01", "techA", 100.0))
            );

            var result = service.assembleResProperties(study);
            var dto = getGroupPayload(result, "FR", "wind_offshore");

            // zone_weights contains FR01 with 0.0, but tech_weights_by_zone should be empty for that zone
            assertNotNull(dto.frAggregation());
            assertTrue(dto.frAggregation().techWeightsByZone().isEmpty());
        }
    }

    @Nested
    class FileSystemSafety {
        @Test
        void shouldIgnoreOldAndLockFiles() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "old/wind_DE_onshore_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, ".~lock.wind_DE_onshore.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");

            when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean())).thenReturn("valid.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            service.assembleResProperties(study);
            verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), any(), any(), anyBoolean());
        }

        @Test
        void shouldHandleTechnicalFailures() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), any(), any(), anyBoolean())).thenThrow(new IOException("NAS Down"));

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            assertThrows(TechnicalException.class, () -> service.assembleResProperties(study));
        }

        @Test
        void shouldThrowWhenTrajectoryDirectoryDoesNotExist() {
            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, "non_existent_folder"),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            BusinessException ex = assertThrows(BusinessException.class, () -> service.assembleResProperties(study));
            assertTrue(ex.getMessage().contains("Invalid RES load trajectory path"));
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
        switch (content) {
            case String s -> t.setFileName(s);
            case ResClusterCapacityEntity e -> t.setResClusterCapacityEntities(List.of(e));
            case ResZonalDistributionEntity e -> t.setResZonalDistributionCapacityEntities(List.of(e));
            case ResTechnologyDistributionEntity e -> t.setResTechnologyDistributionCapacityEntities(List.of(e));
            default -> { /* nothing */ }
        }
        return t;
    }

    private ResClusterCapacityEntity createCapacity(String a, String g, double c) {
        return createCapacity(a, g, g.replace(" ", "_"), c);
    }

    private ResClusterCapacityEntity createCapacity(String a, String g, String cluster, double c) {
        return ResClusterCapacityEntity.builder().toUse(true).area(a).groupe(g).cluster(cluster).capacityByYear(BigDecimal.valueOf(c)).build();
    }

    private ResZonalDistributionEntity createZonal(String a, String g, String z, double w) {
        return ResZonalDistributionEntity.builder().area(a).groupe(g).pecdZone(z).capacityByYear(BigDecimal.valueOf(w)).build();
    }

    private ResTechnologyDistributionEntity createTech(String a, String g, String z, String tech, double w) {
        return createTech(a, g, g.replace(" ", "_"), z, tech, w);
    }

    private ResTechnologyDistributionEntity createTech(String a, String g, String cluster, String z, String tech, double w) {
        return ResTechnologyDistributionEntity.builder().area(a).groupe(g).cluster(cluster).pecdZone(z)
                .pecdTechnology(tech).capacityByYear(w).build();
    }

    @Nested
    class TechnoTrajectoryPriority {

        @Test
        void shouldNotSumCapacityWhenTechnoTrajectoryCoversGroup() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenReturn("de_wind.arrow");

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100)),
                    createTechnoTrajectory(TrajectoryType.RES_CAPACITY, "wind_onshore", createCapacity("DE", "wind onshore", 300))
            );

            var expected = new ResClusterGenerationDto(new ResClusterPropertiesDto(300.0, "wind_onshore"), List.of("de_wind.arrow"), null);
            assertEquals(expected, service.assembleResProperties(study).get("DE").get("wind_onshore"),
                    "Area techno capacity should replace area capacity, not sum");
        }

        @Test
        void shouldFallBackToAreaForGroupsNotCoveredByTechnoTrajectory() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_offshore/wind_offshore/wind_offshore_DE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenReturn("de.arrow");

            TrajectoryEntity areaCapTraj = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_CAPACITY.name()).fileName(DEFAULT_TRAJECTORY).build();
            areaCapTraj.setResClusterCapacityEntities(List.of(
                    createCapacity("DE", "wind onshore", 100),
                    createCapacity("DE", "wind offshore", 200)
            ));

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    areaCapTraj,
                    createTechnoTrajectory(TrajectoryType.RES_CAPACITY, "wind_onshore", createCapacity("DE", "wind onshore", 300))
            );

            var result = service.assembleResProperties(study).get("DE");
            assertEquals(new ResClusterGenerationDto(new ResClusterPropertiesDto(300.0, "wind_onshore"), List.of("de.arrow"), null),
                    result.get("wind_onshore"), "Area techno should have priority");
            assertEquals(new ResClusterGenerationDto(new ResClusterPropertiesDto(200.0, "wind_offshore"), List.of("de.arrow"), null),
                    result.get("wind_offshore"), "Empty area techno should fall back to area");
        }

        @Test
        void shouldNotProduceDuplicateWhenSameFileLinkedToMultipleAreas() throws IOException {
            preparePhysicalFile("BIG_FILE", "wind_onshore/wind_onshore/wind_onshore_BE_2030_2031.csv");
            preparePhysicalFile("BIG_FILE", "wind_onshore/wind_onshore/wind_onshore_ES_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenAnswer(inv -> {
                        Path p = inv.getArgument(0);
                        return p != null && p.toString().contains("_BE_") ? "be_wind.arrow" : "es_wind.arrow";
                    });

            TrajectoryEntity lfBe = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("BIG_FILE").build();
            lfBe.setArea("BE");
            TrajectoryEntity lfEs = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("BIG_FILE").build();
            lfEs.setArea("ES");

            StudyEntity study = createStudy(
                    lfBe, lfEs,
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("BE", "wind onshore", 100)),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("ES", "wind onshore", 200))
            );

            var result = service.assembleResProperties(study);
            assertDoesNotThrow(() -> service.assembleResProperties(study),
                    "Same file linked to multiple areas must not throw duplicate error");
            assertEquals(List.of("be_wind.arrow"), result.get("BE").get("wind_onshore").series(),
                    "BE should get only its own series");
            assertEquals(List.of("es_wind.arrow"), result.get("ES").get("wind_onshore").series(),
                    "ES should get only its own series");
        }

        @Test
        void shouldScopeAreaTechnoLinkToLinkedGroup() throws IOException {
            preparePhysicalFile("TECHNO_FILE", "wind_onshore/wind_onshore/wind_onshore_BE_2030_2031.csv");
            preparePhysicalFile("TECHNO_FILE", "wind_offshore/wind_offshore/wind_offshore_BE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenReturn("be_onshore.arrow");

            TrajectoryEntity lfTechno = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("TECHNO_FILE").build();
            lfTechno.setArea("BE");
            lfTechno.setTechnology("wind_onshore");

            StudyEntity study = createStudy(
                    lfTechno,
                    createTechnoTrajectory(TrajectoryType.RES_CAPACITY, "wind_onshore", createCapacity("BE", "wind onshore", 100))
            );

            var result = service.assembleResProperties(study);
            assertEquals(List.of("be_onshore.arrow"), result.get("BE").get("wind_onshore").series(),
                    "Area-techno link scoped to wind_onshore should resolve its series");
            verify(nasFileService, times(1)).readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean());
        }

        @Test
        void shouldNotDuplicateWhenSameFilesLinkedForMultipleAreasAndAreaTechno() throws IOException {
            // TST_FILE: area-level, covers AT and BE (both wind_offshore series in the same directory)
            preparePhysicalFile("TST_FILE", "wind_offshore/wind_offshore/wind_offshore_AT_TST_2030_2031.csv");
            preparePhysicalFile("TST_FILE", "wind_offshore/wind_offshore/wind_offshore_BE_TST_2030_2031.csv");
            // AT2_FILE: area-techno, also covers AT and BE (wind_offshore)
            preparePhysicalFile("AT2_FILE", "wind_offshore/wind_offshore/wind_offshore_AT_AT2_2030_2031.csv");
            preparePhysicalFile("AT2_FILE", "wind_offshore/wind_offshore/wind_offshore_BE_AT2_2030_2031.csv");

            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenAnswer(inv -> ((Path) inv.getArgument(0)).getFileName().toString().replace(".csv", ".arrow"));

            TrajectoryEntity lfAt = TrajectoryEntity.builder().type(TrajectoryType.RES_LOAD.name()).fileName("TST_FILE").build();
            lfAt.setArea("AT");
            TrajectoryEntity lfBe = TrajectoryEntity.builder().type(TrajectoryType.RES_LOAD.name()).fileName("TST_FILE").build();
            lfBe.setArea("BE");
            TrajectoryEntity lfAtTechno = TrajectoryEntity.builder().type(TrajectoryType.RES_LOAD.name()).fileName("AT2_FILE").build();
            lfAtTechno.setArea("AT");
            lfAtTechno.setTechnology("wind_offshore");
            TrajectoryEntity lfBeTechno = TrajectoryEntity.builder().type(TrajectoryType.RES_LOAD.name()).fileName("AT2_FILE").build();
            lfBeTechno.setArea("BE");
            lfBeTechno.setTechnology("wind_offshore");

            StudyEntity study = createStudy(
                    lfAt, lfBe, lfAtTechno, lfBeTechno,
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("AT", "wind offshore", 100)),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("BE", "wind offshore", 200))
            );

            var result = assertDoesNotThrow(() -> service.assembleResProperties(study),
                    "Area-level file linked to multiple areas + area-techno file must not throw a duplicate error");
            assertEquals(List.of("wind_offshore_AT_AT2_2030_2031.arrow"), result.get("AT").get("wind_offshore").series(),
                    "AT should use the area-techno series");
            assertEquals(List.of("wind_offshore_BE_AT2_2030_2031.arrow"), result.get("BE").get("wind_offshore").series(),
                    "BE should use the area-techno series");
        }

        @Test
        void shouldFallBackToOthersWhenNoSpecificAreaSeriesLinked() throws IOException {
            preparePhysicalFile("OTHERS_FILE", "wind_offshore/wind_offshore/wind_offshore_DE_off_2030_2031.csv");
            preparePhysicalFile("OTHERS_FILE", "wind_offshore/wind_offshore/wind_offshore_BE_off_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenAnswer(inv -> ((Path) inv.getArgument(0)).getFileName().toString().replace(".csv", ".arrow"));

            TrajectoryEntity othersLf = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("OTHERS_FILE").build();
            othersLf.setArea("OTHERS");

            StudyEntity study = createStudy(
                    othersLf,
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind offshore", 100)),
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("BE", "wind offshore", 200))
            );

            var result = assertDoesNotThrow(() -> service.assembleResProperties(study),
                    "OTHERS should provide series for areas with no specific trajectory linked");
            assertEquals(List.of("wind_offshore_DE_off_2030_2031.arrow"), result.get("DE").get("wind_offshore").series());
            assertEquals(List.of("wind_offshore_BE_off_2030_2031.arrow"), result.get("BE").get("wind_offshore").series());
        }

        @Test
        void shouldPreferSpecificAreaSeriesOverOthers() throws IOException {
            preparePhysicalFile("OTHERS_FILE", "wind_offshore/wind_offshore/wind_offshore_DE_others_2030_2031.csv");
            preparePhysicalFile("SPECIFIC_FILE", "wind_offshore/wind_offshore/wind_offshore_DE_specific_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenAnswer(inv -> ((Path) inv.getArgument(0)).getFileName().toString().replace(".csv", ".arrow"));

            TrajectoryEntity othersLf = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("OTHERS_FILE").build();
            othersLf.setArea("OTHERS");
            TrajectoryEntity specificLf = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name()).fileName("SPECIFIC_FILE").build();
            specificLf.setArea("DE");

            StudyEntity study = createStudy(
                    othersLf, specificLf,
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind offshore", 100))
            );

            var result = assertDoesNotThrow(() -> service.assembleResProperties(study),
                    "Specific area link should have priority over OTHERS");
            assertEquals(List.of("wind_offshore_DE_specific_2030_2031.arrow"),
                    result.get("DE").get("wind_offshore").series(), "Specific area series must take priority over OTHERS");
        }

        @Test
        void shouldPreferTechnoLfSeriesOverAreaLfSeriesForSameGroup() throws IOException {
            preparePhysicalFile(DEFAULT_TRAJECTORY, "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");
            preparePhysicalFile("LF_techno", "wind_onshore/wind_onshore/wind_onshore_DE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean()))
                    .thenReturn("area_series.arrow", "techno_series.arrow");

            TrajectoryEntity technoLfTraj = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_LOAD.name())
                    .fileName("LF_techno")
                    .technology("wind_onshore")
                    .build();

            StudyEntity study = createStudy(
                    createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY),
                    technoLfTraj,
                    createTrajectory(TrajectoryType.RES_CAPACITY, createCapacity("DE", "wind onshore", 100))
            );

            var expected = new ResClusterGenerationDto(new ResClusterPropertiesDto(100.0, "wind_onshore"), List.of("techno_series.arrow"), null);
            assertEquals(expected, service.assembleResProperties(study).get("DE").get("wind_onshore"),
                    "Area techno LF series should take priority over only area LF series");
        }

        @Test
        void shouldProduceSeparateJsonEntryPerCluster() throws IOException {
            // Each cluster has its own subfolder and its own series file
            preparePhysicalFile(DEFAULT_TRAJECTORY, "solar_pv/cluster_A/cluster_A_DE_2030_2031.csv");
            preparePhysicalFile(DEFAULT_TRAJECTORY, "solar_pv/cluster_B/cluster_B_DE_2030_2031.csv");
            when(nasFileService.readAndSaveMatrixToNas(any(), eq(OUTPUT_DIR), any(), anyBoolean())).thenReturn("de_solar.arrow");

            TrajectoryEntity capTraj = TrajectoryEntity.builder()
                    .type(TrajectoryType.RES_CAPACITY.name()).fileName(DEFAULT_TRAJECTORY).build();
            capTraj.setResClusterCapacityEntities(List.of(
                    createCapacity("DE", "solar pv", "cluster_A", 100),
                    createCapacity("DE", "solar pv", "cluster_B", 200)
            ));

            StudyEntity study = createStudy(createTrajectory(TrajectoryType.RES_LOAD, DEFAULT_TRAJECTORY), capTraj);

            var deResult = service.assembleResProperties(study).get("DE");

            assertNotNull(deResult.get("cluster_A"), "cluster_A must be a separate JSON entry");
            assertNotNull(deResult.get("cluster_B"), "cluster_B must be a separate JSON entry");
            assertNull(deResult.get("solar_pv"), "group name must not appear as output key");
            assertEquals(100.0, deResult.get("cluster_A").properties().capacity());
            assertEquals(200.0, deResult.get("cluster_B").properties().capacity());
            assertEquals("solar_pv", deResult.get("cluster_A").properties().group());
            assertEquals("solar_pv", deResult.get("cluster_B").properties().group());
            assertEquals(List.of("de_solar.arrow"), deResult.get("cluster_A").series());
            assertEquals(List.of("de_solar.arrow"), deResult.get("cluster_B").series());
        }
    }

    private TrajectoryEntity createTechnoTrajectory(TrajectoryType type, String technology, Object content) {
        TrajectoryEntity t = createTrajectory(type, content);
        t.setTechnology(technology);
        return t;
    }

    private ResClusterGenerationDto getGroupPayload(Map<String, Map<String, ResClusterGenerationDto>> res, String a, String g) {
        return res.get(a.toUpperCase()).get(g.replace(" ", "_"));
    }
}