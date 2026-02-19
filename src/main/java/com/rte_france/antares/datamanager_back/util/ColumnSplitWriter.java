package com.rte_france.antares.datamanager_back.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Helper for creating writers for split time-series columns with consistent options.
 */
public final class ColumnSplitWriter {

    private ColumnSplitWriter() {}

    /**
     * Opens a writer for the given column header if it is allowed by the provided set.
     * Adds the output path to {@code generatedFiles} when created.
     *
     * @param header            column header (area/cluster name)
     * @param baseName          base name of the source file (without extension)
     * @param targetDir         directory where output files are created
     * @param allowed           set of allowed clusters (lower-cased)
     * @param generatedFiles    list where created file paths are collected (maybe null)
     * @param allowSuffixMatch  when true, accepts either the full header or its suffix after the last underscore
     * @return optional containing the opened BufferedWriter, or empty if not allowed
     * @throws IOException if I/O fails during writer creation
     */
    public static Optional<BufferedWriter> openWriterIfAllowed(String header,
                                                               String baseName,
                                                               Path targetDir,
                                                               Set<String> allowed,
                                                               List<Path> generatedFiles,
                                                               boolean allowSuffixMatch) throws IOException {
        Objects.requireNonNull(baseName, "baseName must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        if (header == null) return Optional.empty();
        String areaCluster = header.trim();
        if (areaCluster.isEmpty()) return Optional.empty();

        boolean isAllowed = false;
        if (allowed != null) {
            String keyLower = areaCluster.toLowerCase();
            if (allowSuffixMatch) {
                String suffixLower = keyLower.contains("_")
                        ? keyLower.substring(keyLower.lastIndexOf('_') + 1)
                        : keyLower;
                isAllowed = allowed.contains(keyLower) || allowed.contains(suffixLower);
            } else {
                isAllowed = allowed.contains(keyLower);
            }
        }

        if (!isAllowed) return Optional.empty();

        Path out = targetDir.resolve(baseName + "_" + areaCluster + ".csv");
        BufferedWriter bw = Files.newBufferedWriter(out,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        if (generatedFiles != null) {
            generatedFiles.add(out);
        }
        return Optional.of(bw);
    }
}
