/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.qa.util;

import io.zeebe.tasklist.schema.ElasticsearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("test")
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @Override
  public void initializeSchema() {
    // do nothing
    LOGGER.info("INIT: no schema will be created");
  }

  public void deleteSchema() {
    final String prefix = tasklistProperties.getElasticsearch().getIndexPrefix();
    LOGGER.info("Removing indices " + prefix + "*");
    retryElasticsearchClient.deleteIndicesFor(prefix + "*");
    retryElasticsearchClient.deleteTemplatesFor(prefix + "*");
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }
}
