package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

@Component
public class PathSecurityUtil {
    private final AntaresDataManagerProperties properties;

    public PathSecurityUtil(AntaresDataManagerProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    /**
     * Resolves untrusted path segments (ex: request parameters) against a base directory
     * and guarantees the result cannot escape it, closing off path traversal.
     * Returns the safe, resolved path so callers never have to call their own
     * resolve/normalize/{@code startsWith}.
     *
     * @param baseDirGetter builds the trusted base directory from config, ex:
     *                      {@code p -> Path.of(p.getNasDirectory(), p.getTrajectoryFilePath(), p.getFlowbasedDirectory())}
     * @param untrustedSegments path segments coming from user input, resolved in order under the base directory
     * @throws BusinessException (400) if the resolved path would fall outside the base directory
     */
    public Path resolveSafePath(Function<AntaresDataManagerProperties, Path> baseDirGetter, String... untrustedSegments) {
        return resolveSafePath(baseDirGetter.apply(properties), untrustedSegments);
    }

    /**
     * Same as {@link #resolveSafePath(Function, String...)}, but chains off
     * an already safe directory instead of config (e.g. a trajectory folder obtained from
     * a {@code resolveSafePath} call), for a second untrusted segment resolved after it
     *
     * @throws BusinessException (400) if the resolved path would fall outside {@code baseDirectory}
     */
    public Path resolveSafePath(Path baseDirectory, String... untrustedSegments) {
        Path normalizedBase = baseDirectory.normalize();

        Path resolved = normalizedBase;
        for (String segment : untrustedSegments) {
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();

        if (!resolved.startsWith(normalizedBase)) {
            throw outsideAllowedDirectory();
        }

        // for symlinks
        if (!realPathOfExistingPrefix(resolved).startsWith(realPathOfExistingPrefix(normalizedBase))) {
            throw outsideAllowedDirectory();
        }

        return resolved;
    }

    private static BusinessException outsideAllowedDirectory() {
        return BusinessException.builder()
                .message("Resolved path is outside of the allowed directory")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    /**
     * Returns where {@code path} really points on disk, following any symlinks but only for
     * the part of it that exists right now. Returns {@code path}
     * unchanged if nothing on it exists at all.
     *
     * @throws BusinessException (400) if resolving an existing part of the path fails for a
     * reason other than it not existing (ex: permissions)
     */
    private static Path realPathOfExistingPrefix(Path path) {
        Path suffix = null;
        for (Path current = path; current != null; current = current.getParent()) {
            try {
                Path realPrefix = current.toRealPath();
                return suffix == null ? realPrefix : realPrefix.resolve(suffix).normalize();
            } catch (NoSuchFileException e) {
                Path name = current.getFileName();
                if (name != null) {
                    suffix = suffix == null ? name : name.resolve(suffix);
                }
            } catch (IOException e) {
                throw outsideAllowedDirectory();
            }
        }
        return path;
    }
}