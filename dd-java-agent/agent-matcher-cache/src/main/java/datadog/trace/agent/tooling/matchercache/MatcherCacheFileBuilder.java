package datadog.trace.agent.tooling.matchercache;

import datadog.trace.agent.tooling.matchercache.classfinder.ClassCollection;
import datadog.trace.agent.tooling.matchercache.classfinder.ClassCollectionLoader;
import datadog.trace.agent.tooling.matchercache.classfinder.ClassFinder;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatcherCacheFileBuilder {
  private static final Logger log = LoggerFactory.getLogger(MatcherCacheFileBuilder.class);

  private final ClassFinder classFinder;
  private final MatcherCacheBuilder matcherCacheBuilder;
  private final ClassMatchers classMatchers;

  public MatcherCacheFileBuilder(
      ClassFinder classFinder,
      MatcherCacheBuilder matcherCacheBuilder,
      ClassMatchers classMatchers) {
    this.classFinder = classFinder;
    this.matcherCacheBuilder = matcherCacheBuilder;
    this.classMatchers = classMatchers;
  }

  public void buildMatcherCacheFile(MatcherCacheFileBuilderParams params) {
    if (!params.validate()) {
      return;
    }

    fillFrom(new File(params.getJavaHome()));

    fillFrom(params.getDDAgentJar());
    for (String cp : params.getClassPaths()) {
      fillFrom(new File(cp));
    }

    // TODO if com.sun.proxy.* never instrumented add them to global ignores, otherwise remove it
    //    matcherCacheBuilder.addSkippedPackage("com.sun.proxy", "<skip-list>");

    if (params.getOutputCacheTextFile() != null) {
      try {
        matcherCacheBuilder.serializeText(new File(params.getOutputCacheTextFile()));
      } catch (IOException e) {
        log.error(
            "Failed to serialize matcher cache text into " + params.getOutputCacheTextFile(), e);
        throw new RuntimeException(e);
      }
    }

    matcherCacheBuilder.optimize();

    // TODO implement separate param for matcher cache text report output file
    try {
      matcherCacheBuilder.serializeBinary(new File(params.getOutputCacheDataFile()));
    } catch (IOException e) {
      log.error(
          "Failed to serialize matcher cache data into " + params.getOutputCacheDataFile(), e);
      throw new RuntimeException(e);
    }
  }

  private void fillFrom(File classPath) {
    final int javaMajorVersion = matcherCacheBuilder.getJavaMajorVersion();
    try {
      ClassCollection classes = classFinder.findClassesIn(classPath);
      ClassCollectionLoader classLoader = new ClassCollectionLoader(classes, javaMajorVersion);
      MatcherCacheBuilder.Stats stats =
          matcherCacheBuilder.fill(classes, classLoader, classMatchers);
      log.info("Scanned {}: {}", classPath, stats);
    } catch (IOException e) {
      log.error("Failed to scan: " + classPath, e);
    }
  }
}
