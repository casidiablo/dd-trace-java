package datadog.trace.api.config;

/** Constant with names of configuration options for CI visibility. */
public final class CiVisibilityConfig {

  public static final String CIVISIBILITY_ENABLED = "civisibility.enabled";
  public static final String CIVISIBILITY_AGENTLESS_ENABLED = "civisibility.agentless.enabled";
  public static final String CIVISIBILITY_AGENTLESS_URL = "civisibility.agentless.url";

  static final boolean DEFAULT_CIVISIBILITY_ENABLED = false;
  static final boolean DEFAULT_CIVISIBILITY_AGENTLESS_ENABLED = false;

  private CiVisibilityConfig() {}
}
