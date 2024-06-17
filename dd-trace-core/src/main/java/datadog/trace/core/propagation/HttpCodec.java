package datadog.trace.core.propagation;

import static datadog.trace.api.TracePropagationStyle.DATADOG;
import static datadog.trace.api.TracePropagationStyle.TRACECONTEXT;

import datadog.trace.api.Config;
import datadog.trace.api.DD128bTraceId;
import datadog.trace.api.DD64bTraceId;
import datadog.trace.api.DDTraceId;
import datadog.trace.api.TraceConfig;
import datadog.trace.api.TracePropagationStyle;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import datadog.trace.bootstrap.instrumentation.api.TagContext;
import datadog.trace.core.DDSpanContext;
import datadog.trace.core.DDSpanLink;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCodec {

  private static final Logger log = LoggerFactory.getLogger(HttpCodec.class);
  // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded
  static final String FORWARDED_KEY = "forwarded";
  static final String FORWARDED_FOR_KEY = "forwarded-for";
  static final String X_FORWARDED_PROTO_KEY = "x-forwarded-proto";
  static final String X_FORWARDED_HOST_KEY = "x-forwarded-host";
  static final String X_FORWARDED_KEY = "x-forwarded";
  static final String X_FORWARDED_FOR_KEY = "x-forwarded-for";
  static final String X_FORWARDED_PORT_KEY = "x-forwarded-port";

  // other headers which may contain real ip
  static final String X_CLIENT_IP_KEY = "x-client-ip";
  static final String TRUE_CLIENT_IP_KEY = "true-client-ip";
  static final String X_CLUSTER_CLIENT_IP_KEY = "x-cluster-client-ip";
  static final String X_REAL_IP_KEY = "x-real-ip";
  static final String USER_AGENT_KEY = "user-agent";
  static final String FASTLY_CLIENT_IP_KEY = "fastly-client-ip";
  static final String CF_CONNECTING_IP_KEY = "cf-connecting-ip";
  static final String CF_CONNECTING_IP_V6_KEY = "cf-connecting-ipv6";

  public interface Injector {
    <C> void inject(
        final DDSpanContext context, final C carrier, final AgentPropagation.Setter<C> setter);
  }

  /** This interface defines propagated context extractor. */
  public interface Extractor {
    /**
     * Extracts a propagated context from the given carrier using the provided getter.
     *
     * @param carrier The carrier containing the propagated context.
     * @param getter The getter used to extract data from the carrier.
     * @param <C> The type of the carrier.
     * @return {@code null} for failed context extraction, a {@link TagContext} instance for partial
     *     context extraction or an {@link ExtractedContext} for complete context extraction.
     */
    <C> TagContext extract(final C carrier, final AgentPropagation.ContextVisitor<C> getter);

    /**
     * Cleans up any thread local resources associated with this extractor.
     *
     * <p>Implementations should override this method if they need to clean up any resources.
     *
     * <p><i>Currently only used from tests.</i>
     */
    default void cleanup() {}
  }

  public static Injector createInjector(
      Config config,
      Set<TracePropagationStyle> styles,
      Map<String, String> invertedBaggageMapping) {
    logg("# CreateInjector returns CompoundInjector");
    ArrayList<Injector> injectors =
        new ArrayList<>(createInjectors(config, styles, invertedBaggageMapping).values());
    return new CompoundInjector(injectors);
  }

  public static Map<TracePropagationStyle, Injector> allInjectorsFor(
      Config config, Map<String, String> reverseBaggageMapping) {
    return createInjectors(
        config, EnumSet.allOf(TracePropagationStyle.class), reverseBaggageMapping);
  }

  private static  void logg(String s) {
    log.error("!!!! "+s);
  }

  private static Map<TracePropagationStyle, Injector> createInjectors(
      Config config,
      Set<TracePropagationStyle> propagationStyles,
      Map<String, String> reverseBaggageMapping) {
    EnumMap<TracePropagationStyle, Injector> result = new EnumMap<>(TracePropagationStyle.class);
    logg("createInjectors");
    for (TracePropagationStyle style : propagationStyles) {
      switch (style) {
        case DATADOG:
          result.put(style, DatadogHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case B3SINGLE:
          result.put(
              style,
              B3HttpCodec.newSingleInjector(config.isTracePropagationStyleB3PaddingEnabled()));
          break;
        case B3MULTI:
          result.put(
              style,
              B3HttpCodec.newMultiInjector(config.isTracePropagationStyleB3PaddingEnabled()));
          break;
        case HAYSTACK:
          result.put(style, HaystackHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case XRAY:
          result.put(style, XRayHttpCodec.newInjector(reverseBaggageMapping));
          break;
        case NONE:
          result.put(style, NoneCodec.INJECTOR);
          break;
        case TRACECONTEXT:
          result.put(style, W3CHttpCodec.newInjector(reverseBaggageMapping));
          break;
        default:
          log.debug("No implementation found to inject propagation style: {}", style);
          break;
      }
    }
    return result;
  }

  public static Extractor createExtractor(
      Config config, Supplier<TraceConfig> traceConfigSupplier) {
    final List<Extractor> extractors = new ArrayList<>();
    for (final TracePropagationStyle style : config.getTracePropagationStylesToExtract()) {
      logg("!!!! add extractor "+style);
      switch (style) {
        case DATADOG:
          extractors.add(DatadogHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case B3SINGLE:
          extractors.add(B3HttpCodec.newSingleExtractor(config, traceConfigSupplier));
          break;
        case B3MULTI:
          extractors.add(B3HttpCodec.newMultiExtractor(config, traceConfigSupplier));
          break;
        case HAYSTACK:
          extractors.add(HaystackHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case XRAY:
          extractors.add(XRayHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        case NONE:
          extractors.add(NoneCodec.newExtractor(config, traceConfigSupplier));
          break;
        case TRACECONTEXT:
          extractors.add(W3CHttpCodec.newExtractor(config, traceConfigSupplier));
          break;
        default:
          log.debug("No implementation found to extract propagation style: {}", style);
          break;
      }
    }
    switch (extractors.size()) {
      case 0:
        return StubExtractor.INSTANCE;
      case 1:
        return extractors.get(0);
      default:
        return new CompoundExtractor(extractors, config.isTracePropagationExtractFirst());
    }
  }

  public static class CompoundInjector implements Injector {

    private final List<Injector> injectors;

    public CompoundInjector(final List<Injector> injectors) {
      int x =1;
      for (Injector i : injectors) {
        logg("!!!! CompoundInjector #"+ x +" " +i);
        x++;
      }
      this.injectors = injectors;
    }

    @Override
    public <C> void inject(
        final DDSpanContext context, final C carrier, final AgentPropagation.Setter<C> setter) {
      logg("!!!! inject " + context + " " + carrier + " " + setter);
      log.debug("Inject context {}", context);
      for (final Injector injector : injectors) {
        logg("!!!! iiinjector "+injector);
        injector.inject(context, carrier, setter);
      }
    }
  }

  private static class StubExtractor implements Extractor {
    private static final StubExtractor INSTANCE = new StubExtractor();

    @Override
    public <C> TagContext extract(C carrier, AgentPropagation.ContextVisitor<C> getter) {
      return null;
    }
  }

  public static class CompoundExtractor implements Extractor {
    private final List<Extractor> extractors;
    private final boolean extractFirst;

    public CompoundExtractor(final List<Extractor> extractors, boolean extractFirst) {
      this.extractors = extractors;
      this.extractFirst = extractFirst;

      StringBuilder sb = new StringBuilder();
      for (Extractor e : extractors) {
        sb.append(e);
        sb.append(",");
      }
      logg("CompoundExtractor-"+extractFirst+"-"+extractors+"-" + sb);
    }

    @Override
    public <C> TagContext extract(
        final C carrier, final AgentPropagation.ContextVisitor<C> getter) {
      logg("extract");
      ExtractedContext context = null;
      TagContext partialContext = null;
      CharSequence w3cTraceParent = null;
      long ddSpanId = 0;
      DDTraceId ddTraceId = null;
      long w3cSpanId = 0;
      DDTraceId w3cTraceId = null;
      // Extract and cache all headers in advance
      ExtractionCache<C> extractionCache = new ExtractionCache<>(carrier, getter);
      logg("extractionCache = " + extractionCache);

      int x=0;
      for (final Extractor extractor : this.extractors) {
        logg("!!!! extractor #"+x+"= "+extractor);
        x++;
        TagContext extracted = extractor.extract(extractionCache, extractionCache);
        // Check if context is valid
        if (extracted instanceof ExtractedContext) {
          logg("!!!! extracted = "+extracted);
          ExtractedContext extractedContext = (ExtractedContext) extracted;
          // If no prior valid context, store it as first valid context
          logg("!!!! context = "+context);
          logg("!!!! extractedContext = "+extractedContext);
          logg("!!!! extractFirst = "+extractFirst);
          boolean comingFromTraceContext = extracted.getPropagationStyle() == TRACECONTEXT;
          boolean comingFromDatadogContext = extracted.getPropagationStyle() == DATADOG;
          if (context == null) {
            context = extractedContext;
            // Stop extraction if only extracting first valid context and drop everything else
            if (this.extractFirst) {
              break;
            }
          }
          // If another valid context is extracted
          else {
            if (traceIdMatch(context.getTraceId(), extractedContext.getTraceId())) {
              logg("trace ID matches "+context.getTraceId()+" "+extractedContext.getTraceId());
              logg("comingFromTraceContext = "+comingFromTraceContext);
              if (comingFromTraceContext) {
                // Propagate newly extracted W3C tracestate to first valid context
                String extractedTracestate =
                    extractedContext.getPropagationTags().getW3CTracestate();
                logg("extractedTracestate = "+extractedTracestate);
                context.getPropagationTags().updateW3CTracestate(extractedTracestate);
                logg("context.getPropagationTags() = "+context.getPropagationTags());
                logg("context.getPropagationTags().createTagMap = "+context.getPropagationTags().createTagMap());
                logg("context.getPropagationTags().getW3CTracestate = "+context.getPropagationTags().getW3CTracestate());
                logg("context="+context);
                logg("tags = " + context.getTags());

                CharSequence getLastParentId = context.getPropagationTags().getLastParentId();
                logg("getLastParentId = "+getLastParentId);
                logg("$$$ context span ID = "+context.getSpanId());
                logg("$$$ extracted context span ID = "+extractedContext.getSpanId());
              }
            } else {
              // Terminate extracted context and add it as span link
              context.addTerminatedContextLink(DDSpanLink.from((ExtractedContext) extracted));
              // TODO Note: Other vendor tracestate will be lost here
            }
          }
          // When iterating over the extractors the order is unknown so collect the dd and w3c values to be used after
          // applying all extractors
          if (comingFromTraceContext) {
            w3cTraceParent = extractedContext.getPropagationTags().getLastParentId(); // p value from tracestate
            w3cTraceId = extractedContext.getTraceId();
            w3cSpanId = extractedContext.getSpanId();
            logg("w3cTraceParent = "+w3cTraceParent);
            logg("w3cTraceId = "+w3cTraceId);
            logg("w3cSpanId = "+w3cSpanId);
          } else if (comingFromDatadogContext) {
            ddTraceId = extractedContext.getTraceId();
            ddSpanId = extractedContext.getSpanId();
            logg("datadogTraceId = "+ddTraceId);
            logg("datadogSpanId = "+ddSpanId);
          }
        }
        // Check if context is at least partial to keep it as first valid partial context found
        else if (extracted != null && partialContext == null) {
          partialContext = extracted;
        }
      }

      logg("ddTraceId  name = "+ddTraceId.getClass().getName());
      logg("w3cTraceId name = "+w3cTraceId.getClass().getName());
      logg("ddTraceId  hex = "+ddTraceId.toHexString());
      logg("w3cTraceId hex = "+w3cTraceId.toHexString());
      if (traceIdMatch(ddTraceId, w3cTraceId)) {
        logg("trace ID matches '"+ddTraceId+"' '"+w3cTraceId+"'");
        logg("w3cTraceParent = "+w3cTraceParent);
        logg("ddSpanId = "+ddSpanId);
        logg("w3cSpanId = "+w3cSpanId);
        if (ddSpanId != w3cSpanId) {
          if (w3cTraceParent != null && !"0000000000000000".contentEquals(w3cTraceParent)) {
            logg("updateLastParentId = "+w3cTraceParent);
            context.getPropagationTags().updateLastParentId(w3cTraceParent);
          } else {
            // if p is unset, _dd.parent_id SHOULD be set using the parent_id extracted from datadog (x-datadog-parent-id)
            context.getPropagationTags().updateLastParentId("xxx");
          }
        }
      } else {
        logg("trace ID does not match '" + ddTraceId + "' '" + w3cTraceId + "'");
      }

      if (context != null) {
        log.debug("!!!! KEEP Extract complete context {}", context);
        return context;
      } else if (partialContext != null) {
        log.debug("!!!! KEEP Extract incomplete context {}", partialContext);
        return partialContext;
      } else {
        log.debug("!!!! KEEP Extract no context");
        return null;
      }
    }
  }

  private static class ExtractionCache<C>
      implements AgentPropagation.KeyClassifier,
          AgentPropagation.ContextVisitor<ExtractionCache<?>> {
    /** Cached context key-values (even indexes are header names, odd indexes are header values). */
    private final List<String> keysAndValues;

    public ExtractionCache(C carrier, AgentPropagation.ContextVisitor<C> getter) {
      this.keysAndValues = new ArrayList<>(32);
      getter.forEachKey(carrier, this);
    }

    @Override
    public boolean accept(String key, String value) {
      this.keysAndValues.add(key);
      this.keysAndValues.add(value);
      return true;
    }

    @Override
    public void forEachKey(ExtractionCache<?> carrier, AgentPropagation.KeyClassifier classifier) {
      List<String> keysAndValues = carrier.keysAndValues;
      for (int i = 0; i < keysAndValues.size(); i += 2) {
        classifier.accept(keysAndValues.get(i), keysAndValues.get(i + 1));
      }
    }
  }

  /**
   * Checks if trace identifier matches, even if they are not encoded using the same size (64-bit vs
   * 128-bit).
   *
   * @param a A trace identifier to check.
   * @param b Another trace identifier to check.
   * @return {@code true} if the trace identifiers matches, {@code false} otherwise.
   */
  private static boolean traceIdMatch(DDTraceId a, DDTraceId b) {
    if (a instanceof DD128bTraceId && b instanceof DD128bTraceId
        || a instanceof DD64bTraceId && b instanceof DD64bTraceId) {
      return a.equals(b);
    } else {
      return a.toLong() == b.toLong();
    }
  }

  /** URL encode value */
  static String encode(final String value) {
    String encoded = value;
    try {
      encoded = URLEncoder.encode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.debug("Failed to encode value - {}", value);
    }
    return encoded;
  }

  /**
   * Encodes baggage value according <a href="https://www.w3.org/TR/baggage/#value">W3C RFC</a>.
   *
   * @param value The baggage value.
   * @return The encoded baggage value.
   */
  static String encodeBaggage(final String value) {
    // Fix encoding to comply with https://www.w3.org/TR/baggage/#value and use percent-encoding
    // (RFC3986)
    // for space ( ) instead of plus (+) from 'application/x-www-form' MIME encoding
    return encode(value).replace("+", "%20");
  }

  /** URL decode value */
  static String decode(final String value) {
    String decoded = value;
    try {
      decoded = URLDecoder.decode(value, "UTF-8");
    } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
      log.debug("Failed to decode value - {}", value);
    }
    return decoded;
  }

  static String firstHeaderValue(final String value) {
    if (value == null) {
      return null;
    }

    int firstComma = value.indexOf(',');
    return firstComma == -1 ? value : value.substring(0, firstComma).trim();
  }
}
