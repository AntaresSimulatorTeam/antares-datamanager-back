package com.rte_france.antares.datamanager_back.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ColumnSplitWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void openWriterIfAllowed_ShouldCreateWriterWhenSuffixMatchIsEnabled() throws Exception {
        List<Path> generated = new ArrayList<>();

        Optional<BufferedWriter> maybeWriter = ColumnSplitWriter.openWriterIfAllowed(
                "daily_min_FR",
                "base",
                tempDir,
                Set.of("fr"),
                generated,
                true,
                true
        );

        assertTrue(maybeWriter.isPresent());
        try (BufferedWriter writer = maybeWriter.get()) {
            writer.write("v");
        }

        assertEquals(1, generated.size());
        Path created = generated.getFirst();
        assertTrue(Files.exists(created));
        assertEquals("base_daily_min_FR.csv", created.getFileName().toString());
    }

    @Test
    void openWriterIfAllowed_ShouldSkipWhenHeaderOrAllowedDoesNotMatch() throws Exception {
        Optional<BufferedWriter> nullHeader = ColumnSplitWriter.openWriterIfAllowed(
                null,
                "base",
                tempDir,
                Set.of("fr"),
                null,
                false,
                false
        );
        Optional<BufferedWriter> blankHeader = ColumnSplitWriter.openWriterIfAllowed(
                "   ",
                "base",
                tempDir,
                Set.of("fr"),
                null,
                false,
                false
        );
        Optional<BufferedWriter> disallowed = ColumnSplitWriter.openWriterIfAllowed(
                "daily_min_FR",
                "base",
                tempDir,
                Set.of("be"),
                null,
                false,
                false
        );

        assertTrue(nullHeader.isEmpty());
        assertTrue(blankHeader.isEmpty());
        assertTrue(disallowed.isEmpty());
    }

    @Test
    void openWriterIfAllowed_ShouldUseHeaderOnlyFileNameWhenBaseNameFlagIsFalse() throws Exception {
        Optional<BufferedWriter> maybeWriter = ColumnSplitWriter.openWriterIfAllowed(
                "daily_min_fr",
                "ignored",
                tempDir,
                Set.of("daily_min_fr"),
                null,
                false,
                false
        );

        assertTrue(maybeWriter.isPresent());
        try (BufferedWriter writer = maybeWriter.get()) {
            writer.write("v");
        }

        assertTrue(Files.exists(tempDir.resolve("daily_min_fr.csv")));
    }
}

