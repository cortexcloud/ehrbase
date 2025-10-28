package org.ehrbase.rest.openehr.dto;

import java.util.List;
import org.ehrbase.openehr.sdk.response.dto.MetaData;

/**
 * Response payload for the composition-specific AQL endpoint.
 */
public record CompositionQueryResponse(
        MetaData meta, String query, List<CompositionRow> compositions) {

    public record CompositionRow(
            String compositionUid,
            Integer version,
            String templateId,
            String format,
            Object content) {}
}