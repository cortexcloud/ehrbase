/*
 * Copyright (c) 2024 vitasystems GmbH.
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
package org.ehrbase.repository;

import java.util.List;
import org.ehrbase.jooq.pg.tables.records.AuditDetailsRecord;
import org.ehrbase.jooq.pg.tables.records.ContributionRecord;

/**
 * Aggregates the records that would be produced when committing a new VERSION entry.
 *
 * @param versionData the generated version and data records
 * @param contributionRecord the contribution metadata record
 * @param auditRecords ordered list of audit detail records
 */
public record VersionCommitPreview(
        VersionDataDbRecord versionData,
        ContributionRecord contributionRecord,
        List<AuditDetailsRecord> auditRecords) {}
