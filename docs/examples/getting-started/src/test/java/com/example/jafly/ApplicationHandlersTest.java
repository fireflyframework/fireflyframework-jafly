package com.example.jafly;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationHandlersTest {
    @Test
    void submittedApplicationsAreAvailableToTheReadHandler() {
        var repository = new InMemoryApplicationRepository();
        var submitHandler = new SubmitApplicationHandler(repository);
        var findHandler = new FindApplicationHandler(repository);

        StepVerifier.create(submitHandler.handle(new SubmitApplication("Ada Lovelace", "ada@example.com"))
                        .flatMap(application -> findHandler.handle(new FindApplication(application.id()))))
                .assertNext(application -> {
                    assertThat(application.id()).isNotNull();
                    assertThat(application.applicantName()).isEqualTo("Ada Lovelace");
                    assertThat(application.email()).isEqualTo("ada@example.com");
                })
                .verifyComplete();
    }

    @Test
    void savingDoesNotChangeRepositoryStateBeforeSubscription() {
        var repository = new InMemoryApplicationRepository();
        var application = new OnboardingApplication(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                "Ada Lovelace", "ada@example.com");
        var save = repository.save(application);

        StepVerifier.create(repository.findById(application.id())).verifyComplete();
        StepVerifier.create(save).expectNext(application).verifyComplete();
        StepVerifier.create(repository.findById(application.id())).expectNext(application).verifyComplete();
    }
}
