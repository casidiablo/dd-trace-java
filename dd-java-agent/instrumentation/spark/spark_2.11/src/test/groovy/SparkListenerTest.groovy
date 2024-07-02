import datadog.trace.instrumentation.spark.AbstractSparkListenerTest
import datadog.trace.instrumentation.spark.DatadogSpark211Listener
import org.apache.spark.SparkConf
import org.apache.spark.scheduler.SparkListener

class SparkListenerTest extends AbstractSparkListenerTest {
  @Override
  protected SparkListener getTestDatadogSparkListener() {
    def conf = new SparkConf()
    return new DatadogSpark211Listener(conf, "some_app_id", "some_version")
  }
}
