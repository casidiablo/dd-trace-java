package datadog.trace.civisibility.config

import datadog.trace.api.civisibility.config.EarlyFlakeDetectionSettings
import datadog.trace.api.civisibility.config.ModuleExecutionSettings
import datadog.trace.api.civisibility.config.TestIdentifier
import spock.lang.Specification

class ModuleExecutionSettingsSerializerTest extends Specification {

  def "test serialization: #settings"() {
    when:
    def serialized = ModuleExecutionSettingsSerializer.serialize(settings)
    def deserialized = ModuleExecutionSettingsSerializer.deserialize(serialized)

    then:
    deserialized == settings

    where:
    settings << [
      new ModuleExecutionSettings(
      false, false, false, false, EarlyFlakeDetectionSettings.DEFAULT, [:], [], null, [:], null, [:], []),
      new ModuleExecutionSettings(
      true, true, false, true,
      new EarlyFlakeDetectionSettings(true, [], 10),
      ["a": "b", "propName": "propValue"],
      ["f"],
      "",
      ["module": [new TestIdentifier("a", "bc", "def", null), new TestIdentifier("abc", "de", "f", null)]],
      [new TestIdentifier("suite", "name", null, null)],
      ["bundle": [new TestIdentifier("a", "b", "c", null)]],
      ["a", "bcde", "f", "ghhi"]
      ),
      new ModuleExecutionSettings(
      false, false, true, false,
      new EarlyFlakeDetectionSettings(true, [new EarlyFlakeDetectionSettings.ExecutionsByDuration(10, 20)], 10),
      ["a": "b", "propName": "propValue"],
      ["f", "e"],
      "itrCorrelationId",
      [:],
      [new TestIdentifier("suite", "name", null, null), new TestIdentifier("a", "b", "c", null)],
      ["bundle": [new TestIdentifier("a", "b", "c", null), new TestIdentifier("aa", "bb", "cc", null)]],
      ["a", "bcde", "f", "ghhi"]
      ),
      new ModuleExecutionSettings(
      true, true, true, true,
      new EarlyFlakeDetectionSettings(true, [
        new EarlyFlakeDetectionSettings.ExecutionsByDuration(10, 20),
        new EarlyFlakeDetectionSettings.ExecutionsByDuration(30, 40)
      ], 10),
      ["a": "b", "propName": "propValue", "anotherProp": "value"],
      ["f", "e", "g"],
      "itrCorrelationId",
      ["module": [new TestIdentifier("a", "bc", "def", null), new TestIdentifier("abc", "de", "f", null)], "module-b": [new TestIdentifier("suite", "name", null, null)], "module-c": []],
      [],
      ["bundle": [new TestIdentifier("a", "b", "c", null)], "bundle-2": [new TestIdentifier("aa", "bb", "cc", null)]],
      []
      )
    ]
  }
}
