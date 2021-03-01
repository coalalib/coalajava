package com.ndmsystems.coala.crypto;

import android.os.Build;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;

import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.crypto.params.AEADParameters;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.jcajce.spec.AEADParameterSpec;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
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
    public byte[] open(byte[] cipherText, byte[] nonce, byte[] authData) throws Exception {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            initCipher();
        }

        // Initialise AES/GCM cipher for decryption
        GCMBlockCipher cipher = createAESGCMCipher(key, false, nonce, authData);
        // Join cipher text and authentication tag to produce cipher input

        byte[] input = new byte[cipherText.length];
        System.arraycopy(cipherText, 0, input, 0, cipherText.length);

        int outputLength = cipher.getOutputSize(input.length);
        byte[] output = new byte[outputLength];

        // Decrypt
        int outputOffset = cipher.processBytes(input, 0, input.length, output, 0);

        // Validate authentication tag
        try {
            outputOffset += cipher.doFinal(output, outputOffset);
        } catch (InvalidCipherTextException e) {
            throw new Exception("Couldn't validate GCM authentication tag when decode, length of the data: " + cipherText.length + ", key: " + Hex.encodeHexString(key.getEncoded()) + ", error: " + e.getMessage(), e);
        }

        return output;
    }

    private static AESEngine createAESCipher(final Key secretKey,
                                             final boolean forEncryption) {

        AESEngine cipher = new AESEngine();

        CipherParameters cipherParams = new KeyParameter(secretKey.getEncoded());

        cipher.init(forEncryption, cipherParams);

        return cipher;
    }



    private static GCMBlockCipher createAESGCMCipher(final Key secretKey,
                                                     final boolean forEncryption,
                                                     final byte[] iv,
                                                     final byte[] authData) {

        // Initialise AES cipher
        BlockCipher cipher = createAESCipher(secretKey, forEncryption);

        // Create GCM cipher with AES
        GCMBlockCipher gcm = new GCMBlockCipher(cipher);

        AEADParameters aeadParams = new AEADParameters(new KeyParameter(secretKey.getEncoded()),
                TAG_LENGTH * 8,
                iv,
                authData);
        gcm.init(forEncryption, aeadParams);

        return gcm;
    }

}
