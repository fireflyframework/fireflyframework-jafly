package org.fireflyframework.orchestration.config;

import org.fireflyframework.orchestration.core.argument.ArgumentResolver;
import org.fireflyframework.orchestration.core.step.StepInvoker;
import org.fireflyframework.orchestration.core.observability.OrchestrationEvents;
import org.fireflyframework.orchestration.core.observability.OrchestrationLoggerEvents;
import org.fireflyframework.orchestration.core.event.OrchestrationEventPublisher;
import org.fireflyframework.orchestration.core.event.NoOpEventPublisher;
import org.fireflyframework.orchestration.core.persistence.ExecutionPersistenceProvider;
import org.fireflyframework.orchestration.core.persistence.InMemoryPersistenceProvider;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.fireflyframework.orchestration.workflow.registry.WorkflowRegistry;
import org.fireflyframework.orchestration.workflow.rest.WorkflowController;
import org.fireflyframework.orchestration.workflow.service.WorkflowService;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import org.fireflyframework.orchestration.workflow.timer.TimerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRestSwitchTest {
    static class Dependencies {
        @Bean StepInvoker stepInvoker() { return new StepInvoker(new ArgumentResolver()); }
        @Bean OrchestrationEvents events() { return new OrchestrationLoggerEvents(); }
        @Bean ExecutionPersistenceProvider persistence() { return new InMemoryPersistenceProvider(); }
        @Bean OrchestrationEventPublisher publisher() { return new NoOpEventPublisher(); }
    }

    private ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner().withUserConfiguration(Dependencies.class)
                .withConfiguration(AutoConfigurations.of(WorkflowAutoConfiguration.class));
    }

    @Test
    void disabledRestRetainsTheActualWorkflowEngineAndServices() {
        runner().withPropertyValues("firefly.orchestration.rest.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(WorkflowController.class)
                    .hasSingleBean(WorkflowEngine.class).hasSingleBean(WorkflowService.class)
                    .hasSingleBean(WorkflowRegistry.class).hasSingleBean(SignalService.class)
                    .hasSingleBean(TimerService.class);
        });
    }

    @Test
    void enabledRestKeepsTheExistingController() {
        runner().withPropertyValues("firefly.orchestration.rest.enabled=true").run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(WorkflowController.class)
                        .hasSingleBean(WorkflowEngine.class));
    }

    @Test
    void missingRestPropertyPreservesBackwardCompatibility() {
        runner().run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(WorkflowController.class).hasSingleBean(WorkflowEngine.class));
    }

    @Test
    void existingWorkflowDisableRemainsIndependentFromTheRestSwitch() {
        runner().withPropertyValues("firefly.orchestration.rest.enabled=true",
                "firefly.orchestration.workflow.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(WorkflowController.class)
                        .doesNotHaveBean(WorkflowEngine.class));
    }
}
