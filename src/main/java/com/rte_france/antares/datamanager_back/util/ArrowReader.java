package com.rte_france.antares.datamanager_back.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ArrowReader {

  public Matrix readMatrixFromTxt(Path filePath, ColumnType columnType) throws IOException {
    var lines = Files.readAllLines(filePath);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    var rowCount = lines.size();
    var columnCount = lines.getFirst().split("\\s+").length;

    if (columnType == ColumnType.FLOAT) {
      float[][] data = new float[rowCount][columnCount];
      for (var i = 0; i < rowCount; i++) {
        var values = lines.get(i).split("\\s+");
        for (var j = 0; j < columnCount; j++) {
          data[i][j] = Float.parseFloat(values[j]);
        }
      }
      return createMatrixFromData(data);
    } else if (columnType == ColumnType.INT) {
      var data = new int[rowCount][columnCount];
      for (var i = 0; i < rowCount; i++) {
        var values = lines.get(i).split("\\s+");
        for (var j = 0; j < columnCount; j++) {
          data[i][j] = Integer.parseInt(values[j]);
        }
      }
      return createMatrixFromData(data);
    } else {
      throw new IllegalArgumentException("Unsupported column type: " + columnType);
    }
  }

  private Matrix createMatrixFromData(float[][] data) {
    var rowCount = data.length;
    var columnCount = data[0].length;
    var columns = new ArrayList<MatrixColumn>(columnCount);

    for (var j = 0; j < columnCount; j++) {
      var columnData = new float[rowCount];
      for (var i = 0; i < rowCount; i++) {
        columnData[i] = data[i][j];
      }
      columns.add(new MatrixColumn("Column" + j, columnData));
    }

    return new Matrix(columns);
  }

  private Matrix createMatrixFromData(int[][] data) {
    var rowCount = data.length;
    var columnCount = data[0].length;
    var columns = new ArrayList<MatrixColumn>(columnCount);

    for (var j = 0; j < columnCount; j++) {
      var columnData = new int[rowCount];
      for (var i = 0; i < rowCount; i++) {
        columnData[i] = data[i][j];
      }
      columns.add(new MatrixColumn("Column" + j, columnData));
    }

    return new Matrix(columns);
  }
}