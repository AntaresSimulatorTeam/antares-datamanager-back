package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroAllocationRepository;
import com.rte_france.antares.datamanager_back.repository.HydroParametersRepository;
import com.rte_france.antares.datamanager_back.repository.HydroSeriesRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroSeriesEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroCoherenceCheckServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HydroCoherenceCheckServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;
    @Mock
    private HydroAllocationRepository hydroAllocationRepository;
    @Mock
    private HydroParametersRepository hydroParametersRepository;
    @Mock
    private HydroSeriesRepository hydroSeriesRepository;

    @InjectMocks
    private HydroCoherenceCheckServiceImpl service;

    private static final Integer STUDY_ID = 1;
    private static final String AREA = "FR";
    private static final String TRAJ_NAME = "hydro_FR_2029-2030";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ── getAreasInHydroSeriesModFiles ─────────────────────────────────────────

    @Test
    void getAreasInHydroSeriesModFiles_returnsEmptyWhenNoEntities() {
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(1)).thenReturn(List.of());

        List<String> result = service.getAreasInHydroSeriesModFiles(1);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAreasInHydroSeriesModFiles_returnsAreaFromModFile() {
        HydroSeriesEntity modEntity = HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build();
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(1)).thenReturn(List.of(modEntity));

        List<String> result = service.getAreasInHydroSeriesModFiles(1);

        assertEquals(List.of("FR"), result);
    }

    @Test
    void getAreasInHydroSeriesModFiles_filtersOutNonModFiles() {
        HydroSeriesEntity rorEntity = HydroSeriesEntity.builder().tsName("ror_FR_2029-2030").build();
        HydroSeriesEntity modEntity = HydroSeriesEntity.builder().tsName("mod_BE_2029-2030").build();
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(1))
                .thenReturn(List.of(rorEntity, modEntity));

        List<String> result = service.getAreasInHydroSeriesModFiles(1);

        assertEquals(List.of("BE"), result);
    }

    @Test
    void getAreasInHydroSeriesModFiles_returnsAllModAreas() {
        HydroSeriesEntity modFR = HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build();
        HydroSeriesEntity modBE = HydroSeriesEntity.builder().tsName("mod_BE_2029-2030").build();
        HydroSeriesEntity rorDE = HydroSeriesEntity.builder().tsName("ror_DE_2029-2030").build();
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(1))
                .thenReturn(List.of(modFR, modBE, rorDE));

        List<String> result = service.getAreasInHydroSeriesModFiles(1);

        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of("FR", "BE")));
    }

    // ── getAreasInHydroAllocationAreas ───────────────────────────────────────

    @Test
    void getAreasInHydroAllocationAreas_returnsEmptyWhenNoEntities() {
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(1)).thenReturn(List.of());

        List<String> result = service.getAreasInHydroAllocationAreas(1);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAreasInHydroAllocationAreas_returnsHydroFieldOfEachEntity() {
        HydroAllocationEntity fr = HydroAllocationEntity.builder().hydro("FR").build();
        HydroAllocationEntity be = HydroAllocationEntity.builder().hydro("BE").build();
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(1))
                .thenReturn(List.of(fr, be));

        List<String> result = service.getAreasInHydroAllocationAreas(1);

        assertEquals(List.of("FR", "BE"), result);
    }

    // ── getAreasInHydroParametersAreas ───────────────────────────────────────

    @Test
    void getAreasInHydroParametersAreas_returnsEmptyWhenNoEntities() {
        when(hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(1)).thenReturn(List.of());

        List<String> result = service.getAreasInHydroParametersAreas(1);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAreasInHydroParametersAreas_returnsNodeFieldOfEachEntity() {
        HydroParametersEntity fr = HydroParametersEntity.builder().node("FR").build();
        HydroParametersEntity be = HydroParametersEntity.builder().node("BE").build();
        when(hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(1))
                .thenReturn(List.of(fr, be));

        List<String> result = service.getAreasInHydroParametersAreas(1);

        assertEquals(List.of("FR", "BE"), result);
    }

    // ── checkHydroSeriesTrajectoriesConsistency ───────────────────────────────

    @Test
    void checkHydroSeriesTrajectoriesConsistency_doesNothingWhenNoTPTrajectoryFound() {
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(null);

        assertDoesNotThrow(() ->
                service.checkHydroSeriesTrajectoriesConsistency(STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_SERIES.name()));
    }

    @Test
    void checkHydroSeriesTrajectoriesConsistency_doesNothingWhenTPTrajectoryHasAllAreas() {
        TrajectoryEntity tpTrajectory = TrajectoryEntity.builder().id(10).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(tpTrajectory);
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(10))
                .thenReturn(List.of(HydroAllocationEntity.builder().hydro("FR").build()));
        when(hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(10))
                .thenReturn(List.of(HydroParametersEntity.builder().node("FR").build()));

        assertDoesNotThrow(() ->
                service.checkHydroSeriesTrajectoriesConsistency(STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_SERIES.name()));
    }

    @Test
    void checkHydroSeriesTrajectoriesConsistency_throwsWhenHydroAllocationMissingArea() {
        TrajectoryEntity tpTrajectory = TrajectoryEntity.builder().id(10).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(tpTrajectory);
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(10))
                .thenReturn(List.of(HydroAllocationEntity.builder().hydro("BE").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkHydroSeriesTrajectoriesConsistency(STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_SERIES.name()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("hydroAllocation"));
        assertTrue(ex.getMessage().contains(TRAJ_NAME));
    }

    @Test
    void checkHydroSeriesTrajectoriesConsistency_throwsWhenHydroParametersMissingArea() {
        TrajectoryEntity tpTrajectory = TrajectoryEntity.builder().id(10).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(tpTrajectory);
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(10))
                .thenReturn(List.of(HydroAllocationEntity.builder().hydro("FR").build()));
        when(hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(10))
                .thenReturn(List.of(HydroParametersEntity.builder().node("BE").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkHydroSeriesTrajectoriesConsistency(STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_SERIES.name()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("hydroParameters"));
        assertTrue(ex.getMessage().contains(TRAJ_NAME));
    }

    @Test
    void checkHydroSeriesTrajectoriesConsistency_doesNotCheckParametersWhenAllocationMissing() {
        TrajectoryEntity tpTrajectory = TrajectoryEntity.builder().id(10).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(tpTrajectory);
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(10))
                .thenReturn(List.of());

        assertThrows(BusinessException.class, () ->
                service.checkHydroSeriesTrajectoriesConsistency(STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_SERIES.name()));

        verify(hydroParametersRepository, never()).findHydroParametersEntitiesByTrajectoryId(any());
    }

    // ── checkHydroTPTrajectoriesConsistency ──────────────────────────────────

    @Test
    void checkHydroTPTrajectoriesConsistency_doesNothingWhenNoSeriesTrajectoryFound() {
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(null);

        assertDoesNotThrow(() ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("FR"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_ALLOCATION.name(), TrajectoryType.HYDRO_SERIES.name()));
    }

    @Test
    void checkHydroTPTrajectoriesConsistency_doesNothingWhenTPFilesContainAllModAreas() {
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));

        assertDoesNotThrow(() ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("FR", "BE"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_ALLOCATION.name(), TrajectoryType.HYDRO_SERIES.name()));
    }

    @Test
    void checkHydroTPTrajectoriesConsistency_throwsWhenTPFilesMissingModArea() {
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("BE"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_ALLOCATION.name(), TrajectoryType.HYDRO_SERIES.name()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains(TRAJ_NAME));
    }

    @Test
    void checkHydroTPTrajectoriesConsistency_errorArgsContainHydroAllocationLabelWhenTypeIsAllocation() {
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("BE"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_ALLOCATION.name(), TrajectoryType.HYDRO_SERIES.name()));

        assertTrue(ex.getErrorMessageArguments().contains("hydroAllocation"));
    }

    @Test
    void checkHydroTPTrajectoriesConsistency_errorArgsContainHydroParametersLabelWhenTypeIsParameters() {
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("BE"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_PARAMETERS.name(), TrajectoryType.HYDRO_SERIES.name()));

        assertTrue(ex.getErrorMessageArguments().contains("hydroParameters"));
    }

    @Test
    void checkHydroTPTrajectoriesConsistency_doesNothingWhenSeriesTrajectoryHasNoModFiles() {
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("ror_FR_2029-2030").build()));

        assertDoesNotThrow(() ->
                service.checkHydroTPTrajectoriesConsistency(
                        STUDY_ID, List.of("BE"), AREA, TRAJ_NAME, TrajectoryType.HYDRO_ALLOCATION.name(), TrajectoryType.HYDRO_SERIES.name()));
    }

    // ── validateHydroSeriesCoherence ─────────────────────────────────────────

    @Test
    void validateHydroSeriesCoherence_delegatesToCheckWithModAreas() {
        Integer trajectoryId = 5;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId).area(AREA).fileName(TRAJ_NAME).build();
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(trajectoryId))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(null);

        assertDoesNotThrow(() -> service.validateHydroSeriesCoherence(STUDY_ID, trajectory));

        verify(hydroSeriesRepository).findHydroSeriesEntitiesByTrajectoryId(trajectoryId);
        verify(trajectoryRepository).findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name());
    }

    @Test
    void validateHydroSeriesCoherence_throwsWhenCoherenceCheckFails() {
        Integer trajectoryId = 5;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId).area(AREA).fileName(TRAJ_NAME).build();
        TrajectoryEntity tpTrajectory = TrajectoryEntity.builder().id(10).build();

        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(trajectoryId))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_FR_2029-2030").build()));
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name()))
                .thenReturn(tpTrajectory);
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(10))
                .thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.validateHydroSeriesCoherence(STUDY_ID, trajectory));
    }

    // ── validateHydroTechnicalParametersCoherence ────────────────────────────

    @Test
    void validateHydroTechnicalParametersCoherence_usesAllocationAreasForHydroAllocationType() {
        Integer trajectoryId = 6;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId).area(AREA).fileName(TRAJ_NAME).build();
        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(trajectoryId))
                .thenReturn(List.of(HydroAllocationEntity.builder().hydro("FR").build()));
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(null);

        assertDoesNotThrow(() -> service.validateHydroTechnicalParametersCoherence(
                STUDY_ID, trajectory, TrajectoryType.HYDRO_ALLOCATION));

        verify(hydroAllocationRepository).findHydroAllocationEntitiesByTrajectoryId(trajectoryId);
        verify(hydroParametersRepository, never()).findHydroParametersEntitiesByTrajectoryId(any());
    }

    @Test
    void validateHydroTechnicalParametersCoherence_usesParametersAreasForHydroParametersType() {
        Integer trajectoryId = 6;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId).area(AREA).fileName(TRAJ_NAME).build();
        when(hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(trajectoryId))
                .thenReturn(List.of(HydroParametersEntity.builder().node("FR").build()));
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(null);

        assertDoesNotThrow(() -> service.validateHydroTechnicalParametersCoherence(
                STUDY_ID, trajectory, TrajectoryType.HYDRO_PARAMETERS));

        verify(hydroParametersRepository).findHydroParametersEntitiesByTrajectoryId(trajectoryId);
        verify(hydroAllocationRepository, never()).findHydroAllocationEntitiesByTrajectoryId(any());
    }

    @Test
    void validateHydroTechnicalParametersCoherence_throwsWhenCoherenceCheckFails() {
        Integer trajectoryId = 6;
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId).area(AREA).fileName(TRAJ_NAME).build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder().id(20).build();

        when(hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(trajectoryId))
                .thenReturn(List.of(HydroAllocationEntity.builder().hydro("FR").build()));
        when(trajectoryRepository.findLatestByStudyIdAndAreaAndType(
                STUDY_ID, AREA, TrajectoryType.HYDRO_SERIES.name()))
                .thenReturn(seriesTrajectory);
        when(hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(20))
                .thenReturn(List.of(HydroSeriesEntity.builder().tsName("mod_DE_2029-2030").build()));

        assertThrows(BusinessException.class, () -> service.validateHydroTechnicalParametersCoherence(
                STUDY_ID, trajectory, TrajectoryType.HYDRO_ALLOCATION));
    }
}
