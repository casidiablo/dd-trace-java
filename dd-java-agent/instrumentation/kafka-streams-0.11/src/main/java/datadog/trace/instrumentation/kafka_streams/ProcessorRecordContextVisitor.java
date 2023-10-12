package datadog.trace.instrumentation.kafka_streams;

import static datadog.trace.instrumentation.kafka_streams.KafkaStreamsDecorator.KAFKA_PRODUCED_KEY;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.internals.ProcessorRecordContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessorRecordContextVisitor
    implements AgentPropagation.ContextVisitor<ProcessorRecordContext>,
        AgentPropagation.BinaryContextVisitor<ProcessorRecordContext>,
        AgentPropagation.BinarySetter<ProcessorRecordContext> {

  private static final Logger log = LoggerFactory.getLogger(ProcessorRecordContextVisitor.class);

  // Using a method handle here to avoid forking the instrumentation for versions 2.7+
  private static final MethodHandle HEADERS_METHOD;

  static {
    MethodHandle method;
    try {
      method =
          MethodHandles.publicLookup()
              .findVirtual(
                  ProcessorRecordContext.class, "headers", MethodType.methodType(Headers.class));
    } catch (Throwable e) {
      log.debug("Exception loading MethodHandle", e);
      method = null;
    }
    HEADERS_METHOD = method;
  }

  public static final ProcessorRecordContextVisitor PR_GETTER_SETTER =
      new ProcessorRecordContextVisitor();

  @Override
  public void forEachKey(
      ProcessorRecordContext carrier, AgentPropagation.KeyClassifier classifier) {
    if (HEADERS_METHOD == null) {
      return;
    }
    try {
      Headers headers = (Headers) HEADERS_METHOD.invokeExact(carrier);
      for (Header header : headers) {
        String key = header.key();
        byte[] value = header.value();
        if (null != value) {
          if (!classifier.accept(key, new String(header.value(), StandardCharsets.UTF_8))) {
            return;
          }
        }
      }
    } catch (Throwable ex) {
      log.debug("Exception getting headers", ex);
    }
  }

  @Override
  public void forEachKey(
      ProcessorRecordContext carrier, AgentPropagation.BinaryKeyClassifier classifier) {
    if (HEADERS_METHOD == null) {
      return;
    }
    try {
      Headers headers = (Headers) HEADERS_METHOD.invokeExact(carrier);
      for (Header header : headers) {
        String key = header.key();
        byte[] value = header.value();
        if (null != value) {
          if (!classifier.accept(key, value)) {
            return;
          }
        }
      }
    } catch (Throwable ex) {
      log.debug("Exception getting headers", ex);
    }
  }

  public long extractTimeInQueueStart(ProcessorRecordContext carrier) {
    if (HEADERS_METHOD == null) {
      return 0;
    }
    try {
      Headers headers = (Headers) HEADERS_METHOD.invokeExact(carrier);
      Header header = headers.lastHeader(KAFKA_PRODUCED_KEY);
      if (null != header) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put(header.value());
        buf.flip();
        return buf.getLong();
      }
    } catch (Throwable e) {
      log.debug("Unable to get kafka produced time", e);
    }
    return 0;
  }

  @Override
  public void set(ProcessorRecordContext carrier, String key, String value) {
    set(carrier, key, value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void set(ProcessorRecordContext carrier, String key, byte[] value) {
    if (HEADERS_METHOD == null) {
      return;
    }
    try {
      Headers headers = (Headers) HEADERS_METHOD.invokeExact(carrier);
      headers.remove(key).add(key, value);
    } catch (Throwable e) {
      log.debug("Unable to set value", e);
    }
  }
}
