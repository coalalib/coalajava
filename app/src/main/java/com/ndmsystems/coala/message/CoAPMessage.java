package com.ndmsystems.coala.message;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.MessageHelper;
import com.ndmsystems.coala.helpers.StringHelper;
import com.ndmsystems.coala.layers.response.ResponseHandler;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class CoAPMessage {

    public static void convertToEmptyAck(CoAPMessage message,
                                         InetSocketAddress from) {
        message.setType(CoAPMessageType.ACK);
        message.setCode(CoAPMessageCode.CoapCodeEmpty);
        message.setAddress(from);
        message.setPayload(null);
    }

    public static CoAPMessage ackTo(CoAPMessage message,
                                    InetSocketAddress from,
                                    CoAPMessageCode code) {
        CoAPMessage result = new CoAPMessage(CoAPMessageType.ACK, code);
        result.setId(message.getId());
        result.setToken(message.getToken());
        result.setURIScheme(message.getURIScheme());
        result.setAddress(from);
        CoAPMessageOption option = message.getOption(CoAPMessageOptionCode.OptionObserve);
        if (option != null)
            result.addOption(option);
        return result;
    }

    public static CoAPMessage resetTo(CoAPMessage message, InetSocketAddress from) {
        CoAPMessage result = new CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty);
        result.setId(message.getId());
        result.setToken(message.getToken());
        result.setURIScheme(message.getURIScheme());
        result.setAddress(from);
        return result;
    }


    private InetSocketAddress destination;

    private int id;
    private CoAPMessageType type;
    private CoAPMessageCode code;
    private CoAPMessagePayload payload;
    private List<CoAPMessageOption> options = new ArrayList<>();
    private byte[] token = null;
    private InetSocketAddress proxy;
    private ResponseHandler responseHandler;
    private ResendHandler resendHandler = () -> LogHelper.v("Resend message: " + id);
    private byte[] peerPublicKey = null;

    public CoAPMessage(CoAPMessage message) {
        this(message.getType(), message.getCode(), message.getId());
        if (message.getPayload() != null) {
            payload = new CoAPMessagePayload(message.toString());
        }
        options = new ArrayList<>(message.getOptions());
        if (message.getToken() != null) {
            token = new byte[message.getToken().length];
            System.arraycopy(message.getToken(), 0, token, 0, token.length);
        }

        if (message.getProxy() != null) this.proxy = message.getProxy();
        if (message.destination != null) this.destination = message.destination;
        if (message.responseHandler != null) this.responseHandler = message.responseHandler;
        if (message.resendHandler != null) this.resendHandler = message.resendHandler;
        if (message.peerPublicKey != null) this.peerPublicKey = message.peerPublicKey;
    }

    public CoAPMessage(CoAPMessageType messageType, CoAPMessageCode messageCode) {
        this(messageType, messageCode, MessageHelper.generateId());
    }

    public CoAPMessage(CoAPMessageType messageType, CoAPMessageCode messageCode, int id) {
        this.type = messageType;
        this.code = messageCode;
        this.id = id;
    }

    public ResponseHandler getResponseHandler() {
        return responseHandler;
    }

    public void setResponseHandler(ResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }

    public boolean isRequest() {
        return code.isRequest();
    }

    public int getId() {
        return id;
    }

    public CoAPMessageType getType() {
        return type;
    }

    public CoAPMessageCode getCode() {
        return code;
    }

    public byte[] getToken() {
        return token;
    }

    public String getHexToken() {
        return Hex.encodeHexString(token);
    }


    public CoAPMessage setId(int id) {
        this.id = id;
        return this;
    }

    public CoAPMessage setType(CoAPMessageType type) {
        this.type = type;
        return this;
    }

    public CoAPMessage setCode(CoAPMessageCode code) {
        this.code = code;
        return this;
    }

    public CoAPMessage setToken(byte[] token) {
        this.token = token;
        return this;
    }

    public CoAPRequestMethod getMethod() {
        switch (this.code) {
            case GET:
                return CoAPRequestMethod.GET;
            case POST:
                return CoAPRequestMethod.POST;
            case PUT:
                return CoAPRequestMethod.PUT;
            case DELETE:
                return CoAPRequestMethod.DELETE;

            default:
                return null;

        }
    }

    public void setOptions(List<CoAPMessageOption> options) {
        this.options = new ArrayList<>(options);
    }

    public List<CoAPMessageOption> getOptions() {
        return new ArrayList<>(options);
    }

    public boolean hasOption(CoAPMessageOptionCode code) {
        return getOption(code) != null;
    }

    public CoAPMessagePayload getPayload() {
        return payload;
    }

    public CoAPMessage setPayload(CoAPMessagePayload payload) {
        this.payload = payload;
        return this;
    }

    public CoAPMessage setStringPayload(String payload) {
        if (this.payload == null) {
            this.payload = new CoAPMessagePayload(payload.getBytes());
        } else {
            this.payload.content = payload.getBytes();
        }
        return this;
    }

    public void setDestination(InetSocketAddress address) {
        this.destination = address;
    }

    public InetSocketAddress getDestination() {
        if (destination != null) return destination;
        return getAddress();
    }

    /**
     * Example: <code>[scheme]://[host]:[port]{/resource}*?{&amp;query}*</code>
     *
     * @return CoAPMessage
     * @throws IllegalArgumentException
     */
    public CoAPMessage setURI(String uri) {
        try {
            if (!uri.startsWith(Scheme.NORMAL.toString() + "://") && !uri.startsWith(Scheme.SECURE.toString() + "://")) {
                uri = Scheme.NORMAL.toString() + "://" + uri;
            }
            return setURI(new URI(uri));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Failed to set uri " + uri + ": " + e.getMessage());
        }
    }

    public CoAPMessage setURI(URI uri) {
        setURIScheme(Scheme.fromString(uri.getScheme()));

        setURIHost(uri.getHost());

        setURIPort(uri.getPort());

        setURIPath(uri.getPath());

        setURIQuery(uri.getQuery());

        return this;
    }

    public String getURI() {
        StringBuilder builder = new StringBuilder();

        if (hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
            builder.append((String) getOption(CoAPMessageOptionCode.OptionProxyURI).value);
        } else {
            builder.append(getURIScheme().toString()).append("://")
                    .append(getURIHost()).append(":").append(getURIPort());
        }

        String uriPath = getURIPathString();
        if (uriPath != null) {
            builder.append("/" + uriPath);
        }

        List<CoAPMessageOption> queryOptions = this.getOptions(CoAPMessageOptionCode.OptionURIQuery);
        if (queryOptions.size() > 0) {
            builder.append("?");
            for (CoAPMessageOption parameter : queryOptions) {
                String parameterString = (String) parameter.value;
                int index = parameterString.indexOf("=");
                String key = parameterString.substring(0, index);
                String value = parameterString.substring(index + 1);
                try {
                    value = URLEncoder.encode(value, "UTF-8").replaceAll("[+]", "%20");
                } catch (UnsupportedEncodingException ignore) {
                    LogHelper.e("Can't encode query parameter: " + value);
                }
                builder.append(key + "=" + value + "&");
            }
            builder.setLength(builder.length() - 1);
        }

        return builder.toString();
    }

    public String getURIHost() {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIHost);

        if (option == null) {
            if (destination != null) {
                return destination.getAddress().getHostAddress();
            }
            return "localhost";
        }

        return (String) option.value;
    }

    public CoAPMessage setURIHost(String host) {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIHost);

        if (option == null) {
            option = new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, host);
        } else option.value = host;

        addOption(option);
        return this;
    }

    public Integer getURIPort() {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIPort);

        if (option == null) {
            if (destination != null) {
                return destination.getPort();
            }
            return 5683;
        }

        return (Integer) option.value;
    }

    public CoAPMessage setURIPort(int port) {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIPort);

        if (option == null) {
            option = new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, port);
        }
        option.value = port;

        addOption(option);
        return this;
    }

    public String getURIPathString() {
        List<CoAPMessageOption> pathOptions = this.getOptions(CoAPMessageOptionCode.OptionURIPath);

        if (pathOptions.size() == 0) {
            return null;
        }

        List<String> pathParts = new ArrayList<String>();
        for (CoAPMessageOption pathElem : pathOptions) {
            pathParts.add((String) pathElem.value);
        }

        return StringHelper.join(pathParts, "/");
    }

    public String getURIQueryString() {
        List<CoAPMessageOption> queryOptions = this.getOptions(CoAPMessageOptionCode.OptionURIQuery);

        if (queryOptions.size() == 0) {
            return null;
        }

        List<String> queryParts = new ArrayList<String>();

        for (CoAPMessageOption queryElem : queryOptions) {
            queryParts.add((String) queryElem.value);
        }

        return StringHelper.join(queryParts, "&");
    }

    public String getURIQuery(String key) {
        List<CoAPMessageOption> queryOptions = this.getOptions(CoAPMessageOptionCode.OptionURIQuery);

        for (CoAPMessageOption queryElem : queryOptions) {
            String[] parts = ((String) queryElem.value).split("=");
            if (parts.length == 2 && parts[0].equals(key)) {
                return parts[1];
            }
        }

        return "";
    }

    public CoAPMessage addQueryParam(String key, String value) {
        addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIQuery, key + "=" + value));
        return this;
    }

    public CoAPMessage addQueryParams(Map<String, String> params) {
        if (params != null)
            for (String key : params.keySet())
                addQueryParam(key, params.get(key));
        return this;
    }

    public void setURIQuery(String query) {
        if (query != null) {
            StringTokenizer st = new StringTokenizer(query, "&");
            while (st.hasMoreTokens()) {
                String[] parts = st.nextToken().split("=");
                if (parts.length < 2)
                    LogHelper.e("Wrong parts");
                else
                    addQueryParam(parts[0], parts[1]);
            }
        }
    }

    public CoAPMessage setURIScheme(Scheme scheme) {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIScheme);

        if (option == null) {
            option = new CoAPMessageOption(CoAPMessageOptionCode.OptionURIScheme, scheme.toInt());
        } else option.value = scheme.toInt();

        addOption(option);
        return this;
    }

    public Scheme getURIScheme() {
        CoAPMessageOption option = this.getOption(CoAPMessageOptionCode.OptionURIScheme);

        if (option == null) {
            return Scheme.NORMAL;
        }

        return Scheme.fromInt((Integer) option.value);
    }

    public CoAPMessage setURIPath(String path) {
        removeOption(CoAPMessageOptionCode.OptionURIPath);
        if (path != null) {
            String[] pathSegments = path.split("/");
            for (String pathSegment : pathSegments)
                if (!pathSegment.isEmpty())
                    addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, pathSegment));
        }
        return this;
    }

    /**
     * Returns the whole list of Options.
     * Used for repeatable options (such as URI-Query)
     *
     * @param optionCode
     * @return List<CoAPMessageOption>
     */
    public List<CoAPMessageOption> getOptions(CoAPMessageOptionCode optionCode) {
        List<CoAPMessageOption> options = new ArrayList<CoAPMessageOption>();

        for (CoAPMessageOption option : this.options) {
            if (option.code == optionCode) {
                options.add(option);
            }
        }

        return options;
    }

    /**
     * Returns the first occurrence of the requested option.
     *
     * @param optionCode
     * @return
     */
    public CoAPMessageOption getOption(CoAPMessageOptionCode optionCode) {
        for (CoAPMessageOption option : this.options) {
            if (option.code == optionCode) {
                return option;
            }
        }

        return null;
    }

    public InetSocketAddress getProxy() {
        return proxy;
    }

    public void setProxy(InetSocketAddress address) {
        if (address == null) return;
        proxy = address;
        CoAPMessageOption option = new CoAPMessageOption(CoAPMessageOptionCode.OptionProxyURI, getURIScheme().toString() +"://" + getURIHost() + ":" + getURIPort().toString());
        addOption(option);
    }

    public void removeOption(CoAPMessageOptionCode optionCode) {
        Iterator<CoAPMessageOption> iterator = options.listIterator();
        while (iterator.hasNext()) {
            CoAPMessageOption option = iterator.next();
            if (option.code == optionCode)
                iterator.remove();
        }
    }

    public CoAPMessage addOption(CoAPMessageOption option) {
        if (option == null)
            return this;

        if (!option.isRepeatable())
            removeOption(option.code);

        options.add(option);
        return this;
    }

    public String toString() {
        if (this.payload == null) {
            return "";
        }
        return this.payload.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoAPMessage message = (CoAPMessage) o;
        return id == message.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void setAddress(InetSocketAddress address) {
        if (address.getAddress() != null
                && address.getAddress().getHostAddress() != null) {
            setURIHost(address.getAddress().getHostAddress());
        } else {
            setURIHost(address.getHostName());
        }
        setURIPort(address.getPort());
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(getURIHost(), getURIPort());
    }

    public void setResendHandler(ResendHandler resendHandler) {
        this.resendHandler = resendHandler;
    }

    public ResendHandler getResendHandler() {
        return resendHandler;
    }

    public byte[] getPeerPublicKey() {
        return peerPublicKey;
    }

    public void setPeerPublicKey(byte[] peerPublicKey) {
        this.peerPublicKey = peerPublicKey;
    }

    public enum MediaType {
        TextPlain(0),
        LinkFormat(40),
        Xml(41),
        OctetStream(42),
        Exi(47),
        Json(50);

        private final int value;

        MediaType(int value) {
            this.value = value;
        }

        public static MediaType fromInt(Integer value) {
            switch (value) {
                case 0:
                    return TextPlain;
                case 40:
                    return LinkFormat;
                case 41:
                    return Xml;
                case 42:
                    return OctetStream;
                case 47:
                    return Exi;
                case 50:
                    return Json;
                default:
                    LogHelper.e("Unknown MediaType");
                    return null;
            }
        }

        public int toInt() {
            return value;
        }
    }

    public enum Scheme {
        NORMAL, SECURE;

        public Integer toInt() {
            switch (this) {
                case SECURE:
                    return 1;
                default:
                    return 0;
            }
        }

        public String toString() {
            switch (this) {
                case SECURE:
                    return "coaps";
                default:
                    return "coap";
            }
        }

        public static Scheme fromInt(Integer value) {
            switch (value) {
                case 1:
                    return SECURE;
                default:
                    return NORMAL;
            }
        }

        public static Scheme fromString(String value) {
            switch (value) {
                case "coaps":
                case "1":
                    return SECURE;
                default:
                    return NORMAL;
            }
        }
    }

    public static interface ResendHandler {
        public void onResend();
    }
}
