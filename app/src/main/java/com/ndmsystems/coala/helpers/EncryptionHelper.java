package com.ndmsystems.coala.helpers;

import com.ndmsystems.coala.crypto.Aead;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.infrastructure.logging.LogHelper;


public class EncryptionHelper {
    public static void encrypt(CoAPMessage message, Aead aead) {
        if (message.getPayload() != null && message.getPayload().content != null)
            message.setPayload(new CoAPMessagePayload(aead.encrypt(message.getPayload().content, message.getId(), null)));

        encryptOptions(message, aead);
    }

    private static void encryptOptions(CoAPMessage message, Aead aead) {
        if (message.hasOption(CoAPMessageOptionCode.OptionURIQuery)
                || message.hasOption(CoAPMessageOptionCode.OptionURIPath)) {
            message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionCoapsURI, aead.encrypt(message.getURI().getBytes(), message.getId(), null)));
            message.removeOption(CoAPMessageOptionCode.OptionURIQuery);
            message.removeOption(CoAPMessageOptionCode.OptionURIPath);
        }
    }

    public static boolean decrypt(CoAPMessage message, Aead aead) {
        if (message.getPayload() != null && message.getPayload().content != null) {
            byte[] newPayload = aead.decrypt(message.getPayload().content, message.getId(), null);
            if (newPayload == null) {
                LogHelper.e("Can't decrypt message with id: " + message.getId() + ", token: " + message.getHexToken());
                message.setPayload(null);
                return false;
            }

            message.setPayload(new CoAPMessagePayload(newPayload));
        }
        decryptOptions(message, aead);
        return true;
    }

    private static void decryptOptions(CoAPMessage message, Aead aead) {
        if (message.hasOption(CoAPMessageOptionCode.OptionCoapsURI)) {
            byte[] uriBytes = aead.decrypt(message.getOption(CoAPMessageOptionCode.OptionCoapsURI).toBytes(), message.getId(), null);
            if (uriBytes != null) {
                message.setURI(new String(uriBytes));
                message.removeOption(CoAPMessageOptionCode.OptionCoapsURI);
            } else LogHelper.w("OptionCoapsURI empty after decrypt");
        }
        if (message.hasOption(CoAPMessageOptionCode.OptionCookie)) {
            //todo узнать что делать с этими куки
//            byte[] cookieBytes = aead.decrypt(message.getOption(CoAPMessageOptionCode.OptionCookie).toBytes(), message.getId(), null);
//            if (cookieBytes != null) {
//                LogHelper.i("Message has Cookie: " + new String(cookieBytes));
//            } else
//                LogHelper.i("OptionCookie empty after decrypt");
        }
    }
}
