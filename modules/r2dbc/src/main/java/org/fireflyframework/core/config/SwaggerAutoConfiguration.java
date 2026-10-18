package org.fireflyframework.core.config;

import org.fireflyframework.core.filters.FilterParameterCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@AutoConfiguration
public class SwaggerAutoConfiguration {
    @ConditionalOnMissingBean
    @Bean
    public FilterParameterCustomizer filterParameterCustomizer() {
        return new FilterParameterCustomizer();
    }
}