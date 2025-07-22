package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        // Arrange
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class))).thenReturn(trajectory);
        doNothing().when(trajectoryService).checkLinkCoherence(anyInt(), anySet(), any(TrajectoryEntity.class), anyString());

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LINK);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LINK);
        verify(trajectoryService).checkLinkCoherence(studyId, warningMessages, trajectory, createdBy);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LinkType_ThrowsIOException() throws Exception {
        // Arrange
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class))).thenThrow(new IOException("Test exception"));

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LINK);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LINK);
        verify(trajectoryService, never()).checkLinkCoherence(anyInt(), anySet(), any(TrajectoryEntity.class), anyString());
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LINK.name(), missingTrajectoryTypes.get(0));
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithNullLoadArea() throws Exception {
        // Arrange
        trajectory.setLoadArea(null);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService, never()).getAreasLoadWithoutTrajectorySelected(anyInt());
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithOthersLoadArea_HasValidArea() throws Exception {
        // Arrange
        trajectory.setLoadArea("OTHERS");

        // Create load entities
        Set<LoadEntity> loadEntities = new HashSet<>();
        LoadEntity loadEntity = new LoadEntity();
        loadEntity.setArea("FR");
        loadEntities.add(loadEntity);
        trajectory.setLoadEntities(loadEntities);

        // Mock available areas
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithOthersLoadArea_NoValidArea() throws Exception {
        // Arrange
        trajectory.setLoadArea("OTHERS");

        // Create load entities with areas not in available areas
        Set<LoadEntity> loadEntities = new HashSet<>();
        LoadEntity loadEntity = new LoadEntity();
        loadEntity.setArea("IT");
        loadEntities.add(loadEntity);
        trajectory.setLoadEntities(loadEntities);

        // Mock available areas that don't include the load entity area
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Assert
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        verify(trajectoryService, never()).linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class));
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LOAD.name(), missingTrajectoryTypes.get(0));
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithSpecificLoadArea_AreaAvailable() throws Exception {
        // Arrange
        trajectory.setLoadArea("FR");

        // Mock available areas
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.LOAD);
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    @Test
    void trajectoryToBeAttached_LoadType_WithSpecificLoadArea_AreaNotAvailable() throws Exception {
        // Arrange
        trajectory.setLoadArea("IT");

        // Mock available areas that don't include the trajectory load area
        List<String> availableAreas = Arrays.asList("FR", "DE", "ES");
        when(loadFileProcessorService.getAreasLoadWithoutTrajectorySelected(studyId)).thenReturn(availableAreas);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.LOAD);

        // Assert
        verify(loadFileProcessorService).getAreasLoadWithoutTrajectorySelected(studyId);
        verify(trajectoryService, never()).linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class));
        assertEquals(1, missingTrajectoryTypes.size());
        assertEquals(TrajectoryType.LOAD.name(), missingTrajectoryTypes.get(0));
    }

    @Test
    void trajectoryToBeAttached_OtherType_Success() throws Exception {
        // Arrange
        when(trajectoryService.linkTrajectoryToStudy(anyInt(), anyInt(), any(TrajectoryType.class))).thenReturn(trajectory);

        // Act
        invokeTrajectoryToBeAttached(trajectory, TrajectoryType.THERMAL_CAPACITY);

        // Assert
        verify(trajectoryService).linkTrajectoryToStudy(trajectory.getId(), studyId, TrajectoryType.THERMAL_CAPACITY);
        verify(trajectoryService, never()).checkLinkCoherence(anyInt(), anySet(), any(TrajectoryEntity.class), anyString());
        verify(loadFileProcessorService, never()).getAreasLoadWithoutTrajectorySelected(anyInt());
        assertTrue(missingTrajectoryTypes.isEmpty());
    }

    /**
     * Helper method to invoke the private trajectoryToBeAttached method via reflection
     */
    private void invokeTrajectoryToBeAttached(TrajectoryEntity trajectory, TrajectoryType type) throws InvocationTargetException, IllegalAccessException {
        trajectoryToBeAttachedMethod.invoke(
                null, // static method doesn't need an instance
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
}