package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicationTrajectoryUtilsTest {

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private LoadFileProcessorServiceImpl loadFileProcessorService;

    private Method trajectoryToBeAttachedMethod;
    private TrajectoryEntity trajectory;
    private Set<WarningMessageEntity> warningMessages;
    private List<String> missingTrajectoryTypes;
    private final String createdBy = "testUser";
    private final Integer studyId = 123;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Use reflection to access the private method
        trajectoryToBeAttachedMethod = DuplicationTrajectoryUtils.class.getDeclaredMethod(
                "trajectoryToBeAttached",
                TrajectoryEntity.class,
                TrajectoryType.class,
                Integer.class,
                TrajectoryServiceImpl.class,
                LoadFileProcessorServiceImpl.class,
                Set.class,
                List.class,
                String.class
        );
        trajectoryToBeAttachedMethod.setAccessible(true);

        // Initialize test objects
        trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        warningMessages = new HashSet<>();
        missingTrajectoryTypes = new ArrayList<>();
    }

    @Test
    void trajectoryToBeAttached_LinkType_Success() throws Exception {
        // Given
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class))).thenReturn(trajectory);
        doNothing().when(trajectoryService).checkLinkCoherence(anyInt(), anySet(), any(TrajectoryEntity.class), anyString());

        // When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LINK);

        // Then
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LINK);
        verify(trajectoryService).checkLinkCoherence(studyId, warningMessages, trajectory, createdBy);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LinkType_ThrowsIOException() throws Exception {
        // Given
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class))).thenThrow(new IOException("Test exception"));

        // When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LINK);

        // Then
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LINK);
        verify(trajectoryService, never()).checkLinkCoherence(anyInt(), anySet(), any(TrajectoryEntity.class), anyString());
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LINK.name(), missingTrajectoryTypes.getFirst());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithNullLoadArea() throws Exception {
        // Given
        trajectory.setArea(null);

        // When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Then
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService, never()).getAreasLoadWithoutTrajectorySelected(anyInt());
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithOthersLoadArea_HasValidArea() throws Exception {
        // Given
        trajectory.setArea("OTHERS");

        // When
        Set<LoadEntity> loadEntities = new HashSet<>();
        LoadEntity loadEntity = new LoadEntity();
        loadEntity.setArea("FR");
        loadEntities.add(loadEntity);
        trajectory.setLoadEntities(loadEntities);


        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Then
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithOthersLoadArea_NoValidArea() throws Exception {
        // Given
        trajectory.setArea("OTHERS");

        // Create load entities with areas not in available areas
        Set<LoadEntity> loadEntities = new HashSet<>();
        LoadEntity loadEntity = new LoadEntity();
        loadEntity.setArea("IT");
        loadEntities.add(loadEntity);
        trajectory.setLoadEntities(loadEntities);

        // Mock available areas that don't include the load entity area
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        //When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Then
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        verify(trajectoryService, never()).linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class));
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LOAD.name(), missingTrajectoryTypes.getFirst());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithSpecificLoadArea_AreaAvailable() throws Exception {
        // Given
        trajectory.setArea("FR");


        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Then
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithSpecificLoadArea_AreaNotAvailable() throws Exception {
        // Given
        trajectory.setArea("IT");

        // Mock available areas that don't include the trajectory load area
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // When
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Then
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        verify(trajectoryService, never()).linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class));
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LOAD.name(), missingTrajectoryTypes.getFirst());
    }



    /**
     * Helper method to invoke the private trajectoryToBeAttached method via reflection
     */
    private void invokeTrajectoryToBeAttached(TrajectoryEntity trajectory, TrajectoryType type) throws InvocationTargetException, IllegalAccessException {
        trajectoryToBeAttachedMethod.invoke(
                null,
                trajectory,
                type,
                studyId,
                trajectoryService,
                loadFileProcessorService,
                warningMessages,
                missingTrajectoryTypes,
                createdBy
        );
    }

    @Test
    void processAndLinkTrajectories_whenAllLoadTrajectoriesFail_shouldAddLOADToMissingTypes_once() throws Exception {
        // Given
        TrajectoryServiceImpl trajectoryService = mock(TrajectoryServiceImpl.class);
        LoadFileProcessorServiceImpl loadFileProcessorService = mock(LoadFileProcessorServiceImpl.class);

        StudyDTO studyDTO = StudyDTO.builder()
                .id(100)
                .createdBy("user1")
                .build();

        // AREA obligatoire (sinon orElseThrow dans findAndLinkAreaTrajectory)
        TrajectoryEntity area = trajectory(TrajectoryType.AREA, 1, null);

        // 2 LOAD avec area non null => on va déclencher le "return" (donc échec) si la liste ne contient pas les areas
        TrajectoryEntity load1 = trajectory(TrajectoryType.LOAD, 10, "FR");
        TrajectoryEntity load2 = trajectory(TrajectoryType.LOAD, 11, "DE");

        // On fournit aussi les autres types supportés (sinon ils seront "missing" et le test devient moins lisible)
        TrajectoryEntity link = trajectory(TrajectoryType.LINK, 20, null);
        TrajectoryEntity thermalCapacity = trajectory(TrajectoryType.THERMAL_CAPACITY, 30, null);
        TrajectoryEntity thermalSpec = trajectory(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, 31, null);
        TrajectoryEntity thermalCommon = trajectory(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, 32, null);
        TrajectoryEntity thermalEcoCost = trajectory(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, 33, null);
        TrajectoryEntity thermalEco = trajectory(TrajectoryType.THERMAL_ECONOMIC_PARAMETER, 34, null);
        TrajectoryEntity thermalMod = trajectory(TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER, 35, null);

        List<TrajectoryEntity> available = new ArrayList<>(List.of(
                area,
                load1, load2,
                link,
                thermalCapacity,
                thermalSpec,
                thermalCommon,
                thermalEcoCost,
                thermalEco,
                thermalMod
        ));

        // La liste des zones "sans trajectoire sélectionnée" ne contient ni FR ni DE => les 2 LOAD échouent
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(100))
                .thenReturn(List.of("ES", "IT"));

        // On laisse les liens OK (pas d'exception) pour les autres types
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    TrajectoryEntity t = new TrajectoryEntity();
                    t.setId(inv.getArgument(0, Integer.class));
                    t.setType(inv.getArgument(2, TrajectoryType.class).name());
                    return t;
                });
        doNothing().when(trajectoryService).checkLinkCoherence(anyInt(), anySet(), any(), anyString());

        // When
        var result = DuplicationTrajectoryUtils.processAndLinkTrajectories(
                available,
                studyDTO,
                trajectoryService,
                loadFileProcessorService,
                "user1"
        );

        // Then
        assertThat(result.missingTrajectoryTypes())
                .contains(TrajectoryType.LOAD.name());

        // important: LOAD ne doit être ajouté qu'une seule fois dans missingTrajectoryTypes (c'est la branche que tu vises)
        assertThat(result.missingTrajectoryTypes().stream().filter(TrajectoryType.LOAD.name()::equals).count())
                .isEqualTo(1);

        // Et surtout: comme ça "return" avant linkTrajectoryToStudy pour LOAD, donc aucun link LOAD ne doit arriver
        verify(trajectoryService, never()).linkTrajectoryToStudy(10, 100, TrajectoryType.LOAD);
        verify(trajectoryService, never()).linkTrajectoryToStudy(11, 100, TrajectoryType.LOAD);
    }

    private static TrajectoryEntity trajectory(TrajectoryType type, int id, String area) {
        TrajectoryEntity t = new TrajectoryEntity();
        t.setId(id);
        t.setType(type.name());
        t.setArea(area);
        return t;
    }
}