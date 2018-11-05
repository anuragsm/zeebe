package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class DocumentCheckHandlingDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/document-check-handling.bpmn";

  public DocumentCheckHandlingDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("approved", ThreadLocalRandom.current().nextDouble());
    return variables;
  }


  public Set<String> getPathVariableNames() {
    Set<String> variableNames = new HashSet<>();
    variableNames.add("approved");
    return variableNames;
  }

}
