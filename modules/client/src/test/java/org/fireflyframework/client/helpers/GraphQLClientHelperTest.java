package org.fireflyframework.client.helpers;

import org.fireflyframework.client.graphql.GraphQLClientHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GraphQLClientHelper.
 */
@DisplayName("GraphQL Client Helper Tests")
class GraphQLClientHelperTest {

    @Test
    @DisplayName("Should create GraphQL helper with valid endpoint")
    void shouldCreateGraphQLHelperWithValidEndpoint() {
        // When
        GraphQLClientHelper helper = new GraphQLClientHelper("https://api.example.com/graphql");

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getEndpoint()).isEqualTo("https://api.example.com/graphql");
    }

    @Test
    @DisplayName("Should create GraphQL helper with custom configuration")
    void shouldCreateGraphQLHelperWithCustomConfiguration() {
        // Given
        Duration timeout = Duration.ofSeconds(60);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        headers.put("X-API-Version", "v1");

        // When
        GraphQLClientHelper helper = new GraphQLClientHelper(
            "https://api.example.com/graphql",
            timeout,
            headers
        );

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getEndpoint()).isEqualTo("https://api.example.com/graphql");
    }

    @Test
    @DisplayName("Should throw exception for null endpoint")
    void shouldThrowExceptionForNullEndpoint() {
        // When/Then
        assertThatThrownBy(() -> new GraphQLClientHelper(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GraphQL endpoint cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty endpoint")
    void shouldThrowExceptionForEmptyEndpoint() {
        // When/Then
        assertThatThrownBy(() -> new GraphQLClientHelper(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GraphQL endpoint cannot be null or empty");
    }

    @Test
    @DisplayName("Should demonstrate GraphQL helper API")
    void shouldDemonstrateGraphQLHelperApi() {
        // Given
        GraphQLClientHelper helper = new GraphQLClientHelper(
            "https://api.example.com/graphql",
            Duration.ofSeconds(30),
            Map.of("X-Client-Name", "test-client")
        );

        // Then: Helper should be properly configured
        assertThat(helper).isNotNull();
        assertThat(helper.getEndpoint()).isEqualTo("https://api.example.com/graphql");
        
        // Note: Actual GraphQL query tests would require a running GraphQL server
        // For integration tests, use WireMock to mock the GraphQL endpoint
    }

    @Test
    @DisplayName("Should demonstrate query construction")
    void shouldDemonstrateQueryConstruction() {
        // Given
        GraphQLClientHelper helper = new GraphQLClientHelper("https://api.example.com/graphql");

        String query = """
            query GetUser($id: ID!) {
                user(id: $id) {
                    id
                    name
                    email
                }
            }
        """;

        Map<String, Object> variables = Map.of("id", "123");

        // Then: Query and variables should be properly formatted
        assertThat(query).contains("query GetUser");
        assertThat(variables).containsEntry("id", "123");
    }

    @Test
    @DisplayName("Should create GraphQL helper with advanced config")
    void shouldCreateGraphQLHelperWithAdvancedConfig() {
        // Given
        GraphQLClientHelper.GraphQLConfig config = GraphQLClientHelper.GraphQLConfig.builder()
            .timeout(Duration.ofSeconds(60))
            .enableRetry(true)
            .maxRetries(5)
            .retryBackoff(Duration.ofMillis(1000))
            .enableQueryCache(true)
            .defaultHeader("Authorization", "Bearer token")
            .defaultHeader("X-API-Version", "v2")
            .build();

        // When
        GraphQLClientHelper helper = new GraphQLClientHelper("https://api.example.com/graphql", config);

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getEndpoint()).isEqualTo("https://api.example.com/graphql");
        assertThat(helper.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should build GraphQL request with builder")
    void shouldBuildGraphQLRequestWithBuilder() {
        // Given
        String query = "query { user { id name } }";

        // When
        GraphQLClientHelper.GraphQLRequest request = GraphQLClientHelper.GraphQLRequest.builder()
            .query(query)
            .variable("id", "123")
            .variable("limit", 10)
            .header("Authorization", "Bearer token")
            .build();

        // Then
        assertThat(request).isNotNull();
        assertThat(request.getQuery()).isEqualTo(query);
        assertThat(request.getVariables()).containsEntry("id", "123");
        assertThat(request.getVariables()).containsEntry("limit", 10);
        assertThat(request.getHeaders()).containsEntry("Authorization", "Bearer token");
    }

    @Test
    @DisplayName("Should throw exception when building request without query")
    void shouldThrowExceptionWhenBuildingRequestWithoutQuery() {
        // When/Then
        assertThatThrownBy(() ->
            GraphQLClientHelper.GraphQLRequest.builder().build()
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Query cannot be null or empty");
    }

    @Test
    @DisplayName("Should clear cache")
    void shouldClearCache() {
        // Given
        GraphQLClientHelper.GraphQLConfig config = GraphQLClientHelper.GraphQLConfig.builder()
            .enableQueryCache(true)
            .build();

        GraphQLClientHelper helper = new GraphQLClientHelper("https://api.example.com/graphql", config);

        // When
        helper.clearCache();

        // Then
        assertThat(helper.getCacheSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should demonstrate GraphQL config builder")
    void shouldDemonstrateGraphQLConfigBuilder() {
        // When
        GraphQLClientHelper.GraphQLConfig config = GraphQLClientHelper.GraphQLConfig.builder()
            .timeout(Duration.ofMinutes(2))
            .enableRetry(true)
            .maxRetries(3)
            .retryBackoff(Duration.ofSeconds(1))
            .enableQueryCache(true)
            .defaultHeaders(Map.of("X-Client", "test"))
            .build();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.toString()).contains("timeout=PT2M");
        assertThat(config.toString()).contains("retry=true");
        assertThat(config.toString()).contains("maxRetries=3");
        assertThat(config.toString()).contains("cache=true");
    }
}

