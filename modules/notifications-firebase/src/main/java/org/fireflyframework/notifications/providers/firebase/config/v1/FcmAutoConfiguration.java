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


package org.fireflyframework.notifications.providers.firebase.config.v1;

import org.fireflyframework.notifications.interfaces.providers.push.v1.PushProvider;
import org.fireflyframework.notifications.providers.firebase.core.v1.FcmPushProvider;
import org.fireflyframework.notifications.providers.firebase.properties.v1.FcmProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.notifications.push.provider", havingValue = "firebase")
@ConditionalOnClass(FirebaseMessaging.class)
@EnableConfigurationProperties(FcmProperties.class)
public class FcmAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
        log.info("Initializing Firebase Cloud Messaging provider for project: {}", properties.getProjectId());
        FirebaseOptions.Builder builder = FirebaseOptions.builder();
        if (properties.getCredentialsPath() != null && !properties.getCredentialsPath().isBlank()) {
            try (FileInputStream serviceAccount = new FileInputStream(properties.getCredentialsPath())) {
                builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            }
        } else {
            builder.setCredentials(GoogleCredentials.getApplicationDefault());
        }
        if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
            builder.setProjectId(properties.getProjectId());
        }
        FirebaseOptions options = builder.build();
        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (apps == null || apps.isEmpty()) {
            return FirebaseApp.initializeApp(options);
        }
        return apps.get(0);
    }

    @ConditionalOnMissingBean
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    @ConditionalOnMissingBean(PushProvider.class)
    public PushProvider fcmPushProvider(FirebaseMessaging firebaseMessaging) {
        return new FcmPushProvider(firebaseMessaging);
    }
}
