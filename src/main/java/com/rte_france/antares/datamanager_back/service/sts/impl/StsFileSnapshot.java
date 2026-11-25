package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class StsFileSnapshot implements AutoCloseable {
    static final String PREFIX = "sts-v1:";

    private final Path root;
    private final Path temporaryDirectory;
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<Path, List<Path>> series = new HashMap<>();

    private record Entry(Path copy) {}

    StsFileSnapshot(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.temporaryDirectory = Files.createTempDirectory("sts-snapshot-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    Path capture(Path source, boolean required) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) {
            throw invalid("STS file is outside the configured directory: {0}", absolute.toString());
        }
        String key = root.relativize(absolute).toString().replace('\\', '/');
        Entry previous = entries.get(key);
        if (previous != null) {
            if (required && previous.copy() == null) {
                throw invalid("Required STS file not found: {0}", absolute.toString());
            }
            return previous.copy();
        }
        if (Files.notExists(absolute)) {
            if (required) throw invalid("Required STS file not found: {0}", absolute.toString());
            entries.put(key, new Entry(null));
            return null;
        }
        if (!Files.isRegularFile(absolute) || !absolute.toRealPath().startsWith(root.toRealPath())) {
            throw invalid("Invalid STS file: {0}", absolute.toString());
        }
        Path copy = temporaryDirectory.resolve(root.relativize(absolute));
        Files.createDirectories(copy.getParent());
        Files.copy(absolute, copy, StandardCopyOption.COPY_ATTRIBUTES);
        if (!hash(copy).equals(hash(absolute))) {
            throw invalid("STS file changed while being captured: {0}", absolute.toString());
        }
        entries.put(key, new Entry(copy));
        return copy;
    }

    List<Path> captureSeries(StStorageEntity storage) throws IOException {
        if (storage.getTsPath() == null || storage.getTsPath().isBlank()) return List.of();
        Path directory = Path.of(storage.getTsPath()).toAbsolutePath().normalize();
        if (series.containsKey(directory)) return series.get(directory);
        List<Path> result = new ArrayList<>();
        for (StsTsFile file : StsTsFile.requiredFiles()) {
            Path original = resolveSeriesFile(directory, file.fileName());
            result.add(capture(original, true));
        }
        for (StsTsFile file : StsTsFile.optionalFiles()) {
            Path original = resolveSeriesFile(directory, file.fileName());
            Path copy = capture(original, false);
            if (copy != null) result.add(copy);
        }
        series.put(directory, List.copyOf(result));
        return series.get(directory);
    }

    private Path resolveSeriesFile(Path directory, String name) throws IOException {
        if (!directory.startsWith(root)) throw invalid("STS file is outside the configured directory: {0}", directory.toString());
        if (Files.notExists(directory)) return directory.resolve(name);
        if (!directory.toRealPath().startsWith(root.toRealPath())) {
            throw invalid("STS directory is outside the configured directory: {0}", directory.toString());
        }
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> matches = paths.filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).toList();
            if (matches.size() > 1) throw invalid("Ambiguous STS series file: {0}", directory.resolve(name).toString());
            return matches.isEmpty() ? directory.resolve(name) : matches.getFirst();
        }
    }

    Path copyOf(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        String key = root.relativize(absolute).toString().replace('\\', '/');
        Entry entry = entries.get(key);
        if (entry == null || entry.copy() == null) throw invalid("STS file was not captured: {0}", absolute.toString());
        return entry.copy();
    }

    String checksum() throws IOException {
        MessageDigest digest = newDigest();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            update(digest, entry.getKey());
            update(digest, entry.getValue().copy() == null ? "missing" : hash(entry.getValue().copy()));
        }
        return PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static BusinessException invalid(String message, String path) {
        return BusinessException.builder().message(message).errorMessageArguments(List.of(path))
                .httpStatus(HttpStatus.BAD_REQUEST).build();
    }

    @Override
    public void close() throws IOException {
        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
