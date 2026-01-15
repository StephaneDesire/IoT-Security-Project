package com.firesystem.app.crypto

/**
 * ASCON-128 AEAD - Byte-array based implementation matching ESP32
 * 
 * ESP32 memory layout (little-endian):
 * - 64-bit value 0x0102030405060708 stored as bytes: 08 07 06 05 04 03 02 01
 * - posn=7 accesses the byte at offset 7, which is 0x01 (MSB as integer)
 * - posn=0 accesses the byte at offset 0, which is 0x08 (LSB as integer)
 * 
 * This implementation uses byte arrays to exactly match ESP32 memory access patterns.
 */
class Ascon128 {
    // State as byte arrays (matching ESP32 little-endian memory layout)
    private val S = ByteArray(40)  // 5 x 8 bytes
    private val K = ByteArray(16)  // 2 x 8 bytes
    private var posn: Int = 7
    private var authMode: Int = 1

    fun clear() {
        S.fill(0)
        K.fill(0)
        posn = 7
        authMode = 1
    }

    fun setKey(key: ByteArray, len: Int): Boolean {
        if (len != 16) return false
        // ESP32: memcpy then be64toh
        // be64toh converts big-endian bytes to little-endian storage
        // Key bytes {01,02,03,04,05,06,07,08} → stored as {08,07,06,05,04,03,02,01}
        for (i in 0..7) {
            K[7 - i] = key[i]
            K[15 - i] = key[8 + i]
        }
        return true
    }

    fun setIV(iv: ByteArray, len: Int): Boolean {
        if (len != 16) return false
        
        // S[0] = 0x80400C0600000000 in little-endian storage
        storeLongLE(S, 0, 0x80400C0600000000UL.toLong())
        
        // S[1] = K[0], S[2] = K[1] (copy key bytes)
        System.arraycopy(K, 0, S, 8, 8)
        System.arraycopy(K, 8, S, 16, 8)
        
        // S[3], S[4] = IV with be64toh (swap to little-endian storage)
        for (i in 0..7) {
            S[24 + 7 - i] = iv[i]
            S[32 + 7 - i] = iv[8 + i]
        }

        posn = 7
        authMode = 1

        permute(0)

        // S[3] ^= K[0], S[4] ^= K[1]
        for (i in 0..7) {
            S[24 + i] = (S[24 + i].toInt() xor K[i].toInt()).toByte()
            S[32 + i] = (S[32 + i].toInt() xor K[8 + i].toInt()).toByte()
        }

        return true
    }

    fun addAuthData(data: ByteArray, len: Int) {
        if (authMode == 0) return

        for (i in 0 until len) {
            // XOR byte at position posn within S[0] (bytes 0-7)
            S[posn] = (S[posn].toInt() xor data[i].toInt()).toByte()

            if (posn > 0) {
                posn--
            } else {
                permute(6)
                posn = 7
            }
        }
        authMode = 2
    }

    fun encrypt(output: ByteArray, input: ByteArray, len: Int) {
        if (authMode != 0) endAuth()

        for (i in 0 until len) {
            S[posn] = (S[posn].toInt() xor input[i].toInt()).toByte()
            output[i] = S[posn]

            if (posn > 0) {
                posn--
            } else {
                permute(6)
                posn = 7
            }
        }
    }

    fun decrypt(output: ByteArray, input: ByteArray, len: Int) {
        if (authMode != 0) endAuth()

        for (i in 0 until len) {
            output[i] = (S[posn].toInt() xor input[i].toInt()).toByte()
            S[posn] = input[i]

            if (posn > 0) {
                posn--
            } else {
                permute(6)
                posn = 7
            }
        }
    }

    fun computeTag(tag: ByteArray, len: Int) {
        if (authMode != 0) endAuth()

        // Pad at current position
        S[posn] = (S[posn].toInt() xor 0x80).toByte()

        // S[1] ^= K[0], S[2] ^= K[1]
        for (i in 0..7) {
            S[8 + i] = (S[8 + i].toInt() xor K[i].toInt()).toByte()
            S[16 + i] = (S[16 + i].toInt() xor K[8 + i].toInt()).toByte()
        }

        permute(0)

        // Tag = htobe64(S[3] ^ K[0]) || htobe64(S[4] ^ K[1])
        // htobe64 converts from little-endian storage to big-endian output
        for (i in 0..7) {
            val s3k0 = S[24 + i].toInt() xor K[i].toInt()
            val s4k1 = S[32 + i].toInt() xor K[8 + i].toInt()
            tag[7 - i] = s3k0.toByte()
            if (len > 8) tag[15 - i] = s4k1.toByte()
        }
    }

    fun checkTag(tag: ByteArray, len: Int): Boolean {
        if (len > 16) return false
        if (authMode != 0) endAuth()

        S[posn] = (S[posn].toInt() xor 0x80).toByte()
        for (i in 0..7) {
            S[8 + i] = (S[8 + i].toInt() xor K[i].toInt()).toByte()
            S[16 + i] = (S[16 + i].toInt() xor K[8 + i].toInt()).toByte()
        }
        permute(0)

        // Compute expected tag
        val computed = ByteArray(16)
        for (i in 0..7) {
            val s3k0 = S[24 + i].toInt() xor K[i].toInt()
            val s4k1 = S[32 + i].toInt() xor K[8 + i].toInt()
            computed[7 - i] = s3k0.toByte()
            computed[15 - i] = s4k1.toByte()
        }

        var diff = 0
        for (i in 0 until len) {
            diff = diff or (computed[i].toInt() xor tag[i].toInt())
        }
        return diff == 0
    }

    private fun endAuth() {
        if (authMode == 2) {
            S[posn] = (S[posn].toInt() xor 0x80).toByte()
            permute(6)
        }
        // S[4] ^= 1 - LSB of S[4] is at byte offset 32 (little-endian)
        S[32] = (S[32].toInt() xor 1).toByte()
        authMode = 0
        posn = 7
    }

    private fun permute(first: Int) {
        // Load state as 64-bit values (little-endian)
        var x0 = loadLongLE(S, 0)
        var x1 = loadLongLE(S, 8)
        var x2 = loadLongLE(S, 16)
        var x3 = loadLongLE(S, 24)
        var x4 = loadLongLE(S, 32)

        var round = first
        while (round < 12) {
            // Round constant
            x2 = x2 xor (((0x0F - round) shl 4) or round).toLong()

            // S-box layer
            x0 = x0 xor x4; x4 = x4 xor x3; x2 = x2 xor x1
            val t0 = x0.inv() and x1
            val t1 = x1.inv() and x2
            val t2 = x2.inv() and x3
            val t3 = x3.inv() and x4
            val t4 = x4.inv() and x0
            x0 = x0 xor t1; x1 = x1 xor t2; x2 = x2 xor t3; x3 = x3 xor t4; x4 = x4 xor t0
            x1 = x1 xor x0; x0 = x0 xor x4; x3 = x3 xor x2; x2 = x2.inv()

            // Linear diffusion layer
            x0 = x0 xor ror(x0, 19) xor ror(x0, 28)
            x1 = x1 xor ror(x1, 61) xor ror(x1, 39)
            x2 = x2 xor ror(x2, 1) xor ror(x2, 6)
            x3 = x3 xor ror(x3, 10) xor ror(x3, 17)
            x4 = x4 xor ror(x4, 7) xor ror(x4, 41)

            round++
        }

        // Store back
        storeLongLE(S, 0, x0)
        storeLongLE(S, 8, x1)
        storeLongLE(S, 16, x2)
        storeLongLE(S, 24, x3)
        storeLongLE(S, 32, x4)
    }

    private fun ror(v: Long, n: Int) = (v ushr n) or (v shl (64 - n))

    private fun loadLongLE(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xFF) or
               ((b[off+1].toLong() and 0xFF) shl 8) or
               ((b[off+2].toLong() and 0xFF) shl 16) or
               ((b[off+3].toLong() and 0xFF) shl 24) or
               ((b[off+4].toLong() and 0xFF) shl 32) or
               ((b[off+5].toLong() and 0xFF) shl 40) or
               ((b[off+6].toLong() and 0xFF) shl 48) or
               ((b[off+7].toLong() and 0xFF) shl 56)
    }

    private fun storeLongLE(b: ByteArray, off: Int, v: Long) {
        b[off]   = (v and 0xFF).toByte()
        b[off+1] = ((v shr 8) and 0xFF).toByte()
        b[off+2] = ((v shr 16) and 0xFF).toByte()
        b[off+3] = ((v shr 24) and 0xFF).toByte()
        b[off+4] = ((v shr 32) and 0xFF).toByte()
        b[off+5] = ((v shr 40) and 0xFF).toByte()
        b[off+6] = ((v shr 48) and 0xFF).toByte()
        b[off+7] = ((v shr 56) and 0xFF).toByte()
    }
}
