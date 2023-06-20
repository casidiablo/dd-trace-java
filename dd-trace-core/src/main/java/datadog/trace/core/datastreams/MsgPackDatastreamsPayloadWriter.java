package datadog.trace.core.datastreams;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import datadog.communication.serialization.GrowableBuffer;
import datadog.communication.serialization.Writable;
import datadog.communication.serialization.WritableFormatter;
import datadog.communication.serialization.msgpack.MsgPackWriter;
import datadog.trace.api.WellKnownTags;
import datadog.trace.common.metrics.Sink;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgPackDatastreamsPayloadWriter implements DatastreamsPayloadWriter {
  private static final Logger log = LoggerFactory.getLogger(MsgPackDatastreamsPayloadWriter.class);
  private static final byte[] ENV = "Env".getBytes(ISO_8859_1);
  private static final byte[] PRIMARY_TAG = "PrimaryTag".getBytes(ISO_8859_1);
  private static final byte[] LANG = "Lang".getBytes(ISO_8859_1);
  private static final byte[] TRACER_VERSION = "TracerVersion".getBytes(ISO_8859_1);
  private static final byte[] STATS = "Stats".getBytes(ISO_8859_1);
  private static final byte[] START = "Start".getBytes(ISO_8859_1);
  private static final byte[] DURATION = "Duration".getBytes(ISO_8859_1);
  private static final byte[] PATHWAY_LATENCY = "PathwayLatency".getBytes(ISO_8859_1);
  private static final byte[] EDGE_LATENCY = "EdgeLatency".getBytes(ISO_8859_1);
  private static final byte[] SERVICE = "Service".getBytes(ISO_8859_1);
  private static final byte[] EDGE_TAGS = "EdgeTags".getBytes(ISO_8859_1);
  private static final byte[] BACKLOGS = "Backlogs".getBytes(ISO_8859_1);
  private static final byte[] HASH = "Hash".getBytes(ISO_8859_1);
  private static final byte[] PARENT_HASH = "ParentHash".getBytes(ISO_8859_1);
  private static final byte[] BACKLOG_VALUE = "Value".getBytes(ISO_8859_1);
  private static final byte[] BACKLOG_TAGS = "Tags".getBytes(ISO_8859_1);
  private static final byte[] THROUGHPUTS_BY_ORIGIN = "ThroughputsByOrigin".getBytes(ISO_8859_1);
  private static final byte[] THROUGHPUTS_BY_EDGE_START = "ThroughputsByEdgeStart".getBytes(ISO_8859_1);
  private static final byte[] THROUGHPUTS_BY_SERVICE_START = "ThroughputsByServiceStart".getBytes(ISO_8859_1);
  private static final byte[] FAN_IN = "FanIn".getBytes(ISO_8859_1);
  private static final byte[] FAN_OUT = "FanOut".getBytes(ISO_8859_1);
  private static final byte[] TERMINATED = "Terminated".getBytes(ISO_8859_1);
  private static final byte[] PRODUCED = "Produced".getBytes(ISO_8859_1);
  private static final byte[] CONSUMED = "Consumed".getBytes(ISO_8859_1);
  private static final byte[] GENERATED = "Generated".getBytes(ISO_8859_1);

  private static final int INITIAL_CAPACITY = 512 * 1024;

  private final WritableFormatter writer;
  private final Sink sink;
  private final GrowableBuffer buffer;
  private final WellKnownTags wellKnownTags;
  private final byte[] tracerVersionValue;
  private final byte[] primaryTagValue;

  public MsgPackDatastreamsPayloadWriter(
      Sink sink, WellKnownTags wellKnownTags, String tracerVersion, String primaryTag) {
    buffer = new GrowableBuffer(INITIAL_CAPACITY);
    writer = new MsgPackWriter(buffer);
    this.sink = sink;
    this.wellKnownTags = wellKnownTags;
    tracerVersionValue = tracerVersion.getBytes(ISO_8859_1);
    primaryTagValue = primaryTag == null ? new byte[0] : primaryTag.getBytes(ISO_8859_1);
  }

  public void reset() {
    buffer.reset();
  }

  @Override
  public void writePayload(Collection<StatsBucket> data) {
    writer.startMap(6);
    /* 1 */
    writer.writeUTF8(ENV);
    writer.writeUTF8(wellKnownTags.getEnv());

    /* 2 */
    writer.writeUTF8(SERVICE);
    writer.writeUTF8(wellKnownTags.getService());

    /* 3 */
    writer.writeUTF8(LANG);
    writer.writeUTF8(wellKnownTags.getLanguage());

    /* 4 */
    writer.writeUTF8(PRIMARY_TAG);
    writer.writeUTF8(primaryTagValue);

    /* 5 */
    writer.writeUTF8(TRACER_VERSION);
    writer.writeUTF8(tracerVersionValue);

    /* 6 */
    writer.writeUTF8(STATS);
    writer.startArray(data.size());
    for (StatsBucket bucket : data) {
      boolean hasBacklogs = !bucket.getBacklogs().isEmpty();
      boolean hasThroughputsByOrigin = !bucket.getThroughputsByOrigin().isEmpty();
      boolean hasThroughputsByEdgeStart = !bucket.getThroughputsByEdgeStart().isEmpty();
      boolean hasThroughputsByServiceStart = !bucket.getThroughputsByServiceStart().isEmpty();
      writer.startMap(3 + (hasBacklogs ? 1 : 0) + (hasThroughputsByOrigin ? 1 : 0) + (hasThroughputsByEdgeStart ? 1 : 0) + (hasThroughputsByServiceStart ? 1 : 0));

      /* 1 */
      writer.writeUTF8(START);
      writer.writeLong(bucket.getStartTimeNanos());

      /* 2 */
      writer.writeUTF8(DURATION);
      writer.writeLong(bucket.getBucketDurationNanos());

      /* 3 */
      writer.writeUTF8(STATS);
      writeBucket(bucket, writer);

      if (hasBacklogs) {
        /* 4 */
        writeBacklogs(bucket.getBacklogs(), writer);
      }

      if (hasThroughputsByOrigin) {
        /* 5 */
        writeThroughputs(THROUGHPUTS_BY_ORIGIN, bucket.getThroughputsByOrigin(), writer);
      }

      if (hasThroughputsByEdgeStart) {
        /* 6 */
        writeThroughputs(THROUGHPUTS_BY_EDGE_START, bucket.getThroughputsByEdgeStart(), writer);
      }

      if (hasThroughputsByServiceStart) {
        /* 7 */
        writeThroughputs(THROUGHPUTS_BY_SERVICE_START, bucket.getThroughputsByServiceStart(), writer);
      }
    }

    buffer.mark();
    sink.accept(buffer.messageCount(), buffer.slice());
    buffer.reset();
  }

  private void writeBucket(StatsBucket bucket, Writable packer) {
    Collection<StatsGroup> groups = bucket.getGroups();
    packer.startArray(groups.size());
    for (StatsGroup group : groups) {
      boolean firstNode = group.getEdgeTags().isEmpty();

      packer.startMap(firstNode ? 4 : 5);

      /* 1 */
      packer.writeUTF8(PATHWAY_LATENCY);
      packer.writeBinary(group.getPathwayLatency().serialize());

      /* 2 */
      packer.writeUTF8(EDGE_LATENCY);
      packer.writeBinary(group.getEdgeLatency().serialize());

      /* 3 */
      packer.writeUTF8(HASH);
      packer.writeUnsignedLong(group.getHash());

      /* 4 */
      packer.writeUTF8(PARENT_HASH);
      packer.writeUnsignedLong(group.getParentHash());

      if (!firstNode) {
        /* 5 */
        packer.writeUTF8(EDGE_TAGS);
        packer.startArray(group.getEdgeTags().size());
        for (String tag : group.getEdgeTags()) {
          packer.writeString(tag, null);
        }
      }
    }
  }

  private void writeThroughputs(byte[] header, Collection<Throughput> throughputs, Writable packer) {
    packer.writeUTF8(header);
    packer.startArray(throughputs.size());
    for (Throughput throughput : throughputs) {
      writer.startMap(7);

      packer.writeUTF8(FAN_IN);
      packer.writeLong(throughput.getFanIn());

      packer.writeUTF8(FAN_OUT);
      packer.writeLong(throughput.getFanOut());

      packer.writeUTF8(TERMINATED);
      packer.writeLong(throughput.getTerminated());

      packer.writeUTF8(PRODUCED);
      packer.writeLong(throughput.getProduced());

      packer.writeUTF8(CONSUMED);
      packer.writeLong(throughput.getConsumed());

      packer.writeUTF8(GENERATED);
      packer.writeLong(throughput.getGenerated());

      packer.writeUTF8(HASH);
      packer.writeUnsignedLong(throughput.getHash());
    }
  }

  private void writeBacklogs(Collection<Map.Entry<List<String>, Long>> backlogs, Writable packer) {
    packer.writeUTF8(BACKLOGS);
    packer.startArray(backlogs.size());
    for (Map.Entry<List<String>, Long> entry : backlogs) {
      packer.startMap(2);
      packer.writeUTF8(BACKLOG_TAGS);
      packer.startArray(entry.getKey().size());
      for (String tag : entry.getKey()) {
        packer.writeString(tag, null);
      }
      packer.writeUTF8(BACKLOG_VALUE);
      packer.writeLong(entry.getValue());
    }
  }
}
