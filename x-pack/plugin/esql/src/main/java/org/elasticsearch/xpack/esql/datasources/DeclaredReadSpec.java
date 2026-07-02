/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.esql.datasources;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.core.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * The read-instructions a declared dataset mapping derives for the data node. Carried as a first-class field along the
 * resolution &rarr; plan &rarr; operator seam ({@code ExternalSourceResolution.ResolvedSource} &rarr;
 * {@link org.elasticsearch.xpack.esql.plan.logical.ExternalRelation}
 * &rarr; {@link org.elasticsearch.xpack.esql.plan.physical.ExternalSourceExec} &rarr;
 * {@link org.elasticsearch.xpack.esql.datasources.spi.SourceOperatorContext} &rarr; the operator factory), so a new
 * declared read-instruction is one typed field rather than another string key fenced and sniffed out of the config map.
 * <ul>
 *   <li>{@code renames} — the declared logical&rarr;physical column renames a {@code path} move produces. Consumed at
 *       the reader-facing boundary via {@link PhysicalNames} (projection + read schema physicalization) and by the
 *       pushdown planner rules. Empty when the mapping renames nothing.</li>
 *   <li>{@code idPath} — the declared {@code mappings._id.path} (a single logical column name), or {@code null}. When
 *       present the data node stamps {@code _id} from that column instead of the synthetic (file+row-position) identity
 *       ({@link VirtualColumnIterator}).</li>
 * </ul>
 * A plain {@link Writeable}: its wire gate lives on the enclosing plan nodes, which only read/write it when the
 * {@code dataset_declared_schema} transport version is supported (mirrors how {@code DatasetMapping.Mappings} is gated
 * by its container rather than self-gating).
 */
public record DeclaredReadSpec(Map<String, String> renames, @Nullable String idPath) implements Writeable {

    /** The empty spec — no renames, no declared {@code _id.path}. The default carried on every non-declared read. */
    public static final DeclaredReadSpec NONE = new DeclaredReadSpec(Map.of(), null);

    public DeclaredReadSpec {
        renames = renames != null ? Map.copyOf(renames) : Map.of();
    }

    /**
     * Canonical factory: collapses an all-empty spec to the {@link #NONE} singleton so an absent declaration always
     * serializes identically. The emptiness test is delegated to {@link #isEmpty()} (the single source of truth), so a
     * future field added to this record only has to update {@code isEmpty()} for the collapse to stay correct.
     */
    public static DeclaredReadSpec of(@Nullable Map<String, String> renames, @Nullable String idPath) {
        DeclaredReadSpec spec = new DeclaredReadSpec(renames, idPath);
        return spec.isEmpty() ? NONE : spec;
    }

    /** True when the mapping declared neither a rename nor an {@code _id.path} — nothing for the data node to apply. */
    public boolean isEmpty() {
        return renames.isEmpty() && idPath == null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(renames, StreamOutput::writeString, StreamOutput::writeString);
        out.writeOptionalString(idPath);
    }

    public static DeclaredReadSpec readFrom(StreamInput in) throws IOException {
        Map<String, String> renames = in.readMap(StreamInput::readString);
        String idPath = in.readOptionalString();
        return of(renames, idPath);
    }
}
