/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class BoolQueryTransformer extends QueryOptionTransformer<SearchBoolQuery, BoolQuery> {

  public BoolQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public BoolQuery apply(final SearchBoolQuery value) {
    final var builder = QueryBuilders.bool();
    final var filter = value.filter();
    final var must = value.must();
    final var should = value.should();
    final var mustNot = value.mustNot();

    if (filter != null && !filter.isEmpty()) {
      builder.filter(of(filter));
    }

    if (must != null && !must.isEmpty()) {
      builder.must(of(must));
    }

    if (should != null && !should.isEmpty()) {
      builder.should(of(should));
    }

    if (mustNot != null && !mustNot.isEmpty()) {
      builder.mustNot(of(mustNot));
    }

    return builder.build();
  }

  private List<Query> of(final List<SearchQuery> values) {
    final var transformer = getQueryTransformer();
    return values.stream().map(transformer::apply).collect(Collectors.toList());
  }
}
