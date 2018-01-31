package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.listeners.TriggerListenerSupport;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AlertSchedulerIT {

  private static final String BEARER = "Bearer ";
  private static final String ALERT = "alert";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeRule.getAlertService().getScheduler().clear();
  }

  @Test
  public void reportUpdateToNotNumberRemovesAlert() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));

    assertThat(response.getStatus(), is(200));

    // when
    ReportDefinitionDto report = getReportDefinitionDto(processDefinitionId);
    report.getData().getGroupBy().setType(GROUP_BY_FLOW_NODE_TYPE);
    report.getData().setVisualization(HEAT_VISUALIZATION);
    updateReport(simpleAlert.getReportId(), report);

    // then
    // scheduler does not contain any triggers
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(0)
    );

    //alert is deleted from ES
    response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
        new GenericType<List<AlertDefinitionDto>>() {}
    );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = getSetupBasicAlert();

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));

    assertThat(response.getStatus(), is(200));

    // when
    response =
        embeddedOptimizeRule.target("report/" + simpleAlert.getReportId())
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(0)
    );

    response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
        new GenericType<List<AlertDefinitionDto>>() {}
        );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = getSetupBasicAlert();

    // when
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));

    // then
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(1)
    );
  }

  private AlertCreationDto getSetupBasicAlert() throws IOException, InterruptedException {
    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinitionId);
    return createSimpleAlert(reportId);
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = getSetupBasicAlert();

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));

    String alertId = response.readEntity(String.class);

    // when
    response =
        embeddedOptimizeRule.target("alert/" + alertId)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(0)
    );
  }

  @Test
  public void updatedAlertIsRescheduled() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = getSetupBasicAlert();

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));

    String alertId = response.readEntity(String.class);
    Trigger trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
        getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
        is(
            OffsetDateTime.now().plus(1,ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
        )
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    response =
        embeddedOptimizeRule.target("alert/" + alertId)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .put(Entity.json(simpleAlert));

    // then
    assertThat(response.getStatus(), is(204));

    trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
        getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
        is(OffsetDateTime.now().plus(30,ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS))
    );
  }

  private TriggerKey getTriggerKey(String alertId) {
    return new TriggerKey(alertId + "-trigger", "statusCheck-trigger");
  }

  @Test
  public void testScheduleTriggers() throws Exception {

    //given
    GreenMail greenMail = initGreenMail();
    try {
      String reportId = startProcessAndCreateReport();
      setEmailConfiguration();

      // when
      String token = embeddedOptimizeRule.getAuthenticationToken();
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(createSimpleAlert(reportId)));
      assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

      //then
      MimeMessage[] emails = greenMail.getReceivedMessages();
      assertThat(emails.length, is(1));
      assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    } finally {
      greenMail.stop();
    }
  }

  private void setEmailConfiguration() {
    embeddedOptimizeRule.getConfigurationService().setAlertEmailUsername("demo");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailPassword("demo");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailAddress("from@localhost.com");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailHostname("127.0.0.1");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailPort(6666);
    embeddedOptimizeRule.getConfigurationService().setAlertEmailProtocol("NONE");
  }

  private String startProcessAndCreateReport() throws IOException, InterruptedException {
    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    return createAndStoreNumberReport(processDefinitionId);
  }

  private GreenMail initGreenMail() {
    GreenMail greenMail = new GreenMail(new ServerSetup(6666, null, ServerSetup.PROTOCOL_SMTP));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("test@camunda.com", "test@camunda.com", "test@camunda.com");
    return greenMail;
  }


  @Test
  public void testCronMinutesInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Minutes");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger).truncatedTo(ChronoUnit.MINUTES);

    assertThat(nextFireTime, is(now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(intervalValue).truncatedTo(ChronoUnit.MINUTES)));
  }

  @Test
  public void testCronHoursInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Hours");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    OffsetDateTime targetTime = now.plusHours(intervalValue).truncatedTo(ChronoUnit.HOURS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.HOURS), is(targetTime));
  }

  @Test
  public void testCronDaysInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Days");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    OffsetDateTime targetTime = now.plusDays(intervalValue).truncatedTo(ChronoUnit.DAYS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.DAYS), is(targetTime));
  }


  @Test
  public void testCronWeeksInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Weeks");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.SECONDS), is(OffsetDateTime.now().plus(intervalValue*7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)));
  }


  private OffsetDateTime getNextFireTime(Trigger cronTrigger) {
    return OffsetDateTime.ofInstant(cronTrigger.getNextFireTime().toInstant(), TimeZone.getDefault().toZoneId());
  }

  private AlertDefinitionDto getAlertDefinitionDto(int intervalValue, String intervalUnit) {
    AlertCreationDto simpleAlert = createSimpleAlert("fakeReport", intervalValue, intervalUnit);

    AlertDefinitionDto alert = createFakeReport(simpleAlert);
    alert.setId(UUID.randomUUID().toString());
    return alert;
  }

  private AlertDefinitionDto createFakeReport(AlertCreationDto fakeReportAlert) {
    AlertDefinitionDto result = new AlertDefinitionDto();

    AlertUtil.mapBasicFields(fakeReportAlert, result);
    return result;
  }

  class TestListener extends TriggerListenerSupport {
    private int fireCounter = 0;

    @Override
    public String getName() {
      return "test-listener";
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
      super.triggerFired(trigger, context);
      this.fireCounter = this.fireCounter + 1;
    }
  }

  private AlertCreationDto createSimpleAlert(String reportId) {
    return createSimpleAlert(reportId, 1,"Seconds");
  }

  private AlertCreationDto createSimpleAlert(String reportId, int intervalValue, String unit) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit(unit);
    interval.setValue(intervalValue);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  private String deploySimpleServiceTaskProcess() throws IOException {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
      .endEvent()
    .done();
    return engineRule.deployProcessAndGetId(processModel);
  }

  private String createAndStoreNumberReport(String processDefinitionId) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getReportDefinitionDto(processDefinitionId);
    updateReport(id, report);
    return id;
  }

  private ReportDefinitionDto getReportDefinitionDto(String processDefinitionId) {
    ReportDataDto reportData = ReportDataHelper.createPiFrequencyCountGroupedByNoneAsNumber(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    return report;
  }

  private String createNewReportHelper() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }
}
