/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.templates;

import io.zeebe.tasklist.schema.indices.IndexDescriptor;

public interface TemplateDescriptor extends IndexDescriptor {

  String PARTITION_ID = "partitionId";

  default String getTemplateName() {
    return getFullQualifiedName() + "template";
  }

  default String getIndexPattern() {
    return getFullQualifiedName() + "*";
  }
}
