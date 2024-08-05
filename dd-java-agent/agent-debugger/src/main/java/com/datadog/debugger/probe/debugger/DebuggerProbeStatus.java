package com.datadog.debugger.probe.debugger;

import datadog.trace.bootstrap.debugger.CapturedContext.Status;
import datadog.trace.bootstrap.debugger.ProbeImplementation;

public class DebuggerProbeStatus extends Status {
  public static final DebuggerProbeStatus EMPTY_DEBUGGER_STATUS =
      new DebuggerProbeStatus(ProbeImplementation.UNKNOWN, false);

  private boolean condition = true;

  private boolean hasConditionErrors;

  private boolean sampled = true;

  private boolean forceSampling;

  private String message;

  public DebuggerProbeStatus(ProbeImplementation probeImplementation) {
    super(probeImplementation);
  }

  private DebuggerProbeStatus(ProbeImplementation probeImplementation, boolean condition) {
    super(probeImplementation);
    this.condition = condition;
  }

  @Override
  public boolean isCapturing() {
    return condition;
  }

  public boolean shouldSend() {
    return sampled && condition && !hasConditionErrors;
  }

  public boolean shouldReportError() {
    return hasConditionErrors;
  }

  public boolean getCondition() {
    return condition;
  }

  public void setCondition(boolean value) {
    this.condition = value;
  }

  public boolean hasConditionErrors() {
    return hasConditionErrors;
  }

  public void setConditionErrors(boolean value) {
    this.hasConditionErrors = value;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setSampled(boolean sampled) {
    this.sampled = sampled;
  }

  public boolean isSampled() {
    return sampled;
  }

  public boolean isForceSampling() {
    return forceSampling;
  }

  public void setForceSampling(boolean forceSampling) {
    this.forceSampling = forceSampling;
  }
}
