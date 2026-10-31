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

package org.fireflyframework.eda.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.HashMap;
import org.fireflyframework.eda.consumer.postgres.*;
import org.fireflyframework.eda.consumer.postgres.impl.PostgresConsumerQueryPool;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresDeliveryRepository;
import org.fireflyframework.eda.consumer.postgres.impl.SpringDataPostgresConsumerGroupRepository;
import org.fireflyframework.eda.consumer.postgres.model.ConsumerGroupId;
import org.fireflyframework.eda.consumer.postgres.model.ConsumerGroupTables;
import org.fireflyframework.eda.listener.EventListenerProcessor;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

/** Named technical query pool and dedicated raw LISTEN connections, never business defaults. */
@AutoConfiguration(after=FireflyEdaAutoConfiguration.class)
@ConditionalOnClass({ConnectionFactory.class,PostgresqlConnectionFactory.class,ConnectionPool.class})
@ConditionalOnProperty(prefix="firefly.eda",name="enabled",havingValue="true",matchIfMissing=true)
@EnableConfigurationProperties(EdaProperties.class)
@Import(FireflyEdaPostgresConsumerAutoConfiguration.QueryPoolConfiguration.class)
public class FireflyEdaPostgresConsumerAutoConfiguration {
    private static final String SELECTED="${firefly.eda.consumer.enabled:false} && ${firefly.eda.consumer.postgres.default.enabled:false}";
    public FireflyEdaPostgresConsumerAutoConfiguration(EdaProperties props){ }

    @Bean(name="fireflyEdaPostgresConsumerConnectionFactory",defaultCandidate=false,destroyMethod="")
    @Lazy
    @ConditionalOnMissingBean(name="fireflyEdaPostgresConsumerConnectionFactory")
    @ConditionalOnExpression(SELECTED)
    public PostgresqlConnectionFactory fireflyEdaPostgresConsumerConnectionFactory(EdaProperties props){
        var config=props.getConsumer().getPostgres().get("default");
        if(config==null||config.getHost()==null||config.getHost().isBlank()||config.getDatabase()==null||config.getDatabase().isBlank()||config.getUsername()==null||config.getUsername().isBlank()||config.getPort()<1||config.getPort()>65535)throw new IllegalArgumentException("PostgreSQL consumer connection configuration is incomplete");
        PostgresConsumerQueryPool.requireTimeout(config.getConnectTimeout());
        var builder=PostgresqlConnectionConfiguration.builder().host(config.getHost()).port(config.getPort()).database(config.getDatabase()).username(config.getUsername()).connectTimeout(config.getConnectTimeout());
        if(config.getPassword()!=null)builder.password(config.getPassword());if(config.getSchema()!=null)builder.schema(config.getSchema());
        if(config.getProperties()!=null&&!config.getProperties().isEmpty()){
            var options=new HashMap<String,String>();config.getProperties().forEach((name,value)->{if(value!=null)options.put(name,value.toString());});builder.options(options);
        }
        return new PostgresqlConnectionFactory(builder.build());
    }

    @Bean(name="fireflyEdaPostgresDeliveryRepository")
    @Lazy
    @ConditionalOnMissingBean(name="fireflyEdaPostgresDeliveryRepository")
    @ConditionalOnExpression(SELECTED)
    public PostgresDeliveryRepository fireflyEdaPostgresDeliveryRepository(@Qualifier("fireflyEdaPostgresConsumerQueryConnectionFactory")ConnectionFactory factory,EdaProperties props){
        var config=props.getConsumer().getPostgres().get("default");Duration timeout=config.getQueryTimeout();
        if(timeout==null||timeout.getNano()!=0||timeout.getSeconds()<1||timeout.getSeconds()>60)throw new IllegalArgumentException("PostgreSQL consumer query timeout requires one to sixty whole seconds");
        if(config.getGroupId()!=null)return new SpringDataPostgresConsumerGroupRepository(factory,
                new ConsumerGroupTables(config.getSchema(),config.getOutboxTable(),config.getGroupRegistryTable(),config.getGroupDeliveriesTable(),config.getGroupRedrivesTable()),
                new ConsumerGroupId(config.getGroupId()),timeout);
        return new SpringDataPostgresDeliveryRepository(factory,config.getSchema(),config.getOutboxTable(),timeout);
    }

    @Bean(name="postgresEventConsumer")
    @ConditionalOnMissingBean(PostgresEventConsumer.class)
    @ConditionalOnExpression(SELECTED)
    public PostgresEventConsumer postgresEventConsumer(PostgresEventDecoder decoder,
            @Qualifier("fireflyEdaPostgresDeliveryRepository")PostgresDeliveryRepository repository,
            @Qualifier("fireflyEdaPostgresConsumerConnectionFactory")ObjectProvider<ConnectionFactory> listenFactory,
            EventListenerProcessor processor,MessageSerializer serializer,EdaProperties props,ObjectMapper mapper){
        var config=props.getConsumer().getPostgres().get("default");
        if(config.getGroupId()!=null){
            new ConsumerGroupId(config.getGroupId());
            new ConsumerGroupTables(config.getSchema(),config.getOutboxTable(),config.getGroupRegistryTable(),config.getGroupDeliveriesTable(),config.getGroupRedrivesTable());
        }
        return new PostgresEventConsumer(listenFactory,processor,serializer,props,mapper,repository,decoder);
    }

    @Configuration(proxyBeanMethods=false)
    @ConditionalOnClass({ConnectionFactory.class,PostgresqlConnectionFactory.class,ConnectionPool.class})
    @ConditionalOnProperty(prefix="firefly.eda",name="enabled",havingValue="true",matchIfMissing=true)
    @ConditionalOnExpression(SELECTED)
    @ConditionalOnMissingBean(name="fireflyEdaPostgresConsumerQueryConnectionFactory")
    @EnableConfigurationProperties(EdaProperties.class)
    static class QueryPoolConfiguration {
        @Bean(name="fireflyEdaPostgresConsumerQueryPoolOwner",destroyMethod="close")
        @Lazy
        PostgresConsumerQueryPool fireflyEdaPostgresConsumerQueryPoolOwner(@Qualifier("fireflyEdaPostgresConsumerConnectionFactory")ConnectionFactory raw,EdaProperties props){
            return new PostgresConsumerQueryPool(raw,props.getConsumer().getPostgres().get("default"));
        }
        @Bean(name="fireflyEdaPostgresConsumerQueryConnectionFactory",defaultCandidate=false,destroyMethod="")
        @Lazy
        ConnectionFactory fireflyEdaPostgresConsumerQueryConnectionFactory(@Qualifier("fireflyEdaPostgresConsumerQueryPoolOwner")PostgresConsumerQueryPool owner){return owner.connectionFactory();}
    }
}
