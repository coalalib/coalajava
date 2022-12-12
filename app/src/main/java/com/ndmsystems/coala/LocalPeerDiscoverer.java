package com.ndmsystems.coala;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Владимир on 26.06.2017.
 */

public class LocalPeerDiscoverer {

    private final Integer port;
    private final ResourceDiscoveryHelper resourceDiscoveryHelper;
    private final CoAPClient client;

    public LocalPeerDiscoverer(ResourceDiscoveryHelper resourceDiscoveryHelper,
                               CoAPClient client, Integer port) {
        this.resourceDiscoveryHelper = resourceDiscoveryHelper;
        this.client = client;
        this.port = port;
    }

    public synchronized void runResourceDiscovery(CoAPServer.OnResourcesDiscovered onResourcesDiscovered) {
        try {
            resourceDiscoveryHelper.clear();
            sendDiscoveryMulticast();
            Thread.sleep(500);
            sendDiscoveryMulticast();//повышаем надежность процесса
            Thread.sleep(500);
            List<ResourceDiscoveryResult> result = resourceDiscoveryHelper.getResultsList();
            if (result == null)
                result = new ArrayList<>();
            onResourcesDiscovered.onResourcesDiscovered(result);
        } catch (InterruptedException ignore) {
        }
    }

    private void sendDiscoveryMulticast() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET); // ID will be auto-generated
        message.setURI("coap://224.0.0.187:" + port + "/info");
        message.setToken(Hex.decodeHex("eb21926ad2e765a7".toCharArray()));// Simple random token, some in ReliabilityLayer. For recognize broadcast
        client.send(message, new CoAPHandler() {
            @Override
            public void onMessage(CoAPMessage message, String error) {
                LogHelper.d("sendDiscoveryMulticast response: " + message.getAddress() + ", payload " + message);
                resourceDiscoveryHelper.addResult(new ResourceDiscoveryResult(message.getPayload() != null ? message.getPayload().toString() : "", message.getAddress()));
            }

            @Override
            public void onAckError(String error) {
                LogHelper.d("sendDiscoveryMulticast onAckError: " + error);
            }
        });
    }

}
