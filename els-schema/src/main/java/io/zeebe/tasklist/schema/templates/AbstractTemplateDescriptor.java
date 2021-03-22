/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.templates;

import io.zeebe.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public String getFullQualifiedName() {
    return String.format(
        "%s-%s-%s_",
        tasklistProperties.getElasticsearch().getIndexPrefix(), getIndexName(), getVersion());
  }
}
