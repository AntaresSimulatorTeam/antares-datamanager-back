package com.rte_france.antares.datamanager_back.util;

import java.io.IOException;
import java.nio.file.Path;

public class ArrowReader {
  Matrix read(Path path) throws IOException {

  }

  MatrixColumn read(Path path, String columnName) throws IOException {
    var matrix = read(path);
    return matrix.getColumns().stream()
            .filter(c -> c.name().equals(columnName))
            .findAny()
            .orElse(null);
  }

}
