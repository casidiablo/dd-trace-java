package com.datadog.debugger.probe.debugger;

import com.datadog.debugger.el.ProbeCondition;
import com.datadog.debugger.probe.DebuggerProbe;
import com.datadog.debugger.probe.LogProbe.Sampling;
import com.datadog.debugger.probe.ProbeDefinition;

public class Builder extends ProbeDefinition.Builder<Builder> {
  private ProbeCondition probeCondition;

  private Sampling sampling;

  public Builder sampling(Sampling sampling) {
    this.sampling = sampling;
    return this;
  }

  public Builder when(ProbeCondition probeCondition) {
    this.probeCondition = probeCondition;
    return this;
  }

  public Builder sampling(double rateLimit) {
    return sampling(new Sampling(rateLimit));
  }

  public DebuggerProbe build() {
    return new DebuggerProbe(language, probeId, tagStrs, where, probeCondition, sampling);
  }
}
