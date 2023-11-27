package datadog.telemetry;

import datadog.communication.ddagent.DDAgentFeaturesDiscovery;
import datadog.communication.ddagent.SharedCommunicationObjects;
import datadog.telemetry.TelemetryRunnable.TelemetryPeriodicAction;
import datadog.telemetry.dependency.DependencyPeriodicAction;
import datadog.telemetry.dependency.DependencyService;
import datadog.telemetry.integration.IntegrationPeriodicAction;
import datadog.telemetry.log.LogPeriodicAction;
import datadog.telemetry.metric.CoreMetricsPeriodicAction;
import datadog.telemetry.metric.IastMetricPeriodicAction;
import datadog.telemetry.metric.WafMetricPeriodicAction;
import datadog.trace.api.Config;
import datadog.trace.api.Platform;
import datadog.trace.api.iast.telemetry.Verbosity;
import datadog.trace.util.AgentThreadFactory;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetrySystem {

  private static final long TELEMETRY_STOP_WAIT_MILLIS = 5000L;
  private static final Logger log = LoggerFactory.getLogger(TelemetrySystem.class);

  private static volatile Thread TELEMETRY_THREAD;
  private static volatile DependencyService DEPENDENCY_SERVICE;

  static DependencyService createDependencyService(final Instrumentation instrumentation) {
    if (Platform.isNativeImage()
        || instrumentation == null
        || !Config.get().isTelemetryDependencyServiceEnabled()) {
      return null;
    }
    final DependencyService dependencyService = new DependencyService();
    dependencyService.installOn(instrumentation);
    dependencyService.schedulePeriodicResolution();
    return dependencyService;
  }

  static Thread createTelemetryRunnable(
      TelemetryService telemetryService,
      DependencyService dependencyService,
      boolean telemetryMetricsEnabled) {
    DEPENDENCY_SERVICE = dependencyService;

    List<TelemetryPeriodicAction> actions = new ArrayList<>();
    if (telemetryMetricsEnabled) {
      actions.add(new CoreMetricsPeriodicAction());
      if (!Platform.isNativeImage()) {
        actions.add(new IntegrationPeriodicAction());
      }
      actions.add(new WafMetricPeriodicAction());
      if (Verbosity.OFF != Config.get().getIastTelemetryVerbosity()) {
        actions.add(new IastMetricPeriodicAction());
      }
    }
    if (null != dependencyService && !Platform.isNativeImage()) {
      actions.add(new DependencyPeriodicAction(dependencyService));
    }
    if (Config.get().isTelemetryLogCollectionEnabled()) {
      actions.add(new LogPeriodicAction());
      log.debug("Telemetry log collection enabled");
    }

    TelemetryRunnable telemetryRunnable = new TelemetryRunnable(telemetryService, actions);
    return AgentThreadFactory.newAgentThread(
        AgentThreadFactory.AgentThread.TELEMETRY, telemetryRunnable);
  }

  /** Called by reflection (see Agent.startTelemetry) */
  public static void startTelemetry(
      Instrumentation instrumentation, SharedCommunicationObjects sco) {
    Config config = Config.get();
    sco.createRemaining(config);
    DependencyService dependencyService = createDependencyService(instrumentation);
    boolean debug = config.isTelemetryDebugRequestsEnabled();
    DDAgentFeaturesDiscovery ddAgentFeaturesDiscovery = sco.featuresDiscovery(config);

    TelemetryClient agentClient = TelemetryClient.buildAgentClient(sco.okHttpClient, sco.agentUrl);
    TelemetryClient intakeClient =
        TelemetryClient.buildIntakeClient(
            config.getSite(),
            TimeUnit.SECONDS.toMillis(config.getAgentTimeout()),
            config.getApiKey());
    TelemetryService telemetryService =
        TelemetryService.build(ddAgentFeaturesDiscovery, agentClient, intakeClient, debug);

    boolean telemetryMetricsEnabled = config.isTelemetryMetricsEnabled();
    TELEMETRY_THREAD =
        createTelemetryRunnable(telemetryService, dependencyService, telemetryMetricsEnabled);
    TELEMETRY_THREAD.start();
  }

  /** Called by reflection (see Agent.stopTelemetry) */
  public static void stop() {
    DependencyService dependencyService = DEPENDENCY_SERVICE;
    if (dependencyService != null) {
      dependencyService.stop();
    }

    Thread telemetryThread = TELEMETRY_THREAD;
    if (telemetryThread != null) {
      telemetryThread.interrupt();
      try {
        telemetryThread.join(TELEMETRY_STOP_WAIT_MILLIS);
      } catch (InterruptedException e) {
        log.warn("Telemetry thread join was interrupted");
      }
      if (telemetryThread.isAlive()) {
        log.warn("Telemetry thread join was not completed");
      }
    }
  }
}
