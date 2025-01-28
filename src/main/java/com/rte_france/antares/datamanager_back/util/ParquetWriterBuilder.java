/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;

import java.nio.file.Path;

/**
 * Implementation of parquet writer builder for our use case.
 *
 * @author Sylvain Leclerc <sylvain.leclerc@rte-france.com>
 */
class ParquetWriterBuilder extends ParquetWriter.Builder<MatrixRow, ParquetWriterBuilder> {

    private final Matrix matrix;

    ParquetWriterBuilder(Matrix matrix, Path path) {
        super(new org.apache.hadoop.fs.Path(path.toString()));
        this.matrix = matrix;
    }

    @Override
    protected ParquetWriterBuilder self() {
        return this;
    }

    @Override
    protected WriteSupport<MatrixRow> getWriteSupport(org.apache.hadoop.conf.Configuration conf) {
        return new MatrixWriteSupport(matrix);
    }
}
