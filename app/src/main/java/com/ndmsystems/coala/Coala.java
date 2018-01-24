package com.ndmsystems.coala;

import android.os.Build;

import com.ndmsystems.coala.di.CoalaComponent;
import com.ndmsystems.coala.di.CoalaModule;
import com.ndmsystems.coala.di.DaggerCoalaComponent;
import com.ndmsystems.coala.exceptions.CoAPException;
import com.ndmsystems.coala.helpers.TokenGenerator;
import com.ndmsystems.coala.layers.response.ResponseData;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.response.ResponseHandler;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.message.CoAPRequestMethod;
import com.ndmsystems.coala.observer.RegistryOfObservingResources;

import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import io.reactivex.Observable;

public class Coala extends CoAPTransport {

    private static CoalaComponent dependencyGraph;

    @Inject
    protected ConnectionProvider connectionProvider;

    @Inject
    protected CoAPMessagePool messagePool;

    @Inject
    protected AckHandlersPool ackHandlersPool;

    @Inject
    protected CoAPSender sender;

    @Inject
    protected CoAPReceiver receiver;

    @Inject
    protected ResourceRegistry resourceRegistry;

    @Inject
    protected RegistryOfObservingResources registryOfObservingResources;

    @Inject
    protected LocalPeerDiscoverer localPeerDiscoverer;

    protected ICoalaStorage storage;

    /**
     * Create instance of coala with default port 5683
     */
    public Coala() {
        this(5683);
    }

    /**
     * Create instance of coala with given port
     */
    public Coala(Integer port) {
        dependencyGraph = DaggerCoalaComponent.builder().coalaModule(new CoalaModule(this, port)).build();
        dependencyGraph.inject(this);
        addWellKnownCore();
        addTestResource();//TODO: Убрать после того как станет ненужен
    }

    public static CoalaComponent getDependencyGraph() {
        return dependencyGraph;
    }

    private void addWellKnownCore() {
        addResource(".well-known/core", CoAPRequestMethod.GET, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                StringBuffer buffer = new StringBuffer();
                for (CoAPResourcesGroupForPath resourcesGroupForPath : resourceRegistry.getResources().values()) {
                    if (resourcesGroupForPath.getPath().equals(".well-known/core")) continue;

                    if (buffer.length() > 0) buffer.append(",");
                    buffer.append("<").append(resourcesGroupForPath.getPath()).append(">");
                }
                LogHelper.d("Received get well known core request, address: " + inputData.address + ", resourceRegistry: " + buffer.toString());
                return new CoAPResourceOutput(new CoAPMessagePayload(buffer.toString().getBytes()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.LinkFormat);
            }
        });
    }

    private void addTestResource() {
        addResource("tests/mirror", CoAPRequestMethod.POST, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                return new CoAPResourceOutput(new CoAPMessagePayload(inputData.message.getPayload().toString()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        addResource("tests/large", CoAPRequestMethod.POST, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                return new CoAPResourceOutput(new CoAPMessagePayload("SUCCESSFUL"), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        addResource("tests/large", CoAPRequestMethod.GET, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                String size = inputData.message.getURIQuery("size");
                Integer sizeInBytes = 1024;
                if (size != null && size.length() > 0) sizeInBytes = Integer.valueOf(size);

                byte[] payload = new byte[sizeInBytes];
                new Random().nextBytes(payload);

                return new CoAPResourceOutput(new CoAPMessagePayload(payload), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });
    }

    @Override
    public CoAPObservableResource getObservableResource(String path) {
        return resourceRegistry.getObservableResource(path);
    }

    /**
     * Add resource that can be observed
     *
     * @param path    path for resource
     * @param handler handler
     */
    public void addObservableResource(String path, CoAPResource.CoAPResourceHandler handler) {
        resourceRegistry.addObservableResource(path, handler);
    }

    /**
     * Add resource that can handle requests
     *
     * @param path    path for resource
     * @param handler handler
     */
    public void addResource(String path, CoAPRequestMethod method, CoAPResource.CoAPResourceHandler handler) {
        resourceRegistry.addResource(path, method, handler);
    }

    public void removeResource(String path, CoAPRequestMethod method) {
        resourceRegistry.removeResource(path, method);
    }

    public CoAPResourcesGroupForPath getResourcesForPath(String path) {
        return resourceRegistry.getResourcesForPath(path);
    }

    /**
     * Find all available to discovery coap resourceRegistry in local network.
     * Warning! Synchronous method. Do not use from main thread.
     *
     * @return list of discovered resourceRegistry
     */
    @Override
    public void runResourceDiscovery(OnResourcesDiscovered onResourcesDiscovered) {
        localPeerDiscoverer.runResourceDiscovery(onResourcesDiscovered);
    }

    public Observable<List<ResourceDiscoveryResult>> discoverLocalResources() {
        return Observable.create(emitter -> localPeerDiscoverer.runResourceDiscovery(emitter::onNext));
    }

    public void cancel(int messageId) {
        messagePool.remove(messageId);
        ackHandlersPool.remove(messageId);
    }

    /**
     * Send the message. Handler will executed then answer received(or message can't be delivered)
     */
    public void send(CoAPMessage message, CoAPHandler handler) {
        if (message != null) {
            if (message.getToken() == null) {
                message.setToken(TokenGenerator.getToken());
            }

            // The handler First!
            if (handler != null) {
                ackHandlersPool.add(message.getId(), handler);
            }

            // Let's get it on!
            messagePool.add(message);
        }
    }

    @Override
    public Observable<ResponseData> sendRequest(CoAPMessage message) {
        return Observable.create(
                emitter -> {
                    ResponseHandler responseHandler = new ResponseHandler() {
                        @Override
                        public void onResponse(ResponseData responseData) {
                            emitter.onNext(responseData);
                            emitter.onComplete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            emitter.onError(error);
                        }
                    };
                    if (sender.isStarted()) {
                        message.setResponseHandler(responseHandler);
                        send(message, null);
                    } else {
                        emitter.onError(new Throwable("Sender not started"));
                    }

                }
        );
    }

    @Override
    public Observable<CoAPMessage> send(CoAPMessage message) {
        return Observable.create(
                emitter -> send(message, new CoAPHandler() {
                    @Override
                    public void onMessage(CoAPMessage response, String error) {
                        if (error != null)
                            emitter.onError(new CoAPException(response.getCode(), error));
                        else {
                            emitter.onNext(response);
                            emitter.onComplete();
                        }
                    }

                    @Override
                    public void onAckError(String error) {
                        emitter.onError(new AckError(error));
                    }
                })
        );
    }

    /**
     * Stop coala, and clear all messages.
     */
    public void stop() {
        LogHelper.i("Coala stop");
        messagePool.clear();
        ackHandlersPool.clear();
        receiver.stop();
        sender.stop();
        connectionProvider.close();
    }

    /**
     * Try to register observer by given uri.
     */
    public Observable<String> registerObserver(String uri) {
        LogHelper.d("registerObserver " + uri);
        return Observable.create(
                emitter -> registryOfObservingResources.registerObserver(uri, new CoAPHandler() {
                    @Override
                    public void onMessage(CoAPMessage message, String error) {
                        if (message.getPayload() != null)
                            emitter.onNext(message.getPayload().toString());
                        else
                            emitter.onError(new Throwable());
                    }

                    @Override
                    public void onAckError(String error) {
                        emitter.onError(new Throwable(error));
                    }
                })
        );
    }

    /**
     * Try to unregister observer by given uri.
     */
    public void unregisterObserver(String uri) {
        LogHelper.d("unregisterObserver " + uri);
        registryOfObservingResources.unregisterObserver(uri);
    }

    public void start() {
        receiver.start();
        sender.start();
    }

    public ICoalaStorage getStorage() {
        return storage;
    }

    public void setStorage(ICoalaStorage storage) {
        this.storage = storage;
    }

    public String getVersion() {
        return BuildConfig.VERSION_NAME;
    }
}