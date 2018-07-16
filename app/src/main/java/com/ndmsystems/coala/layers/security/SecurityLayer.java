package com.ndmsystems.coala.layers.security;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.exceptions.PeerPublicKeyMismatchException;
import com.ndmsystems.coala.helpers.EncryptionHelper;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.layers.response.ResponseHandler;
import com.ndmsystems.coala.layers.security.session.SecuredSession;
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SecurityLayer implements ReceiveLayer, SendLayer {

    private final Set<CoAPMessage> pendingMessages = Collections.synchronizedSet(new HashSet<>());
    private CoAPMessagePool messagePool;
    private AckHandlersPool ackHandlersPool;
    private CoAPClient client;
    private SecuredSessionPool sessionPool;

    public SecurityLayer(CoAPMessagePool messagePool,
                         AckHandlersPool ackHandlersPool,
                         CoAPClient client,
                         SecuredSessionPool sessionPool) {
        this.messagePool = messagePool;
        this.ackHandlersPool = ackHandlersPool;
        this.client = client;
        this.sessionPool = sessionPool;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        InetSocketAddress senderAddress = senderAddressReference.get();

        CoAPMessage mainMessage = messagePool.getSourceMessageByToken(message.getHexToken());

        CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionHandshakeType);
        if (option != null) {
            LogHelper.d("OptionHandshakeType: " + option.value);
            processHandshake(HandshakeType.fromInt((int) option.value), message, mainMessage, senderAddress);
            return false;
        }

        CoAPMessageOption sessionNotFound = message.getOption(CoAPMessageOptionCode.OptionSessionNotFound);
        CoAPMessageOption sessionExpired = message.getOption(CoAPMessageOptionCode.OptionSessionExpired);

        if (sessionNotFound != null
                || sessionExpired != null) {
            LogHelper.w("Session not found or expired for address: " + senderAddress.toString() + ", try to restart.");
            removeSessionForAddressIfNotInProgress(mainMessage);
            messagePool.requeue(message.getId());
            return false;
        }

        if (message.getURIScheme() == CoAPMessage.Scheme.SECURE) {
            SecuredSession session = getSessionForAddress(mainMessage);

            if (session == null || !session.isReady()) {
                LogHelper.e("Encrypt message error: " + message.getId() + ", token: " + message.getHexToken() + ", sessionAddress: " + senderAddress);
                if (mainMessage != null) addMessageToPending(mainMessage);
                sendSessionError(message, senderAddress, CoAPMessageOptionCode.OptionSessionNotFound);
                return false;
            }

            boolean decryptResult = EncryptionHelper.decrypt(message, session.getAead());
            if (decryptResult) {
                message.setPeerPublicKey(session.getPeerPublicKey());
            } else {
                removeSessionForAddressIfNotInProgress(mainMessage);
                LogHelper.w("Can't decrypt, send SessionExpired");
                if (mainMessage != null) addMessageToPending(mainMessage);
                sendSessionError(message, senderAddress, CoAPMessageOptionCode.OptionSessionExpired);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) {
        final InetSocketAddress receiverAddress = receiverAddressReference.get();
        if (message.getURIScheme() == CoAPMessage.Scheme.SECURE) {
            SecuredSession session = getSessionForAddress(message);

            if (session == null) {
                LogHelper.d("Try to start session with: " + receiverAddress.getAddress().getHostAddress() + ":" + receiverAddress.getPort());
                session = new SecuredSession(false);
                setSessionForAddress(session, message);
                sendClientHello(message.getProxy(), receiverAddress, session.getPublicKey(), new CoAPHandler() {
                    @Override
                    public void onMessage(CoAPMessage clientHelloResponseMessage, String error) {
                        if (error == null) {
                            byte[] publicKey = clientHelloResponseMessage.getPayload().content;
                            if (message.getPeerPublicKey() == null
                                    || Arrays.equals(message.getPeerPublicKey(), publicKey)) {
                                LogHelper.d("Session with " + receiverAddress.toString() + " started");
                                SecuredSession securedSession = getSessionForAddress(message);
                                securedSession.start(publicKey);

                                setSessionForAddress(securedSession, message);

                                sendPendingMessage(receiverAddress);
                            } else {
                                LogHelper.w("Expected key: " + Hex.encodeHexString(message.getPeerPublicKey()) + ", actual key: " + Hex.encodeHexString(publicKey));
                                removeSessionForAddress(message);
                                throwMismatchKeysError(message, receiverAddress);

                                removePendingMessagesByAddress(receiverAddress);
                            }

                        } else {
                            LogHelper.e("Error then try to client hello: " + error);
                            removeSessionForAddress(message);
                            removePendingMessagesByAddress(receiverAddress);
                        }
                    }

                    @Override
                    public void onAckError(String error) {
                        LogHelper.e("Error then try to client hello: " + error);
                        removeSessionForAddress(message);
                        removePendingMessagesByAddress(receiverAddress);
                    }
                });
                addMessageToPending(message);
                return false;
            }

            if (!session.isReady()) {
                addMessageToPending(message);
                return false;
            }

            if (message.getPeerPublicKey() == null
                    || Arrays.equals(message.getPeerPublicKey(), session.getPeerPublicKey())) {
                EncryptionHelper.encrypt(message, session.getAead());
            } else {
                LogHelper.w("Expected key: " + Hex.encodeHexString(message.getPeerPublicKey()) + ", actual key: " + Hex.encodeHexString(session.getPeerPublicKey()));
                removeSessionForAddressIfNotInProgress(message);
                throwMismatchKeysError(message, receiverAddress);
                return false;
            }
        }

        return true;
    }

    private void throwMismatchKeysError(CoAPMessage message, InetSocketAddress receiverAddress) {
        ResponseHandler responseHandler = message.getResponseHandler();
        if (responseHandler != null) {
            String errorText = "Can't create session with " + receiverAddress.toString() + ": peer public key mismatch";
            LogHelper.w(errorText);
            responseHandler.onError(new PeerPublicKeyMismatchException(errorText));
        }
    }

    private void addMessageToPending(CoAPMessage message) {
        LogHelper.d("Add message " + message.getId() + " to pending pool");
        messagePool.remove(message);
        synchronized (pendingMessages) {
            pendingMessages.add(message);
        }
    }

    private void removePendingMessagesByAddress(InetSocketAddress address) {
        synchronized (pendingMessages) {
            for (Iterator<CoAPMessage> it = pendingMessages.iterator(); it.hasNext(); ) {
                CoAPMessage message = it.next();
                if (message.getURIHost() != null && message.getURIHost().equals(address.getAddress().getHostAddress())
                        && message.getURIPort() != null && message.getURIPort().equals(address.getPort())) {
                    ackHandlersPool.raiseAckError(message, "Can't create session with: " + address.toString());
                    ResponseHandler responseHandler = message.getResponseHandler();
                    if (responseHandler != null) {
                        String errorText = "Can't create session with: " + address.toString();
                        LogHelper.w(errorText);
                        responseHandler.onError(new CoAPHandler.AckError(errorText));
                    }
                    it.remove();
                }
            }
        }
    }

    private void sendPendingMessage(InetSocketAddress address) {
        LogHelper.d("sendPendingMessages to address: " + address.toString());
        synchronized (pendingMessages) {
            for (Iterator<CoAPMessage> it = pendingMessages.iterator(); it.hasNext(); ) {
                try {
                    CoAPMessage message = it.next();
                    if (message.getURIHost() != null && message.getURIHost().equals(address.getAddress().getHostAddress())
                            && message.getURIPort() != null && message.getURIPort().equals(address.getPort())) {
                        messagePool.add(message);
                        it.remove();
                    }
                } catch (Exception e) {
                    LogHelper.e("Exception: " + e);
                    e.printStackTrace();
                }
            }
        }
    }

    private void processHandshake(HandshakeType handshakeType, CoAPMessage message, CoAPMessage mainMessage, InetSocketAddress senderAddress) {
        switch (handshakeType) {
            case ClientHello:
            case ClientSignature:
                processIncomingHandshake(handshakeType, message, mainMessage, senderAddress);
                break;
            case PeerSignature:
            case PeerHello:
                processOutgoingHandshake(handshakeType, message);
                break;
        }
    }

    //Received answer
    private void processOutgoingHandshake(HandshakeType handshakeType, CoAPMessage message) {
        if (handshakeType == HandshakeType.PeerHello) {
            CoAPHandler handler = ackHandlersPool.get(message.getId());
            if (handler != null) {
                handler.onMessage(message, null);
                ackHandlersPool.remove(message.getId());
            }
            messagePool.remove(message);
        } else { //TODO: Realize!
            LogHelper.e("Received Peer signature");
        }
    }

    private void processIncomingHandshake(HandshakeType handshakeType, CoAPMessage message, CoAPMessage mainMessage, InetSocketAddress senderAddress) {
        if (message.getPayload() == null)
            return;

        if (handshakeType == HandshakeType.ClientHello) {
            SecuredSession peerSession = new SecuredSession(true);
            setSessionForAddress(peerSession, mainMessage);
            LogHelper.d("Received HANDSHAKE Client Public Key");
            // Update peer public key and send my public key to peer
            peerSession.startPeer(message.getPayload().content);
            sendPeerHello(senderAddress, peerSession.getPublicKey(), message);
        } else {//TODO: Realize!
            LogHelper.e("Received Client signature");
        }
    }

    private void sendSessionError(CoAPMessage message, InetSocketAddress senderAddress, CoAPMessageOptionCode code) {
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeUnauthorized, message.getId());

        CoAPMessageOption option = responseMessage.getOption(CoAPMessageOptionCode.OptionURIHost);
        if (option == null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, senderAddress.getAddress().getHostAddress()));
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, senderAddress.getPort()));
        }
        responseMessage.addOption(new CoAPMessageOption(code, 1));
        responseMessage.setToken(message.getToken());

        client.send(responseMessage, null);
    }

    public void sendClientHello(InetSocketAddress proxyAddress, InetSocketAddress address, byte[] myPublicKey, CoAPHandler handler) {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, address.getAddress().getHostAddress()));
        message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, address.getPort()));

        message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.ClientHello.toInt()));
        message.setPayload(new CoAPMessagePayload(myPublicKey));

        message.setProxy(proxyAddress);

        client.send(message, handler);

        LogHelper.d("sendClientHello messageId: " + message.getId() + " address: " + address.getAddress().getHostAddress() + ":" + address.getPort() + ", publicKey: " + Hex.encodeHexString(myPublicKey));
    }

    public void sendPeerHello(InetSocketAddress address, byte[] publicKey, CoAPMessage message) {
        LogHelper.d("sendPeerHello");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, message.getId());
        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, address.getAddress().getHostAddress()));
        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, address.getPort()));
        responseMessage.setURIScheme(message.getURIScheme());

        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()));
        responseMessage.setPayload(new CoAPMessagePayload(publicKey));

        client.send(responseMessage, null);
    }

    private SecuredSession getSessionForAddress(CoAPMessage mainMessage) {
        return sessionPool.get(getHashAddressString(mainMessage));
    }

    private String getHashAddressString(CoAPMessage mainMessage) {
        return mainMessage.getAddress().getAddress().getHostAddress() + ":" + mainMessage.getAddress().getPort() + (mainMessage.getProxy() == null ? "" : mainMessage.getProxy().toString());
    }

    private void setSessionForAddress(SecuredSession securedSession, CoAPMessage mainMessage) {
        this.sessionPool.set(getHashAddressString(mainMessage), securedSession);
    }

    private void removeSessionForAddressIfNotInProgress(CoAPMessage mainMessage) {
        SecuredSession securedSession = getSessionForAddress(mainMessage);

        if (securedSession != null) {
            LogHelper.d("removeSessionForAddressIfNotInProgress, ready: " + securedSession.isReady());
            if (securedSession.isReady())
                removeSessionForAddress(mainMessage);
        }
    }

    private void removeSessionForAddress(CoAPMessage mainMessage) {
        this.sessionPool.remove(getHashAddressString(mainMessage));
    }
}
