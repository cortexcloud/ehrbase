/*
 * Copyright (c) 2025 vitasystems GmbH.
 *
 * This file is part of project EHRbase
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.rest.openehr.dto;

import java.util.List;
import java.util.UUID;
import org.ehrbase.openehr.sdk.response.dto.MetaData;

/**
 * Response payload for the composition-specific AQL endpoint.
 */
public record CompositionQueryResponse(MetaData meta, String query, List<CompositionRow> compositions) {

    public record CompositionRow(
            UUID ehrId, String compositionUid, Integer version, String templateId, String format, Object content) {}
}
