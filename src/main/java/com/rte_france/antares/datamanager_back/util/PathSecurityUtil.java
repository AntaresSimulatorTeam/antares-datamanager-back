package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
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
}