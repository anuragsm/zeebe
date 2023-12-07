/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import io.camunda.zeebe.client.api.command.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;

public abstract class ZeebeTestUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestUtil.class);

  public static final Logger ALL_EVENTS_LOGGER = LoggerFactory.getLogger("io.camunda.operate.ALL_EVENTS");

  /**
   * Deploys the process synchronously.
   * @param client client
   * @param classpathResources classpath resources
   * @return process id
   */
  public static Long deployProcess(ZeebeClient client, String tenantId, String... classpathResources) {
    return deployProcess(false, client, tenantId, classpathResources);
  }

  public static Long deployProcess(boolean ignoreException, ZeebeClient client, String tenantId, String... classpathResources) {
    try {
      if (classpathResources.length == 0) {
        return null;
      }
      DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand();
      for (String classpathResource : classpathResources) {
        deployProcessCommandStep1 = deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
      }
      if (tenantId != null) {
        deployProcessCommandStep1 = ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1).tenantId(
            tenantId);
      }
      final DeploymentEvent deploymentEvent = ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1).send()
          .join();
      logger.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
      return deploymentEvent.getProcesses().get(classpathResources.length - 1).getProcessDefinitionKey();
    } catch (Exception e) {
      if (ignoreException) {
        logger.warn("Deployment failed: " + e.getMessage());
        return null;
      } else {
        throw e;
      }
    }
  }

  public static void deployDecision(ZeebeClient client, String tenantId, String... classpathResources) {
    if (classpathResources.length == 0) {
      return;
    }
    DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand();
    for (String classpathResource: classpathResources) {
      deployProcessCommandStep1 = deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    if (tenantId != null) {
      deployProcessCommandStep1 = ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1).tenantId(
          tenantId);
    }
    ((DeployResourceCommandStep1.DeployResourceCommandStep2)deployProcessCommandStep1)
            .send()
            .join();
    logger.debug("Deployment of resource [{}] was performed", (Object[])classpathResources);
  }

  /**
   * Deploys the process synchronously.
   * @param client client
   * @param processModel processModel
   * @param resourceName resourceName
   * @return process id
   */
  public static Long deployProcess(ZeebeClient client, String tenantId, BpmnModelInstance processModel, String resourceName) {
    DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand()
      .addProcessModel(processModel, resourceName);
    if (tenantId != null) {
      deployProcessCommandStep1 = ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1).tenantId(
          tenantId);
    }
    final DeploymentEvent deploymentEvent =
      ((DeployResourceCommandStep1.DeployResourceCommandStep2)deployProcessCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return deploymentEvent.getProcesses().get(0).getProcessDefinitionKey();
  }

  public static long startProcessInstance(ZeebeClient client, String bpmnProcessId, String payload) {
    return startProcessInstance(client, null, bpmnProcessId, payload);
  }

  /**
   *
   * @param client client
   * @param bpmnProcessId bpmnProcessId
   * @param payload payload
   * @return process instance id
   */
  public static long startProcessInstance(ZeebeClient client, String tenantId, String bpmnProcessId, String payload) {
    return startProcessInstance(false, client, tenantId, bpmnProcessId, payload);
  }

  public static long startProcessInstance(boolean ignoreException, ZeebeClient client, String tenantId, String bpmnProcessId, String payload) {
    return startProcessInstance(false, client, tenantId, bpmnProcessId, null, payload);
  }

  public static long startProcessInstance(boolean ignoreException, ZeebeClient client, String tenantId, String bpmnProcessId, Integer processVersion, String payload) {
    try {
      final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 createProcessInstanceCommandStep3 = (processVersion == null) ?
          client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion() :
          client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).version(processVersion);
      if (tenantId != null) {
        createProcessInstanceCommandStep3.tenantId(tenantId);
      }
      if (payload != null) {
        createProcessInstanceCommandStep3.variables(payload);
      }
      ProcessInstanceEvent processInstanceEvent = null;
      try {
        processInstanceEvent = createProcessInstanceCommandStep3.send().join();
        logger.debug("Process instance created for process [{}]", bpmnProcessId);
      } catch (ClientException ex) {
        //retry once
        sleepFor(300L);
        processInstanceEvent = createProcessInstanceCommandStep3.send().join();
        logger.debug("Process instance created for process [{}]", bpmnProcessId);
      }
      return processInstanceEvent.getProcessInstanceKey();
    } catch (Exception e) {
      if (ignoreException) {
        logger.warn("Instance creation failed: " + e.getMessage());
        return 0L;
      } else {
        throw e;
      }
    }
  }

  public static void cancelProcessInstance(boolean ignoreException, ZeebeClient client, long processInstanceKey) {
    try {
      client.newCancelInstanceCommand(processInstanceKey).send().join();
    } catch (Exception e) {
      if (!ignoreException) {
        throw e;
      } else {
        logger.warn("Cancellation failed: " + e.getMessage());
      }
    }
  }

  public static void cancelProcessInstance(ZeebeClient client, long processInstanceKey) {
    cancelProcessInstance(false, client, processInstanceKey);
  }

  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload) {
    completeTask(client, jobType, workerName, payload, 1);
  }
  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload, int count) {
    handleTasks(client, jobType, workerName, count, (jobClient, job) -> {
      CompleteJobCommandStep1 command = jobClient.newCompleteCommand(job.getKey());
      if (payload != null) {
        command.variables(payload);
      }
      command.send().join();
    });
  }

  public static Long failTask(ZeebeClient client, String jobType, String workerName, int numberOfFailures, String errorMessage) {
    return handleTasks(client, jobType, workerName, numberOfFailures, ((jobClient, job) -> {
      FailJobCommandStep2 failCommand = jobClient.newFailCommand(job.getKey())
          .retries(job.getRetries() - 1);
      if (errorMessage != null) {
        failCommand.errorMessage(errorMessage);
      }
      failCommand.send().join();
    })).get(0);
  }

  public static Long failTaskWithRetriesLeft(ZeebeClient client, String jobType, String workerName, int numberOfRetriesLeft, String errorMessage) {
    return handleTasks(client, jobType, workerName, 1, ((jobClient, job) -> {
      FailJobCommandStep2 failCommand = jobClient.newFailCommand(job.getKey())
          .retries(numberOfRetriesLeft);
      if (errorMessage != null) {
        failCommand.errorMessage(errorMessage);
      }
      failCommand.send().join();
    })).get(0);
  }

  public static Long throwErrorInTask(ZeebeClient client, String jobType, String workerName, int numberOfFailures, String errorCode,String errorMessage) {
    return handleTasks(client, jobType, workerName, numberOfFailures, ((jobClient, job) -> {
      jobClient.newThrowErrorCommand(job.getKey()).errorCode(errorCode).errorMessage(errorMessage).send().join();
    })).get(0);
  }

  private static List<Long> handleTasks(ZeebeClient client, String jobType, String workerName, int jobCount, BiConsumer<JobClient, ActivatedJob> jobHandler) {
    final List<Long> jobKeys = new ArrayList<>();
    while (jobKeys.size() < jobCount) {
      client.newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(jobCount - jobKeys.size())
          .workerName(workerName)
          .timeout(Duration.ofSeconds(2))
          .send()
          .join()
          .getJobs()
          .forEach(job -> {
            jobHandler.accept(client, job);
            jobKeys.add(job.getKey());
          });
    }
    return jobKeys;
  }

  public static void resolveIncident(ZeebeClient client, Long jobKey, Long incidentKey) {
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();
  }

  public static void updateVariables(ZeebeClient client, Long scopeKey, String newPayload) {
    client.newSetVariablesCommand(scopeKey).variables(newPayload).local(true).send().join();
  }

  public static void sendMessages(ZeebeClient client, String messageName, String payload, int count, String correlationKey) {
    for (int i = 0; i<count; i++) {
      client.newPublishMessageCommand()
          .messageName(messageName)
          .correlationKey(correlationKey)
          .variables(payload)
          .timeToLive(Duration.ofSeconds(30))
          .messageId(UUID.randomUUID().toString())
          .send().join();
    }
  }

  public static void sendSignal(ZeebeClient client, String signalName, String payload, int count) {
    for (int i = 0; i<count; i++) {
      client.newBroadcastSignalCommand()
          .signalName(signalName)
          .variables(payload)
          .send().join();
    }
  }


}
