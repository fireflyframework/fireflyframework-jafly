package com.example.jafly;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/applications")
public class ApplicationsController {
    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public ApplicationsController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping
    public Mono<ResponseEntity<OnboardingApplication>> submit(@Valid @RequestBody ApplicationRequest request) {
        return commandBus.send(new SubmitApplication(request.applicantName(), request.email()))
                .map(application -> ResponseEntity.created(URI.create("/applications/" + application.id()))
                        .body(application));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<OnboardingApplication>> find(@PathVariable UUID id) {
        return queryBus.query(new FindApplication(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    public record ApplicationRequest(@NotBlank String applicantName, @NotBlank @Email String email) {
    }
}
