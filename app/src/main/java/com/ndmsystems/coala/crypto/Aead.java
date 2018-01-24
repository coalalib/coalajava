package com.ndmsystems.coala.crypto;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.nio.ByteBuffer;


public class Aead {
    public final byte[] peerKey;
    public final byte[] myKey;
    public final byte[] peerIV;
    public final byte[] myIV;
    private final AesGcm encryptor;
    private final AesGcm decryptor;

    public Aead(byte[] peerKey, byte[] myKey, byte[] peerIV, byte[] myIV) {
        LogHelper.v("Aead peerKey=" + Hex.encodeHexString(peerKey) + ", length: " + peerKey.length);
        LogHelper.v("Aead myKey=" + Hex.encodeHexString(myKey) + ", length: " + myKey.length);
        LogHelper.v("Aead peerIV=" + Hex.encodeHexString(peerIV) + ", length: " + peerIV.length);
        LogHelper.v("Aead myIV=" + Hex.encodeHexString(myIV) + ", length: " + myIV.length);
        this.peerKey = peerKey;
        this.myKey = myKey;
        this.peerIV = peerIV;
        this.myIV = myIV;
        encryptor = new AesGcm(this.myKey);
        decryptor = new AesGcm(this.peerKey);
    }


    public byte[] decrypt(byte[] cipherText, int counter, byte[] associatedData){
        try {
            LogHelper.v("Open, nonce: " + Hex.encodeHexString(makeNonce(peerIV, counter)));
            return decryptor.open(cipherText, makeNonce(peerIV, counter), associatedData);
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e("Error then decrypt: " + e.getMessage() + ", nonce: " + Hex.encodeHexString(makeNonce(peerIV, counter)));
            return null;
        }
    }

    public byte[] encrypt(byte[] plainText, int counter, byte[] associatedData){
        try {
            return encryptor.seal(plainText, makeNonce(myIV, counter), associatedData);
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e("Error then encrypt: " + e.getMessage());
            return null;
        }
    }

    private byte[] makeNonce(byte[] iv, int counter) {
        ByteBuffer nonce = ByteBuffer.allocate(12);
        nonce.put(iv).put((byte)(counter & 0xFF)).put((byte)((counter >> 8) & 0xFF));
        return nonce.array();
    }
}
