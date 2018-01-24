package com.ndmsystems.coala.crypto;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;

import org.spongycastle.jcajce.spec.AEADParameterSpec;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.Key;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by bas on 01.11.16.
 */
public class AesGcm {
    private static BouncyCastleProvider bouncyCastleProvider;
    static {
        bouncyCastleProvider = new BouncyCastleProvider();
        Security.insertProviderAt(bouncyCastleProvider, 1);
    }

    private Key key;
    private static final int TAG_LENGTH = 12;

    public AesGcm(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
        LogHelper.d("Key: " + Hex.encodeHexString(this.key.getEncoded()));
    }

    //TODO: сделать поддержку additionalAuthenticatedData
    public byte[] seal(byte[] plainText, byte[] nonce, byte[] additionalAuthenticatedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", bouncyCastleProvider);
        cipher.init(Cipher.ENCRYPT_MODE, key, new AEADParameterSpec(nonce, TAG_LENGTH * 8));

        return cipher.doFinal(plainText);
    }

    // the input comes from users
    //TODO: сделать поддержку additionalAuthenticatedData
    public byte[] open(byte[] cipherText, byte[] nonce, byte[] additionalAuthenticatedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", bouncyCastleProvider);

        AEADParameterSpec params = new AEADParameterSpec(nonce, TAG_LENGTH * 8);
        cipher.init(Cipher.DECRYPT_MODE, key, params);

        return cipher.doFinal(cipherText);
    }

}
