package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPObservableResource;
import com.ndmsystems.coala.CoAPResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.CoAPServer;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.observer.Observer;
import com.ndmsystems.coala.observer.ObservingResource;
import com.ndmsystems.coala.observer.RegistryOfObservingResources;
import com.ndmsystems.coala.utils.Reference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by Владимир on 28.05.2017.
 */
public class ObserveLayerTest {
    private ObserveLayer observeLayer;
    private AckHandlersPool ackHandlers;
    private RegistryOfObservingResources registryOfObservingResources;
    private CoAPClient client;
    private CoAPServer server;

    @Before
    public void setUp() throws Exception {
        ackHandlers = mock(AckHandlersPool.class);
        registryOfObservingResources = mock(RegistryOfObservingResources.class);
        client = mock(CoAPClient.class);
        server = mock(CoAPServer.class);

        observeLayer = new ObserveLayer(registryOfObservingResources,
                client,
                server,
                ackHandlers);
    }

    /* test send */
    @Test
    public void onSend_registrationRequest_shouldAddObservingResourceToRegistry() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.setURI("coap://123.123.123.123:5556/asd?asd=asd");
        message.setToken(new byte[2]);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);
        final CoAPHandler HANDLER = mock(CoAPHandler.class);
        when(ackHandlers.get(message.getId())).thenReturn(HANDLER);

        boolean shouldContinue = observeLayer.onSend(message, addressReference);
        assertTrue(shouldContinue);

        ArgumentCaptor<ObservingResource> argumentCaptor = ArgumentCaptor.forClass(ObservingResource.class);
        verify(registryOfObservingResources).addObservingResource(eq(message.getToken()), argumentCaptor.capture());

        ObservingResource observingResource = argumentCaptor.getValue();
        assertEquals(message.getURI(), observingResource.getUri());
        assertEquals(HANDLER, observingResource.getHandler());
    }

    @Test
    public void onSend_deregistrationRequest_shouldRemoveObservingResourceFromRegistry() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.setToken(new byte[2]);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        boolean shouldContinue = observeLayer.onSend(message, addressReference);
        assertTrue(shouldContinue);

        verify(registryOfObservingResources).removeObservingResource(message.getToken());
    }

    @Test
    public void onSend_unknownRequest_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 2);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        boolean shouldContinue = observeLayer.onSend(message, addressReference);
        assertTrue(shouldContinue);

        verify(registryOfObservingResources, times(0)).removeObservingResource(message.getToken());
        verify(registryOfObservingResources, times(0)).addObservingResource(any(byte[].class), any(ObservingResource.class));
    }

    /* test receive request */

    @Test
    public void onReceive_registrationRequest_shouldAddObserverToObservableResource_andSendResponse() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.setURIPath("asd");
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        CoAPObservableResource resource = mock(CoAPObservableResource.class);
        CoAPResource.CoAPResourceHandler handler = mock(CoAPResource.CoAPResourceHandler.class);
        CoAPResourceOutput coAPResourceOutput = mock(CoAPResourceOutput.class);
        when(handler.onReceive(any(CoAPResourceInput.class))).thenReturn(coAPResourceOutput);
        when(resource.getHandler()).thenReturn(handler);
        when(server.getObservableResource(message.getURIPathString()))
                .thenReturn(resource);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertFalse(shouldContinue);

        ArgumentCaptor<Observer> argumentCaptor = ArgumentCaptor.forClass(Observer.class);
        verify(resource).addObserver(argumentCaptor.capture());
        Observer observer = argumentCaptor.getValue();
        assertEquals(message, observer.registerMessage);
        assertTrue(address == observer.address);

        verify(resource).send(coAPResourceOutput, observer);
    }

    @Test
    public void onReceive_deregistrationRequest_shouldRemoveObserverFromObservableResource() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        CoAPObservableResource resource = mock(CoAPObservableResource.class);
        when(server.getObservableResource(message.getURIPathString()))
                .thenReturn(resource);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertTrue(shouldContinue);

        ArgumentCaptor<Observer> argumentCaptor = ArgumentCaptor.forClass(Observer.class);
        verify(resource).removeObserver(argumentCaptor.capture());
        Observer observer = argumentCaptor.getValue();
        assertEquals(message, observer.registerMessage);
        assertTrue(address == observer.address);

    }

    @Test
    public void onReceive_unknownRequestType_shoudSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 2);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        CoAPObservableResource resource = mock(CoAPObservableResource.class);
        when(server.getObservableResource(message.getURIPathString()))
                .thenReturn(resource);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertTrue(shouldContinue);

        verify(resource, times(0)).addObserver(any(Observer.class));
        verify(resource, times(0)).removeObserver(any(Observer.class));
    }

    @Test
    public void onReceive_requestForUnknownObservableRecource_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 1);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        when(server.getObservableResource(message.getURIPathString()))
                .thenReturn(null);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertTrue(shouldContinue);
    }

    /* test receive response/notification */

    @Test
    public void onReceive_expectedNotification_shouldProcessNotification_andCancelRequest() {
        final int SEQUENCE_NUMBER = 53;
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent);
        message.setToken(new byte[2]);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, SEQUENCE_NUMBER);
        message.addOption(option);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        ObservingResource observingResource = mock(ObservingResource.class);
        when(registryOfObservingResources.getResource(message.getToken())).thenReturn(observingResource);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertFalse(shouldContinue);

        verify(client).cancel(message.getId());
        verify(registryOfObservingResources).processNotification(message, ObserveLayer.DEFAULT_MAX_AGE, SEQUENCE_NUMBER);
    }

    @Test
    public void onReceive_expectedNotification_whichIsConfirmable_shouldSendAcknowledgeMessage() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        message.setToken(new byte[2]);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 53);
        message.addOption(option);
        message.setURIScheme(CoAPMessage.Scheme.NORMAL);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        ObservingResource observingResource = mock(ObservingResource.class);
        when(registryOfObservingResources.getResource(message.getToken())).thenReturn(observingResource);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertFalse(shouldContinue);

        ArgumentCaptor<CoAPMessage> argumentCaptor = ArgumentCaptor.forClass(CoAPMessage.class);
        verify(client).send(argumentCaptor.capture(), (CoAPHandler) isNull());
        CoAPMessage ackMessage = argumentCaptor.getValue();
        assertEquals(address.getAddress().getHostAddress(), ackMessage.getURIHost());
        assertEquals((long) address.getPort(), (long) ackMessage.getURIPort());
        assertEquals(message.getURIScheme(), ackMessage.getURIScheme());
        assertEquals(CoAPMessageType.ACK, ackMessage.getType());
        assertEquals(CoAPMessageCode.CoapCodeEmpty, ackMessage.getCode());
        assertEquals(message.getId(), ackMessage.getId());
    }

    @Test
    public void onReceive_expectedNotification_withoutObserveOption_orWithNotOkCode_shouldRemoveObservingResource() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        message.setToken(new byte[2]);
        message.setURIScheme(CoAPMessage.Scheme.NORMAL);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        ObservingResource observingResource = mock(ObservingResource.class);
        when(registryOfObservingResources.getResource(message.getToken())).thenReturn(observingResource);

        observeLayer.onReceive(message, addressReference);

        verify(registryOfObservingResources).removeObservingResource(message.getToken());
    }

    @Test
    public void onReceive_unexpectedNotification_shouldResetObservation() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        message.setToken(new byte[2]);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 53);
        message.addOption(option);
        message.setURIScheme(CoAPMessage.Scheme.NORMAL);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        when(registryOfObservingResources.getResource(message.getToken())).thenReturn(null);

        observeLayer.onReceive(message, addressReference);

        ArgumentCaptor<CoAPMessage> argumentCaptor = ArgumentCaptor.forClass(CoAPMessage.class);
        verify(client).send(argumentCaptor.capture(), (CoAPHandler) isNull());
        CoAPMessage resetMessage = argumentCaptor.getValue();
        assertEquals(address.getAddress().getHostAddress(), resetMessage.getURIHost());
        assertEquals((long) address.getPort(), (long) resetMessage.getURIPort());
        assertEquals(message.getURIScheme(), resetMessage.getURIScheme());
        assertEquals(CoAPMessageType.RST, resetMessage.getType());
        assertEquals(CoAPMessageCode.CoapCodeEmpty, resetMessage.getCode());
        assertEquals(message.getId(), resetMessage.getId());
        assertEquals(message.getToken(), resetMessage.getToken());
    }

    @Test
    public void onReceive_notificationWithoudToken_shouldReject() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 53);
        message.addOption(option);
        message.setURIScheme(CoAPMessage.Scheme.NORMAL);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        when(registryOfObservingResources.getResource(message.getToken())).thenReturn(null);

        boolean shouldContinue = observeLayer.onReceive(message, addressReference);
        assertFalse(shouldContinue);

        ArgumentCaptor<CoAPMessage> argumentCaptor = ArgumentCaptor.forClass(CoAPMessage.class);
        verify(client).send(argumentCaptor.capture(), (CoAPHandler) isNull());
        CoAPMessage resetMessage = argumentCaptor.getValue();
        assertEquals(address.getAddress().getHostAddress(), resetMessage.getURIHost());
        assertEquals((long) address.getPort(), (long) resetMessage.getURIPort());
        assertEquals(message.getURIScheme(), resetMessage.getURIScheme());
        assertEquals(CoAPMessageType.RST, resetMessage.getType());
        assertEquals(CoAPMessageCode.CoapCodeEmpty, resetMessage.getCode());
        assertEquals(message.getId(), resetMessage.getId());
    }

    @Test
    public void onReceive_responseWithoutToken_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.CoapCodeContent);
        InetSocketAddress address = mock(InetSocketAddress.class);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        observeLayer.onReceive(message, addressReference);

        verify(registryOfObservingResources, times(0)).addObservingResource((byte[]) any(), (ObservingResource) any());
        verify(registryOfObservingResources, times(0)).removeObservingResource((byte[]) any());
        verify(client, times(0)).cancel(anyInt());
        verify(client, times(0)).send((CoAPMessage) any(), (CoAPHandler) any());
    }

    @Test
    public void onReceive_notNotificationResponse_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        message.setToken(new byte[2]);
        message.setURIScheme(CoAPMessage.Scheme.NORMAL);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        observeLayer.onReceive(message, addressReference);

        verify(registryOfObservingResources, times(0)).addObservingResource((byte[]) any(), (ObservingResource) any());
        verify(registryOfObservingResources, times(0)).removeObservingResource((byte[]) any());
        verify(client, times(0)).cancel(anyInt());
        verify(client, times(0)).send((CoAPMessage) any(), (CoAPHandler) any());
    }
}