package org.fireflyframework.observability.tracing;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Constructor;

/**
 * Registers OpenFeign's {@code MicrometerCapability} when that class is on the classpath.
 * <p>
 * The capability type is loaded reflectively so the observability module does not depend
 * on Feign. Spring Cloud OpenFeign adds every {@code feign.Capability} bean to clients
 * it builds, which creates CLIENT spans and writes {@code traceparent}.
 */
public final class FeignMicrometerCapabilityFactoryImpl implements FactoryBean<Object> {

    private final ObservationRegistry observationRegistry;
    private final Class<?> capabilityType;

    public FeignMicrometerCapabilityFactoryImpl(ObservationRegistry observationRegistry) throws ClassNotFoundException {
        this.observationRegistry = observationRegistry;
        this.capabilityType = Class.forName("feign.micrometer.MicrometerCapability");
    }

    @Override
    public Object getObject() throws Exception {
        for (Constructor<?> constructor : capabilityType.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isInstance(observationRegistry)) {
                return constructor.newInstance(observationRegistry);
            }
        }
        return capabilityType.getDeclaredConstructor().newInstance();
    }

    @Override
    public Class<?> getObjectType() {
        return capabilityType;
    }
}
