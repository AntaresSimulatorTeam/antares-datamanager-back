package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Function;

@Component
public class PathSecurityUtil {
    private final AntaressDataManagerProperties properties;

    public PathSecurityUtil(AntaressDataManagerProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public void validatePathFromBaseDir(String fileName, Function<AntaressDataManagerProperties, String> pathGetter) throws IOException {
        var baseDir = new File(pathGetter.apply(properties));
        var targetFile = new File(baseDir, fileName);
        var canonicalBase = baseDir.getCanonicalPath() + File.separator;
        var canonicalTarget = targetFile.getCanonicalPath();
        if (!canonicalTarget.startsWith(canonicalBase)) {
            throw new IOException("Entry is outside of the allowed directory");
        }
    }

    /**
     * Builds the file path for a trajectory based on the provided trajectory name and type.
     * The method resolves the path against the NAS directory, trajectory file path,
     * and the specific directory corresponding to the trajectory type.
     * Throws an exception if any required directory path is missing or if the trajectory type is invalid.
     *
     * @param trajectoryToUse the name of the trajectory file to construct the path for
     * @param trajectoryType the type of the trajectory, which determines the subdirectory to use
     * @return the constructed and normalized file path for the trajectory
     * @throws BusinessException if the Antaress path configuration is incomplete
     * @throws IllegalStateException if the trajectoryType has an unexpected value
     */
    public Path buildTrajectoryPath(String trajectoryToUse, TrajectoryType trajectoryType) {
        String nasDir = properties.getNasDirectory();
        String trajFilePath = properties.getTrajectoryFilePath();
        String directory = switch (trajectoryType) {
            case LOAD -> properties.getLoadDirectory();
            case THERMAL_TECHNICAL_MODULATION_PARAMETER -> properties.getThermalParamModulationDirectory();
            default -> throw new IllegalStateException("Unexpected value: " + trajectoryType);
        };

        if (nasDir == null || trajFilePath == null || directory == null) {
            throw BusinessException.builder()
                    .message("Antaress path configuration is incomplete")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return Paths.get(nasDir)
                .resolve(trajFilePath)
                .resolve(directory)
                .resolve(trajectoryToUse)
                .normalize();
    }

}