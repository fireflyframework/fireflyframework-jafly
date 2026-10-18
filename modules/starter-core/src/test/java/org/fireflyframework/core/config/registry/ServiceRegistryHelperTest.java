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


package org.fireflyframework.core.config.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ServiceRegistryHelper}.
 */
public class ServiceRegistryHelperTest {

    @Mock
    private DiscoveryClient discoveryClient;

    @Mock
    private Environment environment;

    @Mock
    private ServiceInstance serviceInstance1;

    @Mock
    private ServiceInstance serviceInstance2;

    private ServiceRegistryHelper serviceRegistryHelper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        serviceRegistryHelper = new ServiceRegistryHelper(discoveryClient, environment);
        
        when(serviceInstance1.getUri()).thenReturn(URI.create("http://service1:8080"));
        when(serviceInstance2.getUri()).thenReturn(URI.create("http://service2:8080"));
    }

    @Test
    public void testGetInstances() {
        String serviceId = "test-service";
        List<ServiceInstance> expectedInstances = Arrays.asList(serviceInstance1, serviceInstance2);
        
        when(discoveryClient.getInstances(serviceId)).thenReturn(expectedInstances);
        
        List<ServiceInstance> actualInstances = serviceRegistryHelper.getInstances(serviceId);
        
        assertEquals(expectedInstances, actualInstances);
        verify(discoveryClient).getInstances(serviceId);
    }

    @Test
    public void testGetInstance_WithInstances() {
        String serviceId = "test-service";
        List<ServiceInstance> instances = Arrays.asList(serviceInstance1, serviceInstance2);
        
        when(discoveryClient.getInstances(serviceId)).thenReturn(instances);
        
        Optional<ServiceInstance> instance = serviceRegistryHelper.getInstance(serviceId);
        
        assertTrue(instance.isPresent());
        assertTrue(instances.contains(instance.get()));
        verify(discoveryClient).getInstances(serviceId);
    }

    @Test
    public void testGetInstance_NoInstances() {
        String serviceId = "test-service";
        
        when(discoveryClient.getInstances(serviceId)).thenReturn(Collections.emptyList());
        
        Optional<ServiceInstance> instance = serviceRegistryHelper.getInstance(serviceId);
        
        assertFalse(instance.isPresent());
        verify(discoveryClient).getInstances(serviceId);
    }

    @Test
    public void testGetServiceUri_WithInstances() {
        String serviceId = "test-service";
        String path = "/api/resource";
        
        when(discoveryClient.getInstances(serviceId)).thenReturn(Collections.singletonList(serviceInstance1));
        
        Optional<URI> uri = serviceRegistryHelper.getServiceUri(serviceId, path);
        
        assertTrue(uri.isPresent());
        assertEquals("http://service1:8080/api/resource", uri.get().toString());
        verify(discoveryClient).getInstances(serviceId);
    }

    @Test
    public void testGetServiceUri_NoInstances() {
        String serviceId = "test-service";
        String path = "/api/resource";
        
        when(discoveryClient.getInstances(serviceId)).thenReturn(Collections.emptyList());
        
        Optional<URI> uri = serviceRegistryHelper.getServiceUri(serviceId, path);
        
        assertFalse(uri.isPresent());
        verify(discoveryClient).getInstances(serviceId);
    }

    @Test
    public void testGetApplicationName() {
        String expectedName = "test-application";
        
        when(environment.getProperty("spring.application.name", "unknown")).thenReturn(expectedName);
        
        String actualName = serviceRegistryHelper.getApplicationName();
        
        assertEquals(expectedName, actualName);
        verify(environment).getProperty("spring.application.name", "unknown");
    }

    @Test
    public void testGetServices() {
        List<String> expectedServices = Arrays.asList("service1", "service2", "service3");
        
        when(discoveryClient.getServices()).thenReturn(expectedServices);
        
        List<String> actualServices = serviceRegistryHelper.getServices();
        
        assertEquals(expectedServices, actualServices);
        verify(discoveryClient).getServices();
    }
}
