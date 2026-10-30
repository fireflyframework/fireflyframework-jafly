package org.fireflyframework.core.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.fireflyframework.core.queries.PaginationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.ReactiveSelect;
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.TerminatingSelect;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FilterUtilsLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(FilterUtils.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
        FilterUtils.initializeTemplate(null);
    }

    @Test
    void paginationLogsPresenceWithoutRenderingRequestDetails() {
        var rendered = new AtomicInteger();
        var pagination = new PaginationRequest(0, 10, "private\r\nfield\u2028value", "ASC") {
            @Override public String toString() {
                rendered.incrementAndGet();
                return "PRIVATE_PAGINATION\r\nforged event";
            }
        };
        var result = paginatedFilter().filter(FilterRequest.builder().pagination(pagination).build()).block();
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals("Pagination request present=true", events.list.getFirst().getFormattedMessage());
        assertEquals(Level.INFO, events.list.getFirst().getLevel());
        assertEquals(0, rendered.get());
        assertNull(events.list.getFirst().getThrowableProxy());
    }

    @Test
    void absentPaginationRetainsDefaultPagingAndLogsOnlyPresence() {
        var result = paginatedFilter().filter(FilterRequest.builder().build()).block();
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals("Pagination request present=false", events.list.getFirst().getFormattedMessage());
    }

    @Test
    void filterFailureRetainsCriteriaFallbackWithoutLeakingMessageOrStack() {
        Collection<Object> values = new AbstractCollection<>() {
            @Override public Iterator<Object> iterator() { throw new AssertionError("Not needed"); }
            @Override public int size() { throw new IllegalStateException("PRIVATE_FILTER\r\nforged event\u2028payload"); }
        };
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var request = FilterRequest.builder().filters(new CollectionFilter(values)).build();
        var criteria = assertDoesNotThrow(() -> FilterUtils.createFilter(Entry.class, Function.identity()).getCriteria(request));
        assertTrue(criteria.isEmpty());
        assertEquals(1, events.list.size());
        var event = events.list.getFirst();
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("Unexpected error processing filter field values: type=java.lang.IllegalStateException", event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
        assertFalse(event.getFormattedMessage().contains("PRIVATE_FILTER"));
    }

    @Test
    void disabledLoggingDoesNotRenderPaginationOrChangeResults() {
        logger.setLevel(Level.OFF);
        var rendered = new AtomicInteger();
        var pagination = new PaginationRequest(0, 10, null, "ASC") {
            @Override public String toString() { rendered.incrementAndGet(); return "PRIVATE"; }
        };
        assertEquals(0, paginatedFilter().filter(FilterRequest.builder().pagination(pagination).build()).block().getTotalElements());
        assertEquals(0, rendered.get());
        assertTrue(events.list.isEmpty());
    }

    @Test
    void skippedIdRangeRetainsItsDiagnosticAndEmptyCriteria() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
        var ranges = new java.util.HashMap<String, RangeFilter.Range<?>>();
        ranges.put("id", new RangeFilter.Range<>(1, 2));
        var request = FilterRequest.builder().rangeFilters(new RangeFilter(ranges)).build();
        var criteria = FilterUtils.createFilter(TestEntity.class, Function.identity()).getCriteria(request);
        assertTrue(criteria.isEmpty());
        assertEquals(1, events.list.size());
        assertEquals(Level.DEBUG, events.list.getFirst().getLevel());
        assertEquals("Skipping range filter for ID field: id", events.list.getFirst().getFormattedMessage());
    }

    @SuppressWarnings("unchecked")
    private FilterUtils.GenericFilter<Object, Entry, Entry> paginatedFilter() {
        var template = mock(R2dbcEntityTemplate.class);
        ReactiveSelect<Entry> select = mock(ReactiveSelect.class);
        TerminatingSelect<Entry> matching = mock(TerminatingSelect.class);
        when(template.select(Entry.class)).thenReturn(select);
        when(select.matching(any(Query.class))).thenReturn(matching);
        when(matching.all()).thenReturn(Flux.empty());
        when(matching.count()).thenReturn(Mono.just(0L));
        FilterUtils.initializeTemplate(template);
        return FilterUtils.createFilter(Entry.class, Function.identity());
    }

    private record Entry(String value) {}
    private record CollectionFilter(Collection<?> values) {}
}
