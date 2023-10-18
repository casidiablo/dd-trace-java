package datadog.trace.instrumentation.gson

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.iast.propagation.PropagationModule

class JsonReaderInstrumentationTest extends AgentTestRunner {

  @Override
  protected void configurePreAgent() {
    injectSysConfig("dd.iast.enabled", "true")
  }

  void 'Test Gson instrumented'(){
    given:
    final gson = new Gson()

    when:
    final result = gson.fromJson('{"name": "nameTest", "value" : "valueTest"}', TestBean)

    then:
    result instanceof TestBean
    result.getName() == 'nameTest'
    result.getValue() == 'valueTest'
  }

  void 'test'() {
    given:
    final module = Mock(PropagationModule)
    InstrumentationBridge.registerIastModule(module)
    final gson = new Gson()

    when:
    final reader = new JsonReader(new StringReader(json))

    then:
    1 * module.taintIfInputIsTainted(_ as JsonReader, _ as StringReader)

    when:
    gson.fromJson(reader, clazz)

    then:
    calls * module.taintIfInputIsTainted(_ as String, _ as JsonReader)
    0 * _

    where:
    json | clazz | calls
    '"Test"' | String | 1
    '{"name": "nameTest", "value" : "valueTest"}' | TestBean | 4
    '[{"name": "nameTest", "value" : "valueTest"}]' | TestBean[] | 4
    '[{"name": "nameTest", "value" : "valueTest"}, {"name": "nameTest2", "value" : "valueTest2"}]' | TestBean[].class | 8
  }


  static final class TestBean {

    private String name

    private String value

    String getName() {
      return name
    }

    void setName(String name) {
      this.name = name
    }

    String getValue() {
      return value
    }

    void setValue(String value) {
      this.value = value
    }
  }
}
