package org.fireflyframework.core.config;

import org.fireflyframework.core.filters.FilterUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

@AutoConfiguration
public class R2dbcAutoConfiguration {
    private final R2dbcEntityTemplate template;

    public R2dbcAutoConfiguration(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @PostConstruct
    public void initialize() {
        FilterUtils.initializeTemplate(template);
    }
}