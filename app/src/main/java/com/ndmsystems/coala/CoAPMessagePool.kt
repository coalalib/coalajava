package com.ndmsystems.coala

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import com.ndmsystems.coala.CoAPHandler.AckError
import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.helpers.TimeHelper
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.message.CoAPMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CoAPMessagePool(
    private val ackHandlersPool: AckHandlersPool,
    private val params: Params,
) {
    private val pool: ConcurrentLinkedHashMap<Int, QueueElement>
    private val messageIdForToken = ConcurrentHashMap<String, Int>()
    private val messageDeliveryInfo = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.CREATED)
        .expiration(20, TimeUnit.MINUTES)
        .build<String, MessageDeliveryInfo>()

    init {
        pool = ConcurrentLinkedHashMap.Builder<Int, QueueElement>().maximumWeightedCapacity(1000)
            .build()
        LogHelper.d("Params resendPeriod = ${params.resendPeriod}")
        LogHelper.d("Params resendLongPeriod = ${params.resendLongPeriod}")
        LogHelper.d("Params expirationPeriod = ${params.expirationPeriod}")
        LogHelper.d("Params garbagePeriod = ${params.garbagePeriod}")
        LogHelper.d("Params maxPickAttempts = ${params.maxPickAttempts}")
    }

    companion object {
        data class Params(
            val resendPeriod: Int = 2002, // period to waitForConnection before resend a command
            val resendLongPeriod: Int = 30002, // period to waitForConnection before resend a command, for message with long answer
            val expirationPeriod: Int = 60002, // period to waitForConnection before deleting unsent command (e.g. too many commands in pool)
            val garbagePeriod: Int = 25002, // period to waitForConnection before deleting sent command (before Ack or Error received)
            val maxPickAttempts: Int = 6,
        )
    }

    private inner class QueueElement(var message: CoAPMessage) {
        var sendAttempts = 0
        var sendTime: Long? = null
        var createTime: Long? = null
        var sent = false
        var isNeededSend = true

        init {
            createTime = TimeHelper.timeForMeasurementInMilliseconds
        }
    }

    fun requeue(id: Int) {
        val element = pool[id] ?: return
        element.sendAttempts = 0
        element.sent = false
        element.isNeededSend = true
        pool[id] = element
    }

    operator fun next(): CoAPMessage? {
        // Check if not empty
        if (pool.size == 0) {
            return null
        }
        val iterator: Iterator<QueueElement> = pool.values.iterator()
        while (iterator.hasNext()) {
            val next: QueueElement = try {
                iterator.next()
            } catch (e: ConcurrentModificationException) {
                LogHelper.e(e.message ?: "ConcurrentModificationException")
                continue
            }
            val now = TimeHelper.timeForMeasurementInMilliseconds

            // check if this message is too old to send
            if (next.createTime != null && now - next.createTime!!
                >= (if (next.isNeededSend) params.expirationPeriod else 10 * params.expirationPeriod)
            ) { //10 time longer expiration period for !isNeededSend message, for ARQ original messages
                LogHelper.v("Remove message with id " + next.message.id + " from pool because expired")
                remove(next.message)
                raiseAckError(next.message, "message expired")
                continue
            }

            // check if this message should be already removed from the pool, before ACK
            if (next.isNeededSend && next.sendTime != null
                && now - next.sendTime!! >= if (next.message.isRequestWithLongTimeNoAnswer) params.garbagePeriod * 5
                else params.garbagePeriod
            ) {
                LogHelper.v("Remove message with id " + next.message.id + " from pool because garbage")
                remove(next.message)
                raiseAckError(next.message, "message deleted by garbage")
                continue
            }
            if (next.isNeededSend) {
                if (!next.sent) {
                    if (next.sendAttempts >= params.maxPickAttempts) {
                        LogHelper.v("Remove message with id " + next.message.id + " from pool because too many attempts")
                        remove(next.message)
                        raiseAckError(next.message, "Request Canceled, too many attempts ")
                        continue
                    }

                    val hexToken = next.message.hexToken

                    var currentMessageDeliveryInfo = messageDeliveryInfo[hexToken]
                    if (currentMessageDeliveryInfo == null) {
                        currentMessageDeliveryInfo = MessageDeliveryInfo(0, 0, 0, next.message.address)
                    }
                    if (next.message.proxy != null) {
                        currentMessageDeliveryInfo.viaProxyAttempts += 1
                    } else {
                        currentMessageDeliveryInfo.directAttempts += 1
                    }
                    if (next.sendAttempts > 0) {
                        currentMessageDeliveryInfo.retransmitCount += 1
                    }
                    messageDeliveryInfo[hexToken] = currentMessageDeliveryInfo

                    next.sent = true
                    next.sendTime = TimeHelper.timeForMeasurementInMilliseconds
                    next.sendAttempts++
                    return CoAPMessage(next.message)
                } else {
                    // check if need to resend this message
                    if (
                        next.sendTime != null
                        && (now - next.sendTime!!
                                >= if (next.message.isRequestWithLongTimeNoAnswer) params.resendLongPeriod else params.resendPeriod)
                    ) {
                        next.message.resendHandler.onResend()
                        markAsUnsent(next.message.id) // Do we need a separate function for this?! O_o
                    }
                }
            }
        }
        return null
    }

    fun add(message: CoAPMessage) {
        val token = message.hexToken
        LogHelper.v("Add message with id " + message.id + " and token " + token + " to pool")
        pool[message.id] = QueueElement(message)
        messageIdForToken.putIfAbsent(token, message.id)
    }

    operator fun get(id: Int?): CoAPMessage? {
        val elem = pool[id] ?: return null
        return elem.message
    }

    fun getSourceMessageByToken(token: String): CoAPMessage? {
        val id = messageIdForToken[token]
        LogHelper.v("getSourceMessageByToken: $token, id: $id")
        return id?.let { get(it) }
    }

    fun remove(message: CoAPMessage?) {
        LogHelper.v("Remove message with id " + message!!.id + " from pool, retransmitCounter " + messageDeliveryInfo[message.hexToken])
        pool.remove(message.id)
        val idForToken = messageIdForToken[message.hexToken]
        if (idForToken != null && idForToken == message.id) {
            messageIdForToken.remove(message.hexToken)
        }
    }

    fun clear(exception: BaseCoalaThrowable) {
        CoroutineScope(IO).launch {
            LogHelper.d("Clear message pool, current pool size: ${pool.size}")
            for (queueElement in pool.values) {
                if (queueElement.message.responseHandler != null) {
                    queueElement.message.responseHandler?.onError(exception)
                }
            }
            pool.clear()
            messageIdForToken.clear()
        }
    }

    private fun markAsUnsent(id: Int) {
        val elem = pool[id]
        if (elem != null) {
            elem.sent = false
            pool[id] = elem
        }
    }

    /**
     * Triggers message's handler with Error
     */
    private fun raiseAckError(message: CoAPMessage?, error: String) {
        LogHelper.v("raiseAckError: ${message?.id}")

        message?.let {
            CoroutineScope(IO).launch {
                ackHandlersPool.raiseAckError(message, error)
                message.responseHandler?.onError(
                    AckError("raiseAckError").setMessageDeliveryInfo(
                        getMessageDeliveryInfo(message.hexToken)
                    )
                )
            }
        }
    }

    fun print() {
        LogHelper.w("Printing pool:")
        for (id in pool.keys) {
            val message = pool[id]!!.message
            LogHelper.w("Id: " + id + " " + (" type: " + message.type.name + " code: " + message.code.name + " path: " + message.getURIPathString() + " schema: " + message.getURIScheme()))
        }
    }

    fun setNoNeededSending(message: CoAPMessage) {
        val token = message.hexToken
        val id = messageIdForToken[token]
        if (id != null) {
            val element = pool[id]
            if (element != null) {
                element.isNeededSend = false
                pool[id] = element
            } else LogHelper.i("Try to setNoNeededSending, message not contains in pool, id: " + message.id)
        } else LogHelper.i("Try to setNoNeededSending, id not contains in pool, id: " + message.id)
    }

    fun getMessageDeliveryInfo(messageToken: String): MessageDeliveryInfo? {
        return messageDeliveryInfo[messageToken]
    }

    fun size(): Int {
        return pool.size
    }
}