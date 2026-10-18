/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.web.openapi;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Automatically registers Mockito mock beans for every dependency of
 * {@code @RestController} classes that is not already present in the bean
 * registry. Supports both {@code @Autowired} field injection and
 * constructor injection.
 *
 * <p>Mocks are registered as pre-existing singletons via
 * {@link ConfigurableListableBeanFactory#registerSingleton} so that Spring
 * does not attempt to process {@code @Autowired} annotations on the mock
 * objects themselves (which would fail for concrete service classes that
 * have their own unsatisfied dependencies).
 *
 * <p>Activated only under the {@code openapi-gen} Spring profile.
 */
@Configuration
@Profile("openapi-gen")
public class AutoMockMissingBeansConfig implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMockMissingBeansConfig.class);

    private final Map<String, Class<?>> mocksToRegister = new LinkedHashMap<>();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            String beanClassName = bd.getBeanClassName();
            if (beanClassName == null) {
                continue;
            }

            Class<?> beanClass;
            try {
                beanClass = Class.forName(beanClassName);
            } catch (ClassNotFoundException e) {
                continue;
            }

            if (!beanClass.isAnnotationPresent(RestController.class)) {
                continue;
            }

            // Handle @Autowired field injection
            for (Field field : beanClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }
                collectMissingBean(registry, field.getType());
            }

            // Handle constructor injection
            Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
            if (constructors.length == 1 && constructors[0].getParameterCount() > 0) {
                for (Class<?> paramType : constructors[0].getParameterTypes()) {
                    collectMissingBean(registry, paramType);
                }
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (Map.Entry<String, Class<?>> entry : mocksToRegister.entrySet()) {
            Class<?> fieldType = entry.getValue();
            String mockBeanName = "mock_" + fieldType.getSimpleName();

            logger.info("Registering Mockito mock singleton: {} (type: {})",
                    mockBeanName, fieldType.getName());
            beanFactory.registerSingleton(mockBeanName, Mockito.mock(fieldType));
        }
    }

    private void collectMissingBean(BeanDefinitionRegistry registry, Class<?> type) {
        if (mocksToRegister.containsKey(type.getName())) {
            return;
        }
        if (isBeanTypeRegistered(registry, type)) {
            return;
        }
        String mockBeanName = "mock_" + type.getSimpleName();
        logger.info("Will register Mockito mock for missing bean: {} (type: {})",
                mockBeanName, type.getName());
        mocksToRegister.put(type.getName(), type);
    }

    private boolean isBeanTypeRegistered(BeanDefinitionRegistry registry, Class<?> type) {
        for (String name : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                if (type.isAssignableFrom(clazz)) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // Skip
            }
        }
        return false;
    }
}
