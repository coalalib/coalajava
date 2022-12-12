package com.ndmsystems.coala.message;


import com.ndmsystems.infrastructure.logging.LogHelper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.ndmsystems.coala.message.CoAPMessageOptionCode.OptionProxySecurityID;

public class CoAPMessageOption implements Comparable<CoAPMessageOption> {
    public final CoAPMessageOptionCode code;
    public Object value;

    public CoAPMessageOption(CoAPMessageOptionCode code, Object value) {
        this.code = code;
        this.value = value;
    }

    public CoAPMessageOption(CoAPMessageOptionCode code, byte[] value) {
        this.code = code;
        this.fromBytes(value);
    }

    public boolean isRepeatable() {
        switch (this.code) {
            case OptionURIPath:
            case OptionURIQuery:
            case OptionLocationPath:
            case OptionLocationQuery:
            case OptionIfMatch:
            case OptionEtag:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int compareTo(CoAPMessageOption option) {
        return Integer.compare(code.value, option.code.value);
    }

    public void fromBytes(byte[] data) {
        switch (this.code) {
            // int only
            case OptionBlock1:
            case OptionBlock2:
            case OptionURIPort:
            case OptionContentFormat:
            case OptionMaxAge:
            case OptionAccept:
            case OptionSize1:
            case OptionSize2:
            case OptionHandshakeType:
            case OptionObserve:
            case OptionSessionNotFound:
            case OptionSessionExpired:
            case OptionSelectiveRepeatWindowSize:
            case OptionURIScheme:
                if (data.length > 4) this.value = ByteBuffer.wrap(data).getInt();
                else {
                    byte[] bigData = new byte[4];
                    for (int i = 0; i < data.length; i++)
                        bigData[3 - data.length + i + 1] = data[i];
                    this.value = ByteBuffer.wrap(bigData).getInt();
                }
                break;
            case OptionProxySecurityID:
                if (data.length > 4) this.value = ByteBuffer.wrap(data).getLong();
                else {
                    ByteBuffer buffer = ByteBuffer.allocate(8).put(new byte[]{0, 0, 0, 0}).put(data);
                    buffer.position(0);
                    this.value = buffer.getLong();
                }
                break;
            // string values
            case OptionURIHost:
            case OptionEtag:
            case OptionLocationPath:
            case OptionURIPath:
            case OptionURIQuery:
            case OptionLocationQuery:
            case OptionProxyScheme:
            case OptionProxyURI:
                this.value = new String(data);
                break;
            case OptionCookie:
            case OptionCoapsURI:
                this.value = data;
                break;

            default:
                LogHelper.e("Try from byte unknown option: " + this.code);
                this.value = data;
                // @TODO: Error handling here
                break;
        }
    }

    public int getMaxSizeInBytes() {
        switch (this.code) {
            case OptionBlock1:
            case OptionBlock2:
                return 3;
            case OptionURIScheme:
                return 1;
            default:
                return Integer.MAX_VALUE;
        }
    }

    public byte[] toBytes() {
        if (value != null) {

            //is it long
            if (code == OptionProxySecurityID)
                try {
                    byte[] bytes = new byte[8];
                    ByteBuffer.wrap(bytes).putLong((long) value);
                    return Arrays.copyOfRange(bytes, 4, 8);
                } catch (ClassCastException e) {
                }

            // Is it Integer?
            // @hbz
            try {
                return toByteArray((int) value);
            } catch (ClassCastException e) {
            }

            // Is it String?
            try {
                String stringValue = (String) value;
                return stringValue.getBytes(StandardCharsets.UTF_8);
            } catch (ClassCastException e) {
            }


            try {
                return (byte[])value;
            } catch (ClassCastException e) {
            }
        }

        // can't recognize the value type...
        return new byte[0];
    }

    private byte[] toByteArray(int number) {
        if (number >>> 24 != 0)
            return new byte[]{(byte) (number >>> 24), (byte) (number >>> 16), (byte) (number >>> 8), (byte) number};
        if (number >>> 16 != 0)
            return new byte[]{(byte) (number >>> 16), (byte) (number >>> 8), (byte) number};
        if (number >>> 8 != 0)
            return new byte[]{(byte) (number >>> 8), (byte) number};
        return new byte[]{(byte) number};
    }
}
