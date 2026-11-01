/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.fireflyframework.eda.consumer.ConsumerHealth;
import org.fireflyframework.eda.consumer.postgres.*;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.eda.listener.EventDeliveryOutcome;
import org.fireflyframework.eda.listener.EventListenerProcessorImpl;
import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.properties.EdaProperties.Consumer.PostgresConfig;
import org.fireflyframework.eda.publisher.postgres.PostgresChannelMapper;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/** Owns bounded dispatch; the repository alone owns durable lease authority. */
public final class PostgresDeliveryRuntimeImpl {
    private final ObjectProvider<ConnectionFactory> provider;
    private final EventListenerProcessorImpl processor;
    private final EdaProperties properties;
    private final ObjectMapper mapper;
    private final PostgresDeliveryRepository repository;
    private final PostgresEventDecoder decoder;
    private final Scheduler suppliedScheduler;
    private volatile Run current;
    private final AtomicLong consumed=new AtomicLong(),processed=new AtomicLong(),failures=new AtomicLong();

    public PostgresDeliveryRuntimeImpl(ObjectProvider<ConnectionFactory> provider, EventListenerProcessorImpl processor,
            EdaProperties properties, ObjectMapper mapper, PostgresDeliveryRepository repository,
            PostgresEventDecoder decoder, Scheduler scheduler) {
        this.provider=provider;this.processor=Objects.requireNonNull(processor);this.properties=properties;
        this.mapper=mapper;this.repository=repository;this.decoder=decoder;this.suppliedScheduler=scheduler;
        processor.registerListenerChangeCallback(() -> {Run run=current;if(run!=null)run.wake();});
    }
    public Mono<Void> start() {
        return Mono.defer(() -> {
            if(repository==null||decoder==null)return Mono.error(new IllegalStateException("PostgreSQL delivery repository and registered decoder are required"));
            synchronized(this) {
                if(current!=null)return current.running?current.started:current.stopping?Mono.error(new IllegalStateException("Previous consumer run has not stopped")):current.started;
                Settings settings=Settings.from(properties);
                Run run=new Run(settings);current=run;
                return run.started;
            }
        });
    }
    public Mono<Void> stop(){return Mono.defer(()->{Run run=current;return run==null?Mono.empty():run.stop().doOnSuccess(v->{synchronized(this){if(current==run)current=null;}});});}
    public Flux<EventEnvelope> consume(String... destinations){
        String[] snapshot=destinations==null?new String[0]:destinations.clone();
        return start().thenMany(Flux.defer(()->{Run run=current;return run==null?Flux.error(new IllegalStateException("Consumer is stopped")):run.stream(snapshot);}));
    }
    public boolean isRunning(){Run run=current;return run!=null&&run.running;}
    public boolean isAvailable(){return repository!=null&&decoder!=null&&properties!=null&&properties.isEnabled()&&properties.getConsumer().isEnabled()&&properties.getConsumer().getPostgres().get("default")!=null&&properties.getConsumer().getPostgres().get("default").isEnabled();}
    public Mono<ConsumerHealth> health(){return Mono.fromSupplier(()->{
        Run run=current;boolean queryFailed=run!=null&&run.queryFailed;
        return ConsumerHealth.builder().consumerType("POSTGRES").connectionId("default").available(isAvailable()).running(isRunning())
                .status(!isRunning()?"DOWN":queryFailed?"DEGRADED":"UP").errorMessage(queryFailed?"DELIVERY_QUERY_FAILED":null)
                .messagesConsumed(consumed.get()).messagesProcessed(processed.get()).messagesFailures(failures.get())
                .details(Map.of("protocolVerified",run!=null&&run.verified,"listenConnected",run!=null&&run.listen!=null&&run.listen.readySucceeded,"activation","EXPLICIT")).build();
    });}
    public void shutdown(){Run run=current;stop().block(run==null?Duration.ofSeconds(5):run.settings.shutdown);}

    private final class Run {
        final Settings settings;
        final Scheduler scheduler;
        final boolean ownsScheduler;
        final Semaphore permits;
        final AtomicBoolean requested=new AtomicBoolean(),scheduled=new AtomicBoolean(),draining=new AtomicBoolean();
        final Sinks.Empty<Void> stopped=Sinks.empty();
        final Sinks.Many<EventEnvelope> observations=Sinks.many().multicast().directBestEffort();
        final AtomicReference<Worker> worker=new AtomicReference<>();
        final Set<ManualDeliveryImpl> manualDeliveries=ConcurrentHashMap.newKeySet();
        final Mono<Void> started;
        volatile boolean running,verified,stopping,backingOff,queryFailed;
        volatile Disposable polling,notifications,drain;
        volatile ListenSession listen;
        volatile Sinks.Empty<Void> drainFinished=Sinks.empty();
        Mono<Void> stoppingResult;
        final IllegalStateException startupStopped=new IllegalStateException("Consumer stopped during startup");
        Run(Settings settings){
            this.settings=settings;scheduler=suppliedScheduler==null?Schedulers.newSingle("firefly-pg-consumer"):suppliedScheduler;ownsScheduler=suppliedScheduler==null;permits=new Semaphore(settings.concurrency);
            started=Mono.defer(()->{if(stopping)return Mono.error(startupStopped);var selected=channels();if(selected.isEmpty())return Mono.error(new IllegalArgumentException("PostgreSQL delivery channels are required"));return repository.verifyProtocol(new ClaimRequest(selected,settings.batch,settings.maxAttempts,settings.lease));})
                    .timeout(settings.query,scheduler).then(Mono.<Void>fromRunnable(this::activate)).doOnError(e->{if(!stopping){if(ownsScheduler)scheduler.dispose();synchronized(PostgresDeliveryRuntimeImpl.this){if(current==this)current=null;}}}).cache();
        }
        synchronized void activate(){
            if(stopping)throw startupStopped;
            verified=true;running=true;
            polling=Flux.interval(settings.poll,settings.poll,scheduler).subscribe(v->wake());
            notifications=Mono.defer(()->{
                if(!running)return Mono.empty();
                ConnectionFactory factory=provider.getIfAvailable();
                if(factory==null)return Mono.error(new IllegalStateException("PostgreSQL LISTEN factory is unavailable"));
                return Mono.usingWhen(Mono.from(factory.create()).map(ListenSession::new),session->{
                    listen=session;
                    if(!(session.connection instanceof PostgresqlConnection pg))return Mono.error(new IllegalStateException("LISTEN requires a PostgreSQL connection"));
                    return session.ready.then(Mono.firstWithSignal(
                            pg.getNotifications().doOnNext(n->wake()).then(Mono.error(new IllegalStateException("PostgreSQL notification stream ended"))),
                            session.failed.asMono().then(Mono.error(new IllegalStateException("PostgreSQL LISTEN refresh failed")))));
                },ListenSession::close,(session,error)->session.close(),ListenSession::close);
            }).retryWhen(Retry.backoff(Long.MAX_VALUE,settings.backoff).maxBackoff(settings.maxBackoff).jitter(0).scheduler(scheduler)).subscribe(v->{},e->{ });
            wake();
        }
        Set<String> channels(){
            var values=new LinkedHashSet<String>(settings.configuredChannels);
            processor.getTopicsForConsumerType("POSTGRES").stream().filter(v->!v.equals("*")).map(PostgresChannelMapper::toChannel).forEach(values::add);
            if(values.size()>256)throw new IllegalArgumentException("PostgreSQL delivery channels exceed supported bounds");
            return Set.copyOf(values);
        }
        void wake(){
            requested.set(true);
            if(running&&!backingOff&&scheduled.compareAndSet(false,true))scheduler.schedule(()->{scheduled.set(false);drain();});
        }
        void drain(){
            final Sinks.Empty<Void> finished;
            synchronized(this){
                if(!running||backingOff||!draining.compareAndSet(false,true))return;
                finished=Sinks.empty();drainFinished=finished;
            }
            requested.set(false);
            Worker owner=worker.get();int available=permits.availablePermits();
            if(settings.mode==PostgresConfig.DeliveryMode.MANUAL)available=owner==null?0:(int)Math.min(available,owner.sink.requestedFromDownstream());
            int reservation=Math.min(settings.batch,available);
            Set<String> selected;
            try {selected=channels();}
            catch(IllegalArgumentException error){
                queryFailed=true;backingOff=true;finishIdleDrain(finished);
                if(running)scheduler.schedule(()->{backingOff=false;wake();},settings.backoff.toMillis(),TimeUnit.MILLISECONDS);
                return;
            }
            if(owner!=null&&!owner.channels.isEmpty()){var intersection=new LinkedHashSet<>(selected);intersection.retainAll(owner.channels);selected=Set.copyOf(intersection);}
            if(reservation==0||selected.isEmpty()){finishIdleDrain(finished);return;}
            if(!permits.tryAcquire(reservation)){finishIdleDrain(finished);return;}
            var request=new ClaimRequest(selected,reservation,settings.maxAttempts,settings.lease);
            var batchReceived=new AtomicBoolean();var failed=new AtomicBoolean();
            ListenSession connection=listen;
            Mono<Void> refresh=connection==null?Mono.empty():connection.refresh().onErrorResume(e->{connection.failed.tryEmitEmpty();return Mono.empty();});
            drain=refresh.then(Mono.defer(()->running?repository.reapExhausted(request).timeout(settings.query,scheduler)
                    .then(repository.claim(request).timeout(settings.query,scheduler).switchIfEmpty(Mono.error(new IllegalStateException("Delivery claim returned no batch")))):Mono.empty())).flatMapMany(batch->{
                queryFailed=false;
                if(batch.claims().size()>reservation)return Flux.error(new IllegalStateException("Repository exceeded reserved delivery capacity"));
                batchReceived.set(true);permits.release(reservation-batch.claims().size());
                if(batch.claims().size()==reservation)requested.set(true);
                return Flux.fromIterable(batch.claims()).flatMap(claim->withPermit(process(claim,owner).onErrorResume(e->Mono.empty())),settings.concurrency,1);
            }).then().doOnError(e->{failed.set(true);queryFailed=true;backingOff=true;}).doFinally(signal->{
                if(!batchReceived.get())permits.release(reservation);
                finished.tryEmitEmpty();draining.set(false);
                if(running&&failed.get())scheduler.schedule(()->{backingOff=false;wake();},settings.backoff.toMillis(),TimeUnit.MILLISECONDS);
                else if(running&&requested.get())wake();
            }).subscribe(v->{},e->{ });
        }
        void finishIdleDrain(Sinks.Empty<Void> finished){draining.set(false);finished.tryEmitEmpty();}
        Mono<Void> withPermit(Mono<Void> work){
            var released=new AtomicBoolean();
            Runnable release=()->{if(released.compareAndSet(false,true))permits.release();};
            return work.doOnSuccess(v->release.run()).doOnError(e->release.run()).doFinally(signal->release.run());
        }
        Mono<Void> transition(Mono<TransitionResult> result){return PostgresDeliveryRuntimeImpl.transition(result).timeout(settings.query,scheduler);}
        Mono<Void> process(DeliveryClaim claim,Worker owner){
            var failed=new AtomicBoolean();Runnable markFailure=()->{if(failed.compareAndSet(false,true))failures.incrementAndGet();};
            return Mono.<Void>defer(()->{
                consumed.incrementAndGet();
                if(!running){markFailure.run();return transition(repository.reject(claim,FailureKind.LISTENER_FAILURE,settings.retry));}
                final EventEnvelope event;
                try {event=new PostgresEnvelopeMapperImpl(mapper).map(claim,Objects.requireNonNull(decoder.decode(claim.row().payload(),claim.row().eventClass(),claim.row().eventType())),readOnly());}
                catch(Exception error){markFailure.run();return transition(repository.reject(claim,FailureKind.DESERIALIZATION_FAILURE,settings.retry));}
                if(settings.mode==PostgresConfig.DeliveryMode.MANUAL){
                    if(owner==null||owner!=worker.get()||owner.sink.isCancelled()){markFailure.run();return transition(repository.reject(claim,FailureKind.LISTENER_FAILURE,settings.retry));}
                    ManualDeliveryImpl delivery=new ManualDeliveryImpl(claim,owner,event,markFailure);manualDeliveries.add(delivery);
                    if(!owner.emit(delivery.envelope))return delivery.abort().doFinally(s->manualDeliveries.remove(delivery));
                    return Mono.firstWithSignal(delivery.finished.asMono(),owner.cancelled.asMono().then(Mono.error(new IllegalStateException("Manual processing cancelled"))),stopped.asMono().then(Mono.error(new IllegalStateException("Consumer stopped"))))
                            .timeout(settings.handler,scheduler).onErrorResume(e->delivery.abort()).doFinally(s->manualDeliveries.remove(delivery));
                }
                observations.tryEmitNext(event);
                return Mono.firstWithSignal(processor.processDelivery(event),stopped.asMono().then(Mono.error(new IllegalStateException("Consumer stopped"))))
                        .timeout(settings.handler,scheduler).onErrorReturn(new EventDeliveryOutcome(EventDeliveryOutcome.Disposition.RETRY,0,0,null))
                        .flatMap(outcome->{
                            if(outcome.disposition()!=EventDeliveryOutcome.Disposition.HANDLED)markFailure.run();
                            return transition(switch(outcome.disposition()){
                                case HANDLED->repository.acknowledge(claim);
                                case UNHANDLED->repository.reject(claim,FailureKind.UNHANDLED,settings.retry);
                                case RETRY->repository.reject(claim,FailureKind.LISTENER_FAILURE,settings.retry);
                                case DEAD_LETTER->repository.reject(claim,FailureKind.EXPLICIT_DEAD_LETTER,settings.retry);
                            }).doOnSuccess(v->{if(outcome.disposition()==EventDeliveryOutcome.Disposition.HANDLED)processed.incrementAndGet();});
                        });
            }).doOnError(e->markFailure.run());
        }
        Flux<EventEnvelope> stream(String[] destinations){
            Set<String> filters=Set.copyOf(Arrays.asList(destinations));
            if(filters.stream().anyMatch(v->v==null||v.isBlank()))return Flux.error(new IllegalArgumentException("Delivery destinations must be nonblank"));
            if(settings.mode==PostgresConfig.DeliveryMode.ANNOTATED)return observations.asFlux().filter(e->filters.isEmpty()||filters.contains(e.destination()));
            return Flux.create(sink->{
                Worker next=new Worker(sink,filters);
                if(!worker.compareAndSet(null,next)){sink.error(new IllegalStateException("A manual processing subscriber is already registered"));return;}
                sink.onRequest(n->wake());sink.onDispose(()->{next.cancelled.tryEmitEmpty();worker.compareAndSet(next,null);wake();});wake();
            },FluxSink.OverflowStrategy.ERROR);
        }
        synchronized Mono<Void> stop(){
            if(stoppingResult!=null)return stoppingResult;
            stoppingResult=Mono.defer(()->{
                synchronized(this){stopping=true;running=false;}
                // Cancellation can clear listen synchronously; retain its cached close outcome first.
                ListenSession connection=listen;
                Mono<Void> listenClose=connection==null?Mono.empty():connection.close();
                if(polling!=null)polling.dispose();if(notifications!=null)notifications.dispose();
                stopped.tryEmitEmpty();observations.tryEmitComplete();Worker w=worker.getAndSet(null);if(w!=null){w.cancelled.tryEmitEmpty();w.sink.complete();}
                Mono<Void> wait=draining.get()?drainFinished.asMono():Mono.empty();
                Mono<Void> startup=verified?Mono.empty():started.onErrorResume(error->error==startupStopped?Mono.empty():Mono.error(error));
                return Mono.whenDelayError(startup,wait,listenClose).timeout(settings.shutdown,scheduler)
                        .doOnError(e->{if(drain!=null)drain.dispose();}).doFinally(signal->{if(ownsScheduler)scheduler.dispose();});
            }).cache();return stoppingResult;
        }
        final class ListenSession {
            final Connection connection;
            final Mono<Void> closing,ready;
            volatile boolean readySucceeded;
            final Sinks.Empty<Void> failed=Sinks.empty();
            final Set<String> subscribed=new LinkedHashSet<>();
            ListenSession(Connection connection){
                this.connection=connection;
                closing=Mono.defer(()->Mono.from(connection.close())).timeout(settings.shutdown,scheduler).doFinally(s->{if(listen==this)listen=null;}).cache();
                ready=update().timeout(settings.connect,scheduler).doOnSuccess(v->readySucceeded=true).cache();
            }
            Mono<Void> refresh(){return ready.then(update());}
            Mono<Void> update(){return Mono.defer(()->{
                if(!(connection instanceof PostgresqlConnection pg))return Mono.error(new IllegalStateException("LISTEN requires a PostgreSQL connection"));
                Set<String> desired=channels();var remove=new LinkedHashSet<>(subscribed);remove.removeAll(desired);var add=new LinkedHashSet<>(desired);add.removeAll(subscribed);
                return Flux.fromIterable(remove).concatMap(channel->command(pg,"UNLISTEN",channel).doOnSuccess(v->subscribed.remove(channel)))
                        .thenMany(Flux.fromIterable(add).concatMap(channel->command(pg,"LISTEN",channel).doOnSuccess(v->subscribed.add(channel)))).then().timeout(settings.query,scheduler);
            });}
            Mono<Void> command(PostgresqlConnection pg,String verb,String channel){return Mono.from(pg.createStatement(verb+" "+quote(channel)).execute()).flatMapMany(r->r.getRowsUpdated()).then();}
            Mono<Void> close(){return closing;}
        }
        final class Worker {
            final FluxSink<EventEnvelope> sink;final Set<String> channels;final Sinks.Empty<Void> cancelled=Sinks.empty();
            Worker(FluxSink<EventEnvelope> sink,Set<String> destinations){this.sink=sink;channels=destinations.stream().map(PostgresChannelMapper::toChannel).collect(java.util.stream.Collectors.toUnmodifiableSet());}
            boolean emit(EventEnvelope envelope){if(sink.isCancelled()||sink.requestedFromDownstream()==0)return false;sink.next(envelope);return !sink.isCancelled();}
        }
        final class ManualDeliveryImpl implements EventEnvelope.AckCallback {
            final DeliveryClaim claim;final Worker owner;final EventEnvelope envelope;final Sinks.Empty<Void> finished=Sinks.empty();
            String action;Mono<Void> operation;final Runnable markFailure;
            ManualDeliveryImpl(DeliveryClaim claim,Worker owner,EventEnvelope e,Runnable markFailure){this.claim=claim;this.owner=owner;this.markFailure=markFailure;envelope=new EventEnvelope(e.destination(),e.eventType(),e.payload(),e.transactionId(),e.headers(),e.metadata(),e.timestamp(),e.publisherType(),e.consumerType(),e.connectionId(),this);}
            public Mono<Void> acknowledge(){return Mono.defer(()->call("ACK"));}
            public Mono<Void> reject(Throwable error){return Mono.defer(()->call("NACK"));}
            synchronized Mono<Void> call(String requestedAction){
                if(operation!=null)return action.equals(requestedAction)?operation:Mono.error(new IllegalStateException("Conflicting delivery callback"));
                if(!running||owner.sink.isCancelled())return Mono.error(new IllegalStateException("Delivery owner is no longer active"));
                action=requestedAction;if(action.equals("NACK"))markFailure.run();
                Mono<TransitionResult> change=action.equals("ACK")?repository.acknowledge(claim):repository.reject(claim,FailureKind.LISTENER_FAILURE,settings.retry);
                operation=Mono.firstWithSignal(transition(change),stopped.asMono().then(Mono.error(new IllegalStateException("Consumer stopped"))),owner.cancelled.asMono().then(Mono.error(new IllegalStateException("Manual processing cancelled"))))
                        .doOnSuccess(v->{if(action.equals("ACK"))processed.incrementAndGet();finished.tryEmitEmpty();}).doOnError(e->{markFailure.run();finished.tryEmitError(e);}).cache();return operation;
            }
            synchronized Mono<Void> abort(){
                if(operation!=null)return Mono.empty();action="ABORT";markFailure.run();
                operation=transition(repository.reject(claim,FailureKind.LISTENER_FAILURE,settings.retry)).cache();return operation;
            }
        }
    }
    private static Mono<Void> transition(Mono<TransitionResult> change){return change.switchIfEmpty(Mono.error(new IllegalStateException("Delivery transition returned no result"))).flatMap(result->result==TransitionResult.STALE?Mono.error(new IllegalStateException("Delivery ownership is stale")):Mono.empty());}
    private static EventEnvelope.AckCallback readOnly(){return new EventEnvelope.AckCallback(){public Mono<Void> acknowledge(){return Mono.error(new IllegalStateException("Observation deliveries cannot change transport state"));}public Mono<Void> reject(Throwable e){return acknowledge();}};}
    private static String quote(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    private record Settings(int concurrency,int batch,int maxAttempts,PostgresConfig.DeliveryMode mode,Set<String> configuredChannels,
            Duration poll,Duration lease,Duration retry,Duration handler,Duration backoff,Duration maxBackoff,Duration shutdown,Duration connect,Duration query){
        static Settings from(EdaProperties props){
            if(props==null||!props.isEnabled()||!props.getConsumer().isEnabled())throw new IllegalStateException("PostgreSQL consumer is disabled");
            PostgresConfig c=props.getConsumer().getPostgres().get("default");if(c==null||!c.isEnabled())throw new IllegalStateException("PostgreSQL consumer is disabled");
            if(c.getGroupId()!=null){new ConsumerGroupId(c.getGroupId());new ConsumerGroupTables(c.getSchema(),c.getOutboxTable(),c.getGroupRegistryTable(),c.getGroupDeliveriesTable(),c.getGroupRedrivesTable());}
            int n=props.getConsumer().getConcurrency();if(n<1||n>100||c.getBatchSize()<1||c.getBatchSize()>1024||c.getMaxAttempts()<1||c.getMaxPoolSize()<1||c.getMaxPoolSize()>100)throw new IllegalArgumentException("Invalid PostgreSQL delivery admission bounds");
            Duration poll=positive(c.getPollingInterval()),lease=positive(c.getLeaseDuration()),handler=positive(c.getHandlerTimeout()),backoff=positive(c.getRecoveryBackoff()),maxBackoff=positive(c.getMaxRecoveryBackoff()),shutdown=positive(c.getShutdownTimeout());
            DeliveryBounds.milliseconds(lease,false);DeliveryBounds.milliseconds(c.getRetryDelay(),true);
            DeliveryBounds.milliseconds(poll,false);PostgresConsumerQueryPoolImpl.requireTimeout(c.getConnectTimeout());PostgresConsumerQueryPoolImpl.requireTimeout(c.getAcquireTimeout());PostgresConsumerQueryPoolImpl.requireTimeout(shutdown);
            Duration query=c.getQueryTimeout();if(query==null||query.getNano()!=0||query.getSeconds()<1||query.getSeconds()>60)throw new IllegalArgumentException("PostgreSQL consumer query timeout requires one to sixty whole seconds");
            if(handler.compareTo(lease)>0||backoff.compareTo(maxBackoff)>0||maxBackoff.compareTo(Duration.ofSeconds(60))>0||shutdown.compareTo(Duration.ofSeconds(60))>0)throw new IllegalArgumentException("Invalid PostgreSQL delivery time bounds");
            var channels=new LinkedHashSet<String>();if(c.getChannels()!=null)for(String value:c.getChannels().split(","))if(!value.isBlank()&&!value.trim().equals("*"))channels.add(PostgresChannelMapper.toChannel(value.trim()));
            return new Settings(n,c.getBatchSize(),c.getMaxAttempts(),Objects.requireNonNull(c.getDeliveryMode()),Set.copyOf(channels),poll,lease,c.getRetryDelay(),handler,backoff,maxBackoff,shutdown,positive(c.getConnectTimeout()),positive(c.getQueryTimeout()));
        }
        private static Duration positive(Duration d){if(d==null||d.isZero()||d.isNegative()||d.getNano()%1_000_000!=0)throw new IllegalArgumentException("PostgreSQL delivery duration must be positive whole milliseconds");return d;}
    }
}
