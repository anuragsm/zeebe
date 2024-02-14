/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Tag("ccsm-test")
@ActiveProfiles(CCSM_PROFILE)
public abstract class AbstractCCSMIT extends AbstractIT {
  @RegisterExtension
  @Order(4)
  protected static ZeebeExtension zeebeExtension = new ZeebeExtension();

  protected final Supplier<OptimizeIntegrationTestException> eventNotFoundExceptionSupplier =
    () -> new OptimizeIntegrationTestException("Cannot find exported event");

  @BeforeEach
  public void setupZeebeImportAndReloadConfiguration() {
    final String embeddedZeebePrefix = zeebeExtension.getZeebeRecordPrefix();
    // set the new record prefix for the next test
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setName(embeddedZeebePrefix);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  public void after() {
    // Clear all potential existing Zeebe records in Optimize
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(zeebeExtension.getZeebeRecordPrefix());
  }

  protected void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(new HashMap<>(), CCSM_PROFILE);
  }

  protected void importAllZeebeEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void importAllZeebeEntitiesFromLastIndex() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromLastIndex();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected ProcessInstanceEvent deployAndStartInstanceForProcess(final BpmnModelInstance process) {
    final Process deployedProcess = zeebeExtension.deployProcess(process);
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

  protected BoolQueryBuilder getQueryForProcessableProcessInstanceEvents() {
    return boolQuery().must(termsQuery(
      ZeebeProcessInstanceRecordDto.Fields.intent,
      ProcessInstanceIntent.ELEMENT_ACTIVATING.name(),
      ProcessInstanceIntent.ELEMENT_COMPLETED.name(),
      ProcessInstanceIntent.ELEMENT_TERMINATED.name()
    ));
  }

  protected String getConfiguredZeebeName() {
    return embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().getName();
  }

  protected void waitUntilMinimumDataExportedCount(final int minExportedEventCount, final String indexName,
                                                   final BoolQueryBuilder boolQueryBuilder) {
    waitUntilMinimumDataExportedCount(minExportedEventCount, indexName, boolQueryBuilder, 15);
  }

  protected void waitUntilMinimumProcessInstanceEventsExportedCount(final int minExportedEventCount) {
    waitUntilMinimumDataExportedCount(
      minExportedEventCount,
      DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableProcessInstanceEvents()
    );
  }

  protected void waitUntilNumberOfDefinitionsExported(final int expectedDefinitionsCount) {
    waitUntilMinimumDataExportedCount(
      expectedDefinitionsCount,
      DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME,
      boolQuery().must(termQuery(ZeebeProcessDefinitionRecordDto.Fields.intent, ProcessIntent.CREATED.name()))
    );
  }

  protected void waitUntilRecordMatchingQueryExported(final String indexName, final BoolQueryBuilder boolQuery) {
    waitUntilRecordMatchingQueryExported(1, indexName, boolQuery);
  }

  protected void waitUntilRecordMatchingQueryExported(final long minRecordCount, final String indexName,
                                                      final BoolQueryBuilder boolQuery) {
    waitUntilMinimumDataExportedCount(minRecordCount, indexName, boolQuery, 10);
  }

  protected void waitUntilInstanceRecordWithElementIdExported(final String instanceElementId) {
    waitUntilRecordMatchingQueryExported(
      DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      boolQuery().must(termQuery(
        ZeebeProcessInstanceRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.elementId,
        instanceElementId
      ))
    );
  }

  protected void waitUntilUserTaskRecordWithElementIdExported(final String instanceElementId) {
    waitUntilRecordMatchingQueryExported(
      DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME,
      boolQuery().must(termQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
        instanceElementId
      ))
    );
  }

  protected void waitUntilUserTaskRecordWithElementIdAndIntentExported(final String instanceElementId, final String intent) {
    waitUntilRecordMatchingQueryExported(
      DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME,
      boolQuery().must(termQuery(
          ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
          instanceElementId
        ))
        .must(termQuery(ZeebeRecordDto.Fields.intent, intent))
    );
  }

  protected void waitUntilDefinitionWithIdExported(final String processDefinitionId) {
    waitUntilRecordMatchingQueryExported(
      DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME,
      boolQuery()
        .must(termQuery(ZeebeProcessDefinitionRecordDto.Fields.intent, ProcessIntent.CREATED.name()))
        .must(termQuery(
          ZeebeProcessDefinitionRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnProcessId,
          processDefinitionId
        ))
    );
  }

  protected String getFlowNodeInstanceIdFromProcessInstanceForActivity(final ProcessInstanceDto processInstanceDto,
                                                                       final String activityId) {
    return getPropertyIdFromProcessInstanceForActivity(
      processInstanceDto,
      activityId,
      FlowNodeInstanceDto::getFlowNodeInstanceId
    );
  }

  protected String getPropertyIdFromProcessInstanceForActivity(final ProcessInstanceDto processInstanceDto,
                                                               final String activityId,
                                                               final Function<FlowNodeInstanceDto, String> propertyFunction) {
    return processInstanceDto.getFlowNodeInstances()
      .stream()
      .filter(flowNodeInstanceDto -> flowNodeInstanceDto.getFlowNodeId().equals(activityId))
      .map(propertyFunction)
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException(
        "Could not find property for process instance with key: " + processInstanceDto.getProcessDefinitionKey()));
  }

  protected BpmnModelInstance readProcessDiagramAsInstance(final String diagramPath) {
    InputStream inputStream = getClass().getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }

  protected void setTenantIdForExportedZeebeRecords(final String indexName, final String tenantId) {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
      zeebeExtension.getZeebeRecordPrefix(),
      indexName,
      String.format("ctx._source.value.tenantId = \"%s\";", tenantId)
    );
  }

  protected static boolean isZeebeVersionPre81() {
    final Pattern zeebeVersionPreSequenceField = Pattern.compile("8.0.*");
    return zeebeVersionPreSequenceField.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

  protected static boolean isZeebeVersionPre82() {
    final Pattern zeebeVersionPreSequenceField = Pattern.compile("8.0.*|8.1.*");
    return zeebeVersionPreSequenceField.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

  protected static boolean isZeebeVersionPre83() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*");
    return zeebeVersionPattern.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

  protected static boolean isZeebeVersionPre84() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*|8.3.*");
    return zeebeVersionPattern.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

  protected static boolean isZeebeVersionWithMultiTenancy() {
    return !isZeebeVersionPre83();
  }

  @SneakyThrows
  protected void waitUntilMinimumDataExportedCount(final long minimumCount, final String indexName,
                                                   final BoolQueryBuilder boolQueryBuilder, final long countTimeoutInSeconds) {
    final String expectedIndex = zeebeExtension.getZeebeRecordPrefix() + "-" + indexName;
    final OptimizeElasticsearchClient esClient = databaseIntegrationTestExtension.getOptimizeElasticsearchClient();
    Awaitility.given().ignoreExceptions()
      .timeout(15, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .indices()
          .exists(new GetIndexRequest(expectedIndex), esClient.requestOptions())
      ).isTrue());
    final CountRequest countRequest = new CountRequest(expectedIndex).query(boolQueryBuilder);
    Awaitility.given().ignoreExceptions()
      .timeout(countTimeoutInSeconds, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .count(countRequest, esClient.requestOptions())
          .getCount())
        .isGreaterThanOrEqualTo(minimumCount));
  }

  protected Map<String, List<ZeebeUserTaskRecordDto>> getZeebeExportedUserTaskEventsByElementId() {
    return getZeebeExportedProcessableEvents(
      zeebeExtension.getZeebeRecordPrefix() + "-" + DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME,
      getQueryForProcessableUserTaskEvents(),
      ZeebeUserTaskRecordDto.class
    ).stream()
      .collect(Collectors.groupingBy(event -> event.getValue().getElementId()));
  }

  @SneakyThrows
  protected Map<String, List<ZeebeProcessInstanceRecordDto>> getZeebeExportedProcessInstanceEventsByElementId() {
    return getZeebeExportedProcessableEvents(
      zeebeExtension.getZeebeRecordPrefix() + "-" + DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableProcessInstanceEvents(),
      ZeebeProcessInstanceRecordDto.class
    ).stream()
      .collect(Collectors.groupingBy(event -> event.getValue().getElementId()));
  }

  protected OffsetDateTime getTimestampForZeebeEventsWithIntent(final List<? extends ZeebeRecordDto> eventsForElement,
                                                                final Intent intent) {
    final ZeebeRecordDto startOfElement = eventsForElement.stream()
      .filter(event -> event.getIntent().equals(intent))
      .findFirst().orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  @SneakyThrows
  private <T> List<T> getZeebeExportedProcessableEvents(final String exportIndex,
                                                        final QueryBuilder queryForProcessableEvents,
                                                        final Class<T> zeebeRecordClass) {
    final OptimizeElasticsearchClient esClient =
      databaseIntegrationTestExtension.getOptimizeElasticsearchClient();
    SearchRequest searchRequest = new SearchRequest()
      .indices(exportIndex)
      .source(new SearchSourceBuilder()
                .query(queryForProcessableEvents)
                .trackTotalHits(true)
                .size(100));
    final SearchResponse searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    return ElasticsearchReaderUtil.mapHits(
      searchResponse.getHits(),
      zeebeRecordClass,
      embeddedOptimizeExtension.getObjectMapper()
    );
  }

  private BoolQueryBuilder getQueryForProcessableUserTaskEvents() {
    return boolQuery().must(termsQuery(
      ZeebeUserTaskRecordDto.Fields.intent,
      UserTaskIntent.CREATING.name(),
      UserTaskIntent.COMPLETED.name(),
      UserTaskIntent.CANCELED.name()
    ));
  }

}
