/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ListViewFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromIncidentHandler.class);

  private ListViewTemplate listViewTemplate;

  public ListViewFromIncidentHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<IncidentRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey());
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<IncidentRecordValue> record, FlowNodeInstanceForListViewEntity entity) {
    final Intent intent = record.getIntent();
    final IncidentRecordValue recordValue = record.getValue();

    // update activity instance
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intent == IncidentIntent.CREATED) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intent == IncidentIntent.RESOLVED) {
      entity.setErrorMessage(null);
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      FlowNodeInstanceForListViewEntity entity, OperateElasticsearchBulkRequest batchRequest) {
    LOGGER.debug("Activity instance for list view: id {}", entity.getId());
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
    batchRequest.upsert(
        listViewTemplate.getFullQualifiedName(),
        entity.getProcessInstanceKey().toString(),
        entity,
        updateFields);
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
