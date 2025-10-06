package com.ndmsystems.coala.di

import android.net.ConnectivityManager
import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.CoAPReceiver
import com.ndmsystems.coala.CoAPSender
import com.ndmsystems.coala.CoAPServer
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.ConnectionProvider
import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.LocalPeerDiscoverer
import com.ndmsystems.coala.ResourceRegistry
import com.ndmsystems.coala.crypto.CurveRepository
import com.ndmsystems.coala.layers.LogLayer
import com.ndmsystems.coala.layers.ObserveLayer
import com.ndmsystems.coala.layers.ProxyLayer
import com.ndmsystems.coala.layers.ReliabilityLayer
import com.ndmsystems.coala.layers.RequestLayer
import com.ndmsystems.coala.layers.arq.ArqLayer
import com.ndmsystems.coala.layers.response.ResponseLayer
import com.ndmsystems.coala.layers.security.SecurityLayer
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.observer.RegistryOfObservingResources
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

/**
 * Created by Владимир on 06.07.2017.
 */
@Module
class CoalaModule(private val coala: Coala, private val port: Int, private val params: CoAPMessagePool.Companion.Params, private val connectivityManager: ConnectivityManager) {
    @Provides
    @Singleton
    fun provideResourceRegistry(client: CoAPClient): ResourceRegistry {
        return ResourceRegistry(client)
    }

    @Provides
    @Singleton
    fun provideClient(): CoAPClient {
        return coala
    }

    @Provides
    @Singleton
    fun provideConnectionProvider(): ConnectionProvider {
        return ConnectionProvider(port, connectivityManager)
    }

    @Provides
    @Singleton
    fun provideResourceDiscoveryHelper(): ResourceDiscoveryHelper {
        return ResourceDiscoveryHelper()
    }

    @Provides
    @Singleton
    fun provideLocalPeerDiscoverer(
        resourceDiscoveryHelper: ResourceDiscoveryHelper,
        client: CoAPClient
    ): LocalPeerDiscoverer {
        return LocalPeerDiscoverer(
            resourceDiscoveryHelper,
            client, 5683
        )
    }

    @Provides
    @Singleton
    fun provideSecuredSessionPool(): SecuredSessionPool {
        return SecuredSessionPool()
    }

    @Provides
    @Singleton
    fun provideServer(): CoAPServer {
        return coala
    }

    @Provides
    @Singleton
    fun provideRegistryOfObservingResources(client: CoAPClient): RegistryOfObservingResources {
        return RegistryOfObservingResources(client)
    }

    @Provides
    @Singleton
    fun provideProxyLayer(
        client: CoAPClient,
        messagePool: CoAPMessagePool
    ): ProxyLayer {
        return ProxyLayer(
            client,
            messagePool
        )
    }

    @Provides
    @Singleton
    fun provideSecurityLayer(
        messagePool: CoAPMessagePool,
        ackHandlersPool: AckHandlersPool,
        client: CoAPClient,
        securedSessionPool: SecuredSessionPool
    ): SecurityLayer {
        return SecurityLayer(
            messagePool,
            ackHandlersPool,
            client,
            securedSessionPool
        )
    }

    @Provides
    @Singleton
    fun provideObserveLayer(
        registryOfObservingResources: RegistryOfObservingResources,
        client: CoAPClient,
        server: CoAPServer,
        ackHandlersPool: AckHandlersPool
    ): ObserveLayer {
        return ObserveLayer(
            registryOfObservingResources,
            client,
            server,
            ackHandlersPool
        )
    }

    @Provides
    @Singleton
    fun provideReliabilityLayer(
        messagePool: CoAPMessagePool,
        ackHandlersPool: AckHandlersPool
    ): ReliabilityLayer {
        return ReliabilityLayer(
            messagePool,
            ackHandlersPool
        )
    }

    @Provides
    @Singleton
    fun provideRequestLayer(
        resourceRegistry: ResourceRegistry,
        client: CoAPClient
    ): RequestLayer {
        return RequestLayer(
            resourceRegistry,
            client
        )
    }

    @Provides
    @Singleton
    fun provideResponseLayer(client: CoAPClient): ResponseLayer {
        return ResponseLayer(client)
    }

    @Provides
    @Singleton
    fun provideArqLayer(
        messagePool: CoAPMessagePool,
        client: CoAPClient
    ): ArqLayer {
        return ArqLayer(
            client,
            messagePool
        )
    }

    @Provides
    @Singleton
    fun provideLogLayer(): LogLayer {
        return LogLayer()
    }

    @Provides
    @Named("send")
    @Singleton
    fun provideSendLayerStack(
        responseLayer: ResponseLayer,
        arqLayer: ArqLayer,
        logLayer: LogLayer,
        securityLayer: SecurityLayer,
        observeLayer: ObserveLayer,
        proxyLayer: ProxyLayer
    ): LayersStack {
        return LayersStack(
            arrayOf(
                responseLayer,
                arqLayer,
                logLayer,
                observeLayer,
                proxyLayer,
                securityLayer
            ), null
        )
    }

    @Provides
    @Named("receive")
    @Singleton
    fun provideReceiveLayerStack(
        proxyLayer: ProxyLayer,
        securityLayer: SecurityLayer,
        arqLayer: ArqLayer,
        logLayer: LogLayer,
        reliabilityLayer: ReliabilityLayer,
        observeLayer: ObserveLayer,
        requestLayer: RequestLayer,
        responseLayer: ResponseLayer
    ): LayersStack {
        return LayersStack(
            null, arrayOf(
                proxyLayer,
                securityLayer,
                logLayer,
                arqLayer,
                reliabilityLayer,
                observeLayer,
                requestLayer,
                responseLayer
            )
        )
    }

    @Provides
    @Singleton
    fun provideReceiver(
        connectionProvider: ConnectionProvider,
        @Named("receive") receiveLayerStack: LayersStack
    ): CoAPReceiver {
        return CoAPReceiver(connectionProvider, receiveLayerStack)
    }

    @Provides
    @Singleton
    fun provideSender(
        connectionProvider: ConnectionProvider,
        messagePool: CoAPMessagePool,
        @Named("send") sendLayerStack: LayersStack
    ): CoAPSender {
        return CoAPSender(connectionProvider, messagePool, sendLayerStack)
    }

    @Provides
    @Singleton
    fun provideAckHandlersPool(): AckHandlersPool {
        return AckHandlersPool()
    }

    @Provides
    @Singleton
    fun provideMessagePool(ackHandlersPool: AckHandlersPool): CoAPMessagePool {
        return CoAPMessagePool(ackHandlersPool, params)
    }

    @Provides
    @Singleton
    fun provideCurveRepository(): CurveRepository {
        return CurveRepository(coala.storage)
    }
}