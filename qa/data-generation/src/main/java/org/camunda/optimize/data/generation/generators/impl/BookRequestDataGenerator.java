package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BookRequestDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/book-request.bpmn";

  public BookRequestDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    return new HashMap<>();
  }

  @Override
  protected String[] getCorrelationNames() {
    return new String[]{"ReceivedBookRequest", "HoldBook", "DeclineHold"};
  }
}
