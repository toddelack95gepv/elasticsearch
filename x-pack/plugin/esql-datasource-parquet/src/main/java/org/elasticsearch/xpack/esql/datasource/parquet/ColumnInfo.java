/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.datasource.parquet;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.PrimitiveType;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.esql.core.type.DataType;

/**
 * Per-column metadata used by the Parquet column iterators. Holds the Parquet column descriptor
 * alongside the ESQL type mapping and definition/repetition levels.
 * <p>
 * {@code esqlType} is the type the decode paths PRODUCE — the planner/declared attribute type,
 * not necessarily the file's own — with {@code parquetType}/{@code logicalType} carrying the
 * physical side of the pair (see {@code DeclaredTypeCoercions} for which pairs are decodable).
 * {@code dateFormatter} carries the column's declared date parse pattern for the
 * string&rarr;datetime pair; {@code null} means the ISO default.
 * <p>
 * The synthetic {@code _rowPosition} column has no Parquet descriptor (it is materialised by the
 * iterator from the file's footer + per-row position bookkeeping); use {@link #rowPosition()} to
 * obtain the sentinel and {@link #isRowPosition()} to detect it in emit code paths.
 */
record ColumnInfo(
    ColumnDescriptor descriptor,
    PrimitiveType.PrimitiveTypeName parquetType,
    DataType esqlType,
    int maxDefLevel,
    int maxRepLevel,
    LogicalTypeAnnotation logicalType,
    @Nullable DateFormatter dateFormatter
) {
    /** Formatter-free convenience constructor for the (dominant) columns with no declared date format. */
    ColumnInfo(
        ColumnDescriptor descriptor,
        PrimitiveType.PrimitiveTypeName parquetType,
        DataType esqlType,
        int maxDefLevel,
        int maxRepLevel,
        LogicalTypeAnnotation logicalType
    ) {
        this(descriptor, parquetType, esqlType, maxDefLevel, maxRepLevel, logicalType, null);
    }

    /** Sentinel marker for the synthetic {@code _rowPosition} column slot. */
    private static final ColumnInfo ROW_POSITION = new ColumnInfo(null, null, DataType.LONG, 0, 0, null);

    /** Returns the {@code _rowPosition} sentinel; identity-comparable in iterator emit paths. */
    static ColumnInfo rowPosition() {
        return ROW_POSITION;
    }

    /** Whether this slot represents the synthetic {@code _rowPosition} column. */
    boolean isRowPosition() {
        return this == ROW_POSITION;
    }
}
