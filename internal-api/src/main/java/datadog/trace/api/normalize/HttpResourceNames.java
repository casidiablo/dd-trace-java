package datadog.trace.api.normalize;

import datadog.trace.api.Config;
import datadog.trace.api.Pair;
import datadog.trace.api.cache.DDCache;
import datadog.trace.api.cache.DDCaches;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.ResourceNamePriorities;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpResourceNames {

  private static final Logger log = LoggerFactory.getLogger(HttpResourceNames.class);
  public static final UTF8BytesString DEFAULT_RESOURCE_NAME = UTF8BytesString.create("/");

  private static final Function<Pair<CharSequence, CharSequence>, UTF8BytesString> JOINER =
      input -> {
        CharSequence path = input.getRight();
        if (path == null) {
          return DEFAULT_RESOURCE_NAME;
        }
        StringBuilder sb;
        CharSequence method = input.getLeft();
        if (method == null) {
          sb = new StringBuilder(path.length());
        } else {
          sb = new StringBuilder(path.length() + method.length() + 1);
          sb.append(method);
          // uppercase the method part of the resource name
          for (int i = 0; i < sb.length(); i++) {
            sb.setCharAt(i, Character.toUpperCase(sb.charAt(i)));
          }
          sb.append(' ');
        }
        int l = path.length() - 1;
        if (instance().removeTrailingSlash && l > 0 && path.charAt(l) == '/') {
          // remove trailing slash from the resource name path if needed
          sb.append(path, 0, l);
        } else {
          sb.append(path);
        }
        return UTF8BytesString.create(sb);
      };

  private static final DDCache<Pair<CharSequence, CharSequence>, CharSequence> JOINER_CACHE =
      DDCaches.newFixedSizeCache(128);

  private static final SimpleHttpPathNormalizer simpleHttpPathNormalizer =
      new SimpleHttpPathNormalizer();

  // Not final for testing
  private static HttpResourceNames INSTANCE;

  private final AntPatternHttpPathNormalizer serverAntPatternHttpPathNormalizer;
  private final AntPatternHttpPathNormalizer clientAntPatternHttpPathNormalizer;
  private final boolean removeTrailingSlash;

  private static HttpResourceNames instance() {
    if (null == INSTANCE) {
      INSTANCE = new HttpResourceNames();
    }
    return INSTANCE;
  }

  private HttpResourceNames() {
    serverAntPatternHttpPathNormalizer =
        new AntPatternHttpPathNormalizer(Config.get().getHttpServerPathResourceNameMapping());
    clientAntPatternHttpPathNormalizer =
        new AntPatternHttpPathNormalizer(Config.get().getHttpClientPathResourceNameMapping());
    removeTrailingSlash = Config.get().getHttpResourceRemoveTrailingSlash();
  }

  public static AgentSpan setForServer(
      AgentSpan span, CharSequence method, CharSequence path, boolean encoded) {
    Pair<CharSequence, Byte> result = computeForServer(method, path, encoded);
    if (result.hasLeft()) {
      span.setResourceName(result.getLeft(), result.getRight());
    }

    return span;
  }

  public static Pair<CharSequence, Byte> computeForServer(
      CharSequence method, CharSequence path, boolean encoded) {
    byte priority;
    log.debug("keisuke log - current stack trace of computeForServer: {}", (Object) Thread.currentThread().getStackTrace());
    log.debug("keisuke log - the arguments of computeForServer | method: {}, path: {}, encoded: {}", method, path, encoded);

    String resourcePath =
        instance().serverAntPatternHttpPathNormalizer.normalize(path.toString(), encoded);
    log.debug("keisuke log - instance().serverAntPatternHttpPathNormalizer.normalize(path.toString(), encoded) = {}", resourcePath);
    if (resourcePath != null) {
      priority = ResourceNamePriorities.HTTP_SERVER_CONFIG_PATTERN_MATCH;
    } else {
      resourcePath = simpleHttpPathNormalizer.normalize(path.toString(), encoded);
      log.debug("keisuke log - simpleHttpPathNormalizer.normalize(path.toString(), encoded) = {}", resourcePath);
      priority = ResourceNamePriorities.HTTP_PATH_NORMALIZER;
    }

    return Pair.of(join(method, resourcePath), priority);
  }

  public static Pair<CharSequence, Byte> computeForClient(
      CharSequence method, CharSequence path, boolean encoded) {
    byte priority;
    log.debug("keisuke log - current stack trace of computeForClient: {}", (Object) Thread.currentThread().getStackTrace());
    log.debug("keisuke log - the arguments of computeForClient | method: {}, path: {}, encoded: {}", method, path, encoded);

    String resourcePath =
        instance().clientAntPatternHttpPathNormalizer.normalize(path.toString(), encoded);
    log.debug("keisuke log - instance().clientAntPatternHttpPathNormalizer.normalize(path.toString(), encoded) = {}", resourcePath);
    if (resourcePath != null) {
      priority = ResourceNamePriorities.HTTP_CLIENT_CONFIG_PATTERN_MATCH;
    } else {
      resourcePath = simpleHttpPathNormalizer.normalize(path.toString(), encoded);
      log.debug("keisuke log - simpleHttpPathNormalizer.normalize(path.toString(), encoded) = {}", resourcePath);
      priority = ResourceNamePriorities.HTTP_PATH_NORMALIZER;
    }
    return Pair.of(join(method, resourcePath), priority);
  }

  public static AgentSpan setForClient(
      AgentSpan span, CharSequence method, CharSequence path, boolean encoded) {
    Pair<CharSequence, Byte> result = computeForClient(method, path, encoded);
    log.debug("keisuke log - current stack trace of setForClient: {}", (Object) Thread.currentThread().getStackTrace());
    log.debug("keisuke log - the arguments of setForClient | method: {}, path: {}, encoded: {}", method, path, encoded);
    log.debug("keisuke log - result.hasLeft() = {}", result.hasLeft());
    if (result.hasLeft()) {
      log.debug("keisuke log - span before span.setResourceName(result.getLeft(), result.getRight()): {}", span);
      span.setResourceName(result.getLeft(), result.getRight());
      log.debug("keisuke log - span after span.setResourceName(result.getLeft(), result.getRight()): {}", span);
    }
    return span;
  }

  public static CharSequence join(CharSequence method, CharSequence path) {
    return JOINER_CACHE.computeIfAbsent(Pair.of(method, path), JOINER);
  }
}
