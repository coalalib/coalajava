package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.RBGHelper

/* Ported from C to Java by Dmitry Skiba [sahn0], 23/02/08.
 * Original: http://cds.xs4all.nl:8081/ecdh/
 */
/* Generic 64-bit integer implementation of Curve25519 ECDH
 * Written by Matthijs van Duin, 200608242056
 * Public domain.
 *
 * Based on work by Daniel J Bernstein, http://cr.yp.to/ecdh.html
 */   class Curve25519 {
    private val privateKey: ByteArray
    val publicKey: ByteArray

    constructor() {
        privateKey = generatePrivateKey()
        publicKey = generatePublicKey(privateKey)
    }

    constructor(random: ByteArray?) {
        privateKey = generatePrivateKey(random)
        publicKey = generatePublicKey(privateKey)
    }

    fun generateSharedSecret(peerPublicKey: ByteArray?): ByteArray {
        val sharedSecret = ByteArray(32)
        curve(sharedSecret, privateKey, peerPublicKey)
        return sharedSecret
    }

    ///////////////////////////////////////////////////////////////////////////
    /* sahn0:
     * Using this class instead of long[10] to avoid bounds checks. */
    private class long10 {
        constructor() {}
        constructor(
            _0: Long, _1: Long, _2: Long, _3: Long, _4: Long,
            _5: Long, _6: Long, _7: Long, _8: Long, _9: Long
        ) {
            this._0 = _0
            this._1 = _1
            this._2 = _2
            this._3 = _3
            this._4 = _4
            this._5 = _5
            this._6 = _6
            this._7 = _7
            this._8 = _8
            this._9 = _9
        }

        var _0: Long = 0
        var _1: Long = 0
        var _2: Long = 0
        var _3: Long = 0
        var _4: Long = 0
        var _5: Long = 0
        var _6: Long = 0
        var _7: Long = 0
        var _8: Long = 0
        var _9: Long = 0
    }

    companion object {
        /* key size */
        private const val KEY_SIZE = 32

        /* 0 */
        val ZERO = byteArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        /* the prime 2^255-19 */
        val PRIME = byteArrayOf(
            237.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            127.toByte()
        )

        /* group order (a prime near 2^252+2^124) */
        private val ORDER = byteArrayOf(
            237.toByte(),
            211.toByte(),
            245.toByte(),
            92.toByte(),
            26.toByte(),
            99.toByte(),
            18.toByte(),
            88.toByte(),
            214.toByte(),
            156.toByte(),
            247.toByte(),
            162.toByte(),
            222.toByte(),
            249.toByte(),
            222.toByte(),
            20.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            16.toByte()
        )

        /**
         * Utility Functions
         */
        fun generatePublicKey(privateKey: ByteArray): ByteArray {
            val publicKey = ByteArray(32)
            core(publicKey, null, privateKey, null)
            return publicKey
        }

        @JvmOverloads
        fun generatePrivateKey(random: ByteArray? = RBGHelper.rbg(KEY_SIZE)): ByteArray {
            val privateKey = ByteArray(32)
            System.arraycopy(random, 0, privateKey, 0, 32)
            privateKey[0] = (privateKey[0].toInt() and 248).toByte()
            privateKey[31] = (privateKey[31].toInt() and 127).toByte()
            privateKey[31] = (privateKey[31].toInt() or 64).toByte()
            return privateKey
        }

        /********* KEY AGREEMENT  */ /* Private key clamping
	 *   k [out] your private key for key agreement
	 *   k  [in]  32 random bytes
	 */
        fun clamp(k: ByteArray) {
            k[31] = (k[31].toInt() and 0x7F).toByte()
            k[31] = (k[31].toInt() or 0x40).toByte()
            k[0] = (k[0].toInt() and 0xF8).toByte()
        }

        /* Key agreement
     *   Z  [out] shared secret (needs hashing before use)
     *   k  [in]  your private key for key agreement
     *   P  [in]  peer's public key
     */
        fun curve(Z: ByteArray, k: ByteArray, P: ByteArray?) {
            core(Z, null, k, P)
        }

        /********* DIGITAL SIGNATURES  */ /* deterministic EC-KCDSA
	 *
	 *    s is the private key for signing
	 *    P is the corresponding public key
	 *    Z is the context data (signer public key or certificate, etc)
	 *
	 * signing:
	 *
	 *    m = hash(Z, message)
	 *    x = hash(m, s)
	 *    keygen25519(Y, NULL, x);
	 *    r = hash(Y);
	 *    h = m XOR r
	 *    sign25519(v, h, x, s);
	 *
	 *    output (v,r) as the signature
	 *
	 * verification:
	 *
	 *    m = hash(Z, message);
	 *    h = m XOR r
	 *    verify25519(Y, v, h, P)
	 *
	 *    confirm  r == hash(Y)
	 *
	 * It would seem to me that it would be simpler to have the signer directly do
	 * h = hash(m, Y) and send that to the recipient instead of r, who can verify
	 * the signature by checking h == hash(m, Y).  If there are any problems with
	 * such a scheme, please let me know.
	 *
	 * Also, EC-KCDSA (like most DS algorithms) picks x random, which is a waste of
	 * perfectly good entropy, but does allow Y to be calculated in advance of (or
	 * parallel to) hashing the message.
	 */
        /* Signature generation primitive, calculates (x-h)s mod q
	 *   v  [out] signature value
	 *   h  [in]  signature hash (of message, signature pub key, and context data)
	 *   x  [in]  signature private key
	 *   s  [in]  private key for signing
	 * returns true on success, false on failure (use different x or h)
	 */
        fun sign(v: ByteArray, h: ByteArray, x: ByteArray, s: ByteArray): Boolean {
            /* v = (x - h) s  mod q  */
            val tmp1 = ByteArray(65)
            val tmp2 = ByteArray(33)
            var w: Int
            var i: Int
            i = 0
            while (i < 32) {
                v[i] = 0
                i++
            }
            i = mula_small(v, x, 0, h, 32, -1)
            mula_small(v, v, 0, ORDER, 32, (15 - v[31]) / 16)
            mula32(tmp1, v, s, 32, 1)
            divmod(tmp2, tmp1, 64, ORDER, 32)
            w = 0
            i = 0
            while (i < 32) {
                v[i] = tmp1[i]
                w = w or v[i].toInt()
                i++
            }
            return w != 0
        }

        /* Signature verification primitive, calculates Y = vP + hG
     *   Y  [out] signature public key
     *   v  [in]  signature value
     *   h  [in]  signature hash
     *   P  [in]  public key
     */
        fun verify(Y: ByteArray, v: ByteArray, h: ByteArray, P: ByteArray) {
            /* Y = v abs(P) + h G  */
            val d = ByteArray(32)
            val p = arrayOf(long10(), long10())
            val s = arrayOf(long10(), long10())
            val yx = arrayOf(long10(), long10(), long10())
            val yz = arrayOf(long10(), long10(), long10())
            val t1 = arrayOf(long10(), long10(), long10())
            val t2 = arrayOf(long10(), long10(), long10())
            var vi = 0
            var hi = 0
            var di = 0
            var nvh = 0
            var i: Int
            var j: Int
            var k: Int

            /* set p[0] to G and p[1] to P  */Companion[p[0]] = 9
            unpack(p[1], P)

            /* set s[0] to P+G and s[1] to P-G  */

            /* s[0] = (Py^2 + Gy^2 - 2 Py Gy)/(Px - Gx)^2 - Px - Gx - 486662  */
            /* s[1] = (Py^2 + Gy^2 + 2 Py Gy)/(Px - Gx)^2 - Px - Gx - 486662  */x_to_y2(t1[0], t2[0], p[1]) /* t2[0] = Py^2  */
            sqrt(t1[0], t2[0]) /* t1[0] = Py or -Py  */
            j = is_negative(t1[0]) /*      ... check which  */
            t2[0]._0 += 39420360 /* t2[0] = Py^2 + Gy^2  */
            mul(t2[1], BASE_2Y, t1[0]) /* t2[1] = 2 Py Gy or -2 Py Gy  */
            sub(t1[j], t2[0], t2[1]) /* t1[0] = Py^2 + Gy^2 - 2 Py Gy  */
            add(t1[1 - j], t2[0], t2[1]) /* t1[1] = Py^2 + Gy^2 + 2 Py Gy  */
            cpy(t2[0], p[1]) /* t2[0] = Px  */
            t2[0]._0 -= 9 /* t2[0] = Px - Gx  */
            sqr(t2[1], t2[0]) /* t2[1] = (Px - Gx)^2  */
            recip(t2[0], t2[1], 0) /* t2[0] = 1/(Px - Gx)^2  */
            mul(s[0], t1[0], t2[0]) /* s[0] = t1[0]/(Px - Gx)^2  */
            sub(s[0], s[0], p[1]) /* s[0] = t1[0]/(Px - Gx)^2 - Px  */
            s[0]._0 -= (9 + 486662).toLong() /* s[0] = X(P+G)  */
            mul(s[1], t1[1], t2[0]) /* s[1] = t1[1]/(Px - Gx)^2  */
            sub(s[1], s[1], p[1]) /* s[1] = t1[1]/(Px - Gx)^2 - Px  */
            s[1]._0 -= (9 + 486662).toLong() /* s[1] = X(P-G)  */
            mul_small(s[0], s[0], 1) /* reduce s[0] */
            mul_small(s[1], s[1], 1) /* reduce s[1] */


            /* prepare the chain  */i = 0
            while (i < 32) {
                vi = vi shr 8 xor (v[i].toInt() and 0xFF) xor (v[i].toInt() and 0xFF shl 1)
                hi = hi shr 8 xor (h[i].toInt() and 0xFF) xor (h[i].toInt() and 0xFF shl 1)
                nvh = (vi xor hi).inv()
                di = nvh and (di and 0x80 shr 7) xor vi
                di = di xor (nvh and (di and 0x01 shl 1))
                di = di xor (nvh and (di and 0x02 shl 1))
                di = di xor (nvh and (di and 0x04 shl 1))
                di = di xor (nvh and (di and 0x08 shl 1))
                di = di xor (nvh and (di and 0x10 shl 1))
                di = di xor (nvh and (di and 0x20 shl 1))
                di = di xor (nvh and (di and 0x40 shl 1))
                d[i] = di.toByte()
                i++
            }
            di = nvh and (di and 0x80 shl 1) xor vi shr 8

            /* initialize state */Companion[yx[0]] = 1
            cpy(yx[1], p[di])
            cpy(yx[2], s[0])
            Companion[yz[0]] = 0
            Companion[yz[1]] = 1
            Companion[yz[2]] = 1

            /* y[0] is (even)P + (even)G
		 * y[1] is (even)P + (odd)G  if current d-bit is 0
		 * y[1] is (odd)P + (even)G  if current d-bit is 1
		 * y[2] is (odd)P + (odd)G
		 */vi = 0
            hi = 0

            /* and go for it! */i = 32
            while (i-- != 0) {
                vi = vi shl 8 or (v[i].toInt() and 0xFF)
                hi = hi shl 8 or (h[i].toInt() and 0xFF)
                di = di shl 8 or (d[i].toInt() and 0xFF)
                j = 8
                while (j-- != 0) {
                    mont_prep(t1[0], t2[0], yx[0], yz[0])
                    mont_prep(t1[1], t2[1], yx[1], yz[1])
                    mont_prep(t1[2], t2[2], yx[2], yz[2])
                    k = ((vi xor vi shr 1 shr j and 1)
                            + (hi xor hi shr 1 shr j and 1))
                    mont_dbl(yx[2], yz[2], t1[k], t2[k], yx[0], yz[0])
                    k = di shr j and 2 xor (di shr j and 1 shl 1)
                    mont_add(
                        t1[1], t2[1], t1[k], t2[k], yx[1], yz[1],
                        p[di shr j and 1]
                    )
                    mont_add(
                        t1[2], t2[2], t1[0], t2[0], yx[2], yz[2],
                        s[vi xor hi shr j and 2 shr 1]
                    )
                }
            }
            k = (vi and 1) + (hi and 1)
            recip(t1[0], yz[k], 0)
            mul(t1[1], yx[k], t1[0])
            pack(t1[1], Y)
        }

        /********************* radix 2^8 math  */
        private fun cpy32(d: ByteArray, s: ByteArray) {
            var i: Int
            i = 0
            while (i < 32) {
                d[i] = s[i]
                i++
            }
        }

        /* p[m..n+m-1] = q[m..n+m-1] + z * x */ /* n is the size of x */ /* n+m is the size of p and q */
        private fun mula_small(p: ByteArray, q: ByteArray, m: Int, x: ByteArray, n: Int, z: Int): Int {
            var v = 0
            for (i in 0 until n) {
                v += (q[i + m].toInt() and 0xFF) + z * (x[i].toInt() and 0xFF)
                p[i + m] = v.toByte()
                v = v shr 8
            }
            return v
        }

        /* p += x * y * z  where z is a small integer
     * x is size 32, y is size t, p is size 32+t
     * y is allowed to overlap with p+32 if you don't care about the upper half  */
        private fun mula32(p: ByteArray, x: ByteArray, y: ByteArray, t: Int, z: Int): Int {
            val n = 31
            var w = 0
            var i = 0
            while (i < t) {
                val zy = z * (y[i].toInt() and 0xFF)
                w += mula_small(p, p, i, x, n, zy) +
                        (p[i + n].toInt() and 0xFF) + zy * (x[n].toInt() and 0xFF)
                p[i + n] = w.toByte()
                w = w shr 8
                i++
            }
            p[i + n] = (w + (p[i + n].toInt() and 0xFF)).toByte()
            return w shr 8
        }

        /* divide r (size n) by d (size t), returning quotient q and remainder r
     * quotient is size n-t+1, remainder is size t
     * requires t > 0 && d[t-1] != 0
     * requires that r[-1] and d[-1] are valid memory locations
     * q may overlap with r+t */
        private fun divmod(q: ByteArray, r: ByteArray, n: Int, d: ByteArray, t: Int) {
            var n = n
            var rn = 0
            var dt = d[t - 1].toInt() and 0xFF shl 8
            if (t > 1) {
                dt = dt or (d[t - 2].toInt() and 0xFF)
            }
            while (n-- >= t) {
                var z = rn shl 16 or (r[n].toInt() and 0xFF shl 8)
                if (n > 0) {
                    z = z or (r[n - 1].toInt() and 0xFF)
                }
                z /= dt
                rn += mula_small(r, r, n - t + 1, d, t, -z)
                q[n - t + 1] = (z + rn and 0xFF).toByte() /* rn is 0 or -1 (underflow) */
                mula_small(r, r, n - t + 1, d, t, -rn)
                rn = r[n].toInt() and 0xFF
                r[n] = 0
            }
            r[t - 1] = rn.toByte()
        }

        private fun numsize(x: ByteArray, n: Int): Int {
            var n = n
            while (n-- != 0 && x[n].toInt() == 0);
            return n + 1
        }

        /* Returns x if a contains the gcd, y if b.
     * Also, the returned buffer contains the inverse of a mod b,
     * as 32-byte signed.
     * x and y must have 64 bytes space for temporary use.
     * requires that a[-1] and b[-1] are valid memory locations  */
        private fun egcd32(x: ByteArray, y: ByteArray, a: ByteArray, b: ByteArray): ByteArray {
            var an: Int
            var bn = 32
            var qn: Int
            var i: Int
            i = 0
            while (i < 32) {
                y[i] = 0
                x[i] = y[i]
                i++
            }
            x[0] = 1
            an = numsize(a, 32)
            if (an == 0) return y /* division by zero */
            val temp = ByteArray(32)
            while (true) {
                qn = bn - an + 1
                divmod(temp, b, bn, a, an)
                bn = numsize(b, bn)
                if (bn == 0) return x
                mula32(y, x, temp, qn, -1)
                qn = an - bn + 1
                divmod(temp, a, an, b, bn)
                an = numsize(a, an)
                if (an == 0) return y
                mula32(x, y, temp, qn, -1)
            }
        }

        /********************* radix 2^25.5 GF(2^255-19) math  */
        private const val P25 = 33554431 /* (1 << 25) - 1 */
        private const val P26 = 67108863 /* (1 << 26) - 1 */

        /* Convert to internal format from little-endian byte format */
        private fun unpack(x: long10, m: ByteArray) {
            x._0 = (m[0].toInt() and 0xFF or (m[1].toInt() and 0xFF shl 8) or (
                    m[2].toInt() and 0xFF shl 16) or (m[3].toInt() and 0xFF and 3 shl 24)).toLong()
            x._1 = (m[3].toInt() and 0xFF and 3.inv() shr 2 or (m[4].toInt() and 0xFF shl 6) or (
                    m[5].toInt() and 0xFF shl 14) or (m[6].toInt() and 0xFF and 7 shl 22)).toLong()
            x._2 = (m[6].toInt() and 0xFF and 7.inv() shr 3 or (m[7].toInt() and 0xFF shl 5) or (
                    m[8].toInt() and 0xFF shl 13) or (m[9].toInt() and 0xFF and 31 shl 21)).toLong()
            x._3 = (m[9].toInt() and 0xFF and 31.inv() shr 5 or (m[10].toInt() and 0xFF shl 3) or (
                    m[11].toInt() and 0xFF shl 11) or (m[12].toInt() and 0xFF and 63 shl 19)).toLong()
            x._4 = (m[12].toInt() and 0xFF and 63.inv() shr 6 or (m[13].toInt() and 0xFF shl 2) or (
                    m[14].toInt() and 0xFF shl 10) or (m[15].toInt() and 0xFF shl 18)).toLong()
            x._5 = (m[16].toInt() and 0xFF or (m[17].toInt() and 0xFF shl 8) or (
                    m[18].toInt() and 0xFF shl 16) or (m[19].toInt() and 0xFF and 1 shl 24)).toLong()
            x._6 = (m[19].toInt() and 0xFF and 1.inv() shr 1 or (m[20].toInt() and 0xFF shl 7) or (
                    m[21].toInt() and 0xFF shl 15) or (m[22].toInt() and 0xFF and 7 shl 23)).toLong()
            x._7 = (m[22].toInt() and 0xFF and 7.inv() shr 3 or (m[23].toInt() and 0xFF shl 5) or (
                    m[24].toInt() and 0xFF shl 13) or (m[25].toInt() and 0xFF and 15 shl 21)).toLong()
            x._8 = (m[25].toInt() and 0xFF and 15.inv() shr 4 or (m[26].toInt() and 0xFF shl 4) or (
                    m[27].toInt() and 0xFF shl 12) or (m[28].toInt() and 0xFF and 63 shl 20)).toLong()
            x._9 = (m[28].toInt() and 0xFF and 63.inv() shr 6 or (m[29].toInt() and 0xFF shl 2) or (
                    m[30].toInt() and 0xFF shl 10) or (m[31].toInt() and 0xFF shl 18)).toLong()
        }

        /* Check if reduced-form input >= 2^255-19 */
        private fun is_overflow(x: long10): Boolean {
            return x._0 > P26 - 19 && x._1 and x._3 and x._5 and x._7 and x._9 == P25.toLong() && x._2 and x._4 and x._6 and x._8 == P26.toLong() || x._9 > P25
        }

        /* Convert from internal format to little-endian byte format.  The
     * number must be in a reduced form which is output by the following ops:
     *     unpack, mul, sqr
     *     set --  if input in range 0 .. P25
     * If you're unsure if the number is reduced, first multiply it by 1.  */
        private fun pack(x: long10, m: ByteArray) {
            var ld = 0
            var ud = 0
            var t: Long
            ld = (if (is_overflow(x)) 1 else 0) - if (x._9 < 0) 1 else 0
            ud = ld * -(P25 + 1)
            ld *= 19
            t = ld + x._0 + (x._1 shl 26)
            m[0] = t.toByte()
            m[1] = (t shr 8).toByte()
            m[2] = (t shr 16).toByte()
            m[3] = (t shr 24).toByte()
            t = (t shr 32) + (x._2 shl 19)
            m[4] = t.toByte()
            m[5] = (t shr 8).toByte()
            m[6] = (t shr 16).toByte()
            m[7] = (t shr 24).toByte()
            t = (t shr 32) + (x._3 shl 13)
            m[8] = t.toByte()
            m[9] = (t shr 8).toByte()
            m[10] = (t shr 16).toByte()
            m[11] = (t shr 24).toByte()
            t = (t shr 32) + (x._4 shl 6)
            m[12] = t.toByte()
            m[13] = (t shr 8).toByte()
            m[14] = (t shr 16).toByte()
            m[15] = (t shr 24).toByte()
            t = (t shr 32) + x._5 + (x._6 shl 25)
            m[16] = t.toByte()
            m[17] = (t shr 8).toByte()
            m[18] = (t shr 16).toByte()
            m[19] = (t shr 24).toByte()
            t = (t shr 32) + (x._7 shl 19)
            m[20] = t.toByte()
            m[21] = (t shr 8).toByte()
            m[22] = (t shr 16).toByte()
            m[23] = (t shr 24).toByte()
            t = (t shr 32) + (x._8 shl 12)
            m[24] = t.toByte()
            m[25] = (t shr 8).toByte()
            m[26] = (t shr 16).toByte()
            m[27] = (t shr 24).toByte()
            t = (t shr 32) + (x._9 + ud shl 6)
            m[28] = t.toByte()
            m[29] = (t shr 8).toByte()
            m[30] = (t shr 16).toByte()
            m[31] = (t shr 24).toByte()
        }

        /* Copy a number */
        private fun cpy(out: long10, `in`: long10) {
            out._0 = `in`._0
            out._1 = `in`._1
            out._2 = `in`._2
            out._3 = `in`._3
            out._4 = `in`._4
            out._5 = `in`._5
            out._6 = `in`._6
            out._7 = `in`._7
            out._8 = `in`._8
            out._9 = `in`._9
        }

        /* Set a number to value, which must be in range -185861411 .. 185861411 */
        private operator fun set(out: long10, `in`: Int) {
            out._0 = `in`.toLong()
            out._1 = 0
            out._2 = 0
            out._3 = 0
            out._4 = 0
            out._5 = 0
            out._6 = 0
            out._7 = 0
            out._8 = 0
            out._9 = 0
        }

        /* Add/subtract two numbers.  The inputs must be in reduced form, and the
     * output isn't, so to do another addition or subtraction on the output,
     * first multiply it by one to reduce it. */
        private fun add(xy: long10, x: long10, y: long10) {
            xy._0 = x._0 + y._0
            xy._1 = x._1 + y._1
            xy._2 = x._2 + y._2
            xy._3 = x._3 + y._3
            xy._4 = x._4 + y._4
            xy._5 = x._5 + y._5
            xy._6 = x._6 + y._6
            xy._7 = x._7 + y._7
            xy._8 = x._8 + y._8
            xy._9 = x._9 + y._9
        }

        private fun sub(xy: long10, x: long10, y: long10) {
            xy._0 = x._0 - y._0
            xy._1 = x._1 - y._1
            xy._2 = x._2 - y._2
            xy._3 = x._3 - y._3
            xy._4 = x._4 - y._4
            xy._5 = x._5 - y._5
            xy._6 = x._6 - y._6
            xy._7 = x._7 - y._7
            xy._8 = x._8 - y._8
            xy._9 = x._9 - y._9
        }

        /* Multiply a number by a small integer in range -185861411 .. 185861411.
     * The output is in reduced form, the input x need not be.  x and xy may point
     * to the same buffer. */
        private fun mul_small(xy: long10, x: long10, y: Long): long10 {
            var t: Long
            t = x._8 * y
            xy._8 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x._9 * y
            xy._9 = t and ((1 shl 25) - 1).toLong()
            t = 19 * (t shr 25) + x._0 * y
            xy._0 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x._1 * y
            xy._1 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x._2 * y
            xy._2 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x._3 * y
            xy._3 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x._4 * y
            xy._4 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x._5 * y
            xy._5 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x._6 * y
            xy._6 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x._7 * y
            xy._7 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + xy._8
            xy._8 = t and ((1 shl 26) - 1).toLong()
            xy._9 += t shr 26
            return xy
        }

        /* Multiply two numbers.  The output is in reduced form, the inputs need not
     * be. */
        private fun mul(xy: long10, x: long10, y: long10): long10 {
            /* sahn0:
		 * Using local variables to avoid class access.
		 * This seem to improve performance a bit...
		 */
            val x_0 = x._0
            val x_1 = x._1
            val x_2 = x._2
            val x_3 = x._3
            val x_4 = x._4
            val x_5 = x._5
            val x_6 = x._6
            val x_7 = x._7
            val x_8 = x._8
            val x_9 = x._9
            val y_0 = y._0
            val y_1 = y._1
            val y_2 = y._2
            val y_3 = y._3
            val y_4 = y._4
            val y_5 = y._5
            val y_6 = y._6
            val y_7 = y._7
            val y_8 = y._8
            val y_9 = y._9
            var t: Long
            t = x_0 * y_8 + x_2 * y_6 + x_4 * y_4 + x_6 * y_2 + x_8 * y_0 + 2 * (x_1 * y_7 + x_3 * y_5 + x_5 * y_3 + x_7 * y_1) + 38 * (x_9 * y_9)
            xy._8 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x_0 * y_9 + x_1 * y_8 + x_2 * y_7 + x_3 * y_6 + x_4 * y_5 + x_5 * y_4 + x_6 * y_3 + x_7 * y_2 + x_8 * y_1 + x_9 * y_0
            xy._9 = t and ((1 shl 25) - 1).toLong()
            t =
                x_0 * y_0 + 19 * ((t shr 25) + x_2 * y_8 + x_4 * y_6 + x_6 * y_4 + x_8 * y_2) + 38 * (x_1 * y_9 + x_3 * y_7 + x_5 * y_5 + x_7 * y_3 + x_9 * y_1)
            xy._0 = t and ((1 shl 26) - 1).toLong()
            t =
                (t shr 26) + x_0 * y_1 + x_1 * y_0 + 19 * (x_2 * y_9 + x_3 * y_8 + x_4 * y_7 + x_5 * y_6 + x_6 * y_5 + x_7 * y_4 + x_8 * y_3 + x_9 * y_2)
            xy._1 = t and ((1 shl 25) - 1).toLong()
            t =
                (t shr 25) + x_0 * y_2 + x_2 * y_0 + 19 * (x_4 * y_8 + x_6 * y_6 + x_8 * y_4) + 2 * (x_1 * y_1) + 38 * (x_3 * y_9 + x_5 * y_7 + x_7 * y_5 + x_9 * y_3)
            xy._2 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x_0 * y_3 + x_1 * y_2 + x_2 * y_1 + x_3 * y_0 + 19 * (x_4 * y_9 + x_5 * y_8 + x_6 * y_7 + x_7 * y_6 + x_8 * y_5 + x_9 * y_4)
            xy._3 = t and ((1 shl 25) - 1).toLong()
            t =
                (t shr 25) + x_0 * y_4 + x_2 * y_2 + x_4 * y_0 + 19 * (x_6 * y_8 + x_8 * y_6) + 2 * (x_1 * y_3 + x_3 * y_1) + 38 * (x_5 * y_9 + x_7 * y_7 + x_9 * y_5)
            xy._4 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x_0 * y_5 + x_1 * y_4 + x_2 * y_3 + x_3 * y_2 + x_4 * y_1 + x_5 * y_0 + 19 * (x_6 * y_9 + x_7 * y_8 + x_8 * y_7 + x_9 * y_6)
            xy._5 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x_0 * y_6 + x_2 * y_4 + x_4 * y_2 + x_6 * y_0 + 19 * (x_8 * y_8) + 2 * (x_1 * y_5 + x_3 * y_3 + x_5 * y_1) + 38 * (x_7 * y_9 + x_9 * y_7)
            xy._6 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + x_0 * y_7 + x_1 * y_6 + x_2 * y_5 + x_3 * y_4 + x_4 * y_3 + x_5 * y_2 + x_6 * y_1 + x_7 * y_0 + 19 * (x_8 * y_9 + x_9 * y_8)
            xy._7 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + xy._8
            xy._8 = t and ((1 shl 26) - 1).toLong()
            xy._9 += t shr 26
            return xy
        }

        /* Square a number.  Optimization of  mul25519(x2, x, x)  */
        private fun sqr(x2: long10, x: long10): long10 {
            val x_0 = x._0
            val x_1 = x._1
            val x_2 = x._2
            val x_3 = x._3
            val x_4 = x._4
            val x_5 = x._5
            val x_6 = x._6
            val x_7 = x._7
            val x_8 = x._8
            val x_9 = x._9
            var t: Long
            t = x_4 * x_4 + 2 * (x_0 * x_8 + x_2 * x_6) + 38 * (x_9 * x_9) + 4 * (x_1 * x_7 + x_3 * x_5)
            x2._8 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + 2 * (x_0 * x_9 + x_1 * x_8 + x_2 * x_7 + x_3 * x_6 + x_4 * x_5)
            x2._9 = t and ((1 shl 25) - 1).toLong()
            t = 19 * (t shr 25) + x_0 * x_0 + 38 * (x_2 * x_8 + x_4 * x_6 + x_5 * x_5) + 76 * (x_1 * x_9 + x_3 * x_7)
            x2._0 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + 2 * (x_0 * x_1) + 38 * (x_2 * x_9 + x_3 * x_8 + x_4 * x_7 + x_5 * x_6)
            x2._1 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + 19 * (x_6 * x_6) + 2 * (x_0 * x_2 + x_1 * x_1) + 38 * (x_4 * x_8) + 76 * (x_3 * x_9 + x_5 * x_7)
            x2._2 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + 2 * (x_0 * x_3 + x_1 * x_2) + 38 * (x_4 * x_9 + x_5 * x_8 + x_6 * x_7)
            x2._3 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x_2 * x_2 + 2 * (x_0 * x_4) + 38 * (x_6 * x_8 + x_7 * x_7) + 4 * (x_1 * x_3) + 76 * (x_5 * x_9)
            x2._4 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + 2 * (x_0 * x_5 + x_1 * x_4 + x_2 * x_3) + 38 * (x_6 * x_9 + x_7 * x_8)
            x2._5 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + 19 * (x_8 * x_8) + 2 * (x_0 * x_6 + x_2 * x_4 + x_3 * x_3) + 4 * (x_1 * x_5) + 76 * (x_7 * x_9)
            x2._6 = t and ((1 shl 26) - 1).toLong()
            t = (t shr 26) + 2 * (x_0 * x_7 + x_1 * x_6 + x_2 * x_5 + x_3 * x_4) + 38 * (x_8 * x_9)
            x2._7 = t and ((1 shl 25) - 1).toLong()
            t = (t shr 25) + x2._8
            x2._8 = t and ((1 shl 26) - 1).toLong()
            x2._9 += t shr 26
            return x2
        }

        /* Calculates a reciprocal.  The output is in reduced form, the inputs need not
     * be.  Simply calculates  y = x^(p-2)  so it's not too fast. */
        /* When sqrtassist is true, it instead calculates y = x^((p-5)/8) */
        private fun recip(y: long10, x: long10, sqrtassist: Int) {
            val t0 = long10()
            val t1 = long10()
            val t2 = long10()
            val t3 = long10()
            val t4 = long10()
            var i: Int
            /* the chain for x^(2^255-21) is straight from djb's implementation */sqr(t1, x) /*  2 == 2 * 1	*/
            sqr(t2, t1) /*  4 == 2 * 2	*/
            sqr(t0, t2) /*  8 == 2 * 4	*/
            mul(t2, t0, x) /*  9 == 8 + 1	*/
            mul(t0, t2, t1) /* 11 == 9 + 2	*/
            sqr(t1, t0) /* 22 == 2 * 11	*/
            mul(t3, t1, t2) /* 31 == 22 + 9
					== 2^5   - 2^0	*/
            sqr(t1, t3) /* 2^6   - 2^1	*/
            sqr(t2, t1) /* 2^7   - 2^2	*/
            sqr(t1, t2) /* 2^8   - 2^3	*/
            sqr(t2, t1) /* 2^9   - 2^4	*/
            sqr(t1, t2) /* 2^10  - 2^5	*/
            mul(t2, t1, t3) /* 2^10  - 2^0	*/
            sqr(t1, t2) /* 2^11  - 2^1	*/
            sqr(t3, t1) /* 2^12  - 2^2	*/
            i = 1
            while (i < 5) {
                sqr(t1, t3)
                sqr(t3, t1)
                i++
            }
            mul(t1, t3, t2) /* 2^20  - 2^0	*/
            sqr(t3, t1) /* 2^21  - 2^1	*/
            sqr(t4, t3) /* 2^22  - 2^2	*/
            i = 1
            while (i < 10) {
                sqr(t3, t4)
                sqr(t4, t3)
                i++
            }
            mul(t3, t4, t1) /* 2^40  - 2^0	*/
            i = 0
            while (i < 5) {
                sqr(t1, t3)
                sqr(t3, t1)
                i++
            }
            mul(t1, t3, t2) /* 2^50  - 2^0	*/
            sqr(t2, t1) /* 2^51  - 2^1	*/
            sqr(t3, t2) /* 2^52  - 2^2	*/
            i = 1
            while (i < 25) {
                sqr(t2, t3)
                sqr(t3, t2)
                i++
            }
            mul(t2, t3, t1) /* 2^100 - 2^0	*/
            sqr(t3, t2) /* 2^101 - 2^1	*/
            sqr(t4, t3) /* 2^102 - 2^2	*/
            i = 1
            while (i < 50) {
                sqr(t3, t4)
                sqr(t4, t3)
                i++
            }
            mul(t3, t4, t2) /* 2^200 - 2^0	*/
            i = 0
            while (i < 25) {
                sqr(t4, t3)
                sqr(t3, t4)
                i++
            }
            mul(t2, t3, t1) /* 2^250 - 2^0	*/
            sqr(t1, t2) /* 2^251 - 2^1	*/
            sqr(t2, t1) /* 2^252 - 2^2	*/
            if (sqrtassist != 0) {
                mul(y, x, t2) /* 2^252 - 3 */
            } else {
                sqr(t1, t2) /* 2^253 - 2^3	*/
                sqr(t2, t1) /* 2^254 - 2^4	*/
                sqr(t1, t2) /* 2^255 - 2^5	*/
                mul(y, t1, t0) /* 2^255 - 21	*/
            }
        }

        /* checks if x is "negative", requires reduced input */
        private fun is_negative(x: long10): Int {
            return ((if (is_overflow(x) || x._9 < 0) 1 else 0).toLong() xor (x._0 and 1L)).toInt()
        }

        /* a square root */
        private fun sqrt(x: long10, u: long10) {
            val v = long10()
            val t1 = long10()
            val t2 = long10()
            add(t1, u, u) /* t1 = 2u		*/
            recip(v, t1, 1) /* v = (2u)^((p-5)/8)	*/
            sqr(x, v) /* x = v^2		*/
            mul(t2, t1, x) /* t2 = 2uv^2		*/
            t2._0-- /* t2 = 2uv^2-1		*/
            mul(t1, v, t2) /* t1 = v(2uv^2-1)	*/
            mul(x, u, t1) /* x = uv(2uv^2-1)	*/
        }

        /********************* Elliptic curve  */ /* y^2 = x^3 + 486662 x^2 + x  over GF(2^255-19) */ /* t1 = ax + az
	 * t2 = ax - az  */
        private fun mont_prep(t1: long10, t2: long10, ax: long10, az: long10) {
            add(t1, ax, az)
            sub(t2, ax, az)
        }

        /* A = P + Q   where
     *  X(A) = ax/az
     *  X(P) = (t1+t2)/(t1-t2)
     *  X(Q) = (t3+t4)/(t3-t4)
     *  X(P-Q) = dx
     * clobbers t1 and t2, preserves t3 and t4  */
        private fun mont_add(t1: long10, t2: long10, t3: long10, t4: long10, ax: long10, az: long10, dx: long10) {
            mul(ax, t2, t3)
            mul(az, t1, t4)
            add(t1, ax, az)
            sub(t2, ax, az)
            sqr(ax, t1)
            sqr(t1, t2)
            mul(az, t1, dx)
        }

        /* B = 2 * Q   where
     *  X(B) = bx/bz
     *  X(Q) = (t3+t4)/(t3-t4)
     * clobbers t1 and t2, preserves t3 and t4  */
        private fun mont_dbl(t1: long10, t2: long10, t3: long10, t4: long10, bx: long10, bz: long10) {
            sqr(t1, t3)
            sqr(t2, t4)
            mul(bx, t1, t2)
            sub(t2, t1, t2)
            mul_small(bz, t2, 121665)
            add(t1, t1, bz)
            mul(bz, t1, t2)
        }

        /* Y^2 = X^3 + 486662 X^2 + X
     * t is a temporary  */
        private fun x_to_y2(t: long10, y2: long10, x: long10) {
            sqr(t, x)
            mul_small(y2, x, 486662)
            add(t, t, y2)
            t._0++
            mul(y2, t, x)
        }

        /* P = kG   and  s = sign(P)/k  */
        private fun core(Px: ByteArray, s: ByteArray?, k: ByteArray, Gx: ByteArray?) {
            val dx = long10()
            val t1 = long10()
            val t2 = long10()
            val t3 = long10()
            val t4 = long10()
            val x = arrayOf(long10(), long10())
            val z = arrayOf(long10(), long10())
            var i: Int
            var j: Int

            /* unpack the base */if (Gx != null) unpack(dx, Gx) else Companion[dx] = 9

            /* 0G = point-at-infinity */Companion[x[0]] = 1
            Companion[z[0]] = 0

            /* 1G = G */cpy(x[1], dx)
            Companion[z[1]] = 1
            i = 32
            while (i-- != 0) {
                j = 8
                while (j-- != 0) {

                    /* swap arguments depending on bit */
                    val bit1 = k[i].toInt() and 0xFF shr j and 1
                    val bit0 = (k[i].toInt() and 0xFF).inv() shr j and 1
                    val ax = x[bit0]
                    val az = z[bit0]
                    val bx = x[bit1]
                    val bz = z[bit1]

                    /* a' = a + b	*/
                    /* b' = 2 b	*/mont_prep(t1, t2, ax, az)
                    mont_prep(t3, t4, bx, bz)
                    mont_add(t1, t2, t3, t4, ax, az, dx)
                    mont_dbl(t1, t2, t3, t4, bx, bz)
                }
            }
            recip(t1, z[0], 0)
            mul(dx, x[0], t1)
            pack(dx, Px)

            /* calculate s such that s abs(P) = G  .. assumes G is std base point */if (s != null) {
                x_to_y2(t2, t1, dx) /* t1 = Py^2  */
                recip(t3, z[1], 0) /* where Q=P+G ... */
                mul(t2, x[1], t3) /* t2 = Qx  */
                add(t2, t2, dx) /* t2 = Qx + Px  */
                t2._0 += (9 + 486662).toLong() /* t2 = Qx + Px + Gx + 486662  */
                dx._0 -= 9 /* dx = Px - Gx  */
                sqr(t3, dx) /* t3 = (Px - Gx)^2  */
                mul(dx, t2, t3) /* dx = t2 (Px - Gx)^2  */
                sub(dx, dx, t1) /* dx = t2 (Px - Gx)^2 - Py^2  */
                dx._0 -= 39420360 /* dx = t2 (Px - Gx)^2 - Py^2 - Gy^2  */
                mul(t1, dx, BASE_R2Y) /* t1 = -Py  */
                if (is_negative(t1) != 0) /* sign is 1, so just copy  */ cpy32(s, k) else  /* sign is -1, so negate  */ mula_small(
                    s,
                    ORDER_TIMES_8,
                    0,
                    k,
                    32,
                    -1
                )

                /* reduce s mod q
			 * (is this needed?  do it just in case, it's fast anyway) */
                //divmod((dstptr) t1, s, 32, order25519, 32);

                /* take reciprocal of s mod q */
                val temp1 = ByteArray(32)
                val temp2 = ByteArray(64)
                val temp3 = ByteArray(64)
                cpy32(temp1, ORDER)
                cpy32(s, egcd32(temp2, temp3, s, temp1))
                if (s[31].toInt() and 0x80 != 0) mula_small(s, s, 0, ORDER, 32, 1)
            }
        }

        /* smallest multiple of the order that's >= 2^255 */
        private val ORDER_TIMES_8 = byteArrayOf(
            104.toByte(),
            159.toByte(),
            174.toByte(),
            231.toByte(),
            210.toByte(),
            24.toByte(),
            147.toByte(),
            192.toByte(),
            178.toByte(),
            230.toByte(),
            188.toByte(),
            23.toByte(),
            245.toByte(),
            206.toByte(),
            247.toByte(),
            166.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            128.toByte()
        )

        /* constants 2Gy and 1/(2Gy) */
        private val BASE_2Y = long10(
            39999547, 18689728, 59995525, 1648697, 57546132,
            24010086, 19059592, 5425144, 63499247, 16420658
        )
        private val BASE_R2Y = long10(
            5744, 8160848, 4790893, 13779497, 35730846,
            12541209, 49101323, 30047407, 40071253, 6226132
        )
    }
}