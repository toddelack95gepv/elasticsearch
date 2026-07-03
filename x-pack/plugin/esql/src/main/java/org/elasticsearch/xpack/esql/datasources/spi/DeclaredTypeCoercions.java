/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.datasources.spi;

import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.esql.core.type.DataType;
import org.elasticsearch.xpack.esql.type.EsqlDataTypeConverter;

/**
 * The single source of truth for declared-type coercion on external datasets: which
 * (physical file type &rarr; declared type) pairs an external reader coerces at read time, and the
 * one scalar conversion the fallible pair (string &rarr; date) uses everywhere.
 *
 * <h2>The one concept</h2>
 * A dataset mapping may declare a column type that differs from the type physically in the file
 * (Hive/Trino-style: the declaration is the table schema, readers coerce toward it). A
 * {@code (physicalType, declaredType)} pair is either castable or it is not — {@link #supports}
 * is that predicate. If castable, the reader coerces <b>as it reads</b>; if not, resolution
 * rejects the declaration with an actionable error. There is deliberately no third state: a
 * declared type that cannot be produced from the physical column must never silently read as
 * {@code null}.
 *
 * <h2>Where the check runs — same predicate, different timing</h2>
 * <ul>
 *   <li><b>Columnar formats</b> (Parquet, ORC) know the physical type upfront from the file
 *       footer, so {@code ExternalSourceResolver} runs {@link #supports} once at resolution and
 *       fails fast; the readers then coerce natively-decoded values in their decode loops (and
 *       re-check per file against this same predicate, since a multi-file glob can drift from the
 *       anchor footer).</li>
 *   <li><b>Text formats</b> (CSV/TSV, NDJSON) have no physical schema — every value is a string,
 *       so the parse into the declared type <i>is</i> the coercion and a bad token fails per
 *       value. Their declared date {@code format} parse goes through the same
 *       {@link #parseDatetimeMillis} scalar as the columnar string&rarr;date coercion, so the
 *       same token with the same declared format produces the same instant regardless of which
 *       format carried it.</li>
 * </ul>
 *
 * <h2>The supported matrix</h2>
 * Every entry below is implemented natively by BOTH columnar readers' decode paths; adding a row
 * here without teaching a reader to decode it re-opens the silent-null trap, so the matrix is
 * pinned by {@code DeclaredTypeCoercionsTests} and by the reader ITs.
 * <table border="1">
 *   <caption>physical &rarr; declared coercions</caption>
 *   <tr><th>physical (file)</th><th>declared</th><th>semantics</th></tr>
 *   <tr><td>{@code integer}</td><td>{@code long}</td><td>lossless widen (int32 &sube; int64)</td></tr>
 *   <tr><td>{@code long}</td><td>{@code datetime}</td><td>reinterpret as epoch milliseconds (same 64-bit payload)</td></tr>
 *   <tr><td>{@code keyword}/{@code text}</td><td>{@code datetime}</td>
 *       <td>parse via {@link #parseDatetimeMillis}: the column's declared {@code format} when present,
 *           else the ISO {@code TO_DATETIME} default</td></tr>
 *   <tr><td>{@code keyword}</td><td>{@code text}</td><td>same bytes, different string flavor</td></tr>
 *   <tr><td>{@code text}</td><td>{@code keyword}</td><td>same bytes, different string flavor</td></tr>
 * </table>
 *
 * <p>Deliberate exclusions: numeric narrowing ({@code long → integer}) and {@code long → double}
 * are lossy; {@code integer → datetime} has no plausible epoch encoding in 32 bits;
 * {@code unsigned_long} is stored sign-flip-encoded so reinterprets are wrong by construction;
 * {@code date_nanos} is not a declarable type yet (see {@code DeclaredSchemaValidator}), so no
 * pair targets it — when it becomes declarable, {@code long → date_nanos} (reinterpret as epoch
 * nanos) and {@code keyword → date_nanos} belong here.
 *
 * <p>TODO: the columnar-vs-text classification this predicate pairs with
 * ({@code ExternalSourceResolver.FILE_TYPED_FORMATS}) has a standing TODO to move onto the
 * {@code FormatReader} SPI as a capability; if that happens, per-format coercion support belongs
 * on the same capability surface.
 */
public final class DeclaredTypeCoercions {

    private DeclaredTypeCoercions() {}

    /**
     * Whether an external reader can coerce a value physically stored as {@code from} into the
     * declared type {@code to} at read time. Equal types trivially return {@code true}. This is
     * THE castability predicate — resolution-time rejects and reader-side per-file validation
     * must both consult it so they cannot drift.
     */
    public static boolean supports(DataType from, DataType to) {
        if (from == to) {
            return true;
        }
        if (from == DataType.INTEGER && to == DataType.LONG) {
            return true;
        }
        if (from == DataType.LONG && to == DataType.DATETIME) {
            return true;
        }
        boolean fromString = from == DataType.KEYWORD || from == DataType.TEXT;
        if (fromString && (to == DataType.KEYWORD || to == DataType.TEXT)) {
            return true;
        }
        if (fromString && to == DataType.DATETIME) {
            return true;
        }
        return false;
    }

    /**
     * The one string&rarr;datetime conversion for declared date columns, shared by every reader:
     * the text readers' declared-{@code format} parse (CSV/TSV, NDJSON) and the columnar readers'
     * string&rarr;datetime coercion (Parquet, ORC) all route here, so identical input bytes with
     * an identical declared format produce the identical epoch instant regardless of file format.
     * <p>
     * With a declared format the parse is strict and zone-aware ({@link DateFormatter#parseMillis}
     * defaults a missing zone to UTC); without one it falls back to
     * {@link EsqlDataTypeConverter#dateTimeToLong(String)} — the same ISO
     * ({@code strict_date_optional_time}) semantics as {@code TO_DATETIME}, which is the right
     * mental model for a declared cast. Throws {@link IllegalArgumentException} on an unparseable
     * value; callers decide whether that is a per-row error (text error policy) or a hard read
     * failure (columnar).
     */
    public static long parseDatetimeMillis(String value, @Nullable DateFormatter declaredFormat) {
        return EsqlDataTypeConverter.dateTimeToLong(value, declaredFormat);
    }
}
