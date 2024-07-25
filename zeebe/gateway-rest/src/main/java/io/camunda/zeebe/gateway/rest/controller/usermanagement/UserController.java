/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.CamundaServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ZeebeRestController
@RequestMapping("/v2/users")
public class UserController {
  private final UserServices<UserRecord> userServices;
  private final PasswordEncoder passwordEncoder;

  public UserController(
      final CamundaServices camundaServices, final PasswordEncoder passwordEncoder) {
    userServices = camundaServices.userServices();
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @RequestBody final CamundaUserWithPasswordRequest userWithPasswordDto) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createUser(
                    userWithPasswordDto.getUsername(),
                    userWithPasswordDto.getName(),
                    userWithPasswordDto.getEmail(),
                    passwordEncoder.encode(userWithPasswordDto.getPassword())));
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteUser(@PathVariable final Long id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> findUserById(@PathVariable final Long id) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllUsers(
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateUser(
      @PathVariable final Long id, @RequestBody final CamundaUserWithPasswordRequest user) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
