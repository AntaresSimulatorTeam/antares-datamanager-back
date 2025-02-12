/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.main;

import java.io.IOException;
import java.nio.file.Path;

public sealed interface TimeSeriesReader<T> permits ArrowTSReader, AvroTSReader {
  /**
   * Reads a matrix in one of the data formats from a filepath
   * @param filePath the path to the file
   * @return The matrix in a java class
   * @throws IOException if it runs into any IO issue
   */
  T read(Path filePath) throws IOException;

  /**
   * Reads a matrix from a txt file into a java class
   * @param filePath the path to the txt file
   * @return The matrix in a java class
   * @throws IOException if it runs into any IO issue
   */
  T readFromTxt(Path filePath) throws IOException;
}
