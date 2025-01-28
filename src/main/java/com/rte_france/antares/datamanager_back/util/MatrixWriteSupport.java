/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class MatrixWriteSupport extends WriteSupport<MatrixRow> {

    private final Matrix matrix;
    private RecordConsumer consumer;

    MatrixWriteSupport(Matrix matrix) {
        this.matrix = matrix;
    }

    @Override
    public WriteContext init(org.apache.hadoop.conf.Configuration configuration) {
        var colTypes = matrix.getColumns().stream()
                .map(c -> doubleType(c.name()))
                .collect(Collectors.toList());

        var schema = new MessageType("matrix", colTypes);
        return new WriteContext(schema, Collections.emptyMap());
    }

    private static Type type(ColumnType type, String name) {
      return switch (type) {
        case INT -> intType(name);
        case FLOAT -> floatType(name);
        default -> throw new IllegalArgumentException("Unknown column type " + type);
      };
    }

    private static Type floatType(String name) {
        return Types.primitive(PrimitiveType.PrimitiveTypeName.FLOAT, Type.Repetition.REQUIRED)
                .named(name);
    }

    private static Type intType(String name) {
        return Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, Type.Repetition.REQUIRED)
                .named(name);
    }

    private static Type doubleType(String name) {
        return Types.primitive(PrimitiveType.PrimitiveTypeName.DOUBLE, Type.Repetition.REQUIRED)
                .named(name);
    }

    @Override
    public void prepareForWrite(RecordConsumer recordConsumer) {
        this.consumer = recordConsumer;
    }

    @Override
    public void write(MatrixRow matrixRow) {
        consumer.startMessage();
        var columnIndex = 0;
        for (var c : matrixRow.getMatrix().getColumns()) {
            consumer.startField(c.name(), columnIndex);
            consumer.addDouble(c.values()[matrixRow.getRow()]);
//            switch (c.getType()) {
//                case INT:
//                    consumer.addInteger(c.getIntValues()[matrixRow.getRow()]);
//                    break;
//                case FLOAT:
//                    consumer.addFloat(c.getFloatValues()[matrixRow.getRow()]);
//                    break;
//                default:
//                    throw new IllegalArgumentException("Unknown column type " + c.getType());
//            }
            consumer.endField(c.name(), columnIndex);
            columnIndex++;
        }
        consumer.endMessage();
    }
}
