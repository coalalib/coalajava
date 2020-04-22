package com.ndmsystems.coala.layers.security;

import com.ndmsystems.coala.AckHandlersPool;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.exceptions.PeerPublicKeyMismatchException;
import com.ndmsystems.coala.helpers.EncryptionHelper;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.RandomGenerator;
import com.ndmsystems.coala.layers.LogLayer;
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
import com.ndmsystems.infrastructure.logging.LogHelper;

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
            SecuredSession sessionByAddress = getSessionForAddress(mainMessage != null ? mainMessage : message);
            SecuredSession sessionByPeerProxySecurityId = getSessionByPeerProxySecurityId(message);
            SecuredSession session = sessionByPeerProxySecurityId != null ? sessionByPeerProxySecurityId : sessionByAddress;

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
                removeSessionForAddressIfNotInProgress(mainMessage != null ? mainMessage : message);
                LogHelper.w("Can't decrypt, message: " + LogLayer.getStringToPrintReceivedMessage(message, senderAddressReference) + ", mainMessage:" + (mainMessage != null ? LogLayer.getStringToPrintSendingMessage(mainMessage, senderAddressReference) : "null") + ", send SessionExpired");
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
                session = new SecuredSession(false);
                if (message.getProxy() != null) {
                    generateProxySessionSecurityIdAndAddToMessageAndSession(session, message);
                }
                setSessionForAddress(session, message);
                sendClientHello(message.getProxy(), session.getPeerProxySecurityId(), message.getAddress(), session.getPublicKey(), new CoAPHandler() {
                    @Override
                    public void onMessage(CoAPMessage clientHelloResponseMessage, String error) {
                        if (error == null) {
                            byte[] publicKey = clientHelloResponseMessage.getPayload().content;
                            if (message.getPeerPublicKey() == null
                                    || Arrays.equals(message.getPeerPublicKey(), publicKey)) {
                                LogHelper.d("Session with " + message.getAddress() + " started, publicKey = " + Hex.encodeHexString(publicKey));
                                SecuredSession securedSession = getSessionForAddress(message);
                                if (securedSession != null) {
                                    securedSession.start(publicKey);

                                    setSessionForAddress(securedSession, message);

                                    sendPendingMessage(message.getAddress());
                                } else {
                                    LogHelper.e("Error then try to client hello, session removed: " + error);
                                    removeSessionForAddress(message);
                                    removePendingMessagesByAddress(receiverAddress);
                                }
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

            if (session.getPeerProxySecurityId() != null) {
                message.setProxySecurityId(session.getPeerProxySecurityId());//Don't need be encrypted
            } else {
                if (message.getProxy() != null) {
                    generateProxySessionSecurityIdAndAddToMessageAndSession(session, message);
                }
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

    private void generateProxySessionSecurityIdAndAddToMessageAndSession(SecuredSession session, CoAPMessage message) {
        Long securityId = RandomGenerator.getRandomUnsignedIntAsLong();
        session.setPeerProxySecurityId(securityId);
        message.setProxySecurityId(securityId);
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
                if (message.getAddress().equals(address)) {
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
                    if (message.getAddress().equals(address)) {
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
            setSessionForAddress(peerSession, message);
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
        responseMessage.setAddress(senderAddress);
        if (message.getProxySecurityId() != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, message.getProxySecurityId()));
        }
        responseMessage.addOption(new CoAPMessageOption(code, 1));
        responseMessage.setToken(message.getToken());

        client.send(responseMessage, null);
    }

    public void sendClientHello(InetSocketAddress proxyAddress, Long proxySecurityId, InetSocketAddress address, byte[] myPublicKey, CoAPHandler handler) {
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        responseMessage.setAddress(address);
        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.ClientHello.toInt()));
        if (proxySecurityId != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, proxySecurityId));
        }
        responseMessage.setPayload(new CoAPMessagePayload(myPublicKey));
        responseMessage.setProxy(proxyAddress);

        client.send(responseMessage, handler);

        LogHelper.d("sendClientHello messageId: " + responseMessage.getId() + " address: " + address.getAddress().getHostAddress() + ":" + address.getPort() + ", publicKey: " + Hex.encodeHexString(myPublicKey) + ", securityId " + responseMessage.getOption(CoAPMessageOptionCode.OptionProxySecurityID));
    }

    public void sendPeerHello(InetSocketAddress address, byte[] publicKey, CoAPMessage message) {
        LogHelper.d("sendPeerHello");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, message.getId());
        responseMessage.setAddress(address);
        responseMessage.setURIScheme(message.getURIScheme());

        responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()));
        responseMessage.setPayload(new CoAPMessagePayload(publicKey));

        client.send(responseMessage, null);
    }

    private SecuredSession getSessionForAddress(CoAPMessage mainMessage) {
        return sessionPool.get(getHashAddressString(mainMessage));
    }

    private SecuredSession getSessionByPeerProxySecurityId(CoAPMessage message) {
        CoAPMessageOption opt = message.getOption(CoAPMessageOptionCode.OptionProxySecurityID);
        Object obj = opt != null ? opt.value : null;
        return sessionPool.getByPeerProxySecurityId(obj != null ? (long) obj : null);
    }

    private String getHashAddressString(CoAPMessage mainMessage) {
        if (mainMessage == null) {
            LogHelper.e("Try to get hash for null message!");
            return null;
        }
        return (mainMessage.getAddress() != null ? mainMessage.getAddress().getAddress().getHostAddress() + ":" + mainMessage.getAddress().getPort() : mainMessage.getURI()) + (mainMessage.getProxy() == null ? "" : mainMessage.getProxy().toString());
    }

    private void setSessionForAddress(SecuredSession securedSession, CoAPMessage message) {
        LogHelper.v("setSessionForAddress " + getHashAddressString(message));
        this.sessionPool.set(getHashAddressString(message), securedSession);
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
        LogHelper.v("removeSessionForAddress " + getHashAddressString(mainMessage));
        this.sessionPool.remove(getHashAddressString(mainMessage));
    }
}
