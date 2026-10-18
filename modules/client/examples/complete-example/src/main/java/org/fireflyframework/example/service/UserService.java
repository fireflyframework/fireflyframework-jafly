package org.fireflyframework.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.example.model.User;
import org.fireflyframework.example.model.CreateUserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Example service demonstrating REST client usage with all features:
 * - Circuit breaker
 * - Retry with exponential backoff
 * - Request/response logging
 * - Error handling
 */
@Slf4j
@Service
public class UserService {

    private final RestClient userClient;

    public UserService() {
        this.userClient = ServiceClient.rest("user-service")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .timeout(Duration.ofSeconds(30))
            .build();

        log.info("UserService initialized with REST client");
    }

    /**
     * Get a user by ID.
     */
    public User getUser(Long userId) {
        log.info("Fetching user with ID: {}", userId);

        return userClient.get("/users/{id}", User.class)
            .withPathParam("id", userId)
            .execute()
            .block();
    }

    /**
     * Get all users.
     */
    public List<User> getAllUsers() {
        log.info("Fetching all users");

        return userClient.get("/users", new TypeReference<List<User>>() {})
            .execute()
            .block();
    }

    /**
     * Create a new user.
     */
    public User createUser(CreateUserRequest request) {
        log.info("Creating user: {}", request.getName());

        return userClient.post("/users", User.class)
            .withBody(request)
            .execute()
            .block();
    }

    /**
     * Update an existing user.
     */
    public User updateUser(Long userId, CreateUserRequest request) {
        log.info("Updating user with ID: {}", userId);

        return userClient.put("/users/{id}", User.class)
            .withPathParam("id", userId)
            .withBody(request)
            .execute()
            .block();
    }

    /**
     * Delete a user.
     */
    public void deleteUser(Long userId) {
        log.info("Deleting user with ID: {}", userId);

        userClient.delete("/users/{id}", Void.class)
            .withPathParam("id", userId)
            .execute()
            .block();
    }

    /**
     * Search users with query parameters.
     */
    public List<User> searchUsers(String query) {
        log.info("Searching users with query: {}", query);

        return userClient.get("/users", new TypeReference<List<User>>() {})
            .withQueryParam("q", query)
            .execute()
            .block();
    }
}
