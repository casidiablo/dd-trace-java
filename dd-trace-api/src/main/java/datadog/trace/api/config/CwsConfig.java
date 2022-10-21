package datadog.trace.api.config;

public final class CwsConfig {
  public static final String CWS_ENABLED = "cws.enabled";
  public static final String CWS_TLS_REFRESH = "cws.tls.refresh";

  static final boolean DEFAULT_CWS_ENABLED = false;
  static final int DEFAULT_CWS_TLS_REFRESH = 5000;

  private CwsConfig() {}
}
