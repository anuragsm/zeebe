/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.concurrent.Callable;
import org.camunda.operate.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * Import job for one batch of Zeebe data.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportJob implements Callable<Boolean> {

  private static final Logger logger = LoggerFactory.getLogger(ImportJob.class);

  private ImportBatch importBatch;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  public ImportJob(ImportBatch importBatch) {
    this.importBatch = importBatch;
  }

  @Override
  public Boolean call() {
    try {
      //do import
      elasticsearchBulkProcessor.persistZeebeRecords(importBatch.getRecords(), importBatch.getImportValueType());
      //record latest position
      final long lastProcessedPosition = importBatch.getRecords().get(importBatch.getRecordsCount() - 1).getPosition();
      importPositionHolder.recordLatestLoadedPosition(importBatch.getImportValueType().getAliasTemplate(),
          importBatch.getPartitionId(), lastProcessedPosition);
      importBatch.finished();
      return true;
    } catch (PersistenceException e) {
      logger.error(e.getMessage(), e);
      importBatch.failed();
      return false;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      importBatch.failed();
      return false;
    }
  }
}
