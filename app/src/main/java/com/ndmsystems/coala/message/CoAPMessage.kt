package com.ndmsystems.coala.message

import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.MessageHelper.generateId
import com.ndmsystems.coala.helpers.StringHelper.join
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import com.ndmsystems.coala.layers.response.ResponseHandler
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.StringTokenizer

class CoAPMessage @JvmOverloads constructor(var type: CoAPMessageType, var code: CoAPMessageCode, var id: Int = generateId()) {
    lateinit var address: InetSocketAddress
    var proxy: InetSocketAddress? = null
        private set
    var payload: CoAPMessagePayload? = null
    private var options: MutableList<CoAPMessageOption> = ArrayList()
    var token: ByteArray? = null
    var responseHandler: ResponseHandler? = null
    var resendHandler: ResendHandler = object: ResendHandler {
        override fun onResend() {
            v("Resend message: $id")
        }
    }
    var peerPublicKey: ByteArray? = null
    var isRequestWithLongTimeNoAnswer = false

    constructor(message: CoAPMessage) : this(message.type, message.code, message.id) {
        if (message.payload != null) {
            payload = CoAPMessagePayload(message.payload!!.content)
        }
        options = ArrayList(message.getOptions())
        if (message.token != null) {
            token = ByteArray(message.token!!.size)
            System.arraycopy(message.token!!, 0, token, 0, token!!.size)
        }
        if (message.proxy != null) proxy = message.proxy
        address = message.address
        if (message.responseHandler != null) responseHandler = message.responseHandler
        resendHandler = message.resendHandler
        if (message.peerPublicKey != null) peerPublicKey = message.peerPublicKey
        isRequestWithLongTimeNoAnswer = message.isRequestWithLongTimeNoAnswer
    }

    val isRequest: Boolean
        get() = code.isRequest
    val hexToken: String
        get() = encodeHexString(token)

    fun setId(id: Int): CoAPMessage {
        this.id = id
        return this
    }

    fun setType(type: CoAPMessageType): CoAPMessage {
        this.type = type
        return this
    }

    fun setCode(code: CoAPMessageCode): CoAPMessage {
        this.code = code
        return this
    }

    val method: CoAPRequestMethod?
        get() = when (this.code) {
            CoAPMessageCode.GET -> CoAPRequestMethod.GET
            CoAPMessageCode.POST -> CoAPRequestMethod.POST
            CoAPMessageCode.PUT -> CoAPRequestMethod.PUT
            CoAPMessageCode.DELETE -> CoAPRequestMethod.DELETE
            else -> null
        }

    fun setOptions(options: List<CoAPMessageOption>) {
        this.options = ArrayList(options)
    }

    fun getOptions(): List<CoAPMessageOption> {
        return ArrayList(options)
    }

    fun hasOption(code: CoAPMessageOptionCode): Boolean {
        return getOption(code) != null
    }

    fun setStringPayload(payload: String): CoAPMessage {
        if (this.payload == null) {
            this.payload = CoAPMessagePayload(payload.toByteArray())
        } else {
            this.payload!!.content = payload.toByteArray()
        }
        return this
    }

    /**
     * Example: `[scheme]://[host]:[port]{/resource}*?{&query}*`
     *
     * @return CoAPMessage
     */
    fun setURI(uri: String): CoAPMessage {
        var uri = uri
        return try {
            if (!uri.startsWith(Scheme.NORMAL.toString() + "://") && !uri.startsWith(Scheme.SECURE.toString() + "://")) {
                uri = Scheme.NORMAL.toString() + "://" + uri
            }
            setURI(URI(uri))
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Failed to set uri " + uri + ": " + e.message)
        }
    }

    fun setURI(uri: URI): CoAPMessage {
        setURIScheme(Scheme.fromString(uri.scheme))
        removeOption(CoAPMessageOptionCode.OptionURIHost)
        removeOption(CoAPMessageOptionCode.OptionURIPort)
        val port = if (uri.port != -1) uri.port else Coala.DEFAULT_PORT
        address = InetSocketAddress(uri.host, port)
        uri.path?.let { setURIPath(it) }
        uri.query?.let { setURIQuery(it) }
        return this
    }

    fun getURI(): String {
        val builder = StringBuilder()
        val port: Int = if (address.port != -1) address.port else Coala.DEFAULT_PORT
        val host: String = if (address.address != null && address.address.hostAddress != null) address.address.hostAddress else {
            w("Address is null! return \"null\"")
            "null"
        }
        if (hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
            builder.append(getOption(CoAPMessageOptionCode.OptionProxyURI)!!.value as String?)
        } else {
            builder.append(getURIScheme().toString()).append("://").append(host).append(":").append(port)
        }
        val uriPath = getURIPathString()
        builder.append("/").append(uriPath)

        val queryOptions = this.getOptions(CoAPMessageOptionCode.OptionURIQuery)
        if (queryOptions.isNotEmpty()) {
            builder.append("?")
            for (parameter in queryOptions) {
                val parameterString = parameter.value as String?
                val index = parameterString!!.indexOf("=")
                val key = parameterString.substring(0, index)
                var value = parameterString.substring(index + 1)
                try {
                    value = URLEncoder.encode(value, "UTF-8").replace("[+]".toRegex(), "%20")
                } catch (ignore: UnsupportedEncodingException) {
                    e("Can't encode query parameter: $value")
                }
                builder.append(key).append("=").append(value).append("&")
            }
            builder.setLength(builder.length - 1)
        }
        return builder.toString()
    }

    fun getURIPathString(): String {
        val pathOptions = this.getOptions(CoAPMessageOptionCode.OptionURIPath)
        if (pathOptions.isEmpty()) {
            return ""
        }
        val pathParts: MutableList<String?> = java.util.ArrayList()
        for (pathElem in pathOptions) {
            pathParts.add(pathElem.value as String?)
        }
        return join(pathParts, "/")
    }

    fun getURIQuery(key: String): String {
        val queryOptions = this.getOptions(CoAPMessageOptionCode.OptionURIQuery)
        for (queryElem in queryOptions) {
            val parts = (queryElem.value as String).split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size == 2 && parts[0] == key) {
                return parts[1]
            }
        }
        return ""
    }

    fun addQueryParam(key: String, value: String?): CoAPMessage {
        addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIQuery, "$key=$value"))
        return this
    }

    fun addQueryParams(params: Map<String, String>): CoAPMessage {
        for (key in params.keys) addQueryParam(key, params[key])
        return this
    }

    private fun setURIQuery(query: String) {
        val st = StringTokenizer(query, "&")
        while (st.hasMoreTokens()) {
            val parts = st.nextToken().split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size < 2) e("Wrong parts") else addQueryParam(parts[0], parts[1])
        }
    }

    fun setURIScheme(scheme: Scheme): CoAPMessage {
        var option = getOption(CoAPMessageOptionCode.OptionURIScheme)
        if (option == null) {
            option = CoAPMessageOption(CoAPMessageOptionCode.OptionURIScheme, scheme.toInt())
        } else option.value = scheme.toInt()
        addOption(option)
        return this
    }

    fun getURIScheme(): Scheme {
        val option = getOption(CoAPMessageOptionCode.OptionURIScheme) ?: return Scheme.NORMAL
        return Scheme.fromInt(option.value as Int?)
    }

    fun setURIPath(path: String): CoAPMessage {
        removeOption(CoAPMessageOptionCode.OptionURIPath)
        val pathSegments = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pathSegment in pathSegments) if (pathSegment.isNotEmpty()) addOption(
            CoAPMessageOption(
                CoAPMessageOptionCode.OptionURIPath,
                pathSegment
            )
        )
        return this
    }

    /**
     * Returns the whole list of Options.
     * Used for repeatable options (such as URI-Query)
     *
     * @param optionCode
     * @return List<CoAPMessageOption>
    </CoAPMessageOption> */
    private fun getOptions(optionCode: CoAPMessageOptionCode): List<CoAPMessageOption> {
        val options: MutableList<CoAPMessageOption> = ArrayList()
        for (option in this.options) {
            if (option.code == optionCode) {
                options.add(option)
            }
        }
        return options
    }

    /**
     * Returns the first occurrence of the requested option.
     *
     * @param optionCode
     * @return
     */
    fun getOption(optionCode: CoAPMessageOptionCode): CoAPMessageOption? {
        for (option in options) {
            if (option.code == optionCode) {
                return option
            }
        }
        return null
    }

    fun setProxy(proxyAddress: InetSocketAddress) {
        proxy = proxyAddress
        val option = CoAPMessageOption(
            CoAPMessageOptionCode.OptionProxyURI,
            getURIScheme().toString() + "://" + address.address.hostAddress + ":" + address.port
        )
        addOption(option)
    }

    fun removeOption(optionCode: CoAPMessageOptionCode) {
        val iterator: MutableIterator<CoAPMessageOption> = options.listIterator()
        while (iterator.hasNext()) {
            val option = iterator.next()
            if (option.code == optionCode) iterator.remove()
        }
    }

    fun addOption(option: CoAPMessageOption): CoAPMessage {
        if (!option.isRepeatable) removeOption(option.code)
        options.add(option)
        return this
    }

    override fun toString(): String {
        return if (payload == null) {
            ""
        } else payload.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val message = other as CoAPMessage
        return id == message.id
    }

    override fun hashCode(): Int {
        return id
    }

    fun getProxySecurityId(): Long? {
        val option = getOption(CoAPMessageOptionCode.OptionProxySecurityID) ?: return null
        return option.value as Long?
    }

    fun setProxySecurityId(proxyId: Long): CoAPMessage {
        var option = getOption(CoAPMessageOptionCode.OptionProxySecurityID)
        if (option == null) {
            option = CoAPMessageOption(CoAPMessageOptionCode.OptionProxySecurityID, proxyId)
        }
        option.value = proxyId
        addOption(option)
        return this
    }

    enum class MediaType(private val value: Int) {
        TextPlain(0), LinkFormat(40), Xml(41), OctetStream(42), Exi(47), Json(50);

        fun toInt(): Int {
            return value
        }

        companion object {
        }
    }

    enum class Scheme {
        NORMAL, SECURE;

        fun toInt(): Int {
            return if (this == SECURE) {
                1
            } else 0
        }

        override fun toString(): String {
            return if (this == SECURE) {
                "coaps"
            } else "coap"
        }

        companion object {
            fun fromInt(value: Int?): Scheme {
                return if (value == 1) {
                    SECURE
                } else NORMAL
            }

            fun fromString(value: String?): Scheme {
                return when (value) {
                    "coaps", "1" -> SECURE
                    else -> NORMAL
                }
            }
        }
    }

    interface ResendHandler {
        fun onResend()
    }

    companion object {
        fun convertToEmptyAck(
            message: CoAPMessage,
            from: InetSocketAddress
        ) {
            message.setType(CoAPMessageType.ACK)
            message.setCode(CoAPMessageCode.CoapCodeEmpty)
            message.address = from
            message.payload = null
        }

        fun ackTo(
            message: CoAPMessage,
            from: InetSocketAddress,
            code: CoAPMessageCode
        ): CoAPMessage {
            val result = CoAPMessage(CoAPMessageType.ACK, code)
            result.setId(message.id)
            result.token = message.token
            result.setURIScheme(message.getURIScheme())
            result.address = from
            val option = message.getOption(CoAPMessageOptionCode.OptionObserve)
            if (option != null) result.addOption(option)
            val proxySecurityId = message.getOption(CoAPMessageOptionCode.OptionProxySecurityID)
            if (proxySecurityId != null) result.addOption(proxySecurityId)
            return result
        }

        fun resetTo(message: CoAPMessage, from: InetSocketAddress): CoAPMessage {
            val result = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty)
            result.setId(message.id)
            result.token = message.token
            result.setURIScheme(message.getURIScheme())
            result.address = from

            return result
        }
    }
}