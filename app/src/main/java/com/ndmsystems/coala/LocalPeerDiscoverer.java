package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryHelper;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Владимир on 26.06.2017.
 */

public class LocalPeerDiscoverer {

    private final Integer port;
    private ResourceDiscoveryHelper resourceDiscoveryHelper;
    private CoAPClient client;

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
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET); // ID will be auto-generated
        message.setURI("coap://224.0.0.187:" + port + "/info");
        client.send(message, null);
    }

}
