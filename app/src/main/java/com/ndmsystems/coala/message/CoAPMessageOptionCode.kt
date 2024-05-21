package com.ndmsystems.coala.message

import com.ndmsystems.coala.helpers.logging.LogHelper.e

/**
 * Created by Владимир on 26.06.2017.
 */
enum class CoAPMessageOptionCode
/**
 * Instantiates a new type with the specified integer value.
 *
 * @param value the integer value
 */(
    /**
     * The integer value of a message type.
     */
    val value: Int
) {
    OptionIfMatch(1), OptionURIHost(3), OptionEtag(4), OptionIfNoneMatch(5), OptionObserve(6), OptionURIPort(7), OptionLocationPath(8), OptionURIPath(
        11
    ),
    OptionContentFormat(12), OptionMaxAge(14), OptionURIQuery(15), OptionAccept(17), OptionLocationQuery(20), OptionBlock2(23), OptionBlock1(27), OptionSize2(
        28
    ),
    OptionProxyURI(35), OptionProxyScheme(39), OptionSize1(60), OptionURIScheme(2111), OptionSelectiveRepeatWindowSize(3001), OptionWindowChangeable(3002), OptionProxySecurityID(
        3004
    ),
    OptionCookie(3036), OptionHandshakeType(3999), OptionSessionNotFound(4001), OptionSessionExpired(4003), OptionCoapsURI(4005), DefaultOption(999999);

    //For errors
    companion object {
        @JvmStatic
        fun valueOf(value: Int): CoAPMessageOptionCode {
            return when (value) {
                1 -> OptionIfMatch
                3 -> OptionURIHost
                4 -> OptionEtag
                5 -> OptionIfNoneMatch
                6 -> OptionObserve
                7 -> OptionURIPort
                8 -> OptionLocationPath
                11 -> OptionURIPath
                12 -> OptionContentFormat
                14 -> OptionMaxAge
                15 -> OptionURIQuery
                17 -> OptionAccept
                20 -> OptionLocationQuery
                23 -> OptionBlock2
                27 -> OptionBlock1
                28 -> OptionSize2
                35 -> OptionProxyURI
                39 -> OptionProxyScheme
                60 -> OptionSize1
                2111 -> OptionURIScheme
                3001 -> OptionSelectiveRepeatWindowSize
                3002 -> OptionWindowChangeable //Dont used in apps, but sending by routers, add for now parsing warnings https://ndm.atlassian.net/browse/NDM-3098?focusedCommentId=38983&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-38983
                3004 -> OptionProxySecurityID
                3036 -> OptionCookie
                3999 -> OptionHandshakeType
                4001 -> OptionSessionNotFound
                4003 -> OptionSessionExpired
                4005 -> OptionCoapsURI
                else -> {
                    e("Unknown CoAP Option Code $value")
                    DefaultOption
                }
            }
        }
    }
}