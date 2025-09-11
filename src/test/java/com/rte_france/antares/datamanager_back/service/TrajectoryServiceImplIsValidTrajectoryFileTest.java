package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrajectoryServiceImpl#isValidTrajectoryFile(Path, TrajectoryType)
 */
class TrajectoryServiceImplIsValidTrajectoryFileTest {

    private TrajectoryServiceImpl trajectoryService;

    @BeforeEach
    void setUp() {

        AreaFileProcessorService areaFileProcessorService = Mockito.mock(AreaFileProcessorService.class);
        LinkFileProcessorService linkFileProcessorService = Mockito.mock(LinkFileProcessorService.class);
        AntaressDataManagerProperties antaressDataManagerProperties = Mockito.mock(AntaressDataManagerProperties.class);
        TrajectoryRepository trajectoryRepository = Mockito.mock(TrajectoryRepository.class);
        ThermalFileProcessorService thermalFileProcessorService = Mockito.mock(ThermalFileProcessorService.class);
        LoadFileProcessorService loadFileProcessorService = Mockito.mock(LoadFileProcessorService.class);
        StudyRepository studyRepository = Mockito.mock(StudyRepository.class);
        StudyTrajectoryRepository studyTrajectoryRepository = Mockito.mock(StudyTrajectoryRepository.class);
        AreaConfigRepository areaConfigRepository = Mockito.mock(AreaConfigRepository.class);
        AreaRepository areaRepository = Mockito.mock(AreaRepository.class);
        LinkRepository linkRepository = Mockito.mock(LinkRepository.class);
        WarningRepository warningRepository = Mockito.mock(WarningRepository.class);
        UserService userService = Mockito.mock(UserService.class);
        LoadRepository loadRepository = Mockito.mock(LoadRepository.class);
        LoadFileProcessorServiceImpl loadFileProcessorServiceImpl = Mockito.mock(LoadFileProcessorServiceImpl.class);

        trajectoryService = new TrajectoryServiceImpl(
                areaFileProcessorService,
                linkFileProcessorService,
                antaressDataManagerProperties,
                trajectoryRepository,
                thermalFileProcessorService,
                loadFileProcessorService,
                studyRepository,
                studyTrajectoryRepository,
                areaConfigRepository,
                areaRepository,
                linkRepository,
                warningRepository,
                userService,
                loadRepository,
                loadFileProcessorServiceImpl
        );
    }

    private boolean invokeIsValid(Path path, TrajectoryType type) throws Exception {
        Method m = TrajectoryServiceImpl.class.getDeclaredMethod("isValidTrajectoryFile", Path.class, TrajectoryType.class);
        m.setAccessible(true);
        return (boolean) m.invoke(trajectoryService, path, type);
    }

    @Test
    void areaType_acceptsAreasXlsxOnly() throws Exception {
        assertTrue(invokeIsValid(Path.of("areas_fr_2025.xlsx"), TrajectoryType.AREA));
        assertFalse(invokeIsValid(Path.of("links_fr_2025.xlsx"), TrajectoryType.AREA));
        assertFalse(invokeIsValid(Path.of("areas_fr_2025.csv"), TrajectoryType.AREA));
        // case-insensitive
        assertTrue(invokeIsValid(Path.of("ArEaS_FR_2025.XLSX"), TrajectoryType.AREA));
    }

    @Test
    void linkType_acceptsLinksXlsxOnly() throws Exception {
        assertTrue(invokeIsValid(Path.of("links_be_2026.xlsx"), TrajectoryType.LINK));
        assertFalse(invokeIsValid(Path.of("areas_be_2026.xlsx"), TrajectoryType.LINK));
        assertFalse(invokeIsValid(Path.of("links_be_2026.xls"), TrajectoryType.LINK));
        assertTrue(invokeIsValid(Path.of("LiNkS_be_2026.Xlsx"), TrajectoryType.LINK));
    }

    @Test
    void otherTypes_acceptAnyXlsx() throws Exception {
        // LOAD should accept any .xlsx regardless of prefix
        assertTrue(invokeIsValid(Path.of("thermal_fr.xlsx"), TrajectoryType.THERMAL_CAPACITY));
        assertTrue(invokeIsValid(Path.of("common_param_.xlsx"), TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER));
        assertTrue(invokeIsValid(Path.of("specific_param.xlsx"), TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER));

    }
}
