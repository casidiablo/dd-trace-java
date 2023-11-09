package com.datadog.profiling.ddprof;

import datadog.trace.api.profiling.ProfilingContextAttribute;
import datadog.trace.api.profiling.ProfilingScope;
import datadog.trace.bootstrap.instrumentation.api.ProfilerContext;
import datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration;

/**
 * This class must be installed early to be able to see all scope initialisations, which means it
 * must not be modified to depend on JFR, so that it can be installed before tracing starts.
 */
public class DatadogProfilingIntegration implements ProfilingContextIntegration {

  private static final DatadogProfiler DDPROF = DatadogProfiler.getInstance();
  private static final int SPAN_NAME_INDEX = DDPROF.operationNameOffset();
  private static final int RESOURCE_NAME_INDEX = DDPROF.resourceNameOffset();
  private static final boolean WALLCLOCK_ENABLED =
      DatadogProfilerConfig.isWallClockProfilerEnabled();

  @Override
  public void onAttach() {
    if (WALLCLOCK_ENABLED) {
      DDPROF.addThread();
    }
  }

  @Override
  public void onDetach() {
    if (WALLCLOCK_ENABLED) {
      DDPROF.removeThread();
    }
  }

  @Override
  public int encode(CharSequence constant) {
    return DDPROF.encode(constant);
  }

  @Override
  public int encodeOperationName(CharSequence constant) {
    if (SPAN_NAME_INDEX >= 0) {
      return DDPROF.encode(constant);
    }
    return 0;
  }

  @Override
  public int encodeResourceName(CharSequence constant) {
    if (RESOURCE_NAME_INDEX >= 0) {
      return DDPROF.encode(constant);
    }
    return 0;
  }

  @Override
  public String name() {
    return "ddprof";
  }

  @Override
  public void setContext(ProfilerContext profilerContext) {
    DDPROF.setSpanContext(profilerContext.getSpanId(), profilerContext.getRootSpanId());
    DDPROF.setContextValue(SPAN_NAME_INDEX, profilerContext.getEncodedOperationName());
    DDPROF.setContextValue(RESOURCE_NAME_INDEX, profilerContext.getEncodedResourceName());
  }

  @Override
  public void clearContext() {
    DDPROF.clearSpanContext();
    DDPROF.clearContextValue(SPAN_NAME_INDEX);
    DDPROF.clearContextValue(RESOURCE_NAME_INDEX);
  }

  @Override
  public void setContext(long rootSpanId, long spanId) {
    DDPROF.setSpanContext(spanId, rootSpanId);
  }

  @Override
  public ProfilingContextAttribute createContextAttribute(String attribute) {
    return new DatadogProfilerContextSetter(attribute, DDPROF);
  }

  @Override
  public ProfilingScope newScope() {
    return new DatadogProfilingScope(DDPROF);
  }
}
