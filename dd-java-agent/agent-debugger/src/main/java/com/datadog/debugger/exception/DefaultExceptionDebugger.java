package com.datadog.debugger.exception;

import com.datadog.debugger.agent.ConfigurationUpdater;
import datadog.trace.bootstrap.debugger.DebuggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link DebuggerContext.ExceptionDebugger} that uses {@link
 * ExceptionProbeManager} to instrument the exception stacktrace and send snapshots.
 */
public class DefaultExceptionDebugger implements DebuggerContext.ExceptionDebugger {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExceptionDebugger.class);
  private final ExceptionProbeManager exceptionProbeManager;
  private final ConfigurationUpdater configurationUpdater;

  public DefaultExceptionDebugger(
      ExceptionProbeManager exceptionProbeManager, ConfigurationUpdater configurationUpdater) {
    this.exceptionProbeManager = exceptionProbeManager;
    this.configurationUpdater = configurationUpdater;
  }

  @Override
  public void handleException(Throwable t) {
    String fingerprint = Fingerprinter.fingerprint(t);
    if (fingerprint == null) {
      LOGGER.debug("Unable to fingerprint exception", t);
      return;
    }
    if (exceptionProbeManager.isAlreadyInstrumented(fingerprint)) {
      // TODO trigger send snapshots already captured
    } else {
      exceptionProbeManager.createProbesForException(fingerprint, t.getStackTrace());
      configurationUpdater.reapplyCurrentConfig();
    }
  }
}
