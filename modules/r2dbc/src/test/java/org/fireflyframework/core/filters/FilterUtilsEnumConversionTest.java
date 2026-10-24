package org.fireflyframework.core.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.StatementMapper;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.r2dbc.core.binding.BindTarget;

class FilterUtilsEnumConversionTest {
    @BeforeEach
    void initializeCriteriaFactory() {
        FilterUtils.initializeTemplate(mock(R2dbcEntityTemplate.class));
    }

    @Test
    void scalarEnumUsesTheConfiguredNativeEnumWriter() {
        assertNativeLabel(new ScalarFilter(Status.POSTED));
    }

    @Test
    void enumCollectionUsesTheSameConfiguredWriter() {
        assertNativeLabel(new CollectionFilter(List.of(Status.POSTED)));
    }

    @Test
    void defaultEnumPersistenceRetainsItsStandardName() {
        var conversions = R2dbcCustomConversions.of(PostgresDialect.INSTANCE, List.of());
        assertEquals(List.of("POSTED"), bindings(new ScalarFilter(Status.POSTED), conversions));
    }

    private static void assertNativeLabel(Object input) {
        var conversions = R2dbcCustomConversions.of(PostgresDialect.INSTANCE, new StatusWriter());
        var bindings = bindings(input, conversions);
        assertEquals(1, bindings.size());
        var parameter = assertInstanceOf(Parameter.class, bindings.getFirst());
        assertEquals("posted", parameter.getValue());
        assertEquals(PostgresqlObjectId.UNSPECIFIED, parameter.getType());
    }

    private static List<Object> bindings(Object input, R2dbcCustomConversions conversions) {
        var request = FilterRequest.builder().filters(input).build();
        var criteria = FilterUtils.createFilter(Entry.class, Function.identity()).getCriteria(request);
        var context = new R2dbcMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        var converter = new MappingR2dbcConverter(context, conversions);
        var mapper = StatementMapper.create(PostgresDialect.INSTANCE, converter).forType(Entry.class);
        var operation = assertDoesNotThrow(() -> mapper.getMappedObject(
                mapper.createSelect("enum_conversion_fixture").withProjection("id").withCriteria(criteria)),
                "Enum filters must reach the configured persistence writer with their original type");
        var bindings = new ArrayList<Object>();
        operation.bindTo(new BindTarget() {
            public void bind(String name, Object value) { bindings.add(value); }
            public void bind(int index, Object value) { bindings.add(value); }
            public void bindNull(String name, Class<?> type) { fail("Unexpected null binding"); }
            public void bindNull(int index, Class<?> type) { fail("Unexpected null binding"); }
        });
        return bindings;
    }

    private enum Status { POSTED }
    private record ScalarFilter(Status status) {}
    private record CollectionFilter(List<Status> status) {}

    @Table("enum_conversion_fixture")
    private record Entry(@Id UUID id, Status status) {}

    @WritingConverter
    private static final class StatusWriter implements Converter<Status, Parameter> {
        public Parameter convert(Status status) {
            return Parameters.in(PostgresqlObjectId.UNSPECIFIED, status.name().toLowerCase(Locale.ROOT));
        }
    }
}
