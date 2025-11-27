package com.sunbay.softpos.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec

/**
 * Manages RSA key pair generation and storage using Android Keystore
 * Keys are stored securely and never leave the secure hardware (if available)
 */
class KeyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "KeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sunbay_softpos_device_key"
        private const val KEY_SIZE = 2048
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }
    
    /**
     * Get or create RSA public key in Base64 format
     * If key doesn't exist, generates a new key pair
     * @return Base64 encoded public key
     */
    fun getOrCreatePublicKey(): String {
        return try {
            val publicKey = if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.d(TAG, "Using existing key pair")
                getExistingPublicKey()
            } else {
                Log.d(TAG, "Generating new key pair")
                generateKeyPair()
            }
            
            // Encode public key to Base64
            val encoded = publicKey.encoded
            Base64.encodeToString(encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating public key", e)
            throw SecurityException("Failed to get or create public key", e)
        }
    }
    
    /**
     * Get existing public key from keystore
     */
    private fun getExistingPublicKey(): PublicKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Key entry not found")
        
        return entry.certificate.publicKey
    }
    
    /**
     * Generate new RSA key pair in Android Keystore
     * Uses hardware-backed storage if available (StrongBox or TEE)
     */
    private fun generateKeyPair(): PublicKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )
        
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE)
            .setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setUserAuthenticationRequired(false) // Don't require user authentication for each use
        
        // Try to use StrongBox if available (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
                    builder.setIsStrongBoxBacked(true)
                    Log.d(TAG, "Using StrongBox-backed key storage")
                }
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox not available, using TEE", e)
            }
        }
        
        val spec = builder.build()
        keyPairGenerator.initialize(spec)
        
        val keyPair = keyPairGenerator.generateKeyPair()
        Log.d(TAG, "Key pair generated successfully")
        
        return keyPair.public
    }
    
    /**
     * Get private key for signing operations
     * Note: Private key never leaves the secure hardware
     */
    fun getPrivateKey(): PrivateKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Private key not found")
        
        return entry.privateKey
    }
    
    /**
     * Delete the key pair from keystore
     * Useful for testing or key rotation
     */
    fun deleteKeyPair() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
            Log.d(TAG, "Key pair deleted")
        }
    }
    
    /**
     * Check if key pair exists
     */
    fun hasKeyPair(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }
}
