package com.ndmsystems.coala.crypto;

import android.os.Build;

import com.ndmsystems.coala.BuildConfig;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;

import org.spongycastle.jcajce.spec.AEADParameterSpec;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
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
    Cipher cipher;

    public AesGcm(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
        initCipher();
    }

    private void initCipher() {
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding", bouncyCastleProvider);
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e("Fatal error, can't get cipher");
        }
    }

    //TODO: сделать поддержку additionalAuthenticatedData
    public byte[] seal(byte[] plainText, byte[] nonce, byte[] additionalAuthenticatedData) throws InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            initCipher();
        }

        cipher.init(Cipher.ENCRYPT_MODE, key, new AEADParameterSpec(nonce, TAG_LENGTH * 8));

        return cipher.doFinal(plainText);
    }

    // the input comes from users
    //TODO: сделать поддержку additionalAuthenticatedData
    public byte[] open(byte[] cipherText, byte[] nonce, byte[] additionalAuthenticatedData) throws Exception {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            initCipher();
        }

        AEADParameterSpec params = new AEADParameterSpec(nonce, TAG_LENGTH * 8);
        cipher.init(Cipher.DECRYPT_MODE, key, params);

        return cipher.doFinal(cipherText);
    }

}
