package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PathSecurityUtilTest {

    @Mock
    private AntaresDataManagerProperties properties;

    private PathSecurityUtil pathSecurityUtil;

    @TempDir
    Path tempDir;

    private Path baseDir;

    @BeforeEach
    void setUp() throws IOException {
        baseDir = tempDir.resolve("nas_root");
        Files.createDirectories(baseDir);

        lenient().when(properties.getNasDirectory()).thenReturn(tempDir.toString());
        lenient().when(properties.getTrajectoryFilePath()).thenReturn("nas_root");

        pathSecurityUtil = new PathSecurityUtil(properties);
    }

    @Test
    void resolveSafePath_acceptsALegitimateSegmentUnderTheBaseDirectory() {
        Path result = pathSecurityUtil.resolveSafePath(baseDir, "trajectory_name");

        assertThat(result).isEqualTo(baseDir.resolve("trajectory_name"));
    }

    @Test
    void resolveSafePath_acceptsAPathThatDoesNotExistYet() {
        // resolveSafePath is normally called before the caller checks Files.exists/isDirectory
        Path result = pathSecurityUtil.resolveSafePath(baseDir, "not_created_yet", "nested");

        assertThat(result).isEqualTo(baseDir.resolve("not_created_yet").resolve("nested"));
    }

    @Test
    void resolveSafePath_rejectsLexicalDotDotTraversal() {
        assertThatThrownBy(() -> pathSecurityUtil.resolveSafePath(baseDir, "../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    @Test
    void resolveSafePath_rejectsAnAbsolutePathSegmentThatWouldReplaceTheBase() {
        assertThatThrownBy(() -> pathSecurityUtil.resolveSafePath(baseDir, "/etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    @Test
    void resolveSafePath_rejectsASymlinkPlantedInsideTheBaseDirectoryThatPointsOutsideIt() throws IOException {
        Path secretOutsideBase = tempDir.resolve("secret.txt");
        Files.writeString(secretOutsideBase, "top secret");

        Path symlink = baseDir.resolve("escape_link");
        createSymlinkOrSkip(symlink, secretOutsideBase);

        assertThatThrownBy(() -> pathSecurityUtil.resolveSafePath(baseDir, "escape_link"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    @Test
    void resolveSafePath_acceptsASymlinkThatStaysInsideTheBaseDirectory() throws IOException {
        Path realFile = baseDir.resolve("real_file.txt");
        Files.writeString(realFile, "hello");

        Path symlink = baseDir.resolve("inside_link");
        createSymlinkOrSkip(symlink, realFile);

        Path result = pathSecurityUtil.resolveSafePath(baseDir, "inside_link");

        assertThat(result).isEqualTo(symlink);
    }

    @Test
    void resolveSafePath_withConfigBackedBaseDirectory_resolvesTheSameWayAsWithAnExplicitPath() {
        Path result = pathSecurityUtil.resolveSafePath(
                p -> Path.of(p.getNasDirectory(), p.getTrajectoryFilePath()),
                "trajectory_name"
        );

        assertThat(result).isEqualTo(baseDir.resolve("trajectory_name"));
    }

    @Test
    void resolveSafePath_withConfigBackedBaseDirectory_rejectsTraversal() {
        assertThatThrownBy(() -> pathSecurityUtil.resolveSafePath(
                p -> Path.of(p.getNasDirectory(), p.getTrajectoryFilePath()),
                "../../etc/passwd"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside of the allowed directory");
    }

    @Test
    void resolveSafePath_chainsOffAnAlreadyResolvedSafeDirectory() {
        Path trajectoryFolder = pathSecurityUtil.resolveSafePath(baseDir, "trajectory_name");

        Path fileInFolder = pathSecurityUtil.resolveSafePath(trajectoryFolder, "data.xlsx");

        assertThat(fileInFolder).isEqualTo(trajectoryFolder.resolve("data.xlsx"));
    }

    /**
     * For not elevated environments (can't create symlinks)
     */
    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException e) {
            Assumptions.assumeTrue(false, "Symbolic links are not supported/permitted in this environment: " + e.getMessage());
        }
    }
}
