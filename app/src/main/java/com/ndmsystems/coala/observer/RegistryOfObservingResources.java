package com.ndmsystems.coala.observer;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.RandomGenerator;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by bas on 14.11.16.
 */

public class RegistryOfObservingResources {

    private static final long PERIOD_OF_CHECKING = 10000;

    private HashMap<String, ObservingResource> observingResources = new HashMap<>();
    private Timer timer;
    private TimerTask checkResourcesTask;
    private CoAPClient client;

    public RegistryOfObservingResources(CoAPClient client) {
        this.client = client;
    }

    public void unregisterObserver(String uri) {
        LogHelper.d("unregisterObserver");
        removeObservingResource(getTokenForObservingResourceUri(uri));
    }

    private synchronized byte[] getTokenForObservingResourceUri(String stringUri) {
        for (ObservingResource resource : observingResources.values()) {
            LogHelper.d("uri1 = " + resource.getUri());
            LogHelper.d("uri2 = " + stringUri);
            LogHelper.d("uri1 equals uri2 ? " + resource.getUri().equals(stringUri));
            if (resource.getUri().equals(stringUri)) {
                LogHelper.d("initial message token: " + Hex.encodeHexString(resource.getInitiatingMessage().getToken()));
                return resource.getInitiatingMessage().getToken();
            }
        }
        return null;
    }

    public void registerObserver(String uri, CoAPHandler handler) {
        LogHelper.d("registerObserver " + uri);
        byte[] token = getTokenForObservingResourceUri(uri);
        LogHelper.d("token for observing resources: " + Hex.encodeHexString(token));
        if (token == null)
            token = RandomGenerator.getRandom(8);

        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.setURI(uri);
        message.setToken(token);
        LogHelper.v("Token: " + Hex.encodeHexString(token));
        message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0));
        client.send(message, handler);
    }

    private synchronized void checkResources() {
        LogHelper.v("checkResourcesTask: " + observingResources.size());
        for (ObservingResource resource : observingResources.values()) {
            LogHelper.d("resource: "+resource.getUri());
            if (resource.isExpired()) {
                LogHelper.d("checkResourcesTask, register: " + resource.getUri());
                registerObserver(resource.getUri(), resource.getHandler());
            }
        }
    }

    public synchronized void addObservingResource(byte[] token, ObservingResource resource) {
        String strToken = Hex.encodeHexString(token);
        LogHelper.d("addObservingResource " + strToken);
        observingResources.put(strToken, resource);
        if (!isTimerRunning()) {
            timer = new Timer(true);
            checkResourcesTask = new TimerTask() {
                @Override
                public void run() {
                    checkResources();
                }
            };
            timer.scheduleAtFixedRate(checkResourcesTask, PERIOD_OF_CHECKING, PERIOD_OF_CHECKING);
        }
    }

    public ObservingResource getResource(String token) {
        return observingResources.get(token);
    }

    public ObservingResource getResource(byte[] token) {
        String strToken = Hex.encodeHexString(token);
        return getResource(strToken);
    }

    public synchronized void removeObservingResource(byte[] token) {
        String hexToken = Hex.encodeHexString(token);
        LogHelper.v("removeObservingResource " + hexToken);
        if (!observingResources.containsKey(hexToken)) return;
        observingResources.remove(hexToken);
        if (observingResources.size() == 0) {
            if (isTimerRunning()) {
                checkResourcesTask.cancel();
                checkResourcesTask = null;
                timer.cancel();
                timer = null;
            }
        }
    }

    private boolean isTimerRunning() {
        return timer != null;
    }

    public void processNotification(CoAPMessage message, Integer maxAge, Integer sequenceNumber) {
        ObservingResource resource = getResource(message.getToken());
        LogHelper.v("processNotification");
        LogHelper.v("resource sequence number = " + resource.getSequenceNumber());
        LogHelper.v("message sequence number = " + sequenceNumber);
        if ((sequenceNumber != null && sequenceNumber > resource.getSequenceNumber()) ||
                resource.getSequenceNumber() == -1) {
            resource.setMaxAge(maxAge == null ? 30 : maxAge);
            resource.setSequenceNumber(sequenceNumber == null ? -1 : sequenceNumber);
            resource.getHandler().onMessage(message, null);
        } else {
            LogHelper.e("Wrong sequence number");
        }
    }
}