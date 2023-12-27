/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class PersistedFormTest {
  @Test
  void copyIsIndependent() {
    // given
    final var original = new PersistedForm();
    original.wrap(
        new FormRecord()
            .setFormId("form")
            .setFormKey(1)
            .setVersion(1)
            .setResourceName("test")
            .setResource(new UnsafeBuffer(new byte[] {1, 2, 3, 4}))
            .setChecksum(new UnsafeBuffer(new byte[] {1, 2, 3, 4})));
    final var copy = original.copy();

    // when -- modify original
    original.getResourceName().byteArray()[0] = 'x';
    original.getResource().byteArray()[0] = 0;
    original.getChecksum().byteArray()[0] = 0;

    // then -- copy is not modified
    final var copiedResourceName = new byte[copy.getResourceName().capacity()];
    copy.getResourceName().getBytes(0, copiedResourceName);
    final var copiedResource = new byte[copy.getResource().capacity()];
    copy.getResource().getBytes(0, copiedResource);
    final var copiedChecksum = new byte[copy.getChecksum().capacity()];
    copy.getChecksum().getBytes(0, copiedChecksum);

    assertThat(copiedResourceName).isEqualTo("test".getBytes());
    assertThat(copiedResource).isEqualTo(new byte[] {1, 2, 3, 4});
    assertThat(copiedChecksum).isEqualTo(new byte[] {1, 2, 3, 4});
  }
}
