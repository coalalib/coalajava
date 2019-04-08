package com.ndmsystems.coala.message;

import com.ndmsystems.infrastructure.logging.LogHelper;

/**
 * Created by Владимир on 26.06.2017.
 */

public enum CoAPMessageOptionCode {
    OptionIfMatch(1),
    OptionURIHost(3),
    OptionEtag(4),
    OptionIfNoneMatch(5),
    OptionObserve(6),
    OptionURIPort(7),
    OptionLocationPath(8),
    OptionURIPath(11),
    OptionContentFormat(12),
    OptionMaxAge(14),
    OptionURIQuery(15),
    OptionAccept(17),
    OptionLocationQuery(20),
    OptionBlock2(23),
    OptionBlock1(27),
    OptionSize2(28),
    OptionProxyURI(35),
    OptionProxyScheme(39),
    OptionSize1(60),
    OptionURIScheme(2111),
    //OptionURIScheme(71),
    OptionSelectiveRepeatWindowSize(3001),
    OptionProxySecurityID(3004),
    OptionHandshakeType(3999),
    OptionSessionNotFound(4001),
    OptionSessionExpired(4003),
    OptionCoapsURI(4005),
    DefaultOption(999999);//For errors
    //OptionHandshakeType(81);

    /**
     * The integer value of a message type.
     */
    public final int value;

    /**
     * Instantiates a new type with the specified integer value.
     *
     * @param value the integer value
     */
    CoAPMessageOptionCode(int value) {
        this.value = value;
    }

    public static CoAPMessageOptionCode valueOf(final int value) {
        switch (value) {
            case 1:
                return OptionIfMatch;
            case 3:
                return OptionURIHost;
            case 4:
                return OptionEtag;
            case 5:
                return OptionIfNoneMatch;
            case 6:
                return OptionObserve;
            case 7:
                return OptionURIPort;
            case 8:
                return OptionLocationPath;
            case 11:
                return OptionURIPath;
            case 12:
                return OptionContentFormat;
            case 14:
                return OptionMaxAge;
            case 15:
                return OptionURIQuery;
            case 17:
                return OptionAccept;
            case 20:
                return OptionLocationQuery;
            case 23:
                return OptionBlock2;
            case 27:
                return OptionBlock1;
            case 28:
                return OptionSize2;
            case 35:
                return OptionProxyURI;
            case 39:
                return OptionProxyScheme;
            case 60:
                return OptionSize1;
            case 2111:
                return OptionURIScheme;
            //case 71: return OptionURIScheme;OptionSelectiveRepeatWindowSize
            case 3001:
                return OptionSelectiveRepeatWindowSize;
            case 3004:
                return OptionProxySecurityID;
            case 3999:
                return OptionHandshakeType;
            case 4001:
                return OptionSessionNotFound;
            case 4003:
                return OptionSessionExpired;
            case 4005:
                return OptionCoapsURI;
            //case 81: return OptionHandshakeType;
            default:
                LogHelper.e("Unknown CoAP Option Code " + value);
                return DefaultOption;
        }
    }
}