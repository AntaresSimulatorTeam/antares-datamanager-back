package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

import java.util.Map;
import java.util.Objects;

public class MatrixReadSupport extends ReadSupport<Matrix> {
    private final int rowCount;

    public MatrixReadSupport(int rowCount) {
        Objects.checkIndex(rowCount, 8761);
        this.rowCount = rowCount;
    }

    @Override
    public ReadContext init(InitContext context) {
        var schema = context.getFileSchema();
        return new ReadContext(schema);
    }

    @Override
    public RecordMaterializer<Matrix> prepareForRead(
            org.apache.hadoop.conf.Configuration configuration,
            Map<String, String> keyValueMetaData,
            MessageType fileSchema,
            ReadContext readContext) {
        return new RecordMaterializer<Matrix>() {
            private final MatrixGroupConverter converter = new MatrixGroupConverter(fileSchema, rowCount);

            @Override
            public Matrix getCurrentRecord() {
                return converter.getMatrix();
            }

            @Override
            public GroupConverter getRootConverter() {
                return converter;
            }
        };
    }
}
