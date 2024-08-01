package datadog.trace.core.propagation

import static datadog.trace.api.TracePropagationStyle.NONE

import datadog.trace.api.Config
import datadog.trace.api.DDSpanId
import datadog.trace.api.DDTraceId
import datadog.trace.api.DynamicConfig
import datadog.trace.bootstrap.instrumentation.api.TagContext
import datadog.trace.bootstrap.instrumentation.api.ContextVisitors
import datadog.trace.test.util.DDSpecification
import spock.lang.Shared

import static datadog.trace.api.TracePropagationStyle.B3MULTI
import static datadog.trace.api.TracePropagationStyle.DATADOG
import static datadog.trace.api.TracePropagationStyle.TRACECONTEXT
import static datadog.trace.core.CoreTracer.TRACE_ID_MAX

class HttpExtractorTest extends DDSpecification {

  private static final String W3C_TRACE_ID = "00000000000000000000000000000001"
  private static final long W3C_SPAN_ID_LONG = 1311768467463790320L
  private static final String W3C_SPAN_ID = Long.toString(W3C_SPAN_ID_LONG)
  private static final CharSequence TRACE_PARENT_VALUE = '00-00000000000000000000000000000001-123456789abcdef0-01'

  @Shared
  String outOfRangeTraceId = (TRACE_ID_MAX + 1).toString()

  def "extract http headers using #styles"() {
    setup:
    Config config = Mock(Config) {
      getTracePropagationStylesToExtract() >> styles
    }
    DynamicConfig dynamicConfig = DynamicConfig.create()
      .setHeaderTags(["SOME_HEADER": "some-tag"])
      .setBaggageMapping([:])
      .apply()
    HttpCodec.Extractor extractor = HttpCodec.createExtractor(config, { dynamicConfig.captureTraceConfig() })

    final Map<String, String> actual = [:]
    if (datadogTraceId != null) {
      actual.put(DatadogHttpCodec.TRACE_ID_KEY.toUpperCase(), datadogTraceId)
    }
    if (datadogSpanId != null) {
      actual.put(DatadogHttpCodec.SPAN_ID_KEY.toUpperCase(), datadogSpanId)
    }
    if (b3TraceId != null) {
      actual.put(B3HttpCodec.TRACE_ID_KEY.toUpperCase(), b3TraceId)
    }
    if (b3SpanId != null) {
      actual.put(B3HttpCodec.SPAN_ID_KEY.toUpperCase(), b3SpanId)
    }
    if (w3cTraceParent != null) {
      actual.put(W3CHttpCodec.TRACE_PARENT_KEY.toUpperCase(), w3cTraceParent)
    }

    if (putDatadogFields) {
      actual.put("SOME_HEADER", "my-interesting-info")
    }

    when:
    final TagContext context = extractor.extract(actual, ContextVisitors.stringValuesMap())

    then:
    if (tagContext) {
      assert context instanceof TagContext
    } else {
      if (expectedTraceId == null) {
        assert context == null
      } else {
        if (expectedTraceId == W3C_TRACE_ID) {
          assert context.traceId == DDTraceId.fromHex(expectedTraceId)
        } else {
          assert context.traceId.toLong() == DDTraceId.from(expectedTraceId).toLong()
        }
        if (expectedSpanId == W3C_SPAN_ID) {
          assert context.spanId == W3C_SPAN_ID_LONG
        } else {
          assert context.spanId == DDSpanId.from(expectedSpanId)
        }
      }
    }

    if (expectDatadogFields) {
      if (tagContext && b3TraceId != null) {
        assert context.tags == ["b3.traceid": b3TraceId, "b3.spanid": b3SpanId, "some-tag": "my-interesting-info"]
        // TODO: check for w3ctraceid case
      } else {
        assert context.tags == ["some-tag": "my-interesting-info"]
      }
    }

    where:
    // spotless:off
    styles                           | datadogTraceId    | datadogSpanId     | b3TraceId         | b3SpanId          | w3cTraceParent     | expectedTraceId | expectedSpanId | putDatadogFields | expectDatadogFields | tagContext
    [DATADOG, B3MULTI]               | "1"               | "2"               | "a"               | "b"               | null               | "1"             | "2"            | true             | true                | false
    [DATADOG, B3MULTI]               | null              | null              | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | true
    [DATADOG, B3MULTI]               | null              | null              | "a"               | "b"               | null               | null            | null           | true             | true                | true
    [DATADOG]                        | "1"               | "2"               | "a"               | "b"               | null               | "1"             | "2"            | true             | true                | false
    [B3MULTI]                        | "1"               | "2"               | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | false
    [B3MULTI, DATADOG]               | "1"               | "2"               | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | false
    []                               | "1"               | "2"               | "a"               | "b"               | null               | null            | null           | false            | false               | false
    [DATADOG, B3MULTI]               | "abc"             | "2"               | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | false
    [DATADOG]                        | "abc"             | "2"               | "a"               | "b"               | null               | null            | null           | false            | false               | false
    [DATADOG, B3MULTI]               | outOfRangeTraceId | "2"               | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | false
    [DATADOG, B3MULTI]               | "1"               | outOfRangeTraceId | "a"               | "b"               | null               | "10"            | "11"           | false            | false               | false
    [DATADOG]                        | outOfRangeTraceId | "2"               | "a"               | "b"               | null               | null            | null           | false            | false               | false
    [DATADOG]                        | "1"               | outOfRangeTraceId | "a"               | "b"               | null               | null            | null           | false            | false               | false
    [DATADOG, B3MULTI]               | "1"               | "2"               | outOfRangeTraceId | "b"               | null               | "1"             | "2"            | true             | false               | false
    [DATADOG, B3MULTI]               | "1"               | "2"               | "a"               | outOfRangeTraceId | null               | "1"             | "2"            | true             | false               | false
    [NONE]                           | "1"               | "2"               | null              | null              | null               | null            | null           | true             | false               | true
    [DATADOG, TRACECONTEXT]          | "1"               | "2"               | null              | null              | TRACE_PARENT_VALUE | "1"             | W3C_SPAN_ID    | false            | false               | false
    [DATADOG, TRACECONTEXT, B3MULTI] | "1"               | "2"               | "1"               | "2"               | TRACE_PARENT_VALUE | "1"             | W3C_SPAN_ID    | false            | false               | false
    [TRACECONTEXT, DATADOG]          | "1"               | "2"               | null              | null              | TRACE_PARENT_VALUE | W3C_TRACE_ID    | W3C_SPAN_ID    | false            | false               | false
    [TRACECONTEXT, B3MULTI]          | null              | null              | "1"               | "2"               | TRACE_PARENT_VALUE | W3C_TRACE_ID    | W3C_SPAN_ID    | false            | false               | false
    [TRACECONTEXT, B3MULTI, DATADOG] | "1"               | "2"               | "1"               | "4"               | TRACE_PARENT_VALUE | W3C_TRACE_ID    | W3C_SPAN_ID    | false            | false               | false
    [B3MULTI, DATADOG, TRACECONTEXT] | "1"               | "2"               | "1"               | "4"               | TRACE_PARENT_VALUE | "1"             | W3C_SPAN_ID    | false            | false               | false
    [TRACECONTEXT]                   | null              | null              | null              | null              | TRACE_PARENT_VALUE | W3C_TRACE_ID    | W3C_SPAN_ID    | false            | false               | false
    // spotless:on
  }
}
