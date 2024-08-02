package datadog.trace.instrumentation.commonshttpclient;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.api.iast.Sink;
import datadog.trace.api.iast.VulnerabilityTypes;
import datadog.trace.instrumentation.appsec.rasp.modules.NetworkConnectionModule;
import datadog.trace.instrumentation.appsec.utils.InstrumentationLogger;
import net.bytebuddy.asm.Advice;
import org.apache.commons.httpclient.HttpMethod;

@AutoService(InstrumenterModule.class)
public class RaspCommonsHttpClientInstrumentation extends InstrumenterModule.AppSec
    implements Instrumenter.ForSingleType {

  public RaspCommonsHttpClientInstrumentation() {
    super("commons-http-client");
  }

  @Override
  public String instrumentedType() {
    return "org.apache.commons.httpclient.HttpClient";
  }

  @Override
  public String muzzleDirective() {
    return "commons-http-client-x";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      InstrumentationLogger.class.getName(), NetworkConnectionModule.class.getName()
    };
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod()
            .and(named("executeMethod"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod"))),
        RaspCommonsHttpClientInstrumentation.class.getName() + "$NetworkConnectionRaspAdvice");
  }

  public static class NetworkConnectionRaspAdvice {
    @Advice.OnMethodEnter()
    @Sink(VulnerabilityTypes.SSRF)
    public static void methodEnter(@Advice.Argument(1) final HttpMethod httpMethod) {
      if (httpMethod == null) {
        return;
      }
      String uri = null;
      try {
        uri = httpMethod.getURI().toString();
      } catch (Exception e) {
        InstrumentationLogger.debug(
            "Failed to get URI from HttpMethod", NetworkConnectionRaspAdvice.class, e);
      }
      NetworkConnectionModule.INSTANCE.onNetworkConnection(uri);
    }
  }
}
