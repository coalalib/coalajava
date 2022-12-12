package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * CoAP message starts with a fixed-size 4-byte header.
 * <p>
 * This is followed by a variable-length Token value, which can be between 0 and 8 bytes long.
 * <p>
 * Following the Token value comes a sequence of zero or more CoAP
 * Options in Type-Length-Value (TLV) format, optionally followed by a
 * payload that takes up the rest of the datagram.
 * <p>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Ver| T |  TKL  |      Code     |          Message ID           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Token (if any, TKL bytes) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Options (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1 1 1 1 1 1 1 1|    Payload (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * The fields in the header are defined as follows:
 * <p>
 * Version (Ver):  2-bit unsigned integer.  Indicates the CoAP version
 * number.  Implementations of this specification MUST set this field
 * to 1 (01 binary).  Other values are reserved for future versions.
 * Messages with unknown version numbers MUST be silently ignored.
 * <p>
 * Type (T):  2-bit unsigned integer.  Indicates if this message is of
 * type Confirmable (0), Non-confirmable (1), Acknowledgement (2), or
 * Reset (3).
 * <p>
 * Token Length (TKL):  4-bit unsigned integer.  Indicates the length of
 * the variable-length Token field (0-8 bytes).  Lengths 9-15 are
 * reserved, MUST NOT be sent, and MUST be processed as a message
 * format error.
 * <p>
 * Code:  8-bit unsigned integer, split into a 3-bit class (most
 * significant bits) and a 5-bit detail (least significant bits),
 * documented as "c.dd" where "c" is a digit from 0 to 7 for the
 * 3-bit subfield and "dd" are two digits from 00 to 31 for the 5-bit
 * subfield.
 */

public class CoAPSerializer {

    private static final int COAP_PAYLOAD_MARKER = 0xff;
    private static final int COAP_HEADER_SIZE = 4;
    private static final int COAP_PROTOCOL_VERSION = 1;
    private static final int MAX_OPTION_DELTA = 65804;
    private static final int MAX_OPTION_LENGTH = 65804;
    private static final int MAX_TOKEN_LENGTH = 8;

    /**
     * @param data
     * @return
     * @TODO: optimize memory consumption
     */
    public static CoAPMessage fromBytes(byte[] data) throws DeserializeException {
        if (data.length < COAP_HEADER_SIZE) {
            raiseDeserializeException(null, "Encoded CoAP messages MUST have min. 4 bytes. This has " + data.length);
            return null;
        }
        int version = data[0] >>> 6;
        int type = (data[0] >>> 4) & 3;
        int tokenLength = data[0] & 15;
        int code = data[1] & 0xFF;
        int messageId = (data[2] & 0xFF) << 8 | (data[3] & 0xFF);

        // Check whether the protocol version is supported (=1)
        if (version != COAP_PROTOCOL_VERSION) {
            raiseDeserializeException(messageId, "Invalid CoAP version. Should be: " + COAP_PROTOCOL_VERSION);
            return null;
        }

        // Check whether TKL indicates a not allowed token length
        if (tokenLength > MAX_TOKEN_LENGTH) {
            raiseDeserializeException(messageId, "TKL value (" + tokenLength + ") is larger than 8!");
            return null;
        }

        // Check whether there are enough unread bytes left to read the token
        if ((data.length - COAP_HEADER_SIZE) < tokenLength) {
            raiseDeserializeException(messageId, "TKL value is " + tokenLength + " but only " + (data.length - COAP_HEADER_SIZE) + " bytes left!");
            return null;
        }

        /* Validate */

        CoAPMessageType messageType;
        try {
            messageType = CoAPMessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            raiseDeserializeException(messageId, e.getMessage());
            return null;
        }

        CoAPMessageCode messageCode;
        try {
            messageCode = CoAPMessageCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            raiseDeserializeException(messageId, e.getMessage());
            return null;
        }

        CoAPMessage message = new CoAPMessage(messageType, messageCode, messageId);

        // Read the token
        if (tokenLength > 0) {
            byte[] token = new byte[tokenLength];
            System.arraycopy(data, COAP_HEADER_SIZE, token, 0, tokenLength);
            message.setToken(token);
        }

        // This is how many bytes we should pass before further data reading
        int offset = COAP_HEADER_SIZE + tokenLength;

        if ((data.length - offset) > 0) {
            ByteArrayInputStream is = new ByteArrayInputStream(data, offset, data.length);

            /* Read Options */
            decodeOptions(message, is);

            /* Read Payload */
            if (is.available() > 0) {
                byte[] payload = new byte[is.available()];
                try {
                    is.read(payload);
                    message.setPayload(new CoAPMessagePayload(payload));
                } catch (IOException e) {
                    raiseDeserializeException(messageId, e.getMessage());
                    return null;
                }
            }
        }

        return message;
    }

    private static void raiseDeserializeException(Integer messageId, String exceptionString) throws DeserializeException {
        LogHelper.e(exceptionString);
        throw new DeserializeException(messageId, exceptionString);
    }

    /**
     * 0   1   2   3   4   5   6   7
     * +---------------+---------------+
     * |               |               |
     * |  Option Delta | Option Length |   1 byte
     * |               |               |
     * +---------------+---------------+
     * \                               \
     * /         Option Delta          /   0-2 bytes
     * \          (extended)           \
     * +-------------------------------+
     * \                               \
     * /         Option Length         /   0-2 bytes
     * \          (extended)           \
     * +-------------------------------+
     * \                               \
     * /                               /
     * \                               \
     * /         Option Value          /   0 or more bytes
     * \                               \
     * /                               /
     * \                               \
     * +-------------------------------+
     */
    private static void decodeOptions(CoAPMessage message, ByteArrayInputStream is) {
        //Decode the options
        int previousOptionNumber = 0;
        int firstByte = is.read() & 0xFF;

        while (firstByte != 0xFF && is.available() >= 0) {
            int optionDelta = (firstByte & 0xF0) >>> 4;
            int optionLength = firstByte & 0x0F;

            if (optionDelta == 13) {
                optionDelta += is.read() & 0xFF;
            } else if (optionDelta == 14) {
                optionDelta = 269 + ((is.read() & 0xFF) << 8) + (is.read() & 0xFF);
            }

            if (optionLength == 13) {
                optionLength += is.read() & 0xFF;
            } else if (optionLength == 14) {
                optionLength = 269 + ((is.read() & 0xFF) << 8) + (is.read() & 0xFF);
            }

            int actualOptionNumber = previousOptionNumber + optionDelta;

            try {
                byte[] optionValue = new byte[optionLength];
                is.read(optionValue);

                CoAPMessageOptionCode optionCode = CoAPMessageOptionCode.valueOf(actualOptionNumber);
                message.addOption(new CoAPMessageOption(optionCode, optionValue));

            } catch (IOException | IllegalArgumentException e) {
                LogHelper.e(e.getMessage());
                continue;
            }

            previousOptionNumber = actualOptionNumber;

            if (is.available() > 0) {
                firstByte = is.read() & 0xFF;
            } else {
                // this is necessary if there is no payload and the last option is empty (e.g. UintOption with value 0)
                firstByte = 0xFF;
            }
        }
    }

    public static byte[] toBytes(CoAPMessage message) {
        // start encoding
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // encode HEADER and TOKEN
        try {
            encodeHeader(buffer, message);
        } catch (IOException e) {
            LogHelper.e(e.getMessage());
            return null;
        }

        // encode OPTIONS (if any)
        try {
            encodeOptions(buffer, message);
        } catch (Exception e) {
            LogHelper.e(e.getMessage());
            return null;
        }

        // encode payload (if any)
        if (message.getPayload() != null && message.getPayload().content != null) {
            byte[] rawPayload = message.getPayload().content;
            if (rawPayload.length > 0) {
                // add END-OF-OPTIONS marker only if there is payload
                buffer.write(COAP_PAYLOAD_MARKER);
                // add payload
                buffer.write(rawPayload, 0, rawPayload.length);
            }
        }

        return buffer.toByteArray();
    }

    private static void encodeHeader(ByteArrayOutputStream buffer, CoAPMessage message) throws IOException {

        byte[] token = message.getToken();

        int encodedHeader = ((COAP_PROTOCOL_VERSION & 0x03) << 30)
                | ((message.getType().value & 0x03) << 28)
                | (((token != null ? token.length : 0) & 0x0F) << 24)
                | ((message.getCode().value & 0xFF) << 16)
                | ((message.getId() & 0xFFFF));
        buffer.write((encodedHeader & 0xFF000000) >> 24);
        buffer.write((encodedHeader & 0x00FF0000) >> 16);
        buffer.write((encodedHeader & 0x0000FF00) >> 8);
        buffer.write((encodedHeader & 0x000000FF));

        // Write token
        if (token != null && token.length > 0) {
            buffer.write(token, 0, token.length);
        }
    }

    private static void encodeOptions(ByteArrayOutputStream buffer, CoAPMessage message) throws Exception {

        List<CoAPMessageOption> options = message.getOptions();

        if (options == null || options.size() == 0) {
            return;
        }

        // sort options by Option Number first
        Collections.sort(options, CoAPMessageOption::compareTo);

        //Encode options one after the other and append buf option to the buf
        int previousOptionNumber = 0;

        for (CoAPMessageOption option : options) {
            // encode
            encodeOption(buffer, option, previousOptionNumber);

            // remember previous number
            previousOptionNumber = option.code.value;
        }
    }

    private static void encodeOption(ByteArrayOutputStream buffer, CoAPMessageOption option, int previousNumber) throws Exception {
        int optionNumber = option.code.value;

        //The previous option number must be smaller or equal to the actual one
        if (previousNumber > optionNumber) {
            throw new Exception("Previous option " + previousNumber + " must not be larger then current option no " + optionNumber);
        }

        byte[] rawOptionValue = option.toBytes();
        if (rawOptionValue.length == 0) LogHelper.w("Option with null length: " + optionNumber);

        int optionDelta = optionNumber - previousNumber;
        int optionLength = Math.min(rawOptionValue.length, option.getMaxSizeInBytes());

        if (optionLength > MAX_OPTION_LENGTH) {
            throw new Exception("Option no. " + optionNumber + " exceeds maximum option length: " + optionLength + " vs " + MAX_OPTION_LENGTH);
        }
        if (optionDelta > MAX_OPTION_DELTA) {
            throw new Exception("Option no. " + optionNumber + " exceeds maximum option delta: " + optionDelta + " vs " + MAX_OPTION_DELTA);
        }

        if (optionNumber == CoAPMessageOptionCode.OptionContentFormat.value) {
            LogHelper.d("encodeOption, length: " + optionLength + ", delta: " + optionDelta);
        }

        if (optionDelta < 13) {
            //option delta < 13
            if (optionLength < 13) {
                buffer.write(((optionDelta & 0xFF) << 4) | (optionLength & 0xFF));
            } else if (optionLength < 269) {
                buffer.write(((optionDelta << 4) & 0xFF) | (13 & 0xFF));
                buffer.write((optionLength - 13) & 0xFF);
            } else {
                buffer.write(((optionDelta << 4) & 0xFF) | (14 & 0xFF));
                buffer.write(((optionLength - 269) & 0xFF00) >>> 8);
                buffer.write((optionLength - 269) & 0xFF);
            }
        } else if (optionDelta < 269) {
            //13 <= option delta < 269
            if (optionLength < 13) {
                buffer.write(((13 & 0xFF) << 4) | (optionLength & 0xFF));
                buffer.write((optionDelta - 13) & 0xFF);
            } else if (optionLength < 269) {
                buffer.write(((13 & 0xFF) << 4) | (13 & 0xFF));
                buffer.write((optionDelta - 13) & 0xFF);
                buffer.write((optionLength - 13) & 0xFF);
            } else {
                buffer.write((13 & 0xFF) << 4 | (14 & 0xFF));
                buffer.write((optionDelta - 13) & 0xFF);
                buffer.write(((optionLength - 269) & 0xFF00) >>> 8);
                buffer.write((optionLength - 269) & 0xFF);
            }
        } else {
            //269 <= option delta < 65805
            if (optionLength < 13) {
                buffer.write(((14 & 0xFF) << 4) | (optionLength & 0xFF));
                buffer.write(((optionDelta - 269) & 0xFF00) >>> 8);
                buffer.write((optionDelta - 269) & 0xFF);
            } else if (optionLength < 269) {
                buffer.write(((14 & 0xFF) << 4) | (13 & 0xFF));
                buffer.write(((optionDelta - 269) & 0xFF00) >>> 8);
                buffer.write((optionDelta - 269) & 0xFF);
                buffer.write((optionLength - 13) & 0xFF);
            } else {
                buffer.write(((14 & 0xFF) << 4) | (14 & 0xFF));
                buffer.write(((optionDelta - 269) & 0xFF00) >>> 8);
                buffer.write((optionDelta - 269) & 0xFF);
                buffer.write(((optionLength - 269) & 0xFF00) >>> 8);
                buffer.write((optionLength - 269) & 0xFF);
            }
        }

        // Write option value
        buffer.write(rawOptionValue, 0, optionLength);
    }

    public static class DeserializeException extends Exception {
        private final Integer messageId;

        public DeserializeException(Integer messageId, String message) {
            super(message);
            this.messageId = messageId;
        }

        public Integer getMessageId() {
            return messageId;
        }
    }

}
