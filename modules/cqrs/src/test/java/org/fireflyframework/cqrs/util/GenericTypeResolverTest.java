package org.fireflyframework.cqrs.util;

import org.fireflyframework.cqrs.command.Command;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.command.ContextAwareCommandHandler;
import org.fireflyframework.cqrs.context.ExecutionContext;
import org.fireflyframework.cqrs.query.ContextAwareQueryHandler;
import org.fireflyframework.cqrs.query.Query;
import org.fireflyframework.cqrs.query.QueryHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericTypeResolverTest {
    @Test
    void constructsContextAwareCommandHandlerWithConcreteRequestAndResultTypes() {
        var handler = new ContextCommand();
        assertThat(handler.getCommandType()).isEqualTo(TestCommand.class);
        assertThat(handler.getResultType()).isEqualTo(String.class);
    }

    @Test
    void constructsContextAwareQueryHandlerWithConcreteRequestAndResultTypes() {
        var handler = new ContextQuery();
        assertThat(handler.getQueryType()).isEqualTo(TestQuery.class);
        assertThat(handler.getResultType()).isEqualTo(String.class);
    }

    @Test
    void carriesReorderedCommandBindingsAcrossMultipleSuperclasses() {
        var handler = new InheritedCommand();
        assertThat(handler.getCommandType()).isEqualTo(TestCommand.class);
        assertThat(handler.getResultType()).isEqualTo(String.class);
    }

    @Test
    void carriesReorderedQueryBindingsAcrossMultipleSuperclasses() {
        var handler = new InheritedQuery();
        assertThat(handler.getQueryType()).isEqualTo(TestQuery.class);
        assertThat(handler.getResultType()).isEqualTo(String.class);
    }

    @Test
    void resolvesInheritedInterfaceBindingsWithoutAssumingParameterOrder() {
        assertThat(GenericTypeResolver.resolveGenericType(InterfaceImplementation.class, Pair.class, 0)).isEqualTo(String.class);
        assertThat(GenericTypeResolver.resolveGenericType(InterfaceImplementation.class, Pair.class, 1)).isEqualTo(Integer.class);
    }

    @Test
    void retainsRawClassOfResolvedParameterizedArguments() {
        assertThat(GenericTypeResolver.resolveGenericType(ListImplementation.class, Pair.class, 0)).isEqualTo(List.class);
        assertThat(GenericTypeResolver.resolveGenericType(ListImplementation.class, Pair.class, 1)).isEqualTo(String.class);
    }

    @Test
    void unresolvedCommandVariableDoesNotFallBackToItsCommandBound() {
        assertThat(GenericTypeResolver.resolveGenericType(UnresolvedCommand.class, CommandHandler.class, 0)).isNull();
        assertThatThrownBy(() -> GenericTypeResolver.resolveCommandType(UnresolvedCommand.class))
                .isInstanceOf(GenericTypeResolver.TypeResolutionException.class);
    }

    @Test
    void unresolvedQueryVariableDoesNotFallBackToItsQueryBound() {
        assertThat(GenericTypeResolver.resolveQueryType(UnresolvedQuery.class)).isNull();
    }

    @Test
    void rawContextAwareHandlersRemainUnresolved() {
        assertThat(GenericTypeResolver.resolveGenericType(ContextAwareCommandHandler.class, CommandHandler.class, 0)).isNull();
        assertThat(GenericTypeResolver.resolveGenericType(ContextAwareCommandHandler.class, CommandHandler.class, 1)).isNull();
        assertThat(GenericTypeResolver.resolveQueryType(ContextAwareQueryHandler.class)).isNull();
        assertThat(GenericTypeResolver.resolveQueryResultType(ContextAwareQueryHandler.class)).isNull();
    }

    @Test
    void rawAndUnresolvedInterfacesRemainUnresolved() {
        assertThat(GenericTypeResolver.resolveGenericType(RawImplementation.class, Pair.class, 0)).isNull();
        assertThat(GenericTypeResolver.resolveGenericType(UnresolvedPair.class, Pair.class, 0)).isNull();
    }

    @Test
    void explicitlyBoundObjectRemainsAValidResultType() {
        assertThat(GenericTypeResolver.resolveGenericType(ObjectImplementation.class, Pair.class, 0)).isEqualTo(Object.class);
    }

    @Test
    void unrelatedTargetAndOutOfRangeIndexRemainUnresolved() {
        assertThat(GenericTypeResolver.resolveGenericType(String.class, Pair.class, 0)).isNull();
        assertThat(GenericTypeResolver.resolveGenericType(InterfaceImplementation.class, Pair.class, 2)).isNull();
    }

    @Test
    void rejectsNullInputsAndNegativeIndices() {
        assertThatThrownBy(() -> GenericTypeResolver.resolveGenericType(null, Pair.class, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericTypeResolver.resolveGenericType(InterfaceImplementation.class, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenericTypeResolver.resolveGenericType(InterfaceImplementation.class, Pair.class, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    record TestCommand() implements Command<String> { }
    record TestQuery() implements Query<String> { }

    static class ContextCommand extends ContextAwareCommandHandler<TestCommand, String> {
        @Override protected Mono<String> doHandle(TestCommand command, ExecutionContext context) { return Mono.just("command"); }
    }
    static class ContextQuery extends ContextAwareQueryHandler<TestQuery, String> {
        @Override protected Mono<String> doHandle(TestQuery query, ExecutionContext context) { return Mono.just("query"); }
    }
    abstract static class ReorderedCommand<R, C extends Command<R>> extends ContextAwareCommandHandler<C, R> { }
    static class BoundCommand extends ReorderedCommand<String, TestCommand> {
        @Override protected Mono<String> doHandle(TestCommand command, ExecutionContext context) { return Mono.just("command"); }
    }
    static class InheritedCommand extends BoundCommand { }
    abstract static class ReorderedQuery<R, Q extends Query<R>> extends ContextAwareQueryHandler<Q, R> { }
    static class BoundQuery extends ReorderedQuery<String, TestQuery> {
        @Override protected Mono<String> doHandle(TestQuery query, ExecutionContext context) { return Mono.just("query"); }
    }
    static class InheritedQuery extends BoundQuery { }
    abstract static class UnresolvedCommand<C extends Command<String>> extends ContextAwareCommandHandler<C, String> { }
    abstract static class UnresolvedQuery<Q extends Query<String>> extends ContextAwareQueryHandler<Q, String> { }
    interface Pair<A, B> { }
    interface ReorderedPair<B, A> extends Pair<A, B> { }
    abstract static class PairBase<X, Y> implements ReorderedPair<Y, X> { }
    static class InterfaceImplementation extends PairBase<String, Integer> { }
    static class ListImplementation extends PairBase<List<Integer>, String> { }
    static class ObjectImplementation extends PairBase<Object, String> { }
    static class UnresolvedPair<T extends Number> extends PairBase<T, String> { }
    @SuppressWarnings("rawtypes")
    static class RawImplementation implements Pair { }
}
