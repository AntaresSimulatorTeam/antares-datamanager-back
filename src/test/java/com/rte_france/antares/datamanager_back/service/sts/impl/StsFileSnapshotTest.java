package com.rte_france.antares.datamanager_back.service.sts.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StsFileSnapshotTest {

    @TempDir
    Path root;

    @Test
    void createsPrivateSnapshotAndDeletesItOnClose() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path source = Files.writeString(root.resolve("series.txt"), "snapshot content");
        Path snapshotDirectory;
        try (StsFileSnapshot snapshot = new StsFileSnapshot(root)) {
            Path copy = snapshot.capture(source, true);
            snapshotDirectory = copy.getParent();
            assertThat(Files.getPosixFilePermissions(snapshotDirectory))
                    .isEqualTo(PosixFilePermissions.fromString("rwx------"));
            assertThat(Files.readString(copy)).isEqualTo("snapshot content");
        }
        assertThat(snapshotDirectory).doesNotExist();
    }
}
