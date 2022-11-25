package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.helpers.TimeHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by bas on 25.08.17.
 */

public abstract class LoggableState implements CoAPMessage.ResendHandler  {
    private final Long startTime;
    private Integer diffTime = null;
    private Integer numberOfMessages;
    private Integer numberOfResend;
    LoggableState() {
        startTime = TimeHelper.getTimeForMeasurementInMilliseconds();
        numberOfMessages = 0;
        numberOfResend = 0;
    }

    void onTransferCompleted() {
        LogHelper.d("onTransferCompleted");
        if (diffTime == null) diffTime = (int) (TimeHelper.getTimeForMeasurementInMilliseconds() - startTime);
    }

    public abstract int getDataSize();

    public abstract byte[] getToken();

    public long getSpeed() {
        if (diffTime == null) return -1;
        return (long) (((double)getDataSize()) / (diffTime / 1000.0));
    };

    public Double getPercentOfLoss() {
        if (numberOfMessages == 0) return null;
        return ((double) numberOfResend * 100) / (numberOfResend + numberOfMessages);
    }

    public Integer getDiffTime() {
        return diffTime;
    }

    public abstract boolean isIncoming();


    public Integer getNumberOfResend() {
        return numberOfResend;
    }

    @Override
    public void onResend() {
        numberOfResend++;
    }

    public void incrementNumberOfMessage() {
        numberOfMessages++;
    }
}
