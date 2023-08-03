/**
 * Datadog Telemetry API Generated by Openapi Generator
 * https://github.com/openapitools/openapi-generator
 *
 * <p>The version of the OpenAPI document: 1.0.0
 *
 * <p>NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 */
package datadog.telemetry.api;

import java.util.List;

public class AppClientConfigurationChange extends Payload {

  @com.squareup.moshi.Json(name = "configuration")
  private List<KeyValue> configuration = null;

  /**
   * Get configuration
   *
   * @return configuration
   */
  public List<KeyValue> getConfiguration() {
    return configuration;
  }

  /** Set configuration */
  public void setConfiguration(List<KeyValue> configuration) {
    this.configuration = configuration;
  }

  public AppClientConfigurationChange configuration(List<KeyValue> configuration) {
    this.configuration = configuration;
    return this;
  }

  public AppClientConfigurationChange addConfigurationItem(KeyValue configurationItem) {
    this.configuration.add(configurationItem);
    return this;
  }

  /** Create a string representation of this pojo. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AppClientConfigurationChange {\n");
    sb.append("    ").append(super.toString()).append("\n");
    sb.append("    configuration: ").append(configuration).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
