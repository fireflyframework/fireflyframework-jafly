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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAPIAutoConfigurationTest {

    @Test
    void customOpenAPI_WithDefaultValues() {
        // Arrange
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        
        OpenAPIAutoConfiguration configuration = new OpenAPIAutoConfiguration(mockEnvironment);
        ReflectionTestUtils.setField(configuration, "applicationName", "Test Service");
        ReflectionTestUtils.setField(configuration, "applicationVersion", "1.0.0");
        ReflectionTestUtils.setField(configuration, "applicationDescription", "Test Description");
        ReflectionTestUtils.setField(configuration, "teamName", "Test Team");
        ReflectionTestUtils.setField(configuration, "teamEmail", "test@example.com");
        ReflectionTestUtils.setField(configuration, "teamUrl", "https://example.com");
        ReflectionTestUtils.setField(configuration, "licenseName", "Test License");
        ReflectionTestUtils.setField(configuration, "licenseUrl", "https://example.com/license");
        ReflectionTestUtils.setField(configuration, "contextPath", "/api");
        ReflectionTestUtils.setField(configuration, "serverPort", "8080");
        ReflectionTestUtils.setField(configuration, "serversEnabled", true);

        // Act
        OpenAPI result = configuration.customOpenAPI();

        // Assert
        assertNotNull(result);
        
        // Verify info
        Info info = result.getInfo();
        assertNotNull(info);
        assertEquals("Test Service API", info.getTitle());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("Test Description", info.getDescription());
        
        // Verify contact
        Contact contact = info.getContact();
        assertNotNull(contact);
        assertEquals("Test Team", contact.getName());
        assertEquals("test@example.com", contact.getEmail());
        assertEquals("https://example.com", contact.getUrl());
        
        // Verify license
        License license = info.getLicense();
        assertNotNull(license);
        assertEquals("Test License", license.getName());
        assertEquals("https://example.com/license", license.getUrl());
        
        // Verify servers
        assertNotNull(result.getServers());
        assertFalse(result.getServers().isEmpty());
        
        // Verify no security
        assertNull(result.getComponents());
    }
}
