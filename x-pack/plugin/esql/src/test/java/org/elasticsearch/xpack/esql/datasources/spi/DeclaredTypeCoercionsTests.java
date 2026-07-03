/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.datasources.spi;

import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.esql.core.type.DataType;

import java.util.Set;

import static org.hamcrest.Matchers.equalTo;

/**
 * Pins the declared-type coercion matrix ({@link DeclaredTypeCoercions#supports}) and the shared
 * string&rarr;datetime conversion ({@link DeclaredTypeCoercions#parseDatetimeMillis}). The matrix is the single source
 * of truth every external reader and the resolver consult, so any drift — a new supported pair a reader doesn't
 * implement, or a dropped pair — must fail here rather than surface as a silent-null or a deep engine error.
 */
public class DeclaredTypeCoercionsTests extends ESTestCase {

    /**
     * Exactly the documented supported pairs return true and everything else false. A curated type set (the declarable
     * types plus a few non-declarable ones as negative controls) is walked exhaustively; encode the expectation
     * independently of the implementation so a change to either side is caught.
     */
    public void testSupportedMatrixPinned() {
        Set<DataType> types = Set.of(
            DataType.KEYWORD,
            DataType.TEXT,
            DataType.INTEGER,
            DataType.LONG,
            DataType.DOUBLE,
            DataType.BOOLEAN,
            DataType.DATETIME,
            DataType.DATE_NANOS,
            DataType.IP,
            DataType.UNSIGNED_LONG
        );
        for (DataType from : types) {
            for (DataType to : types) {
                boolean expected = expectedSupported(from, to);
                assertThat(
                    "supports(" + from.typeName() + ", " + to.typeName() + ")",
                    DeclaredTypeCoercions.supports(from, to),
                    equalTo(expected)
                );
            }
        }
    }

    /** The expected matrix, written out longhand so it is not a copy of the implementation. */
    private static boolean expectedSupported(DataType from, DataType to) {
        if (from == to) {
            return true; // identity is always a no-op coercion
        }
        if (from == DataType.INTEGER && to == DataType.LONG) {
            return true; // lossless widen
        }
        if (from == DataType.LONG && to == DataType.DATETIME) {
            return true; // reinterpret epoch millis
        }
        boolean fromString = from == DataType.KEYWORD || from == DataType.TEXT;
        if (fromString && (to == DataType.KEYWORD || to == DataType.TEXT)) {
            return true; // string flavor relabel
        }
        return fromString && to == DataType.DATETIME; // parse
    }

    /** A few excluded pairs called out explicitly, so the reason each is unsupported is documented as a test. */
    public void testDeliberateExclusions() {
        assertFalse("narrowing long->integer is lossy", DeclaredTypeCoercions.supports(DataType.LONG, DataType.INTEGER));
        assertFalse("long->double is lossy", DeclaredTypeCoercions.supports(DataType.LONG, DataType.DOUBLE));
        assertFalse("integer has no 32-bit epoch encoding", DeclaredTypeCoercions.supports(DataType.INTEGER, DataType.DATETIME));
        assertFalse("unsigned_long is sign-flip encoded", DeclaredTypeCoercions.supports(DataType.UNSIGNED_LONG, DataType.DATETIME));
        assertFalse("date_nanos is not a declarable type yet", DeclaredTypeCoercions.supports(DataType.LONG, DataType.DATE_NANOS));
        assertFalse("no datetime->long reverse", DeclaredTypeCoercions.supports(DataType.DATETIME, DataType.LONG));
    }

    public void testParseDatetimeMillisDeclaredFormatZoneAware() {
        // Same token + format the CSV/NDJSON reader tests pin: the -0700 offset is honored, landing at 20:55:36Z.
        DateFormatter fmt = DateFormatter.forPattern("dd/MMM/yyyy:HH:mm:ss Z");
        assertThat(DeclaredTypeCoercions.parseDatetimeMillis("10/Oct/2000:13:55:36 -0700", fmt), equalTo(971211336000L));
    }

    public void testParseDatetimeMillisIsoDefaultWhenNoFormat() {
        // No declared format falls back to strict_date_optional_time (the TO_DATETIME default).
        assertThat(DeclaredTypeCoercions.parseDatetimeMillis("2000-10-10T20:55:36Z", null), equalTo(971211336000L));
    }

    public void testParseDatetimeMillisThrowsOnGarbage() {
        expectThrows(IllegalArgumentException.class, () -> DeclaredTypeCoercions.parseDatetimeMillis("not-a-date", null));
    }
}
