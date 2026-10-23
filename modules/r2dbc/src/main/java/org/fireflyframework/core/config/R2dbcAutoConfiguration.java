package org.fireflyframework.core.config;

import org.fireflyframework.core.filters.FilterUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

@AutoConfiguration(afterName = "org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration")
@ConditionalOnClass(R2dbcEntityTemplate.class)
@ConditionalOnBean(R2dbcEntityTemplate.class)
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
