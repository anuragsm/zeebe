/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MigrateProcessInstanceConcurrentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldContinueMigratedInstanceWithJobCompleteBefore() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", a -> a.zeebeJobType("A"))
                    .serviceTask("B_v1", s -> s.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", a -> a.zeebeJobType("A"))
                    .serviceTask("B_v2", s -> s.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command().job(JobIntent.COMPLETE, new JobRecord()).key(jobKey),
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueMigratedInstanceWithJobCompleteAfter() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", a -> a.zeebeJobType("A"))
                    .serviceTask("B_v1", s -> s.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", a -> a.zeebeJobType("A"))
                    .serviceTask("B_v2", s -> s.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))),
        RecordToWrite.command().job(JobIntent.COMPLETE, new JobRecord()).key(jobKey));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("A_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("A_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueMigratedInstanceWithTimerBefore() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", t -> t.zeebeJobType("A"))
                    .boundaryEvent("timer_v1", b -> b.timerWithDuration("PT1H"))
                    .serviceTask("B_v1", t -> t.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .moveToActivity("A_v1")
                    .endEvent("end_2_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", t -> t.zeebeJobType("A"))
                    .boundaryEvent("timer_v2", b -> b.timerWithDuration("PT1H"))
                    .serviceTask("B_v2", t -> t.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .moveToActivity("A_v2")
                    .endEvent("end_2_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()),
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueMigratedInstanceWithTimerAfter() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", t -> t.zeebeJobType("A"))
                    .boundaryEvent("timer_v1", b -> b.timerWithDuration("PT1H"))
                    .serviceTask("B_v1", t -> t.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .moveToActivity("A_v1")
                    .endEvent("end_2_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", t -> t.zeebeJobType("A"))
                    .boundaryEvent("timer_v2", b -> b.timerWithDuration("PT1H"))
                    .serviceTask("B_v2", t -> t.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .moveToActivity("A_v2")
                    .endEvent("end_2_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))),
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, timerCreated.getValue())
            .key(timerCreated.getKey()));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGER)
                .withProcessInstanceKey(processInstanceKey)
                .onlyCommandRejections()
                .findFirst())
        .describedAs(
            "Expect that the timer command is rejected because the migration recreate the subscription")
        .isPresent();

    ENGINE.increaseTime(Duration.ofHours(1));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueMigratedInstanceWithMessageBefore() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    // the new process must use a different message name because of an inconsistent state exception
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "message_v1",
                        b -> b.message(m -> m.name("message").zeebeCorrelationKeyExpression("key")))
                    .serviceTask("B_v1", t -> t.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .moveToActivity("A_v1")
                    .endEvent("end_2_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "message_v2",
                        b ->
                            b.message(m -> m.name("message2").zeebeCorrelationKeyExpression("key")))
                    .serviceTask("B_v2", t -> t.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .moveToActivity("A_v2")
                    .endEvent("end_2_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("message")
                    .setCorrelationKey(helper.getCorrelationValue())
                    .setTimeToLive(Duration.ofHours(1).toMillis())),
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))));

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueMigratedInstanceWithMessageAfter() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    // the new process must use a different message name because of an inconsistent state exception
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A_v1", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "message_v1",
                        b -> b.message(m -> m.name("message").zeebeCorrelationKeyExpression("key")))
                    .serviceTask("B_v1", t -> t.zeebeJobType("B"))
                    .endEvent("end_v1")
                    .moveToActivity("A_v1")
                    .endEvent("end_2_v1")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A_v2", t -> t.zeebeJobType("A"))
                    .boundaryEvent(
                        "message_v2",
                        b ->
                            b.message(m -> m.name("message2").zeebeCorrelationKeyExpression("key")))
                    .serviceTask("B_v2", t -> t.zeebeJobType("B"))
                    .endEvent("end_v2")
                    .moveToActivity("A_v2")
                    .endEvent("end_2_v2")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .migration(
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(processInstanceKey)
                    .setTargetProcessDefinitionKey(targetProcessDefinitionKey)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("A_v1")
                            .setTargetElementId("A_v2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("B_v1")
                            .setTargetElementId("B_v2"))),
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("message")
                    .setCorrelationKey(helper.getCorrelationValue())
                    .setTimeToLive(Duration.ofHours(1).toMillis())));

    // The new process waits for a message with a different name. Continue the process instance by
    // publishing the message.
    ENGINE
        .message()
        .withName("message2")
        .withCorrelationKey(helper.getCorrelationValue())
        .publish();

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("B_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple("B_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end_v1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static long extractProcessDefinitionKeyByProcessId(
      final Record<DeploymentRecordValue> deployment, final String processId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(processId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
