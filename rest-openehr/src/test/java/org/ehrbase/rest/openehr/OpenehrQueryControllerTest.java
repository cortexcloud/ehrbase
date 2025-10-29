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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.support.identification.ObjectVersionId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ehrbase.api.dto.AqlQueryContext;
import org.ehrbase.api.dto.AqlQueryRequest;
import org.ehrbase.api.exception.InvalidApiParameterException;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.service.AqlQueryService;
import org.ehrbase.api.service.CompositionService;
import org.ehrbase.api.service.StoredQueryService;
import org.ehrbase.openehr.sdk.response.dto.MetaData;
import org.ehrbase.openehr.sdk.response.dto.QueryResponseData;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.CompositionFormat;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryDefinitionResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.QueryResultDto;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.StructuredString;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.StructuredStringFormat;
import org.ehrbase.rest.util.OpenEhrQueryRequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.ehrbase.openehr.sdk.response.dto.ehrscape.query.ResultHolder;
import org.ehrbase.rest.openehr.dto.CompositionQueryResponse;

public class OpenehrQueryControllerTest {

    public static final String SAMPLE_QUERY = "SELECT s FROM EHR_STATUS s";
    public static final Map<String, Object> SAMPLE_PARAMETER_MAP = Map.of("key", "value");
    public static final MetaData SAMPLE_META_DATA = new MetaData();

    private final AqlQueryService mockAqlQueryService = mock();

    private final StoredQueryService mockStoredQueryService = mock();

    private final CompositionService mockCompositionService = mock();

    private final AqlQueryContext mockQueryContext = mock();

    private final OpenehrQueryController spyController = spy(new OpenehrQueryController(
            mockAqlQueryService,
            mockStoredQueryService,
            mockQueryContext,
            mockCompositionService,
            new ObjectMapper()));

    @BeforeEach
    void setUp() {
        Mockito.reset(mockAqlQueryService, mockStoredQueryService, mockCompositionService, mockQueryContext, spyController);
        doReturn("https://openehr.test.query.controller.com/rest")
                .when(spyController)
                .getContextPath();
    }

    @AfterEach
    void tearDown() {
        // ensure the context is clean after each test
        RequestContextHolder.resetRequestAttributes();
    }

    private OpenehrQueryController controller() {
        doReturn(SAMPLE_META_DATA).when(mockQueryContext).createMetaData(any());
        doReturn(new QueryResultDto()).when(mockAqlQueryService).query(any());
        return spyController;
    }

    private OpenehrQueryController controllerStoredQuery() {
        QueryDefinitionResultDto queryDefinitionResultDto = new QueryDefinitionResultDto();
        queryDefinitionResultDto.setQueryText(SAMPLE_QUERY);
        queryDefinitionResultDto.setQualifiedName("test_query");
        doReturn(queryDefinitionResultDto).when(mockStoredQueryService).retrieveStoredQuery(any(), any());
        return controller();
    }

    @ParameterizedTest
    @CsvSource({",", "10,0", "0,25"})
    void executeAddHocQueryUsingGET(Integer fetch, Integer offset) {
        ResponseEntity<QueryResponseData> response = controller()
                .executeAdHocQuery(SAMPLE_QUERY, offset, fetch, SAMPLE_PARAMETER_MAP, MediaType.APPLICATION_JSON_VALUE);
        assertMetaData(response);
        assertAqlQueryRequest(
                AqlQueryRequest.prepare(SAMPLE_QUERY, SAMPLE_PARAMETER_MAP, toLong(fetch), toLong(offset)));
    }

    private Long toLong(Object obj) {
        return switch (obj) {
            case null -> null;
            case Integer i -> i.longValue();
            case String s -> Long.parseLong(s);
            default ->
                throw new IllegalArgumentException(
                        "unexpected type " + obj.getClass().getName());
        };
    }

    @ParameterizedTest
    @CsvSource({",", "10,0", "0,25", "'1','2'"})
    void executeAddHocQueryUsingPOST(Object fetch, Object offset) {
        ResponseEntity<QueryResponseData> response = controller()
                .executeAdHocQuery(
                        sampleAqlQuery(fetch, offset),
                        MediaType.APPLICATION_JSON_VALUE,
                        MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        assertMetaData(response);
        assertAqlQueryRequest(
                AqlQueryRequest.prepare(SAMPLE_QUERY, SAMPLE_PARAMETER_MAP, toLong(fetch), toLong(offset)));
    }

    private static Map<String, Object> sampleAqlQuery(Object fetch, Object offset) {
        Map<String, Object> map = sampleAqlJson(fetch, offset);
        map.put("q", SAMPLE_QUERY);
        return map;
    }

    private static Map<String, Object> sampleAqlJson(Object fetch, Object offset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query_parameters", SAMPLE_PARAMETER_MAP);
        if (fetch != null) {
            map.put("fetch", fetch);
        }
        if (offset != null) {
            map.put("offset", offset);
        }
        return map;
    }

    @Test
    void executeAddHocQueryUsingPOSTWithFetchInvalid() {

        String message = assertThrowsExactly(InvalidApiParameterException.class, () -> controller()
                        .executeAdHocQuery(
                                sampleAqlQuery("invalid", null),
                                MediaType.APPLICATION_JSON_VALUE,
                                MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .getMessage();
        assertEquals("invalid 'fetch' value 'invalid'", message);
    }

    @Test
    void executeAddHocQueryUsingPOSTWithOffsetInvalid() {
        String message = assertThrowsExactly(InvalidApiParameterException.class, () -> controller()
                        .executeAdHocQuery(
                                sampleAqlQuery(null, "invalid"),
                                MediaType.APPLICATION_JSON_VALUE,
                                MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .getMessage();
        assertEquals("invalid 'offset' value 'invalid'", message);
    }

    @ParameterizedTest
    @CsvSource({",", "10,0", "0,25"})
    void executeStoredQueryUsingGET(Integer fetch, Integer offset) {
        ResponseEntity<QueryResponseData> response = controllerStoredQuery()
                .executeStoredQuery(
                        "my_qualified_query",
                        "v1.0.0",
                        offset,
                        fetch,
                        SAMPLE_PARAMETER_MAP,
                        MediaType.APPLICATION_JSON_VALUE);
        assertMetaData(response);
        assertAqlQueryRequest(
                AqlQueryRequest.prepare(SAMPLE_QUERY, SAMPLE_PARAMETER_MAP, toLong(fetch), toLong(offset)));
    }

    @Test
    void executeStoredQueryUsingGETNoExist() {

        OpenehrQueryController openehrQueryController = controllerStoredQuery();
        doThrow(new ObjectNotFoundException(
                        "QUERY", "Stored query 'does_not_exist' with version 'v1.0.0' does not exist"))
                .when(mockStoredQueryService)
                .retrieveStoredQuery(any(), any());
        String message = assertThrows(
                        ObjectNotFoundException.class,
                        () -> openehrQueryController.executeStoredQuery(
                                "does_not_exist",
                                "v1.0.0",
                                null,
                                null,
                                SAMPLE_PARAMETER_MAP,
                                MediaType.APPLICATION_JSON_VALUE))
                .getMessage();
        assertEquals(message, "Stored query 'does_not_exist' with version 'v1.0.0' does not exist");
    }

    @ParameterizedTest
    @CsvSource({",", "10,0", "0,25", "'1','2'"})
    void executeStoredQueryUsingPOST(Object fetch, Object offset) {
        ResponseEntity<QueryResponseData> response = controllerStoredQuery()
                .executeStoredQuery(
                        "my_qualified_query",
                        "v1.0.0",
                        MediaType.APPLICATION_JSON_VALUE,
                        MediaType.APPLICATION_JSON_VALUE,
                        sampleAqlJson(fetch, offset));
        assertMetaData(response);
        assertAqlQueryRequest(
                AqlQueryRequest.prepare(SAMPLE_QUERY, SAMPLE_PARAMETER_MAP, toLong(fetch), toLong(offset)));
    }

    @Test
    void executeStoredQueryUsingPOSTWithFetchInvalid() {

        String message = assertThrowsExactly(InvalidApiParameterException.class, () -> controllerStoredQuery()
                        .executeStoredQuery(
                                "my_qualified_query",
                                "v1.0.0",
                                MediaType.APPLICATION_JSON_VALUE,
                                MediaType.APPLICATION_JSON_VALUE,
                                sampleAqlJson("invalid", null)))
                .getMessage();
        assertEquals("invalid 'fetch' value 'invalid'", message);
    }

    @Test
    void executeStoredQueryUsingPOSTWithOffsetInvalid() {

        String message = assertThrowsExactly(InvalidApiParameterException.class, () -> controllerStoredQuery()
                        .executeStoredQuery(
                                "my_qualified_query",
                                "v1.0.0",
                                MediaType.APPLICATION_JSON_VALUE,
                                MediaType.APPLICATION_JSON_VALUE,
                                sampleAqlJson(null, "invalid")))
                .getMessage();
        assertEquals("invalid 'offset' value 'invalid'", message);
    }

    @Test
    void executeCompositionQueryReturnsSerializedResult() {
        OpenehrQueryController controller = controller();

        UUID compositionId = UUID.randomUUID();
        ObjectVersionId versionId = new ObjectVersionId(compositionId + "::ehrbase::1");
        String alias = "comp";
        String templateId = "test-template";

        Composition composition = mock(Composition.class);
        doReturn(versionId).when(composition).getUid();
        doReturn(templateId).when(mockCompositionService).retrieveTemplateId(compositionId);

        ResultHolder holder = new ResultHolder();
        holder.putResult(alias, composition);

        QueryResultDto dto = new QueryResultDto();
        dto.setResultSet(List.of(holder));
        doReturn(dto).when(mockAqlQueryService).query(any());

        StructuredString serialized = new StructuredString("{\"serialized\":true}", StructuredStringFormat.JSON);
        doReturn(serialized).when(mockCompositionService).serialize(any(), any());

        Map<String, Object> requestBody = Map.of("q", "SELECT c as %s FROM COMPOSITION c".formatted(alias));

        ResponseEntity<CompositionQueryResponse> response = controller.executeCompositionQuery(
                requestBody, null, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE);

        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        CompositionQueryResponse body = response.getBody();
        assertNotNull(body);
        assertSame(SAMPLE_META_DATA, body.meta());
        assertEquals(requestBody.get("q"), body.query());
        assertThat(body.compositions()).hasSize(1);

        CompositionQueryResponse.CompositionRow row = body.compositions().getFirst();
        assertEquals(versionId.getValue(), row.compositionUid());
        assertEquals(Integer.valueOf(1), row.version());
        assertEquals(templateId, row.templateId());
        assertEquals("JSON", row.format());
        assertThat(row.content()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) row.content();
        assertThat(content).containsEntry("serialized", Boolean.TRUE);

        verify(mockCompositionService).serialize(any(), eq(CompositionFormat.JSON));
        verify(mockCompositionService).retrieveTemplateId(compositionId);
    }

        @Test
        void executeCompositionQueryReturnsXmlContentUnchanged() {
                OpenehrQueryController controller = controller();

                UUID compositionId = UUID.randomUUID();
                ObjectVersionId versionId = new ObjectVersionId(compositionId + "::ehrbase::1");
                String templateId = "test-template";

                Composition composition = mock(Composition.class);
                doReturn(versionId).when(composition).getUid();
                doReturn(templateId).when(mockCompositionService).retrieveTemplateId(compositionId);

                ResultHolder holder = new ResultHolder();
                holder.putResult("alias", composition);

                QueryResultDto dto = new QueryResultDto();
                dto.setResultSet(List.of(holder));
                doReturn(dto).when(mockAqlQueryService).query(any());

                StructuredString serialized = new StructuredString("<composition/>", StructuredStringFormat.XML);
                doReturn(serialized).when(mockCompositionService).serialize(any(), any());

                Map<String, Object> requestBody = Map.of("q", "SELECT c FROM COMPOSITION c");

                ResponseEntity<CompositionQueryResponse> response = controller.executeCompositionQuery(
                                requestBody, null, MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE);

                CompositionQueryResponse body = response.getBody();
                assertNotNull(body);
                assertThat(body.compositions()).singleElement().satisfies(row -> {
                        assertThat(row.content()).isEqualTo(serialized.getValue());
                        assertThat(row.format()).isEqualTo("XML");
                });
        }

    @Test
    void executeCompositionQueryRejectsNonCompositionResult() {
        OpenehrQueryController controller = controller();

        ResultHolder holder = new ResultHolder();
        holder.putResult("alias", "not a composition");

        QueryResultDto dto = new QueryResultDto();
        dto.setResultSet(List.of(holder));
        doReturn(dto).when(mockAqlQueryService).query(any());

        Map<String, Object> requestBody = Map.of("q", "SELECT c FROM COMPOSITION c");

        InvalidApiParameterException exception = assertThrowsExactly(
                InvalidApiParameterException.class,
                () -> controller.executeCompositionQuery(
                        requestBody, null, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE));

        assertEquals("Query result does not contain COMPOSITION objects.", exception.getMessage());
    }

    @Test
    void executeCompositionQueryRejectsSubPathSelection() {
        OpenehrQueryController controller = controller();

        Map<String, Object> requestBody = Map.of("q", "SELECT c/data FROM COMPOSITION c");

        InvalidApiParameterException exception = assertThrowsExactly(
                InvalidApiParameterException.class,
                () -> controller.executeCompositionQuery(
                        requestBody, null, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE));

        assertEquals(
                "Composition query must select the composition root without sub-paths.",
                exception.getMessage());
    }

    @Test
    void createRequestWithXmlParamsAdjusted() {

        AqlQueryRequest request = AqlQueryRequest.prepare(
                "SELECT e FROM EHR e",
                OpenEhrQueryRequestUtils.rewriteExplicitParameterTypes(new HashMap<>(Map.of(
                        "p_string", "some-string",
                        "p_xml_num", Map.of("type", "num", "", 42.12),
                        "p_xml_int", Map.of("type", "int", "", 11)
                        // "p_list": L
                        ))),
                null,
                null);
        assertThat(request.parameters())
                .containsAllEntriesOf(Map.of("p_string", "some-string", "p_xml_num", 42.12, "p_xml_int", 11));
        assertThat(request.fetch()).isNull();
        assertThat(request.offset()).isNull();
    }

    @Test
    void createRequestWithXmlParamsWithoutTypeAdjusted() {
        AqlQueryRequest request = AqlQueryRequest.prepare(
                "SELECT c FROM COMPOSITION c",
                OpenEhrQueryRequestUtils.rewriteExplicitParameterTypes(new HashMap<>(Map.of(
                        "p_xml_num", Map.of("num", 42.12),
                        "p_xml_int", Map.of("int", 11)
                        // "p_list": L
                        ))),
                null,
                null);
        assertThat(request.parameters()).containsAllEntriesOf(Map.of("p_xml_num", 42.12, "p_xml_int", 11));
        assertThat(request.fetch()).isNull();
        assertThat(request.offset()).isNull();
    }

    @Test
    void createRequestWithXmlParamListsAdjusted() {

        AqlQueryRequest request = AqlQueryRequest.prepare(
                "SELECT e, c FROM EHR e CONTAINS COMPOSITION c",
                OpenEhrQueryRequestUtils.rewriteExplicitParameterTypes(new HashMap<>(Map.of(
                        "p_xml_list", Map.of("", List.of("value_1", "value_2")),
                        "p_xml_list_alternative", List.of("some", "other", "value")))),
                null,
                null);
        assertThat(request.parameters())
                .containsAllEntriesOf(Map.of(
                        "p_xml_list", List.of("value_1", "value_2"),
                        "p_xml_list_alternative", List.of("some", "other", "value")));
        assertThat(request.fetch()).isNull();
        assertThat(request.offset()).isNull();
    }

    private void assertAqlQueryRequest(AqlQueryRequest aqlQueryRequest) {
        ArgumentCaptor<AqlQueryRequest> argument = ArgumentCaptor.forClass(AqlQueryRequest.class);
        verify(mockAqlQueryService).query(argument.capture());
        assertEquals(aqlQueryRequest, argument.getValue());
    }

    private void assertMetaData(ResponseEntity<QueryResponseData> response) {
        QueryResponseData body = response.getBody();
        assertNotNull(body);
        assertSame(SAMPLE_META_DATA, body.getMeta());
        verify(mockQueryContext).createMetaData(any());
    }
}
