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


package org.fireflyframework.core.config;

import org.springframework.boot.Banner;
import org.springframework.boot.ResourceBanner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.PrintStream;

/**
 * Auto-configuration class for setting up the custom Firefly banner.
 * This configuration ensures that the Firefly ASCII art banner is displayed
 * instead of the default Spring Boot banner when any microservice uses this library.
 * 
 * The banner includes:
 * - Firefly ASCII art
 * - Copyright 2024-2026 Firefly Software Foundation
 * - License Apache 2.0
 * 
 * Configuration is based on spring.banner.location property which defaults to
 * classpath:/static/custom-banner.txt but can be overridden by applications.
 */
@AutoConfiguration
@EnableConfigurationProperties(FireflyBannerAutoConfiguration.BannerProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FireflyBannerAutoConfiguration implements ApplicationListener<ApplicationStartingEvent> {

    /**
     * Bean definition for the custom Firefly banner.
     * This ensures the banner is available as a Spring bean.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean
    public Banner fireflyBanner() {
        return new FireflyBanner();
    }

    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        // Set the banner mode to CONSOLE to ensure our custom banner is displayed
        event.getSpringApplication().setBannerMode(Banner.Mode.CONSOLE);
        
        // Set our custom banner - this happens very early in the application lifecycle
        event.getSpringApplication().setBanner(new FireflyBanner());
    }

    /**
     * Configuration properties for banner settings.
     */
    @ConfigurationProperties(prefix = "spring.banner")
    public static class BannerProperties {
        /**
         * Location of the banner file. Defaults to classpath:/banner.txt
         */
        private String location = "classpath:/banner.txt";
        
        /**
         * Banner mode. Defaults to console.
         */
        private String mode = "console";
        
        public String getLocation() {
            return location;
        }
        
        public void setLocation(String location) {
            this.location = location;
        }
        
        public String getMode() {
            return mode;
        }
        
        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    /**
     * Custom banner implementation that displays the Firefly ASCII art
     * and copyright information using Spring Boot's ResourceBanner for proper processing.
     */
    public static class FireflyBanner implements Banner {
        
        @Override
        public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
            try {
                // Try to load banner from configured location first
                String bannerLocation = environment.getProperty("spring.banner.location", "classpath:/banner.txt");
                ClassPathResource bannerResource = new ClassPathResource(bannerLocation.replace("classpath:", ""));
                
                if (bannerResource.exists()) {
                    // Use ResourceBanner to properly handle ANSI colors and variable substitution
                    ResourceBanner resourceBanner = new ResourceBanner(bannerResource);
                    resourceBanner.printBanner(environment, sourceClass, out);
                    return;
                }
                
                // Fallback to original banner.txt location
                bannerResource = new ClassPathResource("banner.txt");
                if (bannerResource.exists()) {
                    // Use ResourceBanner for proper processing
                    ResourceBanner resourceBanner = new ResourceBanner(bannerResource);
                    resourceBanner.printBanner(environment, sourceClass, out);
                    return;
                }
                
                // Final fallback banner if no resource is found
                printFallbackBanner(out);
            } catch (Exception e) {
                // If there's any error loading the banner, use fallback
                printFallbackBanner(out);
            }
        }
        
        private void printFallbackBanner(PrintStream out) {
            out.println();
            out.println(" _______ _____ _____  ______ ______ _      __     __");
            out.println("|  _____|_   _|  __ \\|  ____|  ____| |     \\ \\   / /");
            out.println("| |__     | | | |__) | |__  | |__  | |      \\ \\_/ / ");
            out.println("|  __|    | | |  _  /|  __| |  __| | |       \\   /  ");
            out.println("| |      _| |_| | \\ \\| |____| |    | |____    | |   ");
            out.println("|_|     |_____|_|  \\_\\______|_|    |______|   |_|   ");
            out.println();
            out.println("Copyright 2024-2026 Firefly Software Foundation");
            out.println("License Apache 2.0");
            out.println();
        }
    }
}