package org.camunda.optimize.service.es.report.command.count;

import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportUtil.getDateHistogramInterval;

public class CountProcessInstanceFrequencyByStartDateCommand extends ReportCommand<MapReportResultDto> {

  public static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  protected MapReportResultDto evaluate() throws OptimizeException {

    logger.debug("Evaluating count process instance frequency grouped by start date report " +
      "for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionId(),
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(reportData.getGroupBy().getUnit()))
      .get();

    MapReportResultDto mapResult = new MapReportResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    return mapResult;
  }

  private AggregationBuilder createAggregation(String unit) throws OptimizeException {
    DateHistogramInterval interval = getDateHistogramInterval(unit);
    return AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(ProcessInstanceType.START_DATE)
      .dateHistogramInterval(interval);
  }

  Map<String, Long> processAggregations(Aggregations aggregations) {
    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Long> result = new LinkedHashMap<>();
    // For each entry
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();    // Key
      long docCount = entry.getDocCount();         // Doc count
      String formattedDate = key.toString(configurationService.getOptimizeDateFormat());
      result.put(formattedDate, docCount);
    }
    return result;
  }

}
