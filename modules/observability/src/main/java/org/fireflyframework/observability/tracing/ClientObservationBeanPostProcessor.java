package org.fireflyframework.observability.tracing;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;

/**
 * Turns existing client and messaging beans into traced clients.
 * <p>
 * HTTP builders created by the application or by a module that does not call
 * {@link ObservedClients} still pass through here: WebClient, RestClient, and
 * RestTemplate beans receive the observation registry, and Kafka/Rabbit templates
 * and listener containers get {@code observationEnabled}. Opt out with
 * {@code firefly.observability.tracing.client-instrumentation=false} or
 * {@code firefly.observability.tracing.messaging-instrumentation=false}. The matching
 * Spring Boot property ({@code spring.kafka.*} / {@code spring.rabbitmq.*}) is also
 * honored so an explicit {@code false} in application configuration wins.
 */
public class ClientObservationBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(ClientObservationBeanPostProcessor.class);

    private static final boolean WEB_CLIENT_PRESENT = ClassUtils.isPresent(
            "org.springframework.web.reactive.function.client.WebClient",
            ClientObservationBeanPostProcessor.class.getClassLoader());
    private static final Class<?> WEB_CLIENT_TYPE = loadWebClientType("org.springframework.web.reactive.function.client.WebClient");
    private static final Class<?> WEB_CLIENT_BUILDER_TYPE = loadWebClientType("org.springframework.web.reactive.function.client.WebClient$Builder");

    private final Environment environment;
    private BeanFactory beanFactory;

    public ClientObservationBeanPostProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean == null || !tracingEnabled()) {
            return bean;
        }
        try {
            Object instrumented = instrumentHttpClient(bean);
            if (instrumented != bean) {
                return instrumented;
            }
            enableMessaging(bean);
        } catch (RuntimeException ex) {
            log.debug("Left bean {} uninstrumented: {}", beanName, ex.toString());
        }
        return bean;
    }

    private Object instrumentHttpClient(Object bean) {
        if (!clientInstrumentationEnabled()) {
            return bean;
        }
        ObservationRegistry registry = registry();
        if (registry == null) {
            return bean;
        }
        if (bean instanceof RestTemplate restTemplate) {
            restTemplate.setObservationRegistry(registry);
            return restTemplate;
        }
        if (bean instanceof RestClient.Builder restClientBuilder) {
            restClientBuilder.observationRegistry(registry);
            return restClientBuilder;
        }
        if (bean instanceof RestClient restClient) {
            return restClient.mutate().observationRegistry(registry).build();
        }
        if (WEB_CLIENT_BUILDER_TYPE != null && WEB_CLIENT_BUILDER_TYPE.isInstance(bean)) {
            return instrumentWebClient(bean, WEB_CLIENT_BUILDER_TYPE);
        }
        if (WEB_CLIENT_TYPE != null && WEB_CLIENT_TYPE.isInstance(bean)) {
            return instrumentWebClient(bean, WEB_CLIENT_TYPE);
        }
        return bean;
    }

    private Object instrumentWebClient(Object bean, Class<?> webClientType) {
        TracingRegistry.install(registry());
        try {
            Class<?> observed = Class.forName("org.fireflyframework.observability.tracing.ObservedClients");
            return observed.getMethod("instrument", webClientType).invoke(null, bean);
        } catch (ReflectiveOperationException ex) {
            log.debug("WebClient instrumentation skipped: {}", ex.toString());
            return bean;
        }
    }

    private static Class<?> loadWebClientType(String name) {
        if (!WEB_CLIENT_PRESENT) {
            return null;
        }
        try {
            return Class.forName(name, false, ClientObservationBeanPostProcessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private void enableMessaging(Object bean) {
        if (!messagingInstrumentationEnabled()) {
            return;
        }
        String type = bean.getClass().getName();
        if (type.startsWith("org.springframework.kafka.")) {
            if (!bootProperty(kafkaProperty(type))) {
                return;
            }
            invokeBoolean(bean, "setObservationEnabled", true);
            Object properties = invoke(bean, "getContainerProperties");
            if (properties != null) {
                invokeBoolean(properties, "setObservationEnabled", true);
            }
        } else if (type.startsWith("org.springframework.amqp.")) {
            if (!bootProperty(rabbitProperty(type))) {
                return;
            }
            invokeBoolean(bean, "setObservationEnabled", true);
            Object properties = invoke(bean, "getContainerProperties");
            if (properties != null) {
                invokeBoolean(properties, "setObservationEnabled", true);
            }
        }
    }

    private static String kafkaProperty(String typeName) {
        if (typeName.contains("Template")) {
            return "spring.kafka.template.observation-enabled";
        }
        return "spring.kafka.listener.observation-enabled";
    }

    private static String rabbitProperty(String typeName) {
        if (typeName.contains("Template")) {
            return "spring.rabbitmq.template.observation-enabled";
        }
        return "spring.rabbitmq.listener.simple.observation-enabled";
    }

    private ObservationRegistry registry() {
        ObservationRegistry installed = TracingRegistry.registry();
        if (installed != null) {
            return installed;
        }
        if (beanFactory == null) {
            return null;
        }
        try {
            ObservationRegistry registry = beanFactory.getBeanProvider(ObservationRegistry.class).getIfAvailable();
            TracingRegistry.install(registry);
        } catch (BeansException ex) {
            return null;
        }
        return TracingRegistry.registry();
    }

    private boolean tracingEnabled() {
        return bootProperty("firefly.observability.tracing.enabled");
    }

    private boolean clientInstrumentationEnabled() {
        return bootProperty("firefly.observability.tracing.client-instrumentation");
    }

    private boolean messagingInstrumentationEnabled() {
        return bootProperty("firefly.observability.tracing.messaging-instrumentation");
    }

    private boolean bootProperty(String name) {
        return environment == null || environment.getProperty(name, Boolean.class, true);
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void invokeBoolean(Object target, String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Not every Kafka/AMQP type exposes the method. Callers that do are updated.
        }
    }

}
