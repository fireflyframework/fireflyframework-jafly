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

package org.fireflyframework.notifications.core.services.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FreeMarker-based implementation of {@link NotificationTemplateEngine}.
 *
 * <p>Loads templates from a configurable classpath prefix (default: {@code /notification-templates})
 * and an optional filesystem directory. Template files use the {@code .ftl} extension by convention.
 */
@Slf4j
public class FreemarkerNotificationTemplateEngine implements NotificationTemplateEngine {

    private final Configuration configuration;

    public FreemarkerNotificationTemplateEngine(String classpathPrefix, String filesystemDir) {
        this.configuration = buildConfiguration(classpathPrefix, filesystemDir);
        log.info("FreemarkerNotificationTemplateEngine initialized (classpath: {}, filesystem: {})",
                classpathPrefix, filesystemDir);
    }

    public FreemarkerNotificationTemplateEngine() {
        this("/notification-templates", null);
    }

    @Override
    public Mono<String> render(String templateId, Map<String, Object> variables) {
        return Mono.fromCallable(() -> {
                    String templateName = templateId.endsWith(".ftl") ? templateId : templateId + ".ftl";
                    Template template = configuration.getTemplate(templateName);
                    StringWriter writer = new StringWriter();
                    template.process(variables != null ? variables : Map.of(), writer);
                    return writer.toString();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to render template '{}': {}", templateId, e.getMessage()));
    }

    private Configuration buildConfiguration(String classpathPrefix, String filesystemDir) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        cfg.setLocale(Locale.US);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        List<TemplateLoader> loaders = new ArrayList<>();
        loaders.add(new ClassTemplateLoader(getClass().getClassLoader(),
                classpathPrefix != null ? classpathPrefix : "/notification-templates"));

        if (filesystemDir != null) {
            try {
                File dir = new File(filesystemDir);
                if (dir.isDirectory()) {
                    loaders.add(new FileTemplateLoader(dir));
                }
            } catch (IOException e) {
                log.warn("Could not configure filesystem template loader for '{}': {}", filesystemDir, e.getMessage());
            }
        }

        if (loaders.size() == 1) {
            cfg.setTemplateLoader(loaders.get(0));
        } else {
            cfg.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0])));
        }

        return cfg;
    }
}
