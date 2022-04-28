package com.ndmsystems.coala;

import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

//TODO стоит разделить класс на два - ReceiveLayerStack и SendLayerStack т.к. их функции не пересекаются (см. следующую тудушку)
public class LayersStack {

    private SendLayer[] sendStack;
    private ReceiveLayer[] receiveStack;

    public class InterruptedException extends Exception {
    }

    public LayersStack(SendLayer[] sendStack,
                       ReceiveLayer[] receiveStack){
        this.sendStack = sendStack;
        this.receiveStack = receiveStack;
    }

    //TODO методы onReceive и onSend никак не связаны, зачем они находятся в одном классе? Основной критерий объединения методов в классы - их сильная связность. Но здесь связность нулевая.
    public void onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) throws LayersStack.InterruptedException {
        for (ReceiveLayer layer : receiveStack) {
            boolean shouldContinue = layer.onReceive(message, senderAddressReference);

            if (!shouldContinue) {
                break;
            }
        }
    }

    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) throws LayersStack.InterruptedException {
        for (SendLayer layer : sendStack) {
            boolean shouldContinue = layer.onSend(message, receiverAddressReference);

            if (!shouldContinue) {
                return false;
            }
        }

        return true;
    }
}
