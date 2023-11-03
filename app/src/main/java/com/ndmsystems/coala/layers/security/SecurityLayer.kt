package com.ndmsystems.coala.layers.security

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPHandler.AckError
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.exceptions.PeerPublicKeyMismatchException
import com.ndmsystems.coala.helpers.EncryptionHelper
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.RandomGenerator
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.layers.LogLayer
import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.layers.security.session.SecuredSession
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.Arrays
import java.util.Collections

class SecurityLayer(private val messagePool: CoAPMessagePool,
                    private val ackHandlersPool: AckHandlersPool,
                    private val client: CoAPClient,
                    private val sessionPool: SecuredSessionPool) : ReceiveLayer, SendLayer {
    private val pendingMessages = Collections.synchronizedSet(HashSet<CoAPMessage>())
    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult {
        val senderAddress = senderAddressReference.get()
        val mainMessage = messagePool.getSourceMessageByToken(message.hexToken)
        val option = message.getOption(CoAPMessageOptionCode.OptionHandshakeType)
        if (option != null) {
            LogHelper.d("OptionHandshakeType: " + option.value)
            processHandshake(HandshakeType.fromInt(option.value as Int), message, senderAddress)
            return LayersStack.LayerResult(false)
        }
        val sessionNotFound = message.getOption(CoAPMessageOptionCode.OptionSessionNotFound)
        val sessionExpired = message.getOption(CoAPMessageOptionCode.OptionSessionExpired)
        if (sessionNotFound != null
                || sessionExpired != null) {
            LogHelper.i("Session not found or expired for address: $senderAddress, try to restart.")
            removeSessionForAddressIfNotInProgress(mainMessage)
            messagePool.requeue(message.id)
            return LayersStack.LayerResult(false)
        }
        if (message.getURIScheme() == CoAPMessage.Scheme.SECURE) {
            val sessionByAddress = getSessionForAddress(mainMessage ?: message)
            val sessionByPeerProxySecurityId = getSessionByPeerProxySecurityId(message)
            val session = sessionByPeerProxySecurityId ?: sessionByAddress
            if (session == null || !session.isReady) {
                LogHelper.e("Encrypt message error: " + message.id + ", token: " + message.hexToken + ", sessionAddress: " + senderAddress)
                mainMessage?.let { addMessageToPending(it) }
                sendSessionError(message, senderAddress, CoAPMessageOptionCode.OptionSessionNotFound)
                return LayersStack.LayerResult(false)
            }
            val decryptResult = EncryptionHelper.decrypt(message, session.aead!!)
            if (decryptResult) {
                message.peerPublicKey = session.peerPublicKey
            } else {
                removeSessionForAddressIfNotInProgress(mainMessage ?: message)
                LogHelper.w("Can't decrypt, message: " + LogLayer.getStringToPrintReceivedMessage(message, senderAddressReference) + ", mainMessage:" + (if (mainMessage != null) LogLayer.getStringToPrintReceivedMessage(mainMessage, senderAddressReference) else "null") + ", send SessionExpired")
                mainMessage?.let { addMessageToPending(it) }
                sendSessionError(message, senderAddress, CoAPMessageOptionCode.OptionSessionExpired)
                return LayersStack.LayerResult(false)
            }
        }
        return LayersStack.LayerResult(true)
    }

    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult {
        val receiverAddress = receiverAddressReference.get()
        if (message.getURIScheme() == CoAPMessage.Scheme.SECURE) {
            var session = getSessionForAddress(message)
            if (session == null) {
                session = SecuredSession(false)
                if (message.proxy != null) {
                    generateProxySessionSecurityIdAndAddToMessageAndSession(session, message)
                }
                setSessionForAddress(session, message)
                sendClientHello(message.proxy, session.peerProxySecurityId, message.address, session.publicKey, object : CoAPHandler {
                    override fun onMessage(clientHelloResponseMessage: CoAPMessage, error: String?) {
                        if (error == null) {
                            val publicKey = clientHelloResponseMessage.payload!!.content
                            if (message.peerPublicKey == null
                                    || Arrays.equals(message.peerPublicKey, publicKey)) {
                                LogHelper.d("Session with " + message.address + " started, publicKey = " + Hex.encodeHexString(publicKey))
                                val securedSession = getSessionForAddress(message)
                                if (securedSession != null) {
                                    securedSession.start(publicKey)
                                    setSessionForAddress(securedSession, message)
                                    sendPendingMessage(message.address)
                                } else {
                                    LogHelper.i("Error then try to client hello, session removed, error is nul, securedSession is null")
                                    removeSessionForAddress(message)
                                    removePendingMessagesByAddress(receiverAddress)
                                }
                            } else {
                                LogHelper.w("Expected key: " + Hex.encodeHexString(message.peerPublicKey) + ", actual key: " + Hex.encodeHexString(publicKey))
                                removeSessionForAddress(message)
                                throwMismatchKeysError(message, receiverAddress)
                                removePendingMessagesByAddress(receiverAddress)
                            }
                        } else {
                            LogHelper.i("Error then try to client hello: $error")
                            removeSessionForAddress(message)
                            removePendingMessagesByAddress(receiverAddress)
                        }
                    }

                    override fun onAckError(error: String) {
                        LogHelper.i("Error then try to client hello: $error")
                        removeSessionForAddress(message)
                        removePendingMessagesByAddress(receiverAddress)
                    }
                })
                addMessageToPending(message)
                return LayersStack.LayerResult(false)
            }
            if (!session.isReady) {
                addMessageToPending(message)
                return LayersStack.LayerResult(false)
            }
            if (session.peerProxySecurityId != null) {
                message.setProxySecurityId(session.peerProxySecurityId!!) //Don't need be encrypted
            } else {
                if (message.proxy != null) {
                    generateProxySessionSecurityIdAndAddToMessageAndSession(session, message)
                }
            }
            if (message.peerPublicKey == null
                    || Arrays.equals(message.peerPublicKey, session.peerPublicKey)) {
                EncryptionHelper.encrypt(message, session.aead!!)
            } else {
                LogHelper.w("Expected key: " + Hex.encodeHexString(message.peerPublicKey) + ", actual key: " + Hex.encodeHexString(session.peerPublicKey))
                removeSessionForAddressIfNotInProgress(message)
                throwMismatchKeysError(message, receiverAddress)
                return LayersStack.LayerResult(false)
            }
        }
        return LayersStack.LayerResult(true)
    }

    private fun generateProxySessionSecurityIdAndAddToMessageAndSession(session: SecuredSession, message: CoAPMessage) {
        val securityId = RandomGenerator.randomUnsignedIntAsLong
        session.peerProxySecurityId = securityId
        message.setProxySecurityId(securityId)
    }

    private fun throwMismatchKeysError(message: CoAPMessage, receiverAddress: InetSocketAddress?) {
        val responseHandler = message.responseHandler
        if (responseHandler != null) {
            val errorText = "Can't create session with $receiverAddress: peer public key mismatch"
            LogHelper.w(errorText)
            responseHandler.onError(PeerPublicKeyMismatchException(errorText).setMessageDeliveryInfo(client.getMessageDeliveryInfo(message)))
        }
    }

    private fun addMessageToPending(message: CoAPMessage) {
        LogHelper.d("Add message " + message.id + " to pending pool")
        messagePool.remove(message)
        synchronized(pendingMessages) { pendingMessages.add(message) }
    }

    private fun removePendingMessagesByAddress(address: InetSocketAddress?) {
        LogHelper.d("removePendingMessagesByAddress $address")
        synchronized(pendingMessages) {
            val it = pendingMessages.iterator()
            while (it.hasNext()) {
                val message = it.next()
                if (address == null || message.address == address) {
                    val errorText = "Can't create session with: " + (address?.toString() ?: "null")
                    CoroutineScope(IO).launch {
                        ackHandlersPool.raiseAckError(message, errorText)
                        val responseHandler = message.responseHandler
                        if (responseHandler != null) {
                            LogHelper.i(errorText)
                            responseHandler.onError(AckError(errorText))
                        }
                    }
                    it.remove()
                }
            }
        }
    }

    private fun sendPendingMessage(address: InetSocketAddress) {
        LogHelper.d("sendPendingMessages to address: $address")
        synchronized(pendingMessages) {
            val it = pendingMessages.iterator()
            while (it.hasNext()) {
                try {
                    val message = it.next()
                    if (message.address == address) {
                        messagePool.add(message)
                        it.remove()
                    }
                } catch (e: Exception) {
                    LogHelper.e("Exception: $e")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun processHandshake(handshakeType: HandshakeType?, message: CoAPMessage, senderAddress: InetSocketAddress) {
        when (handshakeType) {
            HandshakeType.ClientHello, HandshakeType.ClientSignature -> processIncomingHandshake(handshakeType, message, senderAddress)
            HandshakeType.PeerSignature, HandshakeType.PeerHello -> processOutgoingHandshake(handshakeType, message)
            else -> {}
        }
    }

    //Received answer
    private fun processOutgoingHandshake(handshakeType: HandshakeType, message: CoAPMessage) {
        if (handshakeType == HandshakeType.PeerHello) {
            val handler = ackHandlersPool[message.id]
            if (handler != null) {
                handler.onMessage(message, null)
                ackHandlersPool.remove(message.id)
            }
            messagePool.remove(message)
        } else { //TODO: Realize!
            LogHelper.e("Received Peer signature")
        }
    }

    private fun processIncomingHandshake(handshakeType: HandshakeType, message: CoAPMessage, senderAddress: InetSocketAddress) {


        if (message.payload == null) return
        if (handshakeType == HandshakeType.ClientHello) {
            LogHelper.d("Received HANDSHAKE Client Public Key")
            /*val peerSession = SecuredSession(true)
            setSessionForAddress(peerSession, message)
            // Update peer public key and send my public key to peer
            peerSession.startPeer(message.payload!!.content)
            sendPeerHello(senderAddress, peerSession.publicKey, message)*/
            //TODO: Disabled before server fixed
            return
        } else { //TODO: Realize!
            LogHelper.e("Received Client signature")
        }
    }

    private fun sendSessionError(message: CoAPMessage, senderAddress: InetSocketAddress, code: CoAPMessageOptionCode) {
        val responseMessage = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeUnauthorized, message.id)
        responseMessage.address = senderAddress
        if (message.getProxySecurityId() != null) {
            responseMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, message.getProxySecurityId()!!))
        }
        responseMessage.addOption(CoAPMessageOption(code, 1))
        responseMessage.token = message.token
        client.send(responseMessage, null)
    }

    private fun sendClientHello(proxyAddress: InetSocketAddress?, proxySecurityId: Long?, address: InetSocketAddress, myPublicKey: ByteArray, handler: CoAPHandler?) {
        val responseMessage = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        responseMessage.address = address
        responseMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.ClientHello.toInt()))
        if (proxySecurityId != null) {
            responseMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, proxySecurityId))
        }
        responseMessage.payload = CoAPMessagePayload(myPublicKey)
        proxyAddress?.let { responseMessage.setProxy(it) }
        client.send(responseMessage, handler)
        LogHelper.d(
                "sendClientHello messageId: " + responseMessage.id
                        + (" address: " + address.address.hostAddress + ":" + address.port)
                        + ", publicKey: " + Hex.encodeHexString(myPublicKey)
                        + ", securityId " + responseMessage.getOption(CoAPMessageOptionCode.OptionProxySecurityID)
        )
    }

    private fun sendPeerHello(address: InetSocketAddress, publicKey: ByteArray, message: CoAPMessage) {
        LogHelper.d("sendPeerHello")
        val responseMessage = CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, message.id)
        responseMessage.address = address
        responseMessage.setURIScheme(message.getURIScheme())
        responseMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionHandshakeType, HandshakeType.PeerHello.toInt()))
        responseMessage.payload = CoAPMessagePayload(publicKey)
        client.send(responseMessage, null)
    }

    private fun getSessionForAddress(mainMessage: CoAPMessage?): SecuredSession? {
        if (mainMessage == null) {
            LogHelper.e("getSessionForAddress, try to get hash for null message!")
        }
        return sessionPool[getHashAddressString(mainMessage)]
    }

    private fun getSessionByPeerProxySecurityId(message: CoAPMessage): SecuredSession? {
        val opt = message.getOption(CoAPMessageOptionCode.OptionProxySecurityID)
        val obj = opt?.value
        return sessionPool.getByPeerProxySecurityId(if (obj != null) obj as Long else null)
    }

    private fun getHashAddressString(mainMessage: CoAPMessage?): String? {
        if (mainMessage == null) {
            LogHelper.e("getHashAddressString, try to get hash for null message!")
            return null
        }
        return (if (mainMessage.address.address?.hostAddress != null) mainMessage.address.address.hostAddress + ":" + mainMessage.address.port else mainMessage.getURI()) + if (mainMessage.proxy == null) "" else mainMessage.proxy.toString()
    }

    private fun setSessionForAddress(securedSession: SecuredSession, message: CoAPMessage) {
        LogHelper.v("setSessionForAddress " + getHashAddressString(message))
        sessionPool[getHashAddressString(message)] = securedSession
    }

    private fun removeSessionForAddressIfNotInProgress(mainMessage: CoAPMessage?) {
        val securedSession = getSessionForAddress(mainMessage)
        if (securedSession != null) {
            LogHelper.d("removeSessionForAddressIfNotInProgress, ready: " + securedSession.isReady)
            if (securedSession.isReady) removeSessionForAddress(mainMessage)
        }
    }

    private fun removeSessionForAddress(mainMessage: CoAPMessage?) {
        if (mainMessage == null) {
            LogHelper.e("removeSessionForAddress, try to get hash for null message!")
        }
        val hashAddress = getHashAddressString(mainMessage)
        hashAddress?.let {
            LogHelper.v("removeSessionForAddress $it")
            sessionPool.remove(it)
        }
    }
}