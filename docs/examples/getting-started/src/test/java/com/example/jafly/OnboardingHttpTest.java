package com.example.jafly;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnboardingHttpTest {
    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void retrievesEachSubmittedApplicationWithoutMixingUpApplicants() {
        String adaId = submit("Ada Lovelace", "ada@example.com");
        String graceId = submit("Grace Hopper", "grace@example.com");
        assertThat(adaId).isNotEqualTo(graceId);

        client().get().uri("/applications/{id}", adaId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(adaId)
                .jsonPath("$.applicantName").isEqualTo("Ada Lovelace")
                .jsonPath("$.email").isEqualTo("ada@example.com");
        client().get().uri("/applications/{id}", graceId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(graceId)
                .jsonPath("$.applicantName").isEqualTo("Grace Hopper")
                .jsonPath("$.email").isEqualTo("grace@example.com");
        client().get().uri("/applications/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsABlankApplicantName() {
        client().post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("applicantName", " ", "email", "ada@example.com"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void rejectsAnInvalidEmail() {
        client().post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("applicantName", "Ada Lovelace", "email", "not-an-email"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void rejectsABlankEmail() {
        client().post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("applicantName", "Ada Lovelace", "email", " "))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void returnsNotFoundForAnUnknownApplication() {
        client().get().uri("/applications/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
    }

    private String submit(String applicantName, String email) {
        var result = client().post().uri("/applications").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("applicantName", applicantName, "email", email))
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult();
        Map<?, ?> body = result.getResponseBody();
        assertThat(body).isNotNull();
        assertThat(body.get("applicantName")).isEqualTo(applicantName);
        assertThat(body.get("email")).isEqualTo(email);
        String id = (String) body.get("id");
        String location = result.getResponseHeaders().getFirst("Location");
        assertThat(location).isEqualTo("/applications/" + id);
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        client().get().uri(location).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
        return id;
    }
}
