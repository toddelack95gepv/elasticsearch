/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.esql.datasources;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.test.AbstractWireSerializingTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DeclaredReadSpecTests extends AbstractWireSerializingTestCase<DeclaredReadSpec> {

    public static DeclaredReadSpec randomDeclaredReadSpec() {
        // Always non-empty so a round-trip yields a distinct-but-equal instance (an all-empty spec collapses to the
        // NONE singleton, which the wire test-harness flags as reference-equal to the original). The empty/NONE
        // collapse is covered directly by testNoneIsEmpty.
        Map<String, String> renames = new HashMap<>();
        int count = between(1, 3);
        for (int i = 0; i < count; i++) {
            renames.put(randomAlphaOfLength(4) + i, randomAlphaOfLength(5));
        }
        String idPath = randomBoolean() ? randomAlphaOfLength(4) : null;
        return DeclaredReadSpec.of(renames, idPath);
    }

    @Override
    protected Writeable.Reader<DeclaredReadSpec> instanceReader() {
        return DeclaredReadSpec::readFrom;
    }

    @Override
    protected DeclaredReadSpec createTestInstance() {
        return randomDeclaredReadSpec();
    }

    @Override
    protected DeclaredReadSpec mutateInstance(DeclaredReadSpec instance) throws IOException {
        Map<String, String> renames = new HashMap<>(instance.renames());
        String idPath = instance.idPath();
        if (randomBoolean()) {
            renames.put(randomAlphaOfLength(6), randomAlphaOfLength(6));
        } else {
            idPath = randomValueOtherThan(idPath, () -> randomBoolean() ? randomAlphaOfLength(5) : null);
        }
        return DeclaredReadSpec.of(renames, idPath);
    }

    public void testNoneIsEmpty() {
        assertTrue(DeclaredReadSpec.NONE.isEmpty());
        assertTrue(DeclaredReadSpec.of(Map.of(), null).isEmpty());
        assertSame(DeclaredReadSpec.NONE, DeclaredReadSpec.of(Map.of(), null));
        assertFalse(DeclaredReadSpec.of(Map.of("a", "b"), null).isEmpty());
        assertFalse(DeclaredReadSpec.of(Map.of(), "id").isEmpty());
    }
}
