package org.fireflyframework.observability.tracing;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.transport.ResponseContext;
import org.springframework.http.HttpStatusCode;

import java.lang.reflect.Method;

/**
 * Marks HTTP 5xx observations so Micrometer's tracing handler records
 * {@code StatusCode.ERROR} and an exception event.
 * <p>
 * Micrometer calls {@code span.error()} only when the observation already carries a
 * throwable. Framework exception handlers turn failures into 5xx responses before that
 * happens, so production spans stayed unset (and the OpenTelemetry bridge then stored
 * them as {@code OK}). This filter runs after the HTTP convention has captured the
 * response and before the tracing handler ends the span. A high-cardinality key named
 * {@code error} is the signal {@code TracingObservationHandler} already understands:
 * it calls {@code span.error()}, which records the exception and sets the error status.
 */
public class HttpErrorStatusObservationFilter implements ObservationFilter {

    static final String ERROR_KEY = "error";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context == null || context.getError() != null || hasErrorKey(context)) {
            return context;
        }
        Integer status = httpStatus(context);
        if (status != null && status >= 500 && status <= 599) {
            context.addHighCardinalityKeyValue(KeyValue.of(ERROR_KEY, "HTTP " + status));
        }
        return context;
    }

    private static boolean hasErrorKey(Observation.Context context) {
        return containsErrorKey(context.getLowCardinalityKeyValues())
                || containsErrorKey(context.getHighCardinalityKeyValues());
    }

    private static boolean containsErrorKey(KeyValues keyValues) {
        if (keyValues == null) {
            return false;
        }
        for (KeyValue keyValue : keyValues) {
            if (ERROR_KEY.equalsIgnoreCase(keyValue.getKey())) {
                return true;
            }
        }
        return false;
    }

    static Integer httpStatus(Observation.Context context) {
        if (context instanceof ResponseContext<?> responseContext) {
            Integer fromResponse = statusOf(responseContext.getResponse());
            if (fromResponse != null) {
                return fromResponse;
            }
        }
        Integer fromKeys = statusOf(context.getLowCardinalityKeyValues());
        if (fromKeys != null) {
            return fromKeys;
        }
        return statusOf(context.getHighCardinalityKeyValues());
    }

    private static Integer statusOf(KeyValues keyValues) {
        if (keyValues == null) {
            return null;
        }
        for (KeyValue keyValue : keyValues) {
            String key = keyValue.getKey();
            if (key == null) {
                continue;
            }
            if ("status".equals(key) || key.endsWith("status_code") || key.endsWith(".status")) {
                Integer parsed = parseStatus(keyValue.getValue());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Integer statusOf(Object response) {
        if (response == null) {
            return null;
        }
        Integer status = invokeStatus(response, "getStatusCode");
        if (status != null) {
            return status;
        }
        status = invokeStatus(response, "statusCode");
        if (status != null) {
            return status;
        }
        return invokeStatus(response, "getStatus");
    }

    private static Integer invokeStatus(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof HttpStatusCode statusCode) {
                return statusCode.value();
            }
            if (value instanceof Integer status) {
                return status;
            }
            if (value instanceof Number status) {
                return status.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static Integer parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int status = Integer.parseInt(value.trim());
            return status >= 100 && status <= 599 ? status : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
