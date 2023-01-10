package datadog.trace.opentelemetry1;

import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
class OtelTracer implements Tracer {
  private final AgentTracer.TracerAPI tracer;
  private final String instrumentationScopeName;

  public OtelTracer(String instrumentationScopeName) {
    this.instrumentationScopeName = instrumentationScopeName;
    this.tracer = AgentTracer.get();
  }

  @Override
  public SpanBuilder spanBuilder(String spanName) {
    AgentTracer.SpanBuilder delegate =
        this.tracer.buildSpan(spanName).withResourceName(this.instrumentationScopeName);
    return new OtelSpanBuilder(delegate);
  }
}
