package org.fireflyframework.example.service;

import org.fireflyframework.client.graphql.GraphQLClientHelper;
import org.fireflyframework.client.graphql.GraphQLClientHelper.GraphQLConfig;
import org.fireflyframework.example.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Example service demonstrating GraphQL client usage with:
 * - Query caching
 * - Automatic retry
 * - Error handling
 * - Variables support
 */
@Slf4j
@Service
public class GraphQLExampleService {

    private final GraphQLClientHelper graphqlClient;

    public GraphQLExampleService() {
        GraphQLConfig config = GraphQLConfig.builder()
            .timeout(Duration.ofSeconds(30))
            .enableQueryCache(true)
            .enableRetry(true)
            .maxRetries(3)
            .build();

        this.graphqlClient = new GraphQLClientHelper(
            "https://api.example.com/graphql",
            config
        );

        log.info("GraphQLExampleService initialized");
    }

    /**
     * Query a user by ID using GraphQL.
     */
    public User getUser(String userId) {
        log.info("Fetching user via GraphQL with ID: {}", userId);

        String query = """
            query GetUser($id: ID!) {
                user(id: $id) {
                    id
                    name
                    email
                    username
                }
            }
            """;

        Map<String, Object> variables = Map.of("id", userId);

        return graphqlClient.<User>query(query, variables, "user", User.class).block();
    }

    /**
     * Create a user using GraphQL mutation.
     */
    public User createUser(String name, String email) {
        log.info("Creating user via GraphQL: {}", name);

        String mutation = """
            mutation CreateUser($name: String!, $email: String!) {
                createUser(input: {name: $name, email: $email}) {
                    id
                    name
                    email
                }
            }
            """;

        Map<String, Object> variables = Map.of(
            "name", name,
            "email", email
        );

        return graphqlClient.<User>mutate(mutation, variables, "createUser", User.class).block();
    }

    /**
     * Batch query multiple users.
     */
    public List<User> batchGetUsers(String... userIds) {
        log.info("Batch fetching {} users via GraphQL", userIds.length);

        String query = """
            query GetUsers($ids: [ID!]!) {
                users(ids: $ids) {
                    id
                    name
                    email
                }
            }
            """;

        Map<String, Object> variables = Map.of("ids", userIds);

        User[] users = graphqlClient.<User[]>query(query, variables, "users", User[].class).block();
        return users == null ? List.of() : Arrays.asList(users);
    }
}
