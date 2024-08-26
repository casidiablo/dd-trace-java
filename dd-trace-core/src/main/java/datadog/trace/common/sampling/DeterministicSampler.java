package datadog.trace.common.sampling;

import datadog.trace.core.CoreSpan;

/**
 * This implements the deterministic sampling algorithm used by the Datadog Agent as well as the
 * tracers for other languages
 */
public abstract class DeterministicSampler implements RateSampler {

  /** Uses trace-id as a sampling id */
  public static final class TraceSampler extends DeterministicSampler {

    public TraceSampler(double rate) {
      super(rate);
    }

    @Override
    protected <T extends CoreSpan<T>> long getSamplingId(T span) {
      return span.getTraceId().toLong();
    }
  }

  /** Uses span-id as a sampling id */
  public static final class SpanSampler extends DeterministicSampler {

    public SpanSampler(double rate) {
      super(rate);
    }

    @Override
    protected <T extends CoreSpan<T>> long getSamplingId(T span) {
      return span.getSpanId();
    }
  }

  private static final long KNUTH_FACTOR = 1111111111111111111L;

  private static final double MAX = Math.pow(2, 64) - 1;

  private final float rate;
  private final java.io.FileWriter fileWriter;

  public DeterministicSampler(final double rate) {
    this.rate = (float) rate;
    try {
    this.fileWriter = new java.io.FileWriter("/tmp/sampling.txt");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T extends CoreSpan<T>> boolean sample(final T span) {
    // unsigned 64 bit comparison with cutoff
    boolean toSample = getSamplingId(span) * KNUTH_FACTOR + Long.MIN_VALUE < cutoff(rate);

//    try {
//      fileWriter.write(":::: sampling span: " + span + " with rate: " + rate + " and cutoff: " + cutoff(rate) + " and samplingId: " + getSamplingId(span) + " and KNUTH_FACTOR: " + KNUTH_FACTOR + " and Long.MIN_VALUE: " + Long.MIN_VALUE + " and result: " + (getSamplingId(span) * KNUTH_FACTOR + Long.MIN_VALUE) + " < " + cutoff(rate) + " = " + toSample);
//      fileWriter.write("\n");
//      if (toSample) {
//        fileWriter.write(":::_: sampled out");
//        fileWriter.write("\n");
//      } else {
//        fileWriter.write(":::-: not sampled out");
//        fileWriter.write("\n");
//      }
//      fileWriter.flush();
//    } catch (java.io.IOException e) {
//      e.printStackTrace();
//    }
    return toSample;
  }

  protected abstract <T extends CoreSpan<T>> long getSamplingId(T span);

  @Override
  public double getSampleRate() {
    return rate;
  }

  public static long cutoff(double rate) {
    if (rate < 0.5) {
      return (long) (rate * MAX) + Long.MIN_VALUE;
    }
    if (rate < 1.0) {
      return (long) ((rate * MAX) + Long.MIN_VALUE);
    }
    return Long.MAX_VALUE;
  }
}
