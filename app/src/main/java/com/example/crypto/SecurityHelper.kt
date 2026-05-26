package com.example.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityHelper {
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    fun getFingerprint(publicKeyString: String): String {
        return try {
            val bytes = Base64.decode(publicKeyString, Base64.DEFAULT)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.take(6).joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun stringToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(spec)
    }

    fun privateKeyToString(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    fun stringToPrivateKey(privateKeyString: String): PrivateKey {
        val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePrivate(spec)
    }

    /**
     * E2EE Encryption Process:
     * 1. Generate a random 256-bit AES symmetric session key.
     * 2. Encrypt the plaintext with this AES key (using standard CBC padding and a random IV).
     * 3. Encrypt the symmetric AES key using the recipient's RSA Public Key.
     * 4. Return the wrapped Base64 components.
     */
    fun encryptPayload(
        plainText: String,
        recipientPublicKeyString: String
    ): EncryptedPackage {
        val recipientPublicKey = stringToPublicKey(recipientPublicKeyString)

        // 1. Generate AES Session Key
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        // 2. Encrypt main payload with AES
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = aesCipher.iv
        val encryptedPayloadBytes = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encryptedPayloadString = Base64.encodeToString(encryptedPayloadBytes, Base64.NO_WRAP)
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)

        // 3. Encrypt AES secret key with Recipient's RSA Public Key
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedSecretKeyBytes = rsaCipher.doFinal(secretKey.encoded)
        val encryptedSecretKeyString = Base64.encodeToString(encryptedSecretKeyBytes, Base64.NO_WRAP)

        return EncryptedPackage(
            encryptedPayload = encryptedPayloadString,
            encryptedAesKey = encryptedSecretKeyString,
            iv = ivString
        )
    }

    /**
     * E2EE Decryption Process:
     * 1. Decrypt the transient AES session key using the local RSA Private Key.
     * 2. Decrypt the payload with the AES key and IV.
     */
    fun decryptPayload(
        encryptedPackage: EncryptedPackage,
        privateKeyString: String
    ): String {
        val privateKey = stringToPrivateKey(privateKeyString)

        // 1. Decrypt the AES Session Key with the RSA Private Key
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decryptedSecretKeyBytes = rsaCipher.doFinal(Base64.decode(encryptedPackage.encryptedAesKey, Base64.NO_WRAP))
        val secretKeySpec = SecretKeySpec(decryptedSecretKeyBytes, "AES")

        // 2. Decrypt the payload using the SecretKey and IV
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = Base64.decode(encryptedPackage.iv, Base64.NO_WRAP)
        aesCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val decryptedBytes = aesCipher.doFinal(Base64.decode(encryptedPackage.encryptedPayload, Base64.NO_WRAP))

        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Integrity Signatures: Ensure that messages cannot be tampered with in-transit
     */
    fun signPayload(payload: String, privateKeyString: String): String {
        val privateKey = stringToPrivateKey(privateKeyString)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(payload.toByteArray(Charsets.UTF_8))
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    fun verifySignature(payload: String, signatureString: String, publicKeyString: String): Boolean {
        return try {
            val publicKey = stringToPublicKey(publicKeyString)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(payload.toByteArray(Charsets.UTF_8))
            val signatureBytes = Base64.decode(signatureString, Base64.NO_WRAP)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}

data class EncryptedPackage(
    val encryptedPayload: String,
    val encryptedAesKey: String,
    val iv: String
)
