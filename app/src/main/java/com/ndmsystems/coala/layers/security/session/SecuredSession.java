package com.ndmsystems.coala.layers.security.session;


import com.ndmsystems.coala.Coala;
import com.ndmsystems.coala.crypto.Aead;
import com.ndmsystems.coala.crypto.Curve25519;
import com.ndmsystems.coala.crypto.Hkdf;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.RBGHelper;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SecuredSession {
    private Curve25519 curve;

    private Aead aead;

    private byte[] peerPublicKey;

    private Long peerProxySecurityId;

    public SecuredSession(boolean incoming) {
    }

    private Curve25519 getCurve() {
        if (curve == null) {
            this.curve = Coala.getDependencyGraph().provideCurveRepository().getCurve();
        }
        return curve;
    }

    public byte[] getPublicKey() {
        byte[] publicKey = getCurve().getPublicKey();
        LogHelper.v("getPublicKey: " + Hex.encodeHexString(publicKey));
        return publicKey;
    }

    public byte[] getSignature() {
        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        byte[] sharedSecret = getCurve().generateSharedSecret(this.peerPublicKey);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // @TODO: Error Handling here
            return new byte[0];
        }

        md.update(sharedSecret);

        return md.digest();
    }

    public void start(byte[] peerPublicKey) {
        LogHelper.d("SecuredSession start start");
        this.peerPublicKey = peerPublicKey;

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        byte[] sharedSecret = getCurve().generateSharedSecret(this.peerPublicKey);

        byte[] salt = null;
        byte[] info  = null; // Should be some public data

        // OK! Session is started! We can communicate now with AES Ephemeral Key!
        setAead(parseHKDF(new Hkdf(sharedSecret, salt, info)));
        LogHelper.d("SecuredSession start end");
    }

    private Aead parseHKDF(Hkdf hkdf) {
        return new Aead(hkdf.peerKey, hkdf.myKey, hkdf.peerIV, hkdf.myIV);
    }

    public void startPeer(byte[] peerPublicKey) {
        LogHelper.d("SecuredSession start start");
        this.peerPublicKey = peerPublicKey;

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        byte[] sharedSecret = getCurve().generateSharedSecret(this.peerPublicKey);

        byte[] salt = null;
        byte[] info  = null; // Should be some public data

        // OK! Session is started! We can communicate now with AES Ephemeral Key!
        setAead(parseHKDFPeer(new Hkdf(sharedSecret, salt, info)));
        LogHelper.d("SecuredSession start end");
    }

    private Aead parseHKDFPeer(Hkdf hkdf) {
        return new Aead(hkdf.myKey, hkdf.peerKey, hkdf.myIV, hkdf.peerIV);
    }

    public byte[] getPeerPublicKey() {
        return peerPublicKey;
    }

    // ASSIGN KEYS IN REVERS ORDER!!!!
    public void PeerVerify(byte[] peerSignature) {
        byte[] signature = this.getSignature();

        // If the Peer is not a Man-In-The-Middle then Peer's Shared Secret is the Same!
        // Hash our Shared Secret to Compare with Peer's Signature!
        if (signature != peerSignature) {
            LogHelper.e("signature and peerSignature are not Equal");
        }

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        byte[] sharedSecret = getCurve().generateSharedSecret(this.peerPublicKey);

        byte[] nonce = RBGHelper.rbg(32); // Just random data
        byte[] info  = new byte[0]; // Should be some public data

        //byte[] keys  = new Hkdf().deriveSecrets(sharedSecret, nonce, info);

        // ASSIGN KEYS IN REVERS ORDER!!!!
        //aead = parseHKDFKeysForPeer(keys);
    }

    public boolean isReady() {
        return getAead() != null;
    }

    public Aead getAead() {
        return aead;
    }

    private void setAead(Aead aead) {
        this.aead = aead;
    }

    public Long getPeerProxySecurityId() {
        return peerProxySecurityId;
    }

    public void setPeerProxySecurityId(Long peerProxySecurityId) {
        this.peerProxySecurityId = peerProxySecurityId;
    }

    // ASSIGN KEYS IN REVERS ORDER!!!!
    /*private Aead parseHKDFKeysForPeer(byte[] hkdfKeys) {
        int keyLength = 16;

        byte[] out = new byte[2*keyLength+2*4];
        System.arraycopy(hkdfKeys, 0, out, 0, hkdfKeys.length);

        Aead aead = new Aead();

        // ASSIGN KEYS IN REVERS ORDER!!!!
        System.arraycopy(out, 0, aead.myKey, 0, keyLength);
        System.arraycopy(out, 0, aead.peerKey, keyLength, 2*keyLength);
        System.arraycopy(out, 0, aead.myIV, 2*keyLength, 2*keyLength+4);
        System.arraycopy(out, 0, aead.peerIV, 2*keyLength+4, out.length);

        return aead;
    }*/
}
