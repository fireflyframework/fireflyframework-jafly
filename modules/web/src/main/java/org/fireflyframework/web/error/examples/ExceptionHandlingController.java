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


package org.fireflyframework.web.error.examples;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * Example controller that demonstrates how to use the exception handling features.
 * This controller shows how to throw business exceptions directly and how to use
 * the exception conversion mechanism in a REST controller.
 */
@RestController
@RequestMapping("/api/examples/exceptions")
@ConditionalOnClass({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
public class ExceptionHandlingController {

    private final ExceptionHandlingExample exampleService;

    /**
     * Creates a new ExceptionHandlingController with the given example service.
     *
     * @param exampleService the example service
     */
    public ExceptionHandlingController(ExceptionHandlingExample exampleService) {
        this.exampleService = exampleService;
    }

    /**
     * Example endpoint that throws business exceptions directly.
     *
     * @param type the type of exception to throw
     * @return a Mono that completes with an error
     */
    @GetMapping("/business/{type}")
    public Mono<String> throwBusinessException(@PathVariable String type) {
        return Mono.defer(() -> {
            exampleService.throwBusinessException(type);
            return Mono.just("This should never be returned");
        });
    }

    /**
     * Example endpoint that manually converts standard exceptions to business exceptions.
     *
     * @param type the type of exception to convert
     * @return a Mono that completes with an error
     */
    @GetMapping("/convert/{type}")
    public Mono<String> convertStandardException(@PathVariable String type) {
        return Mono.defer(() -> {
            BusinessException exception = exampleService.convertStandardException(type);
            return Mono.error(exception);
        });
    }

    /**
     * Example endpoint that automatically converts standard exceptions to business exceptions.
     * The GlobalExceptionHandler will automatically convert any exceptions to business exceptions.
     *
     * @param type the type of exception to throw
     * @return a Mono that completes with an error
     */
    @GetMapping("/auto-convert/{type}")
    public Mono<String> throwStandardException(@PathVariable String type) {
        return Mono.defer(() -> {
            try {
                exampleService.throwStandardException(type);
                return Mono.just("This should never be returned");
            } catch (TimeoutException e) {
                // The GlobalExceptionHandler will automatically convert this to a BusinessException
                throw new RuntimeException("Timeout occurred", e);
            }
        });
    }
}
