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

package org.fireflyframework.rules.core.services;

import org.fireflyframework.rules.core.mappers.ConstantMapper;
import org.fireflyframework.rules.core.services.impl.ConstantServiceImpl;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.enums.ValueType;
import org.fireflyframework.rules.models.entities.Constant;
import org.fireflyframework.rules.models.repositories.ConstantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for ConstantService CRUD operations.
 * Tests all service layer functionality including validation, error handling, and business logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConstantService Tests")
class ConstantServiceTest {

    @Mock
    private ConstantRepository repository;

    @Mock
    private ConstantMapper mapper;

    @InjectMocks
    private ConstantServiceImpl constantService;

    @Nested
    @DisplayName("Create Constant Tests")
    class CreateConstantTests {

        @Test
        @DisplayName("Should create constant successfully")
        void shouldCreateConstantSuccessfully() {
            // Given
            ConstantDTO inputDTO = createTestConstantDTO();
            Constant entity = createTestConstantEntity();
            ConstantDTO outputDTO = createTestConstantDTO();
            outputDTO.setId(entity.getId());

            when(mapper.toEntity(inputDTO)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(Mono.just(entity));
            when(mapper.toDTO(entity)).thenReturn(outputDTO);

            // When & Then
            StepVerifier.create(constantService.createConstant(inputDTO))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(entity.getId());
                        assertThat(result.getCode()).isEqualTo("MIN_CREDIT_SCORE");
                        assertThat(result.getCurrentValue()).isEqualTo(new BigDecimal("700"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle repository errors during creation")
        void shouldHandleRepositoryErrorsDuringCreation() {
            // Given
            ConstantDTO inputDTO = createTestConstantDTO();
            Constant entity = createTestConstantEntity();

            when(mapper.toEntity(inputDTO)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(Mono.error(new RuntimeException("Database error")));

            // When & Then
            StepVerifier.create(constantService.createConstant(inputDTO))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().equals("Database error"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Update Constant Tests")
    class UpdateConstantTests {

        @Test
        @DisplayName("Should update constant successfully")
        void shouldUpdateConstantSuccessfully() {
            // Given
            UUID constantId = UUID.randomUUID();
            ConstantDTO inputDTO = createTestConstantDTO();
            inputDTO.setCurrentValue(new BigDecimal("750")); // Updated value
            
            Constant existingEntity = createTestConstantEntity();
            existingEntity.setId(constantId);
            
            Constant updatedEntity = createTestConstantEntity();
            updatedEntity.setId(constantId);
            updatedEntity.setCurrentValue(new BigDecimal("750"));
            
            ConstantDTO outputDTO = createTestConstantDTO();
            outputDTO.setId(constantId);
            outputDTO.setCurrentValue(new BigDecimal("750"));

            when(repository.findById(constantId)).thenReturn(Mono.just(existingEntity));
            when(mapper.toEntity(any(ConstantDTO.class))).thenReturn(updatedEntity);
            when(repository.save(any(Constant.class))).thenReturn(Mono.just(updatedEntity));
            when(mapper.toDTO(updatedEntity)).thenReturn(outputDTO);

            // When & Then
            StepVerifier.create(constantService.updateConstant(constantId, inputDTO))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(constantId);
                        assertThat(result.getCurrentValue()).isEqualTo(new BigDecimal("750"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when constant not found for update")
        void shouldFailWhenConstantNotFoundForUpdate() {
            // Given
            UUID constantId = UUID.randomUUID();
            ConstantDTO inputDTO = createTestConstantDTO();

            when(repository.findById(constantId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(constantService.updateConstant(constantId, inputDTO))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Delete Constant Tests")
    class DeleteConstantTests {

        @Test
        @DisplayName("Should delete constant successfully")
        void shouldDeleteConstantSuccessfully() {
            // Given
            UUID constantId = UUID.randomUUID();
            Constant entity = createTestConstantEntity();
            entity.setId(constantId);

            when(repository.findById(constantId)).thenReturn(Mono.just(entity));
            when(repository.deleteById(constantId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(constantService.deleteConstant(constantId))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when constant not found for deletion")
        void shouldFailWhenConstantNotFoundForDeletion() {
            // Given
            UUID constantId = UUID.randomUUID();

            when(repository.findById(constantId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(constantService.deleteConstant(constantId))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Get Constant Tests")
    class GetConstantTests {

        @Test
        @DisplayName("Should get constant by ID successfully")
        void shouldGetConstantByIdSuccessfully() {
            // Given
            UUID constantId = UUID.randomUUID();
            Constant entity = createTestConstantEntity();
            entity.setId(constantId);
            ConstantDTO dto = createTestConstantDTO();
            dto.setId(constantId);

            when(repository.findById(constantId)).thenReturn(Mono.just(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            // When & Then
            StepVerifier.create(constantService.getConstantById(constantId))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(constantId);
                        assertThat(result.getCode()).isEqualTo("MIN_CREDIT_SCORE");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when constant not found by ID")
        void shouldFailWhenConstantNotFoundById() {
            // Given
            UUID constantId = UUID.randomUUID();

            when(repository.findById(constantId)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(constantService.getConstantById(constantId))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }

        @Test
        @DisplayName("Should get constant by code successfully")
        void shouldGetConstantByCodeSuccessfully() {
            // Given
            String code = "MIN_CREDIT_SCORE";
            Constant entity = createTestConstantEntity();
            ConstantDTO dto = createTestConstantDTO();

            when(repository.findByCode(code)).thenReturn(Mono.just(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            // When & Then
            StepVerifier.create(constantService.getConstantByCode(code))
                    .assertNext(result -> {
                        assertThat(result).isNotNull();
                        assertThat(result.getCode()).isEqualTo(code);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail when constant not found by code")
        void shouldFailWhenConstantNotFoundByCode() {
            // Given
            String code = "NONEXISTENT_CONSTANT";

            when(repository.findByCode(code)).thenReturn(Mono.empty());

            // When & Then
            StepVerifier.create(constantService.getConstantByCode(code))
                    .expectErrorMatches(throwable -> 
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().contains("not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Get Constants By Codes Tests")
    class GetConstantsByCodesTests {

        @Test
        @DisplayName("Should get multiple constants by codes successfully")
        void shouldGetMultipleConstantsByCodesSuccessfully() {
            // Given
            List<String> codes = Arrays.asList("MIN_CREDIT_SCORE", "MAX_LOAN_AMOUNT");
            
            Constant entity1 = createTestConstantEntity();
            entity1.setCode("MIN_CREDIT_SCORE");
            
            Constant entity2 = createTestConstantEntity();
            entity2.setCode("MAX_LOAN_AMOUNT");
            entity2.setCurrentValue(new BigDecimal("1000000"));
            
            ConstantDTO dto1 = createTestConstantDTO();
            dto1.setCode("MIN_CREDIT_SCORE");
            
            ConstantDTO dto2 = createTestConstantDTO();
            dto2.setCode("MAX_LOAN_AMOUNT");
            dto2.setCurrentValue(new BigDecimal("1000000"));

            when(repository.findByCode("MIN_CREDIT_SCORE")).thenReturn(Mono.just(entity1));
            when(repository.findByCode("MAX_LOAN_AMOUNT")).thenReturn(Mono.just(entity2));
            when(mapper.toDTO(entity1)).thenReturn(dto1);
            when(mapper.toDTO(entity2)).thenReturn(dto2);

            // When & Then
            StepVerifier.create(constantService.getConstantsByCodes(codes))
                    .assertNext(result -> {
                        assertThat(result.getCode()).isEqualTo("MIN_CREDIT_SCORE");
                    })
                    .assertNext(result -> {
                        assertThat(result.getCode()).isEqualTo("MAX_LOAN_AMOUNT");
                        assertThat(result.getCurrentValue()).isEqualTo(new BigDecimal("1000000"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle empty codes list")
        void shouldHandleEmptyCodesList() {
            // Given
            List<String> codes = Arrays.asList();

            // When & Then
            StepVerifier.create(constantService.getConstantsByCodes(codes))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle null codes list")
        void shouldHandleNullCodesList() {
            // When & Then
            StepVerifier.create(constantService.getConstantsByCodes(null))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle missing constants gracefully")
        void shouldHandleMissingConstantsGracefully() {
            // Given
            List<String> codes = Arrays.asList("EXISTING_CONSTANT", "MISSING_CONSTANT");
            
            Constant existingEntity = createTestConstantEntity();
            existingEntity.setCode("EXISTING_CONSTANT");
            
            ConstantDTO existingDTO = createTestConstantDTO();
            existingDTO.setCode("EXISTING_CONSTANT");

            when(repository.findByCode("EXISTING_CONSTANT")).thenReturn(Mono.just(existingEntity));
            when(repository.findByCode("MISSING_CONSTANT")).thenReturn(Mono.empty());
            when(mapper.toDTO(existingEntity)).thenReturn(existingDTO);

            // When & Then
            StepVerifier.create(constantService.getConstantsByCodes(codes))
                    .assertNext(result -> {
                        assertThat(result.getCode()).isEqualTo("EXISTING_CONSTANT");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle repository errors gracefully")
        void shouldHandleRepositoryErrorsGracefully() {
            // Given
            List<String> codes = Arrays.asList("ERROR_CONSTANT");

            when(repository.findByCode("ERROR_CONSTANT"))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When & Then
            StepVerifier.create(constantService.getConstantsByCodes(codes))
                    .verifyComplete(); // Should complete without error, just skip the problematic constant
        }
    }

    // Helper methods
    private ConstantDTO createTestConstantDTO() {
        return ConstantDTO.builder()
                .code("MIN_CREDIT_SCORE")
                .name("Minimum Credit Score")
                .description("Minimum credit score required for loan approval")
                .valueType(ValueType.NUMBER)
                .currentValue(new BigDecimal("700"))
                .required(true)
                .build();
    }

    private Constant createTestConstantEntity() {
        Constant entity = new Constant();
        entity.setId(UUID.randomUUID());
        entity.setCode("MIN_CREDIT_SCORE");
        entity.setName("Minimum Credit Score");
        entity.setDescription("Minimum credit score required for loan approval");
        entity.setValueType(ValueType.NUMBER);
        entity.setCurrentValue(new BigDecimal("700"));
        entity.setRequired(true);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }
}
