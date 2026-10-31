/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.InfrastructureProxy;
import org.fireflyframework.eda.consumer.postgres.*;
import org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoder;
import org.fireflyframework.eda.error.CustomErrorHandlerRegistry;
import org.fireflyframework.eda.listener.EventListenerProcessor;
import org.fireflyframework.eda.serialization.JsonMessageSerializer;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgresConsumerAutoConfigurationTest {
    private ApplicationContextRunner runner(boolean decoder){
        var r=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FireflyEdaPostgresConsumerAutoConfiguration.class))
                .withUserConfiguration(WorkerDependencies.class)
                .withPropertyValues("firefly.eda.consumer.enabled=true","firefly.eda.consumer.postgres.default.enabled=true",
                        "firefly.eda.consumer.postgres.default.host=127.0.0.1","firefly.eda.consumer.postgres.default.port=9",
                        "firefly.eda.consumer.postgres.default.database=technical","firefly.eda.consumer.postgres.default.username=technical",
                        "firefly.eda.consumer.postgres.default.max-pool-size=3");
        return decoder?r.withBean(PostgresEventDecoder.class,()->new RegisteredPostgresEventDecoder(new JsonMessageSerializer(new ObjectMapper()),Map.of("java.lang.String",String.class))):r;
    }
    @Test void namedRealPoolIsLazyBoundedAndDistinctFromRawListen(){
        final ConnectionPool[] owned=new ConnectionPool[1];
        runner(true).run(c->{assertThat(c).hasNotFailed();owned[0]=c.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionPool.class);
            assertThat(owned[0]).isNotSameAs(c.getBean("fireflyEdaPostgresConsumerConnectionFactory"));assertThat(owned[0].getMetrics().orElseThrow().allocatedSize()).isZero();assertThat(owned[0].getMetrics().orElseThrow().getMaxAllocatedSize()).isEqualTo(3);
            assertThat(((AbstractBeanDefinition)c.getBeanFactory().getBeanDefinition("fireflyEdaPostgresConsumerQueryConnectionFactory")).isDefaultCandidate()).isFalse();
            assertThat(((AbstractBeanDefinition)c.getBeanFactory().getBeanDefinition("fireflyEdaPostgresConsumerConnectionFactory")).isDefaultCandidate()).isFalse();});
        assertThat(owned[0].isDisposed()).isTrue();
    }
    @Test void explicitWorkerAndRepositoryDoNotPretendToBeRunningAtBeanCreation(){
        runner(true).run(c->{assertThat(c).hasNotFailed().hasSingleBean(PostgresEventConsumer.class).hasSingleBean(PostgresDeliveryRepository.class);assertThat(c.getBean(PostgresEventConsumer.class).isRunning()).isFalse();assertThat(c).doesNotHaveBean(R2dbcEntityTemplate.class).doesNotHaveBean(R2dbcTransactionManager.class);});
    }
    @Test void selectedPgWithoutRegisteredDecoderFailsClearly(){runner(false).run(c->assertThat(c).hasFailed());}
    @Test void disabledPgDoesNotValidateOrCreateItsTechnicalResources(){
        runner(false).withPropertyValues("firefly.eda.consumer.postgres.default.enabled=false","firefly.eda.consumer.postgres.default.connect-timeout=PT0S").run(c->{assertThat(c).hasNotFailed().doesNotHaveBean(PostgresEventConsumer.class);assertThat(c).doesNotHaveBean("fireflyEdaPostgresConsumerQueryConnectionFactory");});
    }
    @Test void invalidSelectedPgDurationsFailBeforeConnectionUse(){runner(true).withPropertyValues("firefly.eda.consumer.postgres.default.connect-timeout=PT0S").run(c->assertThat(c).hasFailed());}
    @Test void externalExactNamedQueryFactoryIsNeverDisposedOrReplaced(){
        var external=mock(ConnectionFactory.class,withSettings().extraInterfaces(reactor.core.Disposable.class));when(external.getMetadata()).thenReturn(()->"PostgreSQL");
        runner(true).withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->external,b->((AbstractBeanDefinition)b).setDefaultCandidate(false)).run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(PostgresEventConsumer.class);assertThat(c.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory")).isSameAs(external);assertThat(c).doesNotHaveBean("fireflyEdaPostgresConsumerQueryPoolOwner");verify(external,never()).create();});
        verify((reactor.core.Disposable)external,never()).dispose();
    }
    @ParameterizedTest @ValueSource(strings={"URL","DETAILS","EXPLICIT"})
    void actualCorePersistenceAndRepositoryStayOnBusinessFactory(String business){
        var r=runner(true).withConfiguration(AutoConfigurations.of(R2dbcAutoConfiguration.class,DataR2dbcAutoConfiguration.class,DataR2dbcRepositoriesAutoConfiguration.class,R2dbcTransactionManagerAutoConfiguration.class))
                .withUserConfiguration(CoreRepositoryFixture.class).withPropertyValues("spring.r2dbc.pool.initial-size=0","spring.sql.init.mode=never");
        if(business.equals("URL"))r=r.withPropertyValues("spring.r2dbc.url=r2dbc:postgresql://business@127.0.0.1:9/business");
        else if(business.equals("DETAILS"))r=r.withBean(R2dbcConnectionDetails.class,()->()->ConnectionFactoryOptions.parse("r2dbc:postgresql://business@127.0.0.1:9/business"));
        else r=r.withBean("connectionFactory",ConnectionFactory.class,()->{var f=mock(ConnectionFactory.class);when(f.getMetadata()).thenReturn(()->"PostgreSQL");return f;});
        r.run(c->{assertThat(c).hasNotFailed().hasSingleBean(BusinessRepository.class);var factory=c.getBean("connectionFactory",ConnectionFactory.class);assertThat(c.getBean(R2dbcEntityTemplate.class).getDatabaseClient().getConnectionFactory()).isSameAs(factory);assertThat(c.getBean(R2dbcTransactionManager.class).getConnectionFactory()).isSameAs(factory);assertThat(c.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory")).isNotSameAs(factory);assertThat(c.getBean("fireflyEdaPostgresConsumerConnectionFactory")).isNotSameAs(factory);});
    }
    @ParameterizedTest @ValueSource(strings={"TRANSACTION_AWARE","SINGLE_FALSE","SINGLE_TRUE","INFRASTRUCTURE_SCOPED"})
    void externalScopedQueryFactoryFailsBeforeTargetLookupOrAcquisition(String kind){
        var acquisitions=new AtomicInteger();var targetLookups=new AtomicInteger();
        var raw=mock(ConnectionFactory.class);when(raw.getMetadata()).thenReturn(()->"PostgreSQL");
        doReturn(reactor.core.publisher.Mono.defer(()->{acquisitions.incrementAndGet();return reactor.core.publisher.Mono.error(new AssertionError("External connection acquisition forbidden"));})).when(raw).create();
        ConnectionFactory target;
        if(kind.equals("TRANSACTION_AWARE"))target=new org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy(raw);
        else {var single=new org.springframework.r2dbc.connection.SingleConnectionFactory(raw);single.setSuppressClose(!kind.equals("SINGLE_FALSE"));target=single;}
        try(var scope=new GenericApplicationContext()){
            scope.getBeanFactory().registerScope("consumer-graph",new SimpleThreadScope(){
                @Override public Object get(String name,ObjectFactory<?> factory){targetLookups.incrementAndGet();return super.get(name,factory);}
            });
            scope.registerBean("scopedTarget.query",ConnectionFactory.class,()->target,definition->definition.setScope("consumer-graph"));scope.refresh();
            var factory=new ScopedProxyFactoryBean();factory.setTargetBeanName("scopedTarget.query");factory.setProxyTargetClass(false);factory.setBeanFactory(scope.getBeanFactory());
            var scoped=(ConnectionFactory)factory.getObject();assertThat(scoped).isInstanceOf(ScopedObject.class);
            ConnectionFactory provided=kind.equals("INFRASTRUCTURE_SCOPED")?new ExistingInfrastructureFactory(scoped):scoped;
            runner(true).withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->provided,definition->{
                ((AbstractBeanDefinition)definition).setDefaultCandidate(false);((AbstractBeanDefinition)definition).setDestroyMethodName("");
            }).run(context->{
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("Delivery requires the underlying independent ConnectionFactory");
            });
            assertThat(targetLookups).hasValue(0);assertThat(acquisitions).hasValue(0);
        }
        assertThat(targetLookups).hasValue(0);assertThat(acquisitions).hasValue(0);
    }
    static final class ExistingInfrastructureFactory implements ConnectionFactory,InfrastructureProxy {
        private final ConnectionFactory target;
        ExistingInfrastructureFactory(ConnectionFactory target){this.target=target;}
        public Object getWrappedObject(){return target;}
        public org.reactivestreams.Publisher<? extends io.r2dbc.spi.Connection> create(){return target.create();}
        public io.r2dbc.spi.ConnectionFactoryMetadata getMetadata(){return target.getMetadata();}
    }
    static class WorkerDependencies {
        @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean MessageSerializer messageSerializer(ObjectMapper mapper){return new JsonMessageSerializer(mapper);}
        @Bean EventListenerProcessor eventListenerProcessor(ApplicationContext context,ApplicationEventPublisher publisher,Environment environment){return new EventListenerProcessor(context,publisher,mock(CustomErrorHandlerRegistry.class),environment);}
    }
    @EnableR2dbcRepositories(basePackageClasses=PostgresConsumerAutoConfigurationTest.class,considerNestedRepositories=true,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=BusinessRepository.class))
    static class CoreRepositoryFixture { }
    interface BusinessRepository extends R2dbcRepository<BusinessRow,Long> { }
    @Table("core_business_row") record BusinessRow(@Id Long id) { }
}
