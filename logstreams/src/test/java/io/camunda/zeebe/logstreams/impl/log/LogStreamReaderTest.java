/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.LogStreamReaderRule;
import io.camunda.zeebe.logstreams.util.LogStreamRule;
import io.camunda.zeebe.logstreams.util.MutableLogAppendEntry;
import io.camunda.zeebe.util.ByteValue;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class LogStreamReaderTest {
  private static final UnsafeBuffer EVENT_VALUE = new UnsafeBuffer(getBytes("test"));
  private static final int LOG_SEGMENT_SIZE = (int) ByteValue.ofMegabytes(4);

  private final LogStreamRule logStreamRule =
      LogStreamRule.startByDefault(builder -> builder.withMaxFragmentSize(LOG_SEGMENT_SIZE));
  private final LogStreamReaderRule readerRule = new LogStreamReaderRule(logStreamRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(logStreamRule).around(readerRule);

  private final Random random = new Random();

  private LogStreamReader reader;
  private LogStreamBatchWriter writer;
  private long eventKey;

  @Before
  public void setUp() {
    eventKey = random.nextLong();
    reader = readerRule.getLogStreamReader();
    writer = logStreamRule.getLogStreamBatchWriter();
  }

  @Test
  public void shouldNotHaveNextIfReaderIsClosed() {
    // given
    final LogStreamReader reader = logStreamRule.getLogStreamReader();
    reader.close();

    // when - then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowExceptionIfReaderClosedOnNext() {
    // given
    final LogStreamReader reader = logStreamRule.getLogStreamReader();
    reader.close();

    // when - then
    assertThatCode(reader::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldNotHaveNext() {
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldHaveNext() {
    // given
    final long position =
        writer.tryWrite(new MutableLogAppendEntry().key(eventKey).recordValue(EVENT_VALUE));

    // then
    assertThat(reader.hasNext()).isTrue();
    final LoggedEvent next = reader.next();
    assertThat(next.getKey()).isEqualTo(eventKey);
    assertThat(next.getPosition()).isEqualTo(position);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldThrowNoSuchElementExceptionOnNextCall() {
    // given an empty log
    // then
    assertThatCode(reader::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldReturnPositionOfCurrentLoggedEvent() {
    // given
    final long position = writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    reader.seekToFirstEvent();

    // then
    assertThat(reader.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldReturnNoPositionIfNotActiveOrInitialized() {
    // given
    writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    // then
    assertThat(reader.getPosition()).isEqualTo(-1);
  }

  @Test
  public void shouldReopenAndReturnLoggedEvent() {
    // given
    reader.close();
    final long position =
        writer.tryWrite(new MutableLogAppendEntry().key(eventKey).recordValue(EVENT_VALUE));
    reader = readerRule.resetReader();

    // then
    final LoggedEvent loggedEvent = readerRule.nextEvent();
    assertThat(loggedEvent.getKey()).isEqualTo(eventKey);
    assertThat(loggedEvent.getPosition()).isEqualTo(position);
  }

  @Test
  public void shouldWrapAndSeekToEvent() {
    // given
    writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    final long secondPos =
        writer.tryWrite(new MutableLogAppendEntry().key(eventKey).recordValue(EVENT_VALUE));

    // when
    reader = logStreamRule.newLogStreamReader();
    reader.seek(secondPos);

    // then
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getKey()).isEqualTo(eventKey);
    assertThat(loggedEvent.getPosition()).isEqualTo(secondPos);

    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReturnLastEventAfterSeekToLastEvent() {
    // given
    final int eventCount = 10;
    final long lastPosition = writeEvents(eventCount);

    // when
    final long seekedPosition = reader.seekToEnd();

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThat(lastPosition).isEqualTo(seekedPosition);
  }

  @Test
  public void shouldReturnNextAfterSeekToEnd() {
    // given
    final int eventCount = 10;
    final long lastEventPosition = writeEvents(eventCount);
    final long seekedPosition = reader.seekToEnd();

    // when
    final long newLastPosition =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    // then
    assertThat(lastEventPosition).isEqualTo(seekedPosition);
    assertThat(newLastPosition).isGreaterThan(seekedPosition);

    assertThat(reader.hasNext()).isTrue();
    final LoggedEvent loggedEvent = reader.next();
    assertThat(loggedEvent.getPosition()).isEqualTo(newLastPosition);
  }

  @Test
  public void shouldSeekToEnd() {
    // given
    final int eventCount = 1000;
    final long lastPosition = writeEvents(eventCount);

    // when
    final long seekedPosition = reader.seekToEnd();

    // then
    assertThat(lastPosition).isEqualTo(seekedPosition);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldIterateOverManyEventsInOrder() {
    // given
    final int eventCount = 10_000;

    // when
    writeEvents(eventCount);

    // then
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToMiddleOfBatch() {
    // given
    final long firstBatchLastPosition = writeEvents(4);
    writeEvents(8);

    // when
    reader.seekToNextEvent(firstBatchLastPosition + 1);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next().getPosition()).isEqualTo(firstBatchLastPosition + 2);
    assertThat(reader.hasNext()).isTrue();
  }

  @Test
  public void shouldIterateMultipleTimes() {
    // given
    final int eventCount = 500;
    writeEvents(eventCount);

    // when
    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();

    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();

    reader.seekToFirstEvent();
    readerRule.assertEvents(eventCount, EVENT_VALUE);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToFirstEvent() {
    // given
    final long firstPosition =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    writeEvents(2);

    // when
    reader.seekToFirstEvent();

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().getPosition()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldSeekToFirstPositionWhenPositionBeforeFirstEvent() {
    // given
    final long firstPosition =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    writeEvents(2);

    // when
    reader.seek(firstPosition - 1);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().getPosition()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldNotSeekToEventBeyondLastEvent() {
    // given
    final long lastEventPosition = writeEvents(100);

    // when
    reader.seek(lastEventPosition + 1);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReturnNegativeOnSeekToEndOfEmptyLog() {
    // given
    final var reader = logStreamRule.getLogStreamReader();

    // when
    final var result = reader.seekToEnd();

    // then
    assertThat(result).isNegative();
  }

  @Test
  public void shouldSeekToNextEventWhenThereIsNone() {
    // given
    final long lastEventPosition = writeEvents(10);

    // when
    final boolean positionExists = reader.seekToNextEvent(lastEventPosition);

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThat(positionExists).isTrue();
    assertThat(reader.getPosition()).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldSeekToNextEvent() {
    // given
    final long lastEventPosition = writeEvents(10);

    // when
    final boolean positionExists = reader.seekToNextEvent(lastEventPosition - 1);

    // then
    assertThat(positionExists).isTrue();
    assertThat(reader).hasNext();
    assertThat(reader.next().getPosition()).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldNotSeekToNextEvent() {
    // given
    final long lastEventPosition = writeEvents(10);

    // when
    final boolean positionExists = reader.seekToNextEvent(lastEventPosition + 1);

    // then
    assertThat(positionExists).isFalse();
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToFirstEventWhenNextIsNegative() {
    // given
    final long firstEventPosition =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    writeEvents(10);
    reader.seekToEnd();

    // when
    final boolean positionExists = reader.seekToNextEvent(-1);

    // then
    assertThat(positionExists).isTrue();
    assertThat(reader).hasNext();
    assertThat(reader.next().getPosition()).isEqualTo(firstEventPosition);
  }

  @Test
  public void shouldPeekFirstEvent() {
    // given
    final var eventPosition1 =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    assertThat(reader.hasNext()).isTrue();

    // when
    final var nextEvent = reader.peekNext();

    // then
    assertThat(nextEvent.getPosition()).isEqualTo(eventPosition1);
  }

  @Test
  public void shouldPeekNextEvent() {
    // given
    final var eventPosition1 =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    final var eventPosition2 =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().getPosition()).isEqualTo(eventPosition1);

    // when
    final var nextEvent = reader.peekNext();

    // then
    assertThat(nextEvent.getPosition()).isEqualTo(eventPosition2);
  }

  @Test
  public void shouldPeekAndReadNextEvent() {
    // given
    final var eventPosition1 =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));
    final var eventPosition2 =
        writer.tryWrite(new MutableLogAppendEntry().recordValue(EVENT_VALUE));

    assertThat(reader.hasNext()).isTrue();

    final var event = reader.next();
    assertThat(event.getPosition()).isEqualTo(eventPosition1);
    assertThat(reader.hasNext()).isTrue();

    // when / then
    final var peekedEvent = reader.peekNext();
    assertThat(peekedEvent.getPosition()).isEqualTo(eventPosition2);

    assertThat(reader.hasNext()).isTrue();
    final var nextEvent = reader.next();
    assertThat(nextEvent.getPosition()).isEqualTo(eventPosition2);
  }

  @Test
  public void shouldThrowNoSuchElementExceptionOnPeek() {
    // given
    assertThat(reader.hasNext()).isFalse();

    // when / then
    assertThatThrownBy(reader::peekNext).isInstanceOf(NoSuchElementException.class);
  }

  private long writeEvents(final int eventCount) {
    final List<LogAppendEntry> entries =
        IntStream.rangeClosed(1, eventCount)
            .mapToObj(i -> new MutableLogAppendEntry().key(i).recordValue(EVENT_VALUE))
            .collect(Collectors.toList());

    return writer.tryWrite(entries);
  }
}
