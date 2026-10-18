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

package org.fireflyframework.client.dynamic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Example usage of DynamicJsonResponse.
 * 
 * This test demonstrates how to use DynamicJsonResponse to work with JSON
 * responses without defining DTOs.
 */
class DynamicJsonResponseExampleTest {

    @Test
    void exampleUsage() {
        // Simulate a JSON response from an API
        String jsonResponse = """
            {
                "user": {
                    "id": 12345,
                    "username": "johndoe",
                    "email": "john.doe@example.com",
                    "profile": {
                        "firstName": "John",
                        "lastName": "Doe",
                        "age": 30,
                        "address": {
                            "street": "123 Main St",
                            "city": "New York",
                            "country": "USA"
                        }
                    },
                    "roles": ["admin", "user", "moderator"],
                    "settings": {
                        "notifications": true,
                        "theme": "dark"
                    }
                },
                "metadata": {
                    "timestamp": "2025-10-25T10:30:00Z",
                    "version": "1.0"
                }
            }
            """;

        // Parse the JSON response
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(jsonResponse);

        // ========================================
        // Example 1: Access simple fields
        // ========================================
        System.out.println("=== Example 1: Simple Field Access ===");
        Integer userId = response.getInt("user.id");
        String username = response.getString("user.username");
        String email = response.getString("user.email");
        
        System.out.println("User ID: " + userId);
        System.out.println("Username: " + username);
        System.out.println("Email: " + email);

        // ========================================
        // Example 2: Access nested fields with dot notation
        // ========================================
        System.out.println("\n=== Example 2: Nested Field Access ===");
        String firstName = response.getString("user.profile.firstName");
        String lastName = response.getString("user.profile.lastName");
        Integer age = response.getInt("user.profile.age");
        String city = response.getString("user.profile.address.city");
        
        System.out.println("Name: " + firstName + " " + lastName);
        System.out.println("Age: " + age);
        System.out.println("City: " + city);

        // ========================================
        // Example 3: Work with nested objects
        // ========================================
        System.out.println("\n=== Example 3: Nested Objects ===");
        DynamicJsonResponse userObj = response.getObject("user");
        DynamicJsonResponse profileObj = userObj.getObject("profile");
        DynamicJsonResponse addressObj = profileObj.getObject("address");
        
        System.out.println("Street: " + addressObj.getString("street"));
        System.out.println("City: " + addressObj.getString("city"));
        System.out.println("Country: " + addressObj.getString("country"));

        // ========================================
        // Example 4: Work with arrays
        // ========================================
        System.out.println("\n=== Example 4: Arrays ===");
        List<String> roles = response.getList("user.roles", String.class);
        System.out.println("User roles: " + roles);

        // ========================================
        // Example 5: Check field existence
        // ========================================
        System.out.println("\n=== Example 5: Field Existence ===");
        if (response.has("user.email")) {
            System.out.println("Email exists: " + response.getString("user.email"));
        }
        
        if (!response.has("user.phoneNumber")) {
            System.out.println("Phone number not provided");
        }

        // ========================================
        // Example 6: Get schema information
        // ========================================
        System.out.println("\n=== Example 6: Schema Introspection ===");
        Map<String, String> schema = response.getSchema();
        System.out.println("Root level fields:");
        schema.forEach((field, type) -> 
            System.out.println("  - " + field + ": " + type)
        );

        // ========================================
        // Example 7: Convert to Map
        // ========================================
        System.out.println("\n=== Example 7: Convert to Map ===");
        Map<String, Object> map = response.toMap();
        System.out.println("Converted to Map with " + map.size() + " root keys");

        // ========================================
        // Example 8: Pretty print JSON
        // ========================================
        System.out.println("\n=== Example 8: Pretty Print ===");
        String prettyJson = response.toPrettyJson();
        System.out.println(prettyJson);

        // ========================================
        // Example 9: Optional-based access (null-safe)
        // ========================================
        System.out.println("\n=== Example 9: Optional-based Access ===");
        response.getStringOpt("user.email")
            .ifPresent(e -> System.out.println("Email (Optional): " + e));
        
        response.getStringOpt("user.phoneNumber")
            .ifPresentOrElse(
                phone -> System.out.println("Phone: " + phone),
                () -> System.out.println("Phone number not available")
            );

        // ========================================
        // Example 10: Type conversion
        // ========================================
        System.out.println("\n=== Example 10: Type Conversion ===");
        Boolean notifications = response.getBoolean("user.settings.notifications");
        String theme = response.getString("user.settings.theme");
        
        System.out.println("Notifications enabled: " + notifications);
        System.out.println("Theme: " + theme);
    }

    @Test
    void exampleWithArrayOfObjects() {
        String jsonResponse = """
            {
                "products": [
                    {
                        "id": 1,
                        "name": "Laptop",
                        "price": 999.99,
                        "inStock": true
                    },
                    {
                        "id": 2,
                        "name": "Mouse",
                        "price": 29.99,
                        "inStock": false
                    },
                    {
                        "id": 3,
                        "name": "Keyboard",
                        "price": 79.99,
                        "inStock": true
                    }
                ]
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(jsonResponse);

        System.out.println("=== Working with Array of Objects ===");
        
        // Get list of product objects
        List<DynamicJsonResponse> products = response.getObjectList("products");
        
        System.out.println("Found " + products.size() + " products:");
        for (DynamicJsonResponse product : products) {
            Integer id = product.getInt("id");
            String name = product.getString("name");
            Double price = product.getDouble("price");
            Boolean inStock = product.getBoolean("inStock");
            
            System.out.printf("  - Product #%d: %s - $%.2f (In Stock: %s)%n", 
                id, name, price, inStock);
        }
    }

    @Test
    void exampleWithRealWorldAPI() {
        // Simulate a response from a real-world API (e.g., GitHub API)
        String githubResponse = """
            {
                "login": "octocat",
                "id": 1,
                "avatar_url": "https://github.com/images/error/octocat_happy.gif",
                "type": "User",
                "name": "The Octocat",
                "company": "GitHub",
                "blog": "https://github.com/blog",
                "location": "San Francisco",
                "email": "octocat@github.com",
                "bio": "There once was...",
                "public_repos": 8,
                "public_gists": 8,
                "followers": 20,
                "following": 0,
                "created_at": "2008-01-14T04:33:35Z",
                "updated_at": "2008-01-14T04:33:35Z"
            }
            """;

        DynamicJsonResponse user = DynamicJsonResponse.fromJson(githubResponse);

        System.out.println("=== GitHub User Information ===");
        System.out.println("Username: " + user.getString("login"));
        System.out.println("Name: " + user.getString("name"));
        System.out.println("Company: " + user.getString("company"));
        System.out.println("Location: " + user.getString("location"));
        System.out.println("Public Repos: " + user.getInt("public_repos"));
        System.out.println("Followers: " + user.getInt("followers"));
        System.out.println("Bio: " + user.getString("bio"));
        
        // Check optional fields
        if (user.has("email")) {
            System.out.println("Email: " + user.getString("email"));
        }
    }
}

