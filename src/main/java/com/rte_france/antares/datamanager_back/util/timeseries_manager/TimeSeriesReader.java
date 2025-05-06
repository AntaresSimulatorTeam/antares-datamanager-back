/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util.timeseries_manager;

import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.types.pojo.Field;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for reading time series data from Arrow file format
 */
public final class TimeSeriesReader {
  private static final int ROW_COUNT = 8760;

  /**
   * Reads a time series matrix from an Arrow file.
   *
   * @param filePath the path to the Arrow file
   * @return the time series matrix
   * @throws IOException if an I/O error occurs
   */
  public TimeSeriesMatrix read(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var channel = Files.newByteChannel(filePath);
         var allocator = new RootAllocator();
         var reader = new ArrowFileReader(channel, allocator)) {

      reader.loadNextBatch();
      var root = reader.getVectorSchemaRoot();
      var fields = root.getSchema().getFields();

      var columns = new ArrayList<TimeSeriesMatrixColumn>();
      fillMatrixColumns(fields, root, columns);

      return new TimeSeriesMatrix(columns);
    }
  }

  private static void fillMatrixColumns(List<Field> fields, VectorSchemaRoot root, ArrayList<TimeSeriesMatrixColumn> columns) {
    for (var field : fields) {
      var vector = root.getVector(field.getName());
      var values = new double[vector.getValueCount()];
      for (var i = 0; i < vector.getValueCount(); i++) {
        if (vector instanceof Float8Vector f) {
          values[i] = f.get(i);
        } else {
          throw new IllegalStateException();
        }
      }
      columns.add(new TimeSeriesMatrixColumn(field.getName(), values));
    }
  }

  public TimeSeriesMatrix readFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var lines = Files.lines(filePath)) {
      var iterator = lines.iterator();
      if (!iterator.hasNext()) {
        throw TechnicalException.builder().message("File is empty").build();
      }

      var firstLine = iterator.next();
      var columnCount = firstLine.split("\\s+").length;
      var data = new double[columnCount][ROW_COUNT];

      fillDataList(firstLine, iterator, data);

      var columns = new ArrayList<TimeSeriesMatrixColumn>(data.length);
      for (int j = 0; j < data.length; j++) {
        columns.add(new TimeSeriesMatrixColumn("Column" + j, data[j]));
      }

      return new TimeSeriesMatrix(columns);
    }
  }

  private static void fillDataList(String firstLine, Iterator<String> iterator, double[][] data) {
    var rowIndex = 0;
    while (iterator.hasNext()) {
      String[] values;
      if (rowIndex == 0) {
        values = firstLine.split("\\s+");
      } else {
        values = iterator.next().split("\\s+");
      }
      for (var j = 0; j < values.length; j++) {
        data[j][rowIndex] = Double.parseDouble(values[j]);
      }
      rowIndex++;
    }
  }
}