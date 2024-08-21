package com.datadog.debugger.probe;

import com.datadog.debugger.agent.DebuggerAgent;
import com.datadog.debugger.agent.Generated;
import com.datadog.debugger.el.EvaluationException;
import com.datadog.debugger.el.ProbeCondition;
import com.datadog.debugger.instrumentation.CapturedContextInstrumentor;
import com.datadog.debugger.instrumentation.DiagnosticMessage;
import com.datadog.debugger.instrumentation.InstrumentationResult;
import com.datadog.debugger.instrumentation.MethodInfo;
import com.datadog.debugger.probe.LogProbe.Sampling;
import com.datadog.debugger.probe.debugger.DebuggerProbeStatus;
import com.datadog.debugger.sink.DebuggerSink;
import com.datadog.debugger.sink.Snapshot;
import com.squareup.moshi.Json;
import datadog.trace.bootstrap.debugger.CapturedContext;
import datadog.trace.bootstrap.debugger.CapturedContext.Status;
import datadog.trace.bootstrap.debugger.DebuggerContext;
import datadog.trace.bootstrap.debugger.EvaluationError;
import datadog.trace.bootstrap.debugger.MethodLocation;
import datadog.trace.bootstrap.debugger.ProbeId;
import datadog.trace.bootstrap.debugger.ProbeRateLimiter;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebuggerProbe extends ProbeDefinition {
  private static final Logger LOGGER = LoggerFactory.getLogger(DebuggerProbe.class);

  @Json(name = "when")
  private final ProbeCondition probeCondition;

  private final Sampling sampling;

  // no-arg constructor is required by Moshi to avoid creating instance with unsafe and by-passing
  // constructors, including field initializers.
  public DebuggerProbe() {
    this(LANGUAGE, null, (Tag[]) null, null, null, null);
  }

  public DebuggerProbe(
      String language,
      ProbeId probeId,
      String[] tagStrs,
      Where where,
      ProbeCondition probeCondition,
      Sampling sampling) {
    this(language, probeId, Tag.fromStrings(tagStrs), where, probeCondition, sampling);
  }

  private DebuggerProbe(
      String language,
      ProbeId probeId,
      Tag[] tags,
      Where where,
      ProbeCondition probeCondition,
      Sampling sampling) {
    super(language, probeId, tags, where, MethodLocation.ENTRY);
    this.probeCondition = probeCondition;
    this.sampling = sampling;
  }

  public ProbeCondition getProbeCondition() {
    return probeCondition;
  }

  public Sampling getSampling() {
    return sampling;
  }

  @Override
  public InstrumentationResult.Status instrument(
      MethodInfo methodInfo, List<DiagnosticMessage> diagnostics, List<ProbeId> probeIds) {
    return new CapturedContextInstrumentor(this, methodInfo, diagnostics, probeIds, false, null)
        .instrument();
  }

  @Override
  public void evaluate(
      CapturedContext context, CapturedContext.Status status, MethodLocation methodLocation) {
    if (!(status instanceof DebuggerProbeStatus)) {
      throw new IllegalStateException("Invalid status: " + status.getClass());
    }

    DebuggerProbeStatus debugStatus = (DebuggerProbeStatus) status;
    if (!hasCondition()) {
      // sample when no condition associated
      sample(debugStatus, methodLocation);
    }
    debugStatus.setCondition(evaluateCondition(context, debugStatus));
    CapturedContext.CapturedThrowable throwable = context.getCapturedThrowable();
    if (debugStatus.hasConditionErrors() && throwable != null) {
      debugStatus.addError(
          new EvaluationError(
              "uncaught exception", throwable.getType() + ": " + throwable.getMessage()));
    }
    if (hasCondition() && (debugStatus.getCondition() || debugStatus.hasConditionErrors())) {
      // sample if probe has condition and condition is true or has error
      sample(debugStatus, methodLocation);
    }
  }

  private void sample(DebuggerProbeStatus debuggerProbeStatus, MethodLocation methodLocation) {
    if (debuggerProbeStatus.isForceSampling()) {
      return;
    }
    // sample only once and when we need to evaluate
    if (!MethodLocation.isSame(methodLocation, evaluateAt)) {
      return;
    }
    boolean sampled = ProbeRateLimiter.tryProbe(id);
    debuggerProbeStatus.setSampled(sampled);
    if (!sampled) {
      DebuggerAgent.getSink().skipSnapshot(id, DebuggerContext.SkipCause.RATE);
    }
  }

  private boolean evaluateCondition(CapturedContext capture, DebuggerProbeStatus status) {
    if (probeCondition == null) {
      return true;
    }
    long startTs = System.nanoTime();
    try {
      if (!probeCondition.execute(capture)) {
        return false;
      }
    } catch (EvaluationException ex) {
      status.addError(new EvaluationError(ex.getExpr(), ex.getMessage()));
      status.setConditionErrors(true);
      return false;
    } finally {
      LOGGER.debug(
          "ProbeCondition for probe[{}] evaluated in {}ns", id, (System.nanoTime() - startTs));
    }
    return true;
  }

  @Override
  public void commit(
      CapturedContext entryContext,
      CapturedContext exitContext,
      List<CapturedContext.CapturedThrowable> caughtExceptions) {
    decorateTags();
    Snapshot snapshot = createSnapshot();
    boolean shouldCommit = fillSnapshot(entryContext, exitContext, caughtExceptions, snapshot);
    DebuggerSink sink = DebuggerAgent.getSink();
    if (shouldCommit) {
      commitSnapshot(snapshot, sink);
    } else {
      sink.skipSnapshot(id, DebuggerContext.SkipCause.CONDITION);
    }
  }

  protected Snapshot createSnapshot() {
    return new Snapshot(Thread.currentThread(), this, -1);
  }

  protected void commitSnapshot(Snapshot snapshot, DebuggerSink sink) {
    /*
     * Record stack trace having the caller of this method as 'top' frame.
     * For this it is necessary to discard:
     * - Thread.currentThread().getStackTrace()
     * - Snapshot.recordStackTrace()
     * - LogProbe.commitSnapshot
     * - ProbeDefinition.commit()
     * - DebuggerContext.commit() or DebuggerContext.evalAndCommit()
     */
    if (isCaptureSnapshot()) {
      snapshot.recordStackTrace(5);
      sink.addSnapshot(snapshot);
    } else {
      sink.addHighRateSnapshot(snapshot);
    }
  }

  protected boolean fillSnapshot(
      CapturedContext entryContext,
      CapturedContext exitContext,
      List<CapturedContext.CapturedThrowable> caughtExceptions,
      Snapshot snapshot) {
    DebuggerProbeStatus entryStatus = convertStatus(entryContext.getStatus(probeId.getEncodedId()));
    DebuggerProbeStatus exitStatus = convertStatus(exitContext.getStatus(probeId.getEncodedId()));
    String message = null;
    String traceId = null;
    String spanId = null;
    switch (evaluateAt) {
      case ENTRY:
      case DEFAULT:
        message = entryStatus.getMessage();
        traceId = entryContext.getTraceId();
        spanId = entryContext.getSpanId();
        break;
      case EXIT:
        message = exitStatus.getMessage();
        traceId = exitContext.getTraceId();
        spanId = exitContext.getSpanId();
        break;
    }
    boolean shouldCommit = false;
    if (entryStatus.shouldSend() && exitStatus.shouldSend()) {
      snapshot.setTraceId(traceId);
      snapshot.setSpanId(spanId);
      if (isCaptureSnapshot()) {
        snapshot.setEntry(entryContext);
        snapshot.setExit(exitContext);
      }
      snapshot.setMessage(message);
      snapshot.setDuration(exitContext.getDuration());
      snapshot.addCaughtExceptions(caughtExceptions);
      shouldCommit = true;
    }
    if (entryStatus.shouldReportError()) {
      if (entryContext.getCapturedThrowable() != null) {
        // report also uncaught exception
        snapshot.setEntry(entryContext);
      }
      snapshot.addEvaluationErrors(entryStatus.getErrors());
      shouldCommit = true;
    }
    if (exitStatus.shouldReportError()) {
      if (exitContext.getCapturedThrowable() != null) {
        // report also uncaught exception
        snapshot.setExit(exitContext);
      }
      snapshot.addEvaluationErrors(exitStatus.getErrors());
      shouldCommit = true;
    }
    return shouldCommit;
  }

  private DebuggerProbeStatus convertStatus(Status status) {
    if (status == CapturedContext.Status.EMPTY_STATUS) {
      return DebuggerProbeStatus.EMPTY_DEBUGGER_STATUS;
    }
    return (DebuggerProbeStatus) status;
  }

  private void decorateTags() {
    AgentTracer.TracerAPI tracerAPI = AgentTracer.get();
    AgentSpan agentSpan = tracerAPI.activeSpan();
    if (agentSpan == null) {
      LOGGER.debug("Cannot find current active span");
      return;
    }
    agentSpan = agentSpan.getLocalRootSpan();
    if (agentSpan == null) {
      LOGGER.debug("Cannot find root span");
      return;
    }
    agentSpan.setTag("_dd.p.debug", "1");
    agentSpan.setTag("_dd.ld.probe_id", probeId.getId());

    DebuggerAgent.getSink().getProbeStatusSink().addEmitting(probeId);
  }

  @Override
  public boolean hasCondition() {
    return probeCondition != null;
  }

  @Override
  public CapturedContext.Status createStatus() {
    return new DebuggerProbeStatus(this);
  }

  @Generated
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DebuggerProbe that = (DebuggerProbe) o;
    return Objects.equals(language, that.language)
        && Objects.equals(id, that.id)
        && version == that.version
        && Arrays.equals(tags, that.tags)
        && Objects.equals(tagMap, that.tagMap)
        && Objects.equals(where, that.where)
        && Objects.equals(evaluateAt, that.evaluateAt)
        && Objects.equals(probeCondition, that.probeCondition)
        && Objects.equals(sampling, that.sampling);
  }

  @Generated
  @Override
  public int hashCode() {
    int result =
        Objects.hash(language, id, version, tagMap, where, evaluateAt, probeCondition, sampling);
    result = 31 * result + Arrays.hashCode(tags);
    return result;
  }

  @Generated
  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "language='"
        + language
        + '\''
        + ", id='"
        + id
        + '\''
        + ", version="
        + version
        + ", tags="
        + Arrays.toString(tags)
        + ", tagMap="
        + tagMap
        + ", where="
        + where
        + ", evaluateAt="
        + evaluateAt
        + ", when="
        + probeCondition
        + ", sampling="
        + sampling
        + "} ";
  }

  public static com.datadog.debugger.probe.debugger.Builder builder() {
    return new com.datadog.debugger.probe.debugger.Builder();
  }
}
