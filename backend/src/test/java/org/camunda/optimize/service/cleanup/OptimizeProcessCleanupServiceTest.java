/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.writer.BusinessKeyWriter;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptimizeProcessCleanupServiceTest {
  private static final List<String> INSTANCE_IDS = ImmutableList.of("1", "2");

  @Mock
  private ProcessDefinitionReader processDefinitionReader;
  @Mock
  private ProcessInstanceReader processInstanceReader;
  @Mock
  private CompletedProcessInstanceWriter processInstanceWriter;
  @Mock
  private ProcessVariableUpdateWriter processVariableUpdateWriter;
  @Mock
  private VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  @Mock
  private BusinessKeyWriter businessKeyWriter;
  @Mock
  private CamundaActivityEventWriter camundaActivityEventWriter;

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDefaultConfig() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);
    mockGetProcessInstanceIds(processDefinitionKeys);

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultMode() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    getCleanupConfig().setDefaultProcessDataCleanupMode(customMode);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteAllInstanceVariablesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfig().setDefaultTtl(customTtl);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockGetProcessInstanceIds(processDefinitionKeys);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificModeOverridesDefault() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    final List<String> processDefinitionKeysWithSpecificMode = generateRandomDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificMode.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customMode)
    ));
    final List<String> processDefinitionKeysWithDefaultMode = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificMode,
      processDefinitionKeysWithDefaultMode
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    verifyDeleteProcessInstanceExecutionReturnCapturedArguments(processDefinitionKeysWithDefaultMode);
    verifyDeleteAllInstanceVariablesReturnCapturedArguments(processDefinitionKeysWithSpecificMode);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificTtlsOverrideDefault() {
    // given
    final Period customTtl = Period.parse("P2M");
    final List<String> processDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificTtl.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customTtl)
    ));
    final List<String> processDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificTtl,
      processDefinitionKeysWithDefaultTtl
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
      allProcessDefinitionKeys
    );
    assertInstancesWereRetrievedByKeyAndExpectedTtl(capturedArguments, processDefinitionKeysWithSpecificTtl, customTtl);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      capturedArguments, processDefinitionKeysWithDefaultTtl, getCleanupConfig().getDefaultTtl()
    );
  }

  @Test
  public void testCleanupRunOnceForEveryProcessDefinitionKey() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockGetProcessInstanceIds(processDefinitionKeys);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockProcessDefinitions(ListUtils.union(processDefinitionKeys, processDefinitionKeys));

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinition() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfig().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and this key is not present in the known process definition keys
    mockProcessDefinitions(generateRandomDefinitionsKeys(3));

    //when I run the cleanup
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();

    //then it fails with an exception
    OptimizeConfigurationException exception = assertThrows(
      OptimizeConfigurationException.class,
      () -> doCleanup(underTest)
    );
    assertThat(exception.getMessage(), containsString(configuredKey));
  }

  private void mockGetProcessInstanceIds(final List<String> expectedKeys) {
    expectedKeys.forEach(key -> {
      when(processInstanceReader.getProcessInstanceIdsThatEndedBefore(
        eq(key), ArgumentMatchers.any(OffsetDateTime.class), anyInt()
      )).thenReturn(INSTANCE_IDS, Collections.emptyList());
    });
  }

  private void doCleanup(final OptimizeCleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private OptimizeCleanupConfiguration getCleanupConfig() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertDeleteProcessInstancesExecutedFor(final List<String> expectedProcessDefinitionKeys,
                                                       final Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteProcessInstanceExecutionReturnCapturedArguments(expectedProcessDefinitionKeys);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      processInstanceKeysWithDateFilter,
      expectedProcessDefinitionKeys,
      expectedTtl
    );

    verify(processInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByIds(eq(INSTANCE_IDS));
    verify(variableUpdateInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByProcessInstanceIds(eq(INSTANCE_IDS));
  }

  private void assertInstancesWereRetrievedByKeyAndExpectedTtl(final Map<String, OffsetDateTime> capturedInvocationArguments,
                                                               final List<String> expectedDefinitionKeys,
                                                               final Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments = capturedInvocationArguments.entrySet().stream()
      .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments.size(), is(expectedDefinitionKeys.size()));

    final OffsetDateTime dateFilterValue = filteredInvocationArguments.values().toArray(new OffsetDateTime[]{})[0];
    assertThat(dateFilterValue, lessThanOrEqualTo(OffsetDateTime.now().minus(expectedTtl)));
    filteredInvocationArguments.values().forEach(instant -> assertThat(instant, is(dateFilterValue)));
  }

  private Map<String, OffsetDateTime> verifyDeleteProcessInstanceExecutionReturnCapturedArguments(final List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> definitionKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
      .getProcessInstanceIdsThatEndedBefore(definitionKeyCaptor.capture(), endDateFilterCaptor.capture(), anyInt());
    int i = 0;
    final Map<String, OffsetDateTime> definitionKeysWithDateFilter = new HashMap<>();
    for (String key : definitionKeyCaptor.getAllValues()) {
      definitionKeysWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return definitionKeysWithDateFilter;
  }

  private void assertDeleteAllInstanceVariablesExecutedFor(List<String> expectedProcessDefinitionKeys,
                                                           Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteAllInstanceVariablesReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      processInstanceKeysWithDateFilter,
      expectedProcessDefinitionKeys,
      expectedTtl
    );
  }

  private Map<String, OffsetDateTime> verifyDeleteAllInstanceVariablesReturnCapturedArguments(final List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> processInstanceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
      .getProcessInstanceIdsThatHaveVariablesAndEndedBefore(
        processInstanceCaptor.capture(), endDateFilterCaptor.capture(), anyInt()
      );
    int i = 0;
    final Map<String, OffsetDateTime> filteredProcessInstancesWithDateFilter = new HashMap<>();
    for (String key : processInstanceCaptor.getAllValues()) {
      filteredProcessInstancesWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredProcessInstancesWithDateFilter;
  }

  private List<String> mockProcessDefinitions(final List<String> processDefinitionIds) {
    final List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos = processDefinitionIds.stream()
      .map(this::createProcessDefinitionDto)
      .collect(Collectors.toList());
    when(processDefinitionReader.getProcessDefinitions(false, false))
      .thenReturn(processDefinitionOptimizeDtos);
    return processDefinitionIds;
  }

  private List<String> generateRandomDefinitionsKeys(final Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toList());
  }

  private ProcessDefinitionOptimizeDto createProcessDefinitionDto(String key) {
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = ProcessDefinitionOptimizeDto.builder()
      .key(key)
      .build();
    return processDefinitionOptimizeDto;
  }

  private OptimizeCleanupService createOptimizeCleanupServiceToTest() {
    return new OptimizeProcessCleanupService(
      configurationService,
      processDefinitionReader,
      processInstanceReader,
      processInstanceWriter,
      processVariableUpdateWriter,
      businessKeyWriter,
      camundaActivityEventWriter,
      variableUpdateInstanceWriter
    );
  }
}
