package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.multi_energy.MultiEnergyCoherenceCheckService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajectoryServiceImplMeTypesTest {

    @Mock
    StudyRepository studyRepository;

    @Mock
    TrajectoryRepository trajectoryRepository;

    @Mock
    StudyTrajectoryRepository studyTrajectoryRepository;

    @Mock
    WarningRepository warningRepository;

    @Mock
    UserService userService;

    @Mock
    MultiEnergyCoherenceCheckService multiEnergyCoherenceCheckService;

    @InjectMocks
    TrajectoryServiceImpl trajectoryService;

    @Test
    void linkTrajectoryToStudy_whenTypeIsEfficiencyMe_callsCoherenceCheckAndSavesLink() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(TrajectoryType.EFFICIENCY_ME.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());
        when(studyTrajectoryRepository.save(any(StudyTrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.EFFICIENCY_ME);

        assertNotNull(result);
        verify(multiEnergyCoherenceCheckService, times(1))
                .checkAreaMETrajectoryConsistency(studyId, TrajectoryType.EFFICIENCY_ME.name(), trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_whenTypeIsThermalCapacityMe_callsCoherenceCheckAndSavesLink() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(TrajectoryType.THERMAL_CAPACITY_ME.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());
        when(studyTrajectoryRepository.save(any(StudyTrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.THERMAL_CAPACITY_ME);

        assertNotNull(result);
        verify(multiEnergyCoherenceCheckService, times(1))
                .checkAreaMETrajectoryConsistency(studyId, TrajectoryType.THERMAL_CAPACITY_ME.name(), trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_whenTypeIsStsMe_callsCoherenceCheckAndSavesLink() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(TrajectoryType.STS_ME.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());
        when(studyTrajectoryRepository.save(any(StudyTrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.STS_ME);

        assertNotNull(result);
        verify(multiEnergyCoherenceCheckService, times(1))
                .checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_whenTypeIsHydroCapacityMe_callsCoherenceCheckAndSavesLink() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder().id(trajectoryId).type(TrajectoryType.HYDRO_CAPACITY_ME.name()).build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());
        when(studyTrajectoryRepository.save(any(StudyTrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.HYDRO_CAPACITY_ME);

        assertNotNull(result);
        verify(multiEnergyCoherenceCheckService, times(1))
                .checkAreaMETrajectoryConsistency(studyId, TrajectoryType.HYDRO_CAPACITY_ME.name(), trajectory);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
    }

    @Test
    void linkTrajectoryToStudy_whenTypeIsLoadMe_validatesAreasAndSavesLink() throws IOException {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("load_traj")
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(LoadEntity.builder().fileName("load_fr_2030.csv").area("FR").build()))
                .build();

        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("FR").build()).build();
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(3)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId)).thenReturn(List.of(areaMeTrajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());
        when(studyTrajectoryRepository.save(any(StudyTrajectoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.LOAD_ME);

        assertNotNull(result);
        verify(studyTrajectoryRepository, times(1)).save(any(StudyTrajectoryEntity.class));
        verify(multiEnergyCoherenceCheckService, never()).checkAreaMETrajectoryConsistency(any(), any(), any());
    }

    @Test
    void linkTrajectoryToStudy_whenTypeIsLoadMe_andNoMatchingArea_throwsBusinessException() {
        Integer trajectoryId = 1;
        Integer studyId = 2;

        StudyEntity study = StudyEntity.builder().id(studyId).studyTrajectoryEntities(new HashSet<>()).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(trajectoryId)
                .fileName("load_traj")
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(LoadEntity.builder().fileName("load_es_2030.csv").area("ES").build()))
                .build();

        AreaConfigEntity areaConfig = AreaConfigEntity.builder().area(AreaEntity.builder().name("FR").build()).build();
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .id(3)
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(areaConfig))
                .build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(trajectoryRepository.findById(trajectoryId)).thenReturn(Optional.of(trajectory));
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId)).thenReturn(List.of(areaMeTrajectory));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("testUser").build());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                trajectoryService.linkTrajectoryToStudy(trajectoryId, studyId, TrajectoryType.LOAD_ME)
        );

        assertEquals("No area from the LOAD_ME trajectory is present in AREA_ME trajectory {0}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(studyTrajectoryRepository, never()).save(any());
    }
}
