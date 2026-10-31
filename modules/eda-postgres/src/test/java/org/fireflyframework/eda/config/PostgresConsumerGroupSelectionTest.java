/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.fireflyframework.eda.consumer.postgres.*;
import org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoder;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresConsumerGroupRepository;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresDeliveryRepository;
import org.fireflyframework.eda.consumer.postgres.model.ConsumerGroupId;
import org.fireflyframework.eda.consumer.postgres.model.ConsumerGroupTables;
import org.fireflyframework.eda.serialization.JsonMessageSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgresConsumerGroupSelectionTest {
    private ApplicationContextRunner runner(){
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FireflyEdaPostgresConsumerAutoConfiguration.class))
                .withUserConfiguration(PostgresConsumerAutoConfigurationTest.WorkerDependencies.class)
                .withBean(PostgresEventDecoder.class,()->new RegisteredPostgresEventDecoder(new JsonMessageSerializer(new ObjectMapper()),Map.of("java.lang.String",String.class)))
                .withPropertyValues("firefly.eda.consumer.enabled=true","firefly.eda.consumer.postgres.default.enabled=true",
                        "firefly.eda.consumer.postgres.default.host=127.0.0.1","firefly.eda.consumer.postgres.default.port=9",
                        "firefly.eda.consumer.postgres.default.database=technical","firefly.eda.consumer.postgres.default.username=technical");
    }
    @Test void absentPostgresGroupKeepsGlobalCompatibilityWithoutInferringTheGlobalConsumerGroup(){
        runner().withPropertyValues("firefly.eda.consumer.group-id=unrelated-global-id").run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("fireflyEdaPostgresDeliveryRepository")).isInstanceOf(SpringDataPostgresDeliveryRepository.class);
        });
    }
    @Test void actualPostgresGroupPropertySelectsTheGroupBoundAdapter(){
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service").run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("fireflyEdaPostgresDeliveryRepository")).isInstanceOf(SpringDataPostgresConsumerGroupRepository.class);
            assertThat(context.getBean(PostgresEventConsumer.class).isRunning()).isFalse();
        });
    }
    @ParameterizedTest @ValueSource(strings={""," "})
    void explicitlyBlankPostgresGroupRefusesInsteadOfFallingBack(String value){
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id="+value).run(context->assertThat(context).hasFailed());
    }
    static Stream<String> invalidGroupIds(){return Stream.of(String.valueOf((char)0),"x".repeat(256),"é".repeat(128),String.valueOf((char)0xD800));}
    @ParameterizedTest @MethodSource("invalidGroupIds")
    void invalidGroupIdentityRefusesBeforeResourceUse(String value){
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id="+value).run(context->assertThat(context).hasFailed());
    }
    @ParameterizedTest @ValueSource(strings={"group-registry-table","group-deliveries-table","group-redrives-table"})
    void invalidGroupRelationNamesCannotSilentlyUseGlobalDelivery(String property){
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service",
                "firefly.eda.consumer.postgres.default."+property+"="+"x".repeat(64)).run(context->assertThat(context).hasFailed());
    }
    @Test void groupRelationsMustBeDistinct(){
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service",
                "firefly.eda.consumer.postgres.default.group-registry-table=firefly_eda_outbox").run(context->assertThat(context).hasFailed());
    }
    @Test void explicitNamedQueryFactoryRemainsExternalLazyAndSeparateFromListen(){
        var external=mock(ConnectionFactory.class,withSettings().extraInterfaces(reactor.core.Disposable.class));when(external.getMetadata()).thenReturn(()->"PostgreSQL");
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service")
                .withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->external,bean->{
                    ((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");
                }).run(context->{
                    assertThat(context).hasNotFailed();assertThat(context.getBean(PostgresDeliveryRepository.class)).isInstanceOf(SpringDataPostgresConsumerGroupRepository.class);
                    assertThat(context.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory")).isSameAs(external);
                    assertThat(context.getBean("fireflyEdaPostgresConsumerConnectionFactory")).isNotSameAs(external);
                    assertThat(context).doesNotHaveBean("fireflyEdaPostgresConsumerQueryPoolOwner");verify(external,never()).create();
                });
        verify((reactor.core.Disposable)external,never()).dispose();
    }
    @Test void explicitNamedGroupPortIsPreserved(){
        var factory=mock(ConnectionFactory.class);when(factory.getMetadata()).thenReturn(()->"PostgreSQL");
        var repository=new SpringDataPostgresConsumerGroupRepository(factory,new ConsumerGroupTables("public","events","registry","deliveries","redrives"),new ConsumerGroupId("orders-service"),java.time.Duration.ofSeconds(5));
        runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service")
                .withBean("fireflyEdaPostgresDeliveryRepository",PostgresDeliveryRepository.class,()->repository).run(context->{
                    assertThat(context).hasNotFailed();assertThat(context.getBean(PostgresDeliveryRepository.class)).isSameAs(repository);verify(factory,never()).create();
                });
    }
    @ParameterizedTest @ValueSource(strings={"URL","DETAILS","EXPLICIT"})
    void selectedGroupCannotReplaceTheActualCoreFactoryTemplateTransactionManagerOrRepository(String business){
        var configured=runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service","spring.r2dbc.pool.initial-size=0","spring.sql.init.mode=never")
                .withConfiguration(AutoConfigurations.of(R2dbcAutoConfiguration.class,DataR2dbcAutoConfiguration.class,DataR2dbcRepositoriesAutoConfiguration.class,R2dbcTransactionManagerAutoConfiguration.class))
                .withUserConfiguration(PostgresConsumerAutoConfigurationTest.CoreRepositoryFixture.class);
        if(business.equals("URL"))configured=configured.withPropertyValues("spring.r2dbc.url=r2dbc:postgresql://business@127.0.0.1:9/business");
        else if(business.equals("DETAILS"))configured=configured.withBean(R2dbcConnectionDetails.class,()->()->ConnectionFactoryOptions.parse("r2dbc:postgresql://business@127.0.0.1:9/business"));
        else configured=configured.withBean("connectionFactory",ConnectionFactory.class,()->{var factory=mock(ConnectionFactory.class);when(factory.getMetadata()).thenReturn(()->"PostgreSQL");return factory;});
        configured.run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(PostgresConsumerAutoConfigurationTest.BusinessRepository.class);
            var businessFactory=context.getBean("connectionFactory",ConnectionFactory.class);
            assertThat(context.getBean(R2dbcEntityTemplate.class).getDatabaseClient().getConnectionFactory()).isSameAs(businessFactory);
            assertThat(context.getBean(R2dbcTransactionManager.class).getConnectionFactory()).isSameAs(businessFactory);
            assertThat(context.getBean("fireflyEdaPostgresConsumerQueryConnectionFactory")).isNotSameAs(businessFactory);
            assertThat(context.getBean("fireflyEdaPostgresConsumerConnectionFactory")).isNotSameAs(businessFactory);
            assertThat(context.getBean(PostgresDeliveryRepository.class)).isInstanceOf(SpringDataPostgresConsumerGroupRepository.class);
        });
    }
    @ParameterizedTest @ValueSource(strings={"SCOPED","INFRASTRUCTURE_SCOPED"})
    void selectedGroupRejectsScopedQueryFactoriesWithoutResolvingTheTarget(String kind){
        var lookups=new AtomicInteger();var raw=mock(ConnectionFactory.class);when(raw.getMetadata()).thenReturn(()->"PostgreSQL");
        try(var scope=new GenericApplicationContext()){
            scope.getBeanFactory().registerScope("group-query",new SimpleThreadScope(){@Override public Object get(String name,ObjectFactory<?> factory){lookups.incrementAndGet();return super.get(name,factory);}});
            scope.registerBean("scopedTarget.groupQuery",ConnectionFactory.class,()->raw,bean->bean.setScope("group-query"));scope.refresh();
            var proxy=new ScopedProxyFactoryBean();proxy.setTargetBeanName("scopedTarget.groupQuery");proxy.setProxyTargetClass(false);proxy.setBeanFactory(scope.getBeanFactory());
            ConnectionFactory scoped=(ConnectionFactory)proxy.getObject();ConnectionFactory provided=kind.equals("SCOPED")?scoped:new PostgresConsumerAutoConfigurationTest.ExistingInfrastructureFactory(scoped);
            runner().withPropertyValues("firefly.eda.consumer.postgres.default.group-id=orders-service")
                    .withBean("fireflyEdaPostgresConsumerQueryConnectionFactory",ConnectionFactory.class,()->provided,bean->{((AbstractBeanDefinition)bean).setDefaultCandidate(false);((AbstractBeanDefinition)bean).setDestroyMethodName("");})
                    .run(context->assertThat(context).hasFailed());
            assertThat(lookups).hasValue(0);verify(raw,never()).create();
        }
    }
}
