package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identity_table")
data class Identity(
    @PrimaryKey val id: Int = 1, // Only 1 local identity
    val publicKey: String,
    val privateKey: String,
    val fingerprint: String,
    val serverPort: Int = 8282,
    val alias: String = "VaultNode"
)

@Entity(tableName = "peers_table")
data class Peer(
    @PrimaryKey val fingerprint: String, // Calculated hash of public key
    val alias: String,
    val host: String,
    val port: Int,
    val publicKey: String,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages_table")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerFingerprint: String,
    val isIncoming: Boolean,
    val encryptedPayload: String, // Ciphertext in Base64
    val encryptedAesKey: String,   // Encrypted AES session key in Base64
    val iv: String,                // Initialization vector in Base64
    val senderFingerprint: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String,         // Sender's cryptographic sign
    val isVerified: Boolean = false // Sign verification status
)

@Entity(tableName = "logs_table")
data class ServerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: String = "INFO" // INFO, SUCCESS, ERROR, NETWORK
)
