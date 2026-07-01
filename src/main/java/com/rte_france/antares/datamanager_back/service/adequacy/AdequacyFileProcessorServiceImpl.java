package com.rte_france.antares.datamanager_back.service.adequacy;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.getFileNameWithoutExtensionAndWithoutPrefix;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdequacyFileProcessorServiceImpl implements AdequacyFileProcessorService{

    private final TrajectoryRepository trajectoryRepository;
    private final TrajectoryService trajectoryService;
    private final UserService userService;

    @Override
    public TrajectoryEntity processAdequacyFile(String trajectoryToUse, String horizon, String studyId, boolean isCivilYear) throws IOException {

        Path trajectoryFilePath = trajectoryService.buildTrajectoryPath(trajectoryToUse, TrajectoryType.MISC_LOAD);

        String fileName = getFileNameWithoutExtensionAndWithoutPrefix(path.getFileName().toString(), TrajectoryType.ADEQUACY_PATCH.name());
        Optional<TrajectoryEntity> trajectoryEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(fileName, horizon, TrajectoryType.AREA.name());

        String createdBy = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElse("UNKNOWN_USER");

        return null;
    }
}
