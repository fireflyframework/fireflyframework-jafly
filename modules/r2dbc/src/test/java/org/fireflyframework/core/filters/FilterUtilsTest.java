package org.fireflyframework.core.filters;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.ReactiveSelect;
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.TerminatingSelect;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FilterUtilsTest {

    // Fixed UUID values for predictable test outcomes
    private static final UUID TEST_ENTITY_ID_1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_ENTITY_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID TEST_FILTERABLE_ID_1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    private static final UUID TEST_FILTERABLE_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");

    @Mock
    private R2dbcEntityTemplate entityTemplate;

    @Mock
    private ReactiveSelect<TestEntity> reactiveSelect;

    @Mock
    private TerminatingSelect<TestEntity> terminatingSelect;

    private FilterUtils.GenericFilter<TestEntityFilter, TestEntity, TestEntity> filter;

    private final Function<TestEntity, TestEntity> identityMapper = entity -> entity;

    @BeforeEach
    void setUp() {
        FilterUtils.initializeTemplate(entityTemplate);
        filter = FilterUtils.createFilter(TestEntity.class, identityMapper);

        when(entityTemplate.select(TestEntity.class)).thenReturn(reactiveSelect);
        when(reactiveSelect.matching(any(Query.class))).thenReturn(terminatingSelect);
    }

    @Test
    void testStringFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setName("test");

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "name", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).name("test name").build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getName()).isEqualTo("test name");
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("name LIKE '%test%'");
    }

    @Test
    void testCaseInsensitiveStringFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setName("TEST");

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "name", "ASC"))
                .options(FilterUtils.FilterOptions.builder().caseInsensitiveStrings(true).build())
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).name("test name").build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getName()).isEqualTo("test name");
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString().toLowerCase()).contains("name like '%test%'".toLowerCase());
    }

    @Test
    void testNumericFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setCount(10);

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "count", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).count(10).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getCount()).isEqualTo(10);
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("count = 10");
    }

    @Test
    void testBooleanFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setActive(true);

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "active", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).active(true).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getActive()).isTrue();
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString().toLowerCase()).contains("active");
        assertThat(criteria.toString().toLowerCase()).contains("true");
    }

    @Test
    void testIdFieldFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setFilterableId(TEST_FILTERABLE_ID_1);

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_2).filterableId(TEST_FILTERABLE_ID_1).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getFilterableId()).isEqualTo(TEST_FILTERABLE_ID_1);
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString().toLowerCase()).contains("filterableid");
        assertThat(criteria.toString().toLowerCase()).contains(TEST_FILTERABLE_ID_1.toString().toLowerCase());
    }

    @Test
    void testRangeFiltering() {
        // Given
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        Map<String, RangeFilter.Range<?>> ranges = new HashMap<>();
        ranges.put("createdDate", new RangeFilter.Range<>(from, to));
        ranges.put("count", new RangeFilter.Range<>(5, 15));

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .rangeFilters(RangeFilter.builder().ranges(ranges).build())
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).count(10).createdDate(from.plusDays(1)).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getCount()).isEqualTo(10);
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("createdDate BETWEEN");
        assertThat(criteria.toString()).contains("count BETWEEN");
    }

    @Test
    void testCollectionFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setTags(Arrays.asList("tag1", "tag2"));

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).tags(Arrays.asList("tag1", "tag2", "tag3")).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getTags()).contains("tag1", "tag2");
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("tags IN");
    }

    @Test
    void testArrayFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setStringArray(new String[]{"value1", "value2"});

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).stringArray(new String[]{"value1", "value2", "value3"}).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getStringArray()).contains("value1", "value2");
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("stringArray IN");
    }

    @Test
    void testNullValueFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setName(null);
        FilterRequest.setNullFilter(testFilter, "name");

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).name(null).build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getName()).isNull();
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("name IS NULL");
    }

    @Test
    void testNotNullValueFiltering() {
        // Given
        TestEntityFilter testFilter = new TestEntityFilter();
        testFilter.setName(null);
        FilterRequest.setNotNullFilter(testFilter, "name");

        FilterRequest<TestEntityFilter> request = FilterRequest.<TestEntityFilter>builder()
                .filters(testFilter)
                .pagination(new PaginationRequest(0, 10, "id", "ASC"))
                .build();

        TestEntity entity = TestEntity.builder().id(TEST_ENTITY_ID_1).name("test").build();
        when(terminatingSelect.all()).thenReturn(Flux.just(entity));
        when(terminatingSelect.count()).thenReturn(Mono.just(1L));

        // When
        Mono<PaginationResponse<TestEntity>> result = filter.filter(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getName()).isEqualTo("test");
                    assertThat(response.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveSelect, org.mockito.Mockito.atLeastOnce()).matching(queryCaptor.capture());

        Criteria criteria = (Criteria) queryCaptor.getValue().getCriteria().orElse(Criteria.empty());
        assertThat(criteria.toString()).contains("name IS NOT NULL");
    }
}
