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
package org.ehrbase.rest.openehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.support.identification.ObjectVersionId;
import java.util.Map;
import java.util.UUID;
import org.ehrbase.api.exception.PreconditionFailedException;
import org.ehrbase.api.service.CompositionService;
import org.ehrbase.api.service.SystemService;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.CompositionFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class OpenehrCompositionControllerTest {

    private final CompositionService compositionService = mock();
    private final SystemService systemService = mock();
    private final OpenehrCompositionController controller =
            new OpenehrCompositionController(compositionService, systemService);

    @BeforeEach
    void resetMocks() {
        reset(compositionService, systemService);
    }

    @Test
    void previewUpdatedCompositionDelegatesToService() {
        UUID ehrId = UUID.randomUUID();
        UUID versionedObjectId = UUID.randomUUID();
        String ifMatch = versionedObjectId + "::system::1";
        String body = "{}";
        Composition composition = new Composition();

        doReturn(composition).when(compositionService).buildComposition(body, CompositionFormat.JSON, null);

        Map<String, Object> payload = Map.of("comp_version", Map.of());
        doReturn(payload)
                .when(compositionService)
                .previewUpdatedCompDataRecords(eq(ehrId), any(ObjectVersionId.class), eq(composition));

        ResponseEntity<Map<String, Object>> response = controller.previewUpdatedComposition(
                MediaType.APPLICATION_JSON_VALUE,
                null,
                ifMatch,
                ehrId.toString(),
                versionedObjectId.toString(),
                null,
                CompositionFormat.JSON.name(),
                body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(payload);

        verify(compositionService).buildComposition(body, CompositionFormat.JSON, null);

        ArgumentCaptor<ObjectVersionId> captor = ArgumentCaptor.forClass(ObjectVersionId.class);
        verify(compositionService).previewUpdatedCompDataRecords(eq(ehrId), captor.capture(), eq(composition));
        assertThat(captor.getValue().toString()).isEqualTo(ifMatch);
    }

    @Test
    void previewUpdatedCompositionRejectsMismatchedUid() {
        UUID ehrId = UUID.randomUUID();
        UUID versionedObjectId = UUID.randomUUID();
        String ifMatch = versionedObjectId + "::system::1";
        String body = "{}";
        Composition composition = new Composition();
        composition.setUid(new ObjectVersionId(UUID.randomUUID() + "::system::1"));

        doReturn(composition).when(compositionService).buildComposition(body, CompositionFormat.JSON, null);

        assertThrows(
                PreconditionFailedException.class,
                () -> controller.previewUpdatedComposition(
                        MediaType.APPLICATION_JSON_VALUE,
                        null,
                        ifMatch,
                        ehrId.toString(),
                        versionedObjectId.toString(),
                        null,
                        CompositionFormat.JSON.name(),
                        body));

        verify(compositionService, never()).previewUpdatedCompDataRecords(any(), any(), any());
    }
}
