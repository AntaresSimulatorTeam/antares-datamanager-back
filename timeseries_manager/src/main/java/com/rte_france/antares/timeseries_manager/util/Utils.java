/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.util;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

public final class Utils {
  /**
   * Ensures a file is of a certain extension
   * @param path file path
   * @param getFileExt Method to get the correct file format
   * @return The same path or the fixed one
   */
  public static Path ensureExtension(Path path, Supplier<String> getFileExt) {
    Objects.requireNonNull(path);
    Objects.requireNonNull(getFileExt);

    var ext = "." + getFileExt.get();
    if (!path.toString().endsWith(ext)) {
      return path.resolveSibling(path.getFileName() + ext);
    }

    return path;
  }
}
