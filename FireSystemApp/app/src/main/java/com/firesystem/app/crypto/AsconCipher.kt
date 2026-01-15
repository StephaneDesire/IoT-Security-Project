package com.firesystem.app.crypto

/**
 * ASCON Cipher wrapper matching ESP32 code EXACTLY
 * 
 * ESP32 format: nonce(16 bytes) || ciphertext || tag(16 bytes) in HEX string
 * Key: 0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10
 * AAD: "BLE-ASCON-V1"
 * 
 * Uses same API as ESP32 Southern Storm Crypto library:
 * - setKey(), setIV(), addAuthData(), encrypt/decrypt(), computeTag/checkTag()
 */
class AsconCipher {

    // Key MUST match ESP32: uint8_t ASCON_KEY[16]
    private val key = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
    )

    // AAD MUST match ESP32: const char *AAD = "BLE-ASCON-V1"
    private val aad = "BLE-ASCON-V1".toByteArray(Charsets.US_ASCII)

    // Nonce counter for encryption (anti-replay)
    private var nonceCounter: Long = 0

    init {
        // Run self-test on initialization
        selfTest()
    }

    /**
     * Self-test: encrypt then decrypt to verify implementation works
     */
    private fun selfTest() {
        android.util.Log.d("AsconCipher", "=== SELF TEST START ===")
        
        val testPlaintext = "25.50"
        val testNonce = ByteArray(16).also { it[15] = 1 }
        
        val ascon = Ascon128()
        ascon.clear()
        ascon.setKey(key, 16)
        ascon.setIV(testNonce, 16)
        ascon.addAuthData(aad, aad.size)
        
        val plainBytes = testPlaintext.toByteArray(Charsets.US_ASCII)
        val cipherBytes = ByteArray(plainBytes.size)
        ascon.encrypt(cipherBytes, plainBytes, plainBytes.size)
        
        val tag = ByteArray(16)
        ascon.computeTag(tag, 16)
        
        android.util.Log.d("AsconCipher", "Test plaintext: '$testPlaintext'")
        android.util.Log.d("AsconCipher", "Encrypted: ${cipherBytes.joinToString("") { "%02x".format(it) }}")
        android.util.Log.d("AsconCipher", "Tag: ${tag.joinToString("") { "%02x".format(it) }}")
        
        // Now decrypt
        val ascon2 = Ascon128()
        ascon2.clear()
        ascon2.setKey(key, 16)
        ascon2.setIV(testNonce, 16)
        ascon2.addAuthData(aad, aad.size)
        
        val decrypted = ByteArray(cipherBytes.size)
        ascon2.decrypt(decrypted, cipherBytes, cipherBytes.size)
        
        val tagOk = ascon2.checkTag(tag, 16)
        val decryptedStr = String(decrypted, Charsets.US_ASCII)
        
        android.util.Log.d("AsconCipher", "Decrypted: '$decryptedStr'")
        android.util.Log.d("AsconCipher", "Tag OK: $tagOk")
        android.util.Log.d("AsconCipher", "Match: ${decryptedStr == testPlaintext}")
        android.util.Log.d("AsconCipher", "=== SELF TEST END ===")
    }

    /**
     * Generate nonce matching ESP32 format:
     * - First 12 bytes: 0x00
     * - Last 4 bytes: counter (big-endian)
     */
    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(16)
        nonce[12] = ((nonceCounter shr 24) and 0xFF).toByte()
        nonce[13] = ((nonceCounter shr 16) and 0xFF).toByte()
        nonce[14] = ((nonceCounter shr 8) and 0xFF).toByte()
        nonce[15] = (nonceCounter and 0xFF).toByte()
        nonceCounter++
        return nonce
    }

    /**
     * Encrypt plaintext string to hex format matching ESP32
     * Output: HEX string of (nonce || ciphertext || tag)
     * 
     * Matches ESP32 asconEncryptAEAD() function exactly
     */
    fun encrypt(plaintext: String): String {
        val ascon = Ascon128()
        val plaintextBytes = plaintext.toByteArray(Charsets.US_ASCII)
        val nonce = generateNonce()
        val ciphertext = ByteArray(plaintextBytes.size)
        val tag = ByteArray(16)

        // Initialize AEAD (same order as ESP32)
        ascon.clear()
        ascon.setKey(key, 16)
        ascon.setIV(nonce, 16)

        // Add AAD
        ascon.addAuthData(aad, aad.size)

        // Encrypt
        ascon.encrypt(ciphertext, plaintextBytes, plaintextBytes.size)

        // Compute tag
        ascon.computeTag(tag, 16)

        // Format: nonce || ciphertext || tag (as hex)
        return nonce.toHexString() + ciphertext.toHexString() + tag.toHexString()
    }

    /**
     * Decrypt hex string from ESP32
     * Input: HEX string of (nonce || ciphertext || tag)
     * Returns: decrypted string or null if authentication fails
     * 
     * Matches ESP32 asconDecryptAEAD() function exactly
     */
    fun decrypt(hexCipher: String): String? {
        return try {
            android.util.Log.d("AsconCipher", "decrypt() input: '$hexCipher' (len=${hexCipher.length})")
            
            val totalLen = hexCipher.length / 2
            android.util.Log.d("AsconCipher", "Total bytes: $totalLen")
            
            if (totalLen < 32) {
                android.util.Log.e("AsconCipher", "Data too short! Need at least 32 bytes (16 nonce + 16 tag)")
                return null
            }

            val raw = hexCipher.hexToByteArray()
            android.util.Log.d("AsconCipher", "Raw bytes: ${raw.joinToString(" ") { "%02x".format(it) }}")

            // Parse: nonce(16) || ciphertext(n) || tag(16)
            val nonce = raw.copyOfRange(0, 16)
            val cipherLen = totalLen - 32
            val ciphertext = raw.copyOfRange(16, 16 + cipherLen)
            val tag = raw.copyOfRange(16 + cipherLen, 16 + cipherLen + 16)

            android.util.Log.d("AsconCipher", "Nonce: ${nonce.joinToString("") { "%02x".format(it) }}")
            android.util.Log.d("AsconCipher", "Ciphertext ($cipherLen bytes): ${ciphertext.joinToString("") { "%02x".format(it) }}")
            android.util.Log.d("AsconCipher", "Tag: ${tag.joinToString("") { "%02x".format(it) }}")

            val plaintext = ByteArray(cipherLen)
            val ascon = Ascon128()

            // Initialize AEAD (same order as ESP32)
            ascon.clear()
            ascon.setKey(key, 16)
            ascon.setIV(nonce, 16)

            // Add AAD (must be identical)
            ascon.addAuthData(aad, aad.size)

            // Decrypt
            ascon.decrypt(plaintext, ciphertext, cipherLen)

            android.util.Log.d("AsconCipher", "Decrypted bytes: ${plaintext.joinToString("") { "%02x".format(it) }}")

            // Verify tag
            if (!ascon.checkTag(tag, 16)) {
                android.util.Log.e("AsconCipher", "TAG VERIFICATION FAILED!")
                return null
            }
            
            android.util.Log.d("AsconCipher", "Tag verified OK!")

            // Convert to string, trim null bytes
            val result = String(plaintext, Charsets.US_ASCII).trimEnd('\u0000')
            android.util.Log.d("AsconCipher", "Final result: '$result'")
            result
        } catch (e: Exception) {
            android.util.Log.e("AsconCipher", "Exception during decrypt: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Convert ByteArray to lowercase hex string (matching ESP32 format)
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            String.format("%02x", byte)
        }
    }

    /**
     * Convert hex string to ByteArray
     */
    private fun String.hexToByteArray(): ByteArray {
        val hex = this.lowercase()
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val index = i * 2
            hex.substring(index, index + 2).toInt(16).toByte()
        }
    }
}
