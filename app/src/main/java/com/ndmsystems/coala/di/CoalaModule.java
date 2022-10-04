package com.ndmsystems.coala.di;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.CoAPReceiver;
import com.ndmsystems.coala.CoAPSender;
import com.ndmsystems.coala.CoAPServer;
import com.ndmsystems.coala.Coala;
import com.ndmsystems.coala.ConnectionProvider;
import com.ndmsystems.coala.LayersStack;
import com.ndmsystems.coala.LocalPeerDiscoverer;
import com.ndmsystems.coala.ResourceRegistry;
import com.ndmsystems.coala.crypto.CurveRepository;
import com.ndmsystems.coala.layers.LogLayer;
import com.ndmsystems.coala.layers.ObserveLayer;
import com.ndmsystems.coala.layers.ProxyLayer;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.ReliabilityLayer;
import com.ndmsystems.coala.layers.RequestLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.layers.arq.ArqLayer;
import com.ndmsystems.coala.layers.response.ResponseLayer;
import com.ndmsystems.coala.layers.security.SecurityLayer;
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool;
import com.ndmsystems.coala.observer.RegistryOfObservingResources;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Владимир on 06.07.2017.
 */

@Module
public class CoalaModule {

    private final Coala coala;
    private final int port;
    private final CoAPMessagePool.Companion.Params params;

    public CoalaModule(Coala coala, int port, CoAPMessagePool.Companion.Params params) {
        this.coala = coala;
        this.port = port;
        this.params = params;
    }

    @Provides
    @Singleton
    public ResourceRegistry provideResourceRegistry(CoAPClient client) {
        return new ResourceRegistry(client);
    }

    @Provides
    @Singleton
    public CoAPClient provideClient() {
        return coala;
    }

    @Provides
    @Singleton
    public ConnectionProvider provideConnectionProvider() {
        return new ConnectionProvider(port);
    }

    @Provides
    @Singleton
    public ResourceDiscoveryHelper provideResourceDiscoveryHelper() {
        return new ResourceDiscoveryHelper();
    }

    @Provides
    @Singleton
    public LocalPeerDiscoverer provideLocalPeerDiscoverer(ResourceDiscoveryHelper resourceDiscoveryHelper,
                                                          CoAPClient client) {
        return new LocalPeerDiscoverer(resourceDiscoveryHelper,
                client, 5683);
    }

    @Provides
    @Singleton
    public SecuredSessionPool provideSecuredSessionPool() {
        return new SecuredSessionPool();
    }

    @Provides
    @Singleton
    public CoAPServer provideServer() {
        return coala;
    }

    @Provides
    @Singleton
    public RegistryOfObservingResources provideRegistryOfObservingResources(CoAPClient client) {
        return new RegistryOfObservingResources(client);
    }

    @Provides
    @Singleton
    public ProxyLayer provideProxyLayer(CoAPClient client,
                                        CoAPMessagePool messagePool) {
        return new ProxyLayer(client,
                messagePool);
    }

    @Provides
    @Singleton
    public SecurityLayer provideSecurityLayer(CoAPMessagePool messagePool,
                                              AckHandlersPool ackHandlersPool,
                                              CoAPClient client,
                                              SecuredSessionPool securedSessionPool) {
        return new SecurityLayer(messagePool,
                ackHandlersPool,
                client,
                securedSessionPool);
    }

    @Provides
    @Singleton
    public ObserveLayer provideObserveLayer(RegistryOfObservingResources registryOfObservingResources,
                                            CoAPClient client,
                                            CoAPServer server,
                                            AckHandlersPool ackHandlersPool) {
        return new ObserveLayer(registryOfObservingResources,
                client,
                server,
                ackHandlersPool);
    }

    @Provides
    @Singleton
    public ReliabilityLayer provideReliabilityLayer(CoAPMessagePool messagePool,
                                                    AckHandlersPool ackHandlersPool) {
        return new ReliabilityLayer(messagePool,
                ackHandlersPool);
    }

    @Provides
    @Singleton
    public RequestLayer provideRequestLayer(ResourceRegistry resourceRegistry,
                                            CoAPClient client) {
        return new RequestLayer(resourceRegistry,
                client);
    }

    @Provides
    @Singleton
    public ResponseLayer provideResponseLayer(CoAPClient client) {
        return new ResponseLayer(client);
    }

    @Provides
    @Singleton
    public ArqLayer provideArqLayer(CoAPMessagePool messagePool,
                                    CoAPClient client) {
        return new ArqLayer(client,
                messagePool);
    }

    @Provides
    @Singleton
    public LogLayer provideLogLayer() {
        return new LogLayer();
    }

    @Provides
    @Named("send")
    @Singleton
    public LayersStack provideSendLayerStack(ResponseLayer responseLayer,
                                             ArqLayer arqLayer,
                                             LogLayer logLayer,
                                             SecurityLayer securityLayer,
                                             ObserveLayer observeLayer,
                                             ProxyLayer proxyLayer) {
        return new LayersStack(
                new SendLayer[]{
                        responseLayer,
                        arqLayer,
                        logLayer,
                        observeLayer,
                        proxyLayer,
                        securityLayer,
                }, null);
    }

    @Provides
    @Named("receive")
    @Singleton
    public LayersStack provideReceiveLayerStack(ProxyLayer proxyLayer,
                                                SecurityLayer securityLayer,
                                                ArqLayer arqLayer,
                                                LogLayer logLayer,
                                                ReliabilityLayer reliabilityLayer,
                                                ObserveLayer observeLayer,
                                                RequestLayer requestLayer,
                                                ResponseLayer responseLayer) {
        return new LayersStack(null,
                new ReceiveLayer[]{
                        proxyLayer,
                        securityLayer,
                        logLayer,
                        arqLayer,
                        reliabilityLayer,
                        observeLayer,
                        requestLayer,
                        responseLayer
                });
    }

    @Provides
    @Singleton
    public CoAPReceiver provideReceiver(ConnectionProvider connectionProvider,
                                        @Named("receive") LayersStack receiveLayerStack) {
        return new CoAPReceiver(connectionProvider, receiveLayerStack);
    }

    @Provides
    @Singleton
    public CoAPSender provideSender(ConnectionProvider connectionProvider,
                                    CoAPMessagePool messagePool,
                                    @Named("send") LayersStack sendLayerStack) {
        return new CoAPSender(connectionProvider, messagePool, sendLayerStack);
    }

    @Provides
    @Singleton
    public AckHandlersPool provideAckHandlersPool() {
        return new AckHandlersPool();
    }

    @Provides
    @Singleton
    public CoAPMessagePool provideMessagePool(AckHandlersPool ackHandlersPool) {
        return new CoAPMessagePool(ackHandlersPool, params);
    }

    @Provides
    @Singleton
    public CurveRepository provideCurveRepository() {
        return new CurveRepository(coala.getStorage());
    }
}
