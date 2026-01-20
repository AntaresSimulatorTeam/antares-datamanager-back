package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.area_link.impl.AreaFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AreaFileProcessorServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private AreaConfigRepository areaConfigRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AreaFileProcessorServiceImpl areaFileProcessorService;

    @TempDir
    Path tempDir;

    private Path tempFile;


        @BeforeEach
        public void setup() throws IOException {
            MockitoAnnotations.openMocks(this);

            tempFile = CreateExcelTestUtil.createExcelFile( tempDir,"TestFile.xlsx","2030-2031",
                    List.of("areas", "Power To Gas", "Stockage court terme", "x", "y", "r", "g", "b"),
                    List.of(
                            List.of("Area1", "False", "True", 3, 4, 1, 2, 3)
                    )
            );
        }

        @Test
        void processAreaFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
            when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
            var trajectoryEntity = mock(TrajectoryEntity.class);
            trajectoryEntity.setType(TrajectoryType.AREA.name());
            trajectoryEntity.setFileName(tempFile.toString());
            when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                    .thenReturn(Optional.of(trajectoryEntity));

            areaFileProcessorService.processAreaFile(tempFile, "2030-2031");

            verify(trajectoryRepository, times(1)).save(any());
            verify(areaConfigRepository, times(1)).saveAll(any());
        }

    @Test
    void processAreaFile_whenAreaExistsInBDD() throws IOException {
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        var trajectoryEntity = mock(TrajectoryEntity.class);
        trajectoryEntity.setType(TrajectoryType.AREA.name());
        trajectoryEntity.setFileName(tempFile.toString());
        var areaEntity = mock(AreaEntity.class);
        areaEntity.setName("Area1");
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.of(trajectoryEntity));
        when(areaRepository.findAreaByName(anyString()))
                .thenReturn(Optional.of(areaEntity));

        areaFileProcessorService.processAreaFile(tempFile, "2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
    }

        @Test
        void processAreaFile_whenTrajectoryDoesNotExist() throws IOException {
            when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
            when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any())).thenReturn(Optional.empty());

            areaFileProcessorService.processAreaFile(tempFile, "2030-2031");

            verify(trajectoryRepository, times(1)).save(any());
            verify(areaConfigRepository, times(1)).saveAll(any());
        }
}
