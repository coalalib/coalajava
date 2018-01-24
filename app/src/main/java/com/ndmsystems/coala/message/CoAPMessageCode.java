package com.ndmsystems.coala.message;

/**
 * Created by Владимир on 26.06.2017.
 */

/**
 * The enumeration of request codes: GET, POST, PUT and DELETE.
 */
public enum CoAPMessageCode {

    GET(1),
    POST(2),
    PUT(3),
    DELETE(4),

    CoapCodeEmpty(0),
    CoapCodeCreated(65),
    CoapCodeDeleted(66),
    CoapCodeValid(67),
    CoapCodeChanged(68),
    CoapCodeContent(69),
    CoapCodeContinue(95),
    CoapCodeBadRequest(128),
    CoapCodeUnauthorized(129),
    CoapCodeBadOption(130),
    CoapCodeForbidden(131),
    CoapCodeNotFound(132),
    CoapCodeMethodNotAllowed(133),
    CoapCodeNotAcceptable(134),
    CoapCodeConflict(137),
    CoapCodePreconditionFailed(140),
    CoapCodeRequestEntityTooLarge(141),
    CoapCodeUnsupportedContentFormat(143),
    CoapCodeInternalServerError(160),
    CoapCodeNotImplemented(161),
    CoapCodeBadGateway(162),
    CoapCodeServiceUnavailable(163),
    CoapCodeGatewayTimeout(164),
    CoapCodeProxyingNotSupported(165);

    public final int value;

    /**
     * Instantiates a new code with the specified code value.
     *
     * @param value the integer value of the code
     */
    private CoAPMessageCode(final int value) {
        this.value = value;
    }

    /**
     * Converts the specified integer value to a request code.
     *
     * @param value the integer value
     * @return the request code
     * @throws IllegalArgumentException if the integer value does not represent a valid request code.
     */
    public static CoAPMessageCode valueOf(final int value) {
            /*int classCode  = getCodeClass(value);
            int detailCode = getCodeDetail(value);
            if (classCode > 0) {
                return null;
            }*/
        switch (value) {
            case 1:
                return GET;
            case 2:
                return POST;
            case 3:
                return PUT;
            case 4:
                return DELETE;
            case 0:
                return CoapCodeEmpty;
            case 65:
                return CoapCodeCreated;
            case 66:
                return CoapCodeDeleted;
            case 67:
                return CoapCodeValid;
            case 68:
                return CoapCodeChanged;
            case 69:
                return CoapCodeContent;
            case 95:
                return CoapCodeContinue;
            case 128:
                return CoapCodeBadRequest;
            case 129:
                return CoapCodeUnauthorized;
            case 130:
                return CoapCodeBadOption;
            case 131:
                return CoapCodeForbidden;
            case 132:
                return CoapCodeNotFound;
            case 133:
                return CoapCodeMethodNotAllowed;
            case 134:
                return CoapCodeNotAcceptable;
            case 137:
                return CoapCodeConflict;
            case 140:
                return CoapCodePreconditionFailed;
            case 141:
                return CoapCodeRequestEntityTooLarge;
            case 143:
                return CoapCodeUnsupportedContentFormat;
            case 160:
                return CoapCodeInternalServerError;
            case 161:
                return CoapCodeNotImplemented;
            case 162:
                return CoapCodeBadGateway;
            case 163:
                return CoapCodeServiceUnavailable;
            case 164:
                return CoapCodeGatewayTimeout;
            case 165:
                return CoapCodeProxyingNotSupported;
            default:
                throw new IllegalArgumentException("Unknown CoAP code " + value);
        }
    }

    public int getCodeClass() {
        return (value & 11100000) >> 5;
    }

    public int getCodeDetail() {
        return value & 11111;
    }

    @Override
    public String toString() {
        return String.format("%d.%02d", getCodeClass(), getCodeDetail());
    }

    public boolean isRequest() {
        switch (this) {
            case GET:
            case POST:
            case PUT:
            case DELETE:
                return true;
            default:
                return false;
        }
    }
}
