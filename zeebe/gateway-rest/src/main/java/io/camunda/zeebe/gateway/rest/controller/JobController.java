/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.JobServices;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.CompleteJobRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ZeebeRestController
public class JobController {

  private final ResponseObserverProvider responseObserverProvider;
  private final JobServices<JobActivationResponse> jobServices;

  @Autowired
  public JobController(
      final JobServices<JobActivationResponse> jobServices,
      final ResponseObserverProvider responseObserverProvider) {
    this.jobServices = jobServices;
    this.responseObserverProvider = responseObserverProvider;
  }

  @PostMapping(
      path = "/jobs/activation",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> activateJobs(
      @RequestBody final JobActivationRequest activationRequest) {
    return RequestMapper.toJobsActivationRequest(activationRequest)
        .fold(this::activateJobs, RestErrorMapper::mapProblemToCompletedResponse);
  }

  @PostMapping(
      path = "/jobs/{jobKey}/completion",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> completeJob(
      @PathVariable final long jobKey,
      @RequestBody(required = false) final JobCompletionRequest completionRequest) {
    return RequestMapper.toJobCompletionRequest(completionRequest, jobKey)
        .fold(this::completeJob, RestErrorMapper::mapProblemToCompletedResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> activateJobs(
      final ActivateJobsRequest activationRequest) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = responseObserverProvider.apply(result);
    jobServices.activateJobs(
        activationRequest, responseObserver, responseObserver::setCancelationHandler);
    return result.handleAsync(
        (res, ex) -> {
          responseObserver.invokeCancelationHandler();
          return res;
        });
  }

  private CompletableFuture<ResponseEntity<Object>> completeJob(
      final CompleteJobRequest completeJobRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withAuthentication(RequestMapper.getAuthentication())
                .completeJob(completeJobRequest.jobKey(), completeJobRequest.variables()));
  }
}
