package com.datadog.debugger.el;

import com.datadog.debugger.el.values.BooleanValue;
import com.datadog.debugger.el.values.ListValue;
import com.datadog.debugger.el.values.MapValue;
import com.datadog.debugger.el.values.NullValue;
import com.datadog.debugger.el.values.NumericValue;
import com.datadog.debugger.el.values.ObjectValue;
import com.datadog.debugger.el.values.StringValue;
import com.datadog.debugger.el.values.UndefinedValue;
import datadog.trace.bootstrap.debugger.el.Values;
import datadog.trace.bootstrap.debugger.util.Redaction;
import datadog.trace.bootstrap.debugger.util.WellKnownClasses;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Represents any value of the expression language */
public interface Value<T> {
  /** A generic undefined value */
  static Value<?> undefinedValue() {
    return UndefinedValue.INSTANCE;
  }

  static Value<?> nullValue() {
    return NullValue.INSTANCE;
  }

  static Value<?> thisValue() {
    return ObjectValue.THIS;
  }

  @SuppressWarnings("unchecked")
  static <T> Value<T> undefined() {
    return (Value<T>) undefinedValue();
  }

  T getValue();

  default boolean isUndefined() {
    return false;
  }

  default boolean isNull() {
    return false;
  }

  static Value<?> of(Object value) {
    return of(value, null);
  }

  static Value<?> of(Object value, Expression<?> fromExpr) {
    if (value == null || value == Values.NULL_OBJECT || value == nullValue()) {
      return nullValue();
    }
    if (value == Values.UNDEFINED_OBJECT || value == undefinedValue()) {
      return undefinedValue();
    }
    if (value == Values.THIS_OBJECT) {
      return thisValue();
    }
    String typeName = value.getClass().getTypeName();
    if (value == Redaction.REDACTED_VALUE || (Redaction.isRedactedType(typeName))) {
      throwRedactedException(fromExpr);
    }
    if (WellKnownClasses.isStringPrimitive(typeName)) {
      Function<Object, String> toString = WellKnownClasses.getSafeToString(typeName);
      value = toString != null ? toString.apply(value) : value.toString();
    }
    if (value instanceof Boolean) {
      return new BooleanValue((Boolean) value);
    } else if (value instanceof Number) {
      return new NumericValue((Number) value);
    } else if (value instanceof String) {
      return new StringValue((String) value);
    } else if (value instanceof List) {
      return new ListValue(value);
    } else if (value.getClass().isArray()) {
      return new ListValue(value);
    } else if (value instanceof Map) {
      return new MapValue(value);
    } else if (value instanceof Value) {
      return (Value<?>) value;
    } else {
      return new ObjectValue(value);
    }
  }

  static void throwRedactedException(Expression<?> expr) {
    String strExpr = expr != null ? PrettyPrintVisitor.print(expr) : "null";
    throw new RedactedException(
        "Could not evaluate the expression because '" + strExpr + "' was redacted", strExpr);
  }
}
