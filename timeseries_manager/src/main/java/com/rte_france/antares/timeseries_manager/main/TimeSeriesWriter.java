/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.main;

import java.io.IOException;
import java.nio.file.Path;

public sealed interface TimeSeriesWriter<T> permits ArrowTSWriter, AvroTSWriter {
  /**
   * Writes a timeseries matrix into a path
   * @param matrix The matrix to write
   * @param filePath The filepath to write to
   * @throws IOException if any IO issues
   */
  void write(T matrix, Path filePath) throws IOException;

  /**
   * Gets the file extension of the currently used format
   * @return file extension as a String
   */
  String getDefaultFileExtension();
}
