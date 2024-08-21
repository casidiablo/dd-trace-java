package com.datadog.debugger.agent;

import static com.datadog.debugger.agent.SpanDecorationProbeInstrumentationTest.resolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static utils.InstrumentationTestHelper.compileAndLoadClass;

import com.datadog.debugger.probe.DebuggerProbe;
import com.datadog.debugger.sink.DebuggerSink;
import com.datadog.debugger.sink.ProbeStatusSink;
import com.datadog.debugger.util.TestTraceInterceptor;
import datadog.trace.agent.tooling.TracerInstaller;
import datadog.trace.api.Config;
import datadog.trace.api.interceptor.MutableSpan;
import datadog.trace.bootstrap.debugger.DebuggerContext;
import datadog.trace.bootstrap.debugger.ProbeId;
import datadog.trace.bootstrap.debugger.util.Redaction;
import datadog.trace.core.CoreTracer;
import datadog.trace.core.DDSpan;
import java.io.IOException;
import java.net.URISyntaxException;
import org.joor.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

public class DebuggerProbeInstrumentationTest extends ProbeInstrumentationTest {
  private static final ProbeId PROBE_ID = new ProbeId("86753098675309", 0);

  private TestTraceInterceptor traceInterceptor = new TestTraceInterceptor();

  @BeforeEach
  public void setUp() {
    CoreTracer tracer = CoreTracer.builder().build();
    TracerInstaller.forceInstallGlobalTracer(tracer);
    tracer.addTraceInterceptor(traceInterceptor);
  }

  @Override
  @AfterEach
  public void after() {
    super.after();
    Redaction.clearUserDefinedTypes();
  }

  private void registerConfiguration(String expectedClassName, Configuration configuration) {
    Config config = mock(Config.class);
    when(config.isDebuggerEnabled()).thenReturn(true);
    when(config.isDebuggerClassFileDumpEnabled()).thenReturn(true);
    when(config.isDebuggerCodeOriginEnabled()).thenReturn(true);
    when(config.getFinalDebuggerSnapshotUrl())
        .thenReturn("http://localhost:8126/debugger/v1/input");
    when(config.getFinalDebuggerSymDBUrl()).thenReturn("http://localhost:8126/symdb/v1/input");
    probeStatusSink = mock(ProbeStatusSink.class);
    currentTransformer =
        new DebuggerTransformer(
            config, configuration, null, new DebuggerSink(config, probeStatusSink));
    instr.addTransformer(currentTransformer);
    mockSink = new MockSink(config, probeStatusSink);
    DebuggerAgentHelper.injectSink(mockSink);
    DebuggerContext.initProbeResolver((encodedProbeId) -> resolver(encodedProbeId, configuration));
    DebuggerContext.initClassFilter(new DenyListHelper(null));
  }

  private void installProbe(String typeName, String methodName, String signature) {
    DebuggerProbe probe =
        DebuggerProbe.builder().probeId(PROBE_ID).where(typeName, methodName, signature).build();
    registerConfiguration(
        typeName, Configuration.builder().setService(SERVICE_NAME).add(probe).build());
  }

  @Test
  public void testSimpleMethod() throws IOException, URISyntaxException {
    final String CLASS_NAME = "com.datadog.debugger.CapturedSnapshot20";
    installProbe(CLASS_NAME, "process", "int (java.lang.String)");
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    assertEquals(84, result);
    MutableSpan span = traceInterceptor.getFirstSpan();
    assertEquals(
        PROBE_ID.getId(),
        span.getTags().get("_dd.ld.probe_id"),
        span.getTags().keySet().toString());
    String debugFlag =
        ((DDSpan) span.getLocalRootSpan()).context().getPropagationTags().getDebugPropagation();
    assertEquals("1", debugFlag);
    verify(probeStatusSink).addEmitting(ArgumentMatchers.eq(PROBE_ID));
  }
}
