package org.fireflyframework.ecm.adapter.docusign;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;

/** A per-thread Jakarta service-provider scope; every exchange terminates before socket IO. */
public final class ScopedSdkConnector {
    private ScopedSdkConnector() { }
    public record Wire(String method, URI uri, byte[] bytes, String authorization,
                       Object connectTimeout, Object readTimeout) { }
    public record Reply(int status, String body) { }
    public static final class Scope extends ClassLoader implements AutoCloseable {
        private final ClassLoader previous;
        private final Thread owner;
        private final URL service;
        final List<JerseyClient> clients = new CopyOnWriteArrayList<>();
        final List<Wire> exchanges = new CopyOnWriteArrayList<>();
        final AtomicInteger responsesClosed = new AtomicInteger();
        volatile Function<Wire, Reply> responder = this::defaultReply;
        volatile boolean failConnectorClose;
        private volatile boolean closed;
        public Scope(Path serviceFile) throws IOException {
            super(Thread.currentThread().getContextClassLoader());
            if (System.getProperty(ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY) != null)
                throw new IllegalStateException("Existing client-builder override is not adopted");
            previous = getParent(); owner = Thread.currentThread(); service = serviceFile.toUri().toURL();
            owner.setContextClassLoader(this);
        }
        @Override public URL getResource(String name) {
            return name.equals("META-INF/services/" + ClientBuilder.class.getName()) ? service : super.getResource(name);
        }
        @Override public Enumeration<URL> getResources(String name) throws IOException {
            return name.equals("META-INF/services/" + ClientBuilder.class.getName()) ? Collections.enumeration(List.of(service)) : super.getResources(name);
        }
        private Connector connector() {
            return new Connector() {
                @Override public ClientResponse apply(ClientRequest request) {
                    if (closed || !clients.contains(request.getClient()))
                        throw new IllegalStateException("Only the exact active owned SDK scope may exchange");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try {
                        request.setStreamProvider(length -> bytes);
                        if (request.hasEntity()) request.writeEntity();
                    } catch (IOException error) { throw new IllegalStateException("Owned serialization failed", error); }
                    if (bytes.size() > 131072) throw new IllegalStateException("Owned bounded wire required");
                    Wire wire = new Wire(request.getMethod(), request.getUri(), bytes.toByteArray(),
                            request.getHeaderString("Authorization"),
                            request.getConfiguration().getProperty(ClientProperties.CONNECT_TIMEOUT),
                            request.getConfiguration().getProperty(ClientProperties.READ_TIMEOUT));
                    exchanges.add(wire);
                    Reply reply = responder.apply(wire);
                    ClientResponse response = new ClientResponse(Response.Status.fromStatusCode(reply.status()), request);
                    response.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
                    response.setEntityStream(new ByteArrayInputStream(reply.body().getBytes(StandardCharsets.UTF_8)) {
                        private final AtomicBoolean released = new AtomicBoolean();
                        @Override public void close() throws IOException {
                            if (released.compareAndSet(false, true)) responsesClosed.incrementAndGet();
                            super.close();
                        }
                    });
                    return response;
                }
                @Override public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                    try { callback.response(apply(request)); return CompletableFuture.completedFuture(null); }
                    catch (RuntimeException error) { callback.failure(error); return CompletableFuture.failedFuture(error); }
                }
                @Override public String getName() { return "owned-scope-no-network"; }
                @Override public void close() {
                    if (failConnectorClose) throw new IllegalStateException("Controlled owned connector close failure");
                }
            };
        }
        @Override public void close() {
            if (Thread.currentThread() != owner || owner.getContextClassLoader() != this)
                throw new IllegalStateException("Exact scope owner and classloader restoration required");
            try { for (JerseyClient client : clients) if (!client.isClosed()) client.close(); }
            finally { closed = true; owner.setContextClassLoader(previous); }
        }
        Reply defaultReply(Wire wire) {
            String path = wire.uri().getPath();
            if (path.endsWith("/oauth/token"))
                return new Reply(200, "{\"access_token\":\"owned-test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
            if (path.endsWith("/oauth/userinfo"))
                return new Reply(200, "{\"sub\":\"00000000-0000-4000-8000-000000000002\",\"accounts\":[{\"account_id\":\"00000000-0000-4000-8000-000000000003\",\"is_default\":true,\"base_uri\":\"https://owned.invalid\"}]}");
            if (wire.method().equals("GET") && path.endsWith("/envelopes/controlled-envelope"))
                return new Reply(200, "{\"envelopeId\":\"controlled-envelope\",\"status\":\"created\"}");
            throw new IllegalStateException("Unreviewed transport exchange refused before IO");
        }
    }
    /** Invoked only through the standard Jakarta ClientBuilder ServiceLoader contract. */
    public static final class Builder extends JerseyClientBuilder {
        public Builder() { }
        @Override public JerseyClient build() {
            if (!(Thread.currentThread().getContextClassLoader() instanceof Scope scope) || scope.closed)
                throw new IllegalStateException("Exact owned no-network scope required");
            getConfiguration().connectorProvider((client, configuration) -> scope.connector());
            JerseyClient client = super.build(); scope.clients.add(client); return client;
        }
    }
}
