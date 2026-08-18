package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.NuclearSMRMixageDTO;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ClusterDesignationRepository;
import com.rte_france.antares.datamanager_back.repository.model.ClusterDesignationEntity;
import com.rte_france.antares.datamanager_back.repository.model.ClusterDesignationKey;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearAvailabilityAssemblyResult;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.NuclearTimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NuclearAvailabilityAssemblerServiceImplTest {

    @Mock private NasFileService nasFileService;
    @Mock private TimeSeriesReader timeSeriesReader;
    @Mock private NuclearTimeSeriesReader nuclearTimeSeriesReader;
    @Mock private AntaresDataManagerProperties properties;
    @Mock private PathSecurityUtil pathSecurityUtil;
    @Mock private ClusterDesignationRepository clusterDesignationRepository;

    @InjectMocks
    private NuclearAvailabilityAssemblerServiceImpl assembler;

    private static final String HORIZON = "2029-2030";

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("nuc_availability_test_");
        when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        when(properties.getTrajectoryFilePath()).thenReturn("INPUT");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(tempDir);
    }

    private static AreaClusterRefKey key(String area, String clusterName) {
        return new AreaClusterRefKey(area, ThermalClusterRef.builder().name(clusterName).build());
    }

    private static StudyEntity studyWith(TrajectoryEntity trajectory) {
        return StudyEntity.builder().horizon(HORIZON).trajectories(Set.of(trajectory)).build();
    }

    @Nested
    class LongTermAvailability {

        private static final String TRAJ_NAME = "BP25_liv3";
        private TimeSeriesWriter capturingWriter;

        @BeforeEach
        void setUp() throws IOException {
            when(properties.getNuclearLtDirectory()).thenReturn("specific_nuclear/TS_dispo");
            when(properties.getNuclearAvailabilityTsOutputDirectory()).thenReturn("output/nuclear_availability_ts_arrow");

            when(clusterDesignationRepository.findByCluster_TypeCluster("cp0_cp1_cp2")).thenReturn(List.of(
                    ClusterDesignationEntity.builder().id(ClusterDesignationKey.builder().clusterId(2).nomCluster("BLAYAN01").build()).build(),
                    ClusterDesignationEntity.builder().id(ClusterDesignationKey.builder().clusterId(2).nomCluster("BLAYAN02").build()).build()
            ));
            when(clusterDesignationRepository.findByCluster_TypeCluster("n4")).thenReturn(List.of(
                    ClusterDesignationEntity.builder().id(ClusterDesignationKey.builder().clusterId(3).nomCluster("CHOO2N01").build()).build()
            ));
            when(clusterDesignationRepository.findByCluster_TypeCluster("p4")).thenReturn(List.of(
                    ClusterDesignationEntity.builder().id(ClusterDesignationKey.builder().clusterId(4).nomCluster("PALUEN01").build()).build()
            ));

            when(timeSeriesReader.listSheetNames(any())).thenReturn(List.of("s1", "s2"));
            when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), eq("s1"), anySet())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("BLAYAN01", new double[]{1.0, 2.0}),
                    new TimeSeriesMatrixColumn("BLAYAN02", new double[]{3.0, 4.0})
            )));
            when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), eq("s2"), anySet())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("BLAYAN01", new double[]{10.0}),
                    new TimeSeriesMatrixColumn("BLAYAN02", new double[]{20.0})
            )));

            capturingWriter = mock(TimeSeriesWriter.class);
            when(capturingWriter.writeToByteArray(any())).thenReturn(new byte[]{1, 2, 3});
            when(nasFileService.getWriter()).thenReturn(capturingWriter);
            when(nasFileService.saveMatrixBytesToNas(any(), contains("_lt_n4"), anyString())).thenReturn("lt_n4_arrow_file.arrow");
            when(nasFileService.saveMatrixBytesToNas(any(), contains("_lt_p4"), anyString())).thenReturn("lt_p4_arrow_file.arrow");
            when(nasFileService.saveMatrixBytesToNas(any(), contains("_lt_cp0_cp1_cp2"), anyString())).thenReturn("lt_cp0_cp1_cp2_arrow_file.arrow");
        }

        private TrajectoryEntity ltTrajectory() {
            return TrajectoryEntity.builder().type(TrajectoryType.NUCLEAR_FR_TS_LONG_TERM.name()).fileName(TRAJ_NAME).build();
        }

        @Test
        void shouldRouteEachClusterToItsOwnDesignationGroupFile_thenApplyToAllNonPeakNonEprNonSmrClusters() {
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            AreaClusterRefKey cp0Key = key("fr", "Nuclear_cp0_cp1_cp2");
            AreaClusterRefKey n4Key = key("fr", "Nuclear_n4");
            AreaClusterRefKey p4Key = key("fr", "Nuclear_p4");
            AreaClusterRefKey eprKey = key("fr", "Nuclear_epr");
            AreaClusterRefKey smrKey = key("fr", "Nuclear_smr");
            AreaClusterRefKey peakKey = key("fr", "Nuclear_peak1");
            props.put(cp0Key, ThermalClusterGenerationDto.builder().build());
            props.put(n4Key, ThermalClusterGenerationDto.builder().build());
            props.put(p4Key, ThermalClusterGenerationDto.builder().build());
            props.put(eprKey, ThermalClusterGenerationDto.builder().build());
            props.put(smrKey, ThermalClusterGenerationDto.builder().build());
            props.put(peakKey, ThermalClusterGenerationDto.builder().build());

            NuclearAvailabilityAssemblyResult result = assembler.assembleAvailability(studyWith(ltTrajectory()), props);

            assertThat(result.seriesByCluster())
                    .containsEntry(cp0Key, "lt_cp0_cp1_cp2_arrow_file.arrow")
                    .containsEntry(n4Key, "lt_n4_arrow_file.arrow")
                    .containsEntry(p4Key, "lt_p4_arrow_file.arrow")
                    .doesNotContainKey(eprKey)
                    .doesNotContainKey(smrKey)
                    .doesNotContainKey(peakKey);
            assertThat(result.smrMixageByCluster()).isEmpty();
        }

        @Test
        void shouldWriteOneColumnPerOnglet_withDailyValuesDuplicated24TimesAndRounded() throws IOException {
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            props.put(key("fr", "Nuclear_cp0_cp1_cp2"), ThermalClusterGenerationDto.builder().build());

            // Mock values that will result in more than 2 decimals after the sum
            // Use values that also match the original test logic (4.0 and 6.0) but with decimals to test rounding
            when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), eq("s1"), anySet())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("BLAYAN01", new double[]{1.234, 2.0001}),
                    new TimeSeriesMatrixColumn("BLAYAN02", new double[]{3.456, 4.0001})
            )));
            // day 1: 1.234 + 3.456 = 4.69
            // day 2: 2.0001 + 4.0001 = 6.0002 -> 6.0

            when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), eq("s2"), anySet())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("BLAYAN01", new double[]{10.005}),
                    new TimeSeriesMatrixColumn("BLAYAN02", new double[]{20.004})
            )));
            // day 1: 10.005 + 20.004 = 30.009 -> 30.01

            assembler.assembleAvailability(studyWith(ltTrajectory()), props);

            ArgumentCaptor<TimeSeriesMatrix> captor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
            verify(capturingWriter).writeToByteArray(captor.capture());
            TimeSeriesMatrix combined = captor.getValue();

            assertThat(combined.columns()).hasSize(2);
            assertThat(combined.columns().getFirst().name()).isEqualTo("s1");
            assertThat(combined.columns().getFirst().values()).hasSize(48);
            // day 1 sum = 1.234 + 3.456 = 4.69, duplicated across first 24 hourly steps
            assertThat(combined.columns().getFirst().values()[0]).isEqualTo(4.69);
            assertThat(combined.columns().getFirst().values()[23]).isEqualTo(4.69);
            // day 2 sum = 2.0001 + 4.0001 = 6.0002, rounded to 6.0, duplicated across next 24 hourly steps
            assertThat(combined.columns().get(0).values()[24]).isEqualTo(6.0);
            assertThat(combined.columns().get(0).values()[47]).isEqualTo(6.0);

            assertThat(combined.columns().get(1).name()).isEqualTo("s2");
            assertThat(combined.columns().get(1).values()).hasSize(24);
            // single day sum = 10.005 + 20.004 = 30.009, rounded to 30.01
            assertThat(combined.columns().get(1).values()[0]).isEqualTo(30.01);
            assertThat(combined.columns().get(1).values()[23]).isEqualTo(30.01);
        }
    }

    @Nested
    class EprAvailability {

        private static final String STORED_NAME = "BP25_ref";
        private TimeSeriesWriter capturingWriter;

        @BeforeEach
        void setUp() throws IOException {
            when(properties.getNuclearEprDirectory()).thenReturn("specific_nuclear/TS_dispo/EPR");
            when(properties.getNuclearAvailabilityTsOutputDirectory()).thenReturn("output/nuclear_availability_ts_arrow");

            Path eprDir = tempDir.resolve("INPUT").resolve("specific_nuclear/TS_dispo/EPR");
            Files.createDirectories(eprDir);
            Files.createFile(eprDir.resolve("TS_EPR_" + STORED_NAME + ".xlsx"));

            when(nuclearTimeSeriesReader.readFromXlsx(any(Path.class), any(), anyBoolean())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("Simu1", new double[]{1.0, 2.0}),
                    new TimeSeriesMatrixColumn("Simu2", new double[]{3.0, 4.0})
            )));

            capturingWriter = mock(TimeSeriesWriter.class);
            when(capturingWriter.writeToByteArray(any())).thenReturn(new byte[]{1, 2, 3});
            when(nasFileService.getWriter()).thenReturn(capturingWriter);
            when(nasFileService.saveMatrixBytesToNas(any(), anyString(), anyString())).thenReturn("epr_arrow_file.arrow");
        }

        private TrajectoryEntity eprTrajectory(String storedName) {
            return TrajectoryEntity.builder().type(TrajectoryType.NUCLEAR_FR_TS_ERP.name()).fileName(storedName).build();
        }

        @Test
        void shouldExpandEachColumnDailyToHourly_thenApplyOnlyToEprClusters() throws IOException {
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            AreaClusterRefKey eprKey = key("fr", "Nuclear_epr");
            AreaClusterRefKey ltKey = key("fr", "Nuclear_cp0_cp1_cp2");
            props.put(eprKey, ThermalClusterGenerationDto.builder().build());
            props.put(ltKey, ThermalClusterGenerationDto.builder().build());

            NuclearAvailabilityAssemblyResult result = assembler.assembleAvailability(studyWith(eprTrajectory(STORED_NAME)), props);

            assertThat(result.seriesByCluster())
                    .containsEntry(eprKey, "epr_arrow_file.arrow")
                    .doesNotContainKey(ltKey);

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            ArgumentCaptor<Boolean> hasHeaderCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(nuclearTimeSeriesReader).readFromXlsx(pathCaptor.capture(), eq("2030"), hasHeaderCaptor.capture());

            assertThat(pathCaptor.getValue().toString())
                    .endsWith(Path.of("specific_nuclear", "TS_dispo", "EPR", "TS_EPR_" + STORED_NAME + ".xlsx").toString());
            assertThat(hasHeaderCaptor.getValue()).isTrue();

            ArgumentCaptor<TimeSeriesMatrix> writtenCaptor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
            verify(capturingWriter).writeToByteArray(writtenCaptor.capture());
            TimeSeriesMatrix written = writtenCaptor.getValue();

            assertThat(written.columns()).hasSize(2);
            assertThat(written.columns().getFirst().name()).isEqualTo("Simu1");
            assertThat(written.columns().getFirst().values()).hasSize(48);
            assertThat(written.columns().getFirst().values()[0]).isEqualTo(1.0);
            assertThat(written.columns().getFirst().values()[23]).isEqualTo(1.0);
            assertThat(written.columns().getFirst().values()[24]).isEqualTo(2.0);
            assertThat(written.columns().getFirst().values()[47]).isEqualTo(2.0);
        }

        @Test
        void shouldFindFileWithLowercasePrefixCandidate() throws IOException {
            String storedName = "case_test_epr";
            Path eprDir = tempDir.resolve("INPUT").resolve("specific_nuclear/TS_dispo/EPR");
            Files.createFile(eprDir.resolve("ts_epr_" + storedName + ".xlsx"));

            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            props.put(key("fr", "Nuclear_epr"), ThermalClusterGenerationDto.builder().build());

            assembler.assembleAvailability(studyWith(eprTrajectory(storedName)), props);

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            verify(nuclearTimeSeriesReader).readFromXlsx(pathCaptor.capture(), any(), anyBoolean());
            assertThat(pathCaptor.getValue().toString()).endsWith("ts_epr_" + storedName + ".xlsx");
        }

        @Test
        void shouldThrowTechnicalExceptionWhenFileNotFound() {
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            StudyEntity study = studyWith(eprTrajectory("no_such_file"));

            assertThatThrownBy(() -> assembler.assembleAvailability(study, props))
                    .isInstanceOf(TechnicalException.class);
        }
    }

    @Nested
    class SmrAvailability {

        private static final String STORED_NAME = "BP25_ref";
        private TimeSeriesWriter capturingWriter;

        @BeforeEach
        void setUp() throws IOException {
            when(properties.getNuclearSmrDirectory()).thenReturn("specific_nuclear/TS_dispo/SMR");
            when(properties.getNuclearAvailabilityTsOutputDirectory()).thenReturn("output/nuclear_availability_ts_arrow");

            Path smrDir = tempDir.resolve("INPUT").resolve("specific_nuclear/TS_dispo/SMR");
            Files.createDirectories(smrDir);
            Files.createFile(smrDir.resolve("TS_SMR_" + STORED_NAME + ".xlsx"));

            capturingWriter = mock(TimeSeriesWriter.class);
            when(capturingWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
            when(nasFileService.getWriter()).thenReturn(capturingWriter);
            when(nasFileService.saveMatrixBytesToNas(any(), anyString(), anyString())).thenReturn("smr_arrow_file.arrow");
        }

        private TrajectoryEntity smrTrajectory() {
            return TrajectoryEntity.builder().type(TrajectoryType.NUCLEAR_FR_TS_SMR.name()).fileName(STORED_NAME).build();
        }

        @Test
        void shouldWriteOneSharedPoolWithAllColumnsExpandedHourly() throws IOException {
            when(nuclearTimeSeriesReader.readFromXlsx(any(), any(), anyBoolean())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("col1", new double[]{1.0, 2.0}),
                    new TimeSeriesMatrixColumn("col2", new double[]{3.0, 4.0})
            )));

            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            AreaClusterRefKey smrKey = key("fr", "Nuclear_smr");
            props.put(smrKey, ThermalClusterGenerationDto.builder().unitCount(3).build());

            NuclearAvailabilityAssemblyResult result = assembler.assembleAvailability(studyWith(smrTrajectory()), props);

            ArgumentCaptor<TimeSeriesMatrix> captor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
            verify(capturingWriter).writeToByteArray(captor.capture());
            TimeSeriesMatrix writtenPool = captor.getValue();

            // every column from the source pool is kept — no exclusion
            assertThat(writtenPool.columns()).hasSize(2);
            assertThat(writtenPool.columns()).extracting(TimeSeriesMatrixColumn::name).containsExactly("col1", "col2");

            // each retained column is expanded x24 daily-to-hourly, like LT/EPR
            assertThat(writtenPool.columns().getFirst().values()).hasSize(48);
            assertThat(writtenPool.columns().getFirst().values()[0]).isEqualTo(1.0);
            assertThat(writtenPool.columns().getFirst().values()[23]).isEqualTo(1.0);
            assertThat(writtenPool.columns().getFirst().values()[24]).isEqualTo(2.0);
            assertThat(writtenPool.columns().getFirst().values()[47]).isEqualTo(2.0);

            assertThat(result.seriesByCluster()).containsEntry(smrKey, "smr_arrow_file.arrow");
            assertThat(result.smrMixageByCluster()).containsKey(smrKey);
            NuclearSMRMixageDTO mixage = result.smrMixageByCluster().get(smrKey);
            assertThat(mixage.unitCount()).isEqualTo(3);
            assertThat(mixage.seed()).isEqualTo("frNuclear_smrseed-tsgen-thermal");
        }

        @Test
        void shouldKeepDecimalValuesWhenExpandingHourly() throws IOException {
            double decimalValue1 = 0.857;
            double decimalValue2 = 1.23456789;

            when(nuclearTimeSeriesReader.readFromXlsx(any(), any(), anyBoolean())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("col1", new double[]{decimalValue1, decimalValue2})
            )));

            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            AreaClusterRefKey smrKey = key("fr", "Nuclear_smr");
            props.put(smrKey, ThermalClusterGenerationDto.builder().unitCount(1).build());

            assembler.assembleAvailability(studyWith(smrTrajectory()), props);

            ArgumentCaptor<TimeSeriesMatrix> captor = ArgumentCaptor.forClass(TimeSeriesMatrix.class);
            verify(capturingWriter).writeToByteArray(captor.capture());
            TimeSeriesMatrix writtenPool = captor.getValue();

            assertThat(writtenPool.columns().getFirst().values()[0]).isEqualTo(decimalValue1);
            assertThat(writtenPool.columns().getFirst().values()[24]).isEqualTo(decimalValue2);
        }

        @Test
        void shouldShareTheSameArrowFileAcrossMultipleMatchedSmrClusters() throws IOException {
            when(nuclearTimeSeriesReader.readFromXlsx(any(), any(), anyBoolean())).thenReturn(new TimeSeriesMatrix(List.of(
                    new TimeSeriesMatrixColumn("col1", new double[]{1.0}),
                    new TimeSeriesMatrixColumn("col2", new double[]{2.0})
            )));

            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            AreaClusterRefKey smrKey1 = key("fr", "Nuclear_smr");
            AreaClusterRefKey smrKey2 = key("fr", "Nuclear_smr_2");
            props.put(smrKey1, ThermalClusterGenerationDto.builder().unitCount(1).build());
            props.put(smrKey2, ThermalClusterGenerationDto.builder().unitCount(2).build());

            NuclearAvailabilityAssemblyResult result = assembler.assembleAvailability(studyWith(smrTrajectory()), props);

            String smrKey2Series = result.seriesByCluster().get(smrKey2);
            assertThat(result.seriesByCluster()).containsEntry(smrKey1, smrKey2Series);
            assertThat(result.smrMixageByCluster().get(smrKey1).unitCount()).isEqualTo(1);
            assertThat(result.smrMixageByCluster().get(smrKey2).unitCount()).isEqualTo(2);
            // one write-only, even with two matched clusters
            verify(capturingWriter).writeToByteArray(any());
        }

        @Test
        void shouldThrowBusinessExceptionWhenSmrClusterHasNoUnitCount() {
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> props = new LinkedHashMap<>();
            props.put(key("fr", "Nuclear_smr"), ThermalClusterGenerationDto.builder().build());
            StudyEntity study = studyWith(smrTrajectory());

            assertThatThrownBy(() -> assembler.assembleAvailability(study, props))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}