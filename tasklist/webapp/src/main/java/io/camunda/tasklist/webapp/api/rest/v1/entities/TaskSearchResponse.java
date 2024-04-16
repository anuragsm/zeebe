/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskSearchResponse {
  @Schema(description = "The unique identifier of the task.")
  private String id;

  @Schema(description = "The name of the task.")
  private String name;

  @Schema(description = "User Task ID from the BPMN definition.")
  private String taskDefinitionId;

  @Schema(description = "The name of the process.")
  private String processName;

  @Schema(
      description = "When was the task created (renamed equivalent of `Task.creationTime` field).")
  private String creationDate;

  @Schema(
      description =
          "When was the task completed (renamed equivalent of `Task.completionTime` field).")
  private String completionDate;

  @Schema(description = "The username/id of who is assigned to the task.")
  private String assignee;

  @Schema(description = "The state of the task.")
  private TaskState taskState;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Array of values to be copied into `TaskSearchRequest` to request for next or previous page of tasks."))
  private String[] sortValues;

  @Schema(description = "A flag to show that the task is first in the current filter.")
  private boolean isFirst;

  @Schema(description = "Reference to the task form.")
  private String formKey;

  @Schema(
      description =
          "Reference to the ID of a deployed form. If the form is not deployed, this property is null.")
  private String formId;

  @Schema(
      description =
          "Reference to the version of a deployed form. If the form is not deployed, this property is null.")
  private Long formVersion;

  @Schema(
      description =
          "Is the form embedded for this task? If there is no form, this property is null.")
  private Boolean isFormEmbedded;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of `Task.processDefinitionId` field).")
  private String processDefinitionKey;

  @Schema(
      description =
          "Reference to process instance id (renamed equivalent of `Task.processInstanceId` field).")
  private String processInstanceKey;

  @Schema(description = "The tenant ID associated with the task.")
  private String tenantId;

  @Schema(description = "The due date for the task.")
  private OffsetDateTime dueDate;

  @Schema(description = "The follow-up date for the task.")
  private OffsetDateTime followUpDate;

  @ArraySchema(arraySchema = @Schema(description = "The candidate groups for the task."))
  private String[] candidateGroups;

  @ArraySchema(arraySchema = @Schema(description = "The candidate users for the task."))
  private String[] candidateUsers;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array of the task's variables. Only variables specified in `TaskSearchRequest.includeVariables` are returned. Note that a variable's draft value is not returned in `TaskSearchResponse`."))
  private VariableSearchResponse[] variables;

  @Schema(description = "The context variable (from modeler) of the task.")
  private String context;

  private TaskImplementation implementation;

  public String getId() {
    return id;
  }

  public TaskSearchResponse setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskSearchResponse setName(final String name) {
    this.name = name;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskSearchResponse setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public TaskSearchResponse setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public TaskSearchResponse setCreationDate(final String creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public TaskSearchResponse setCompletionDate(final String completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchResponse setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskSearchResponse setTaskState(final TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskSearchResponse setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskSearchResponse setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskSearchResponse setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskSearchResponse setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskSearchResponse setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskSearchResponse setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskSearchResponse setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskSearchResponse setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskSearchResponse setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskSearchResponse setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchResponse setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchResponse setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchResponse setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public VariableSearchResponse[] getVariables() {
    return variables;
  }

  public TaskSearchResponse setVariables(final VariableSearchResponse[] variables) {
    this.variables = variables;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskSearchResponse setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getContext() {
    return context;
  }

  public TaskSearchResponse setContext(final String context) {
    this.context = context;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            name,
            taskDefinitionId,
            processName,
            creationDate,
            completionDate,
            assignee,
            taskState,
            isFirst,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            processDefinitionKey,
            processInstanceKey,
            tenantId,
            dueDate,
            followUpDate,
            implementation,
            context);
    result = 31 * result + Arrays.hashCode(sortValues);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(variables);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskSearchResponse that = (TaskSearchResponse) o;
    return isFirst == that.isFirst
        && implementation == that.implementation
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(completionDate, that.completionDate)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(context, that.context)
        && taskState == that.taskState
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Arrays.equals(variables, that.variables);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("processName='" + processName + "'")
        .add("creationDate='" + creationDate + "'")
        .add("completionDate='" + completionDate + "'")
        .add("assignee='" + assignee + "'")
        .add("taskState=" + taskState)
        .add("implementation=" + implementation)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("isFirst=" + isFirst)
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate='" + dueDate + "'")
        .add("followUpDate='" + followUpDate + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("variables=" + Arrays.toString(variables))
        .add("taskContext='" + context + "'")
        .toString();
  }
}
