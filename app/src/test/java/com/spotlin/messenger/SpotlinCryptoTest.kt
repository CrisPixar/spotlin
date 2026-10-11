package com.spotlin.messenger

import com.spotlin.messenger.crypto.SpotlinCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class SpotlinCryptoTest {

    @Test
    fun testEncryptionAndDecryption() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        val plainText = "Spotlin - Защищенный школьный мессенджер"
        val payload = SpotlinCryptoEngine.encrypt(plainText, secretKey)

        assertNotNull(payload)
        assertTrue(payload.isNotEmpty())

        val decrypted = SpotlinCryptoEngine.decrypt(payload, secretKey)
        assertEquals(plainText, decrypted)
    }
}
