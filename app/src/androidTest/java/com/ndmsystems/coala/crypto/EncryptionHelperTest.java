package com.ndmsystems.coala.crypto;

import android.support.test.runner.AndroidJUnit4;

import com.ndmsystems.coala.BaseTest;
import com.ndmsystems.coala.helpers.EncryptionHelper;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EncryptionHelperTest extends BaseTest {
    CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET, 101).setURI("coaps://192.168.1.1:8080/test?param=1&taram=ololo");

    private static final byte[] peerKey =   Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray());
    private static final byte[] myKey =     Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray());
    private static final byte[] peerIV =    Hex.decodeHex("799212a9".toCharArray());
    private static final byte[] myIV =      Hex.decodeHex("b3efe5ce".toCharArray());

    private static final Aead myAead = new Aead(peerKey, myKey, peerIV, myIV);
    private static final Aead peerAead = new Aead(myKey, peerKey, myIV, peerIV);

    @Test
    public void testOptionsEncryptDecrypt() {
        CoAPMessage encryptedMessage = new CoAPMessage(message);
        EncryptionHelper.encrypt(encryptedMessage, myAead);

        assertTrue(encryptedMessage.hasOption(CoAPMessageOptionCode.OptionCoapsURI));
        assertTrue(encryptedMessage.getOption(CoAPMessageOptionCode.OptionCoapsURI).toBytes().length > 0);
        assertTrue(!encryptedMessage.hasOption(CoAPMessageOptionCode.OptionURIPath));
        assertTrue(!encryptedMessage.hasOption(CoAPMessageOptionCode.OptionURIQuery));

        CoAPMessage decryptedMessage = new CoAPMessage(encryptedMessage);
        EncryptionHelper.decrypt(decryptedMessage, peerAead);

        assertTrue(!decryptedMessage.hasOption(CoAPMessageOptionCode.OptionCoapsURI));
        assertTrue(decryptedMessage.hasOption(CoAPMessageOptionCode.OptionURIPath));
        assertTrue(decryptedMessage.hasOption(CoAPMessageOptionCode.OptionURIQuery));
        assertEquals(message.getURI(), decryptedMessage.getURI());
    }
}
