package com.example.data

import com.example.crypto.EncryptedPackage
import com.example.crypto.SecurityHelper
import com.example.network.P2PClient
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class ChatRepository(
    private val identityDao: IdentityDao,
    private val peerDao: PeerDao,
    private val messageDao: MessageDao,
    private val serverLogDao: ServerLogDao
) {
    val allPeers: Flow<List<Peer>> = peerDao.getAllPeers()
    val logs: Flow<List<ServerLog>> = serverLogDao.getLogs()

    fun getMessagesForPeer(peerFingerprint: String): Flow<List<Message>> {
        return messageDao.getMessagesForPeer(peerFingerprint)
    }

    suspend fun getIdentity(): Identity? {
        return identityDao.getIdentityOnce()
    }

    suspend fun getOrCreateIdentity(alias: String = "VaultNode", port: Int = 8282): Identity {
        val existing = identityDao.getIdentityOnce()
        if (existing != null) return existing

        val keyPair = SecurityHelper.generateKeyPair()
        val pubStr = SecurityHelper.publicKeyToString(keyPair.public)
        val privStr = SecurityHelper.privateKeyToString(keyPair.private)
        val fingerprint = SecurityHelper.getFingerprint(pubStr)

        val newIdentity = Identity(
            publicKey = pubStr,
            privateKey = privStr,
            fingerprint = fingerprint,
            serverPort = port,
            alias = alias
        )
        identityDao.insertOrUpdateIdentity(newIdentity)
        log("Cryptographic Keypair generated! Fingerprint: $fingerprint", "SUCCESS")
        return newIdentity
    }

    suspend fun updateIdentity(identity: Identity) {
        identityDao.insertOrUpdateIdentity(identity)
        log("Identity updated. Alias set to '${identity.alias}' and Port to ${identity.serverPort}", "SUCCESS")
    }

    suspend fun resolveUniqueAlias(candidateAlias: String, fingerprint: String): String {
        val peers = peerDao.getAllPeersOnce()
        val ownIdentity = getIdentity()

        val existingAliases = peers
            .filter { it.fingerprint != fingerprint }
            .map { it.alias.lowercase().trim() }
            .toMutableSet()

        ownIdentity?.let { existingAliases.add(it.alias.lowercase().trim()) }

        if (!existingAliases.contains(candidateAlias.lowercase().trim())) {
            return candidateAlias.trim()
        }

        // Collision detected! Generate a unique random cryptographic name
        val adjectives = listOf("Cyber", "Stealth", "Onion", "Sentinel", "Quantum", "Omega", "Helix", "Cryptic", "Shadow", "Vector", "Cosmic", "Ghost", "Aero", "Pulse", "Vortex")
        val nouns = listOf("Node", "Vault", "Peer", "Grid", "Core", "Daemon", "Sentry", "Aether", "Pulse", "Matrix", "Beacon", "Relay", "Spectre", "Cipher")

        var uniqueAlias = candidateAlias.trim()
        var attempts = 0
        while ((existingAliases.contains(uniqueAlias.lowercase().trim()) || uniqueAlias.isBlank()) && attempts < 100) {
            val randomAdjective = adjectives.random()
            val randomNoun = nouns.random()
            val randomHex = (1000..9999).random()
            uniqueAlias = "${randomAdjective}${randomNoun}_$randomHex"
            attempts++
        }
        return uniqueAlias
    }

    suspend fun addPeer(peer: Peer) {
        val uniqueAlias = resolveUniqueAlias(peer.alias, peer.fingerprint)
        val processedPeer = if (uniqueAlias != peer.alias) {
            log("Alias collision detected for '${peer.alias}'. Safe unique alias auto-generated: '$uniqueAlias'", "WARNING")
            peer.copy(alias = uniqueAlias)
        } else {
            peer
        }
        peerDao.insertOrUpdatePeer(processedPeer)
        log("Peer saved: ${processedPeer.alias} (${processedPeer.fingerprint}) at ${processedPeer.host}:${processedPeer.port}", "SUCCESS")
    }

    suspend fun removePeer(peer: Peer) {
        peerDao.deletePeer(peer)
        messageDao.clearChatHistory(peer.fingerprint)
        log("Peer '${peer.alias}' and their local chat histories wiped from database.", "SUCCESS")
    }

    suspend fun log(message: String, type: String = "INFO") {
        serverLogDao.insertLog(ServerLog(message = message, type = type))
    }

    suspend fun clearLogs() {
        serverLogDao.clearLogs()
    }

    /**
     * E2EE Outbox Message Dispatching Pipeline
     */
    suspend fun sendEncryptedMessage(peer: Peer, plainText: String): Boolean {
        val identity = identityDao.getIdentityOnce() ?: return false
        try {
            log("Packaging message for E2EE delivery...", "INFO")
            
            // 1. Core E2EE: Encrypt plaintext using recipient's Public Key
            val encryptedPkg = SecurityHelper.encryptPayload(plainText, peer.publicKey)
            
            // 2. Core Integrity: Sign the ENCRYPTED payload using sender's Private Key
            val signature = SecurityHelper.signPayload(encryptedPkg.encryptedPayload, identity.privateKey)

            // 3. Assemble decentralized standalone JSON payload
            val jsonPayload = JSONObject().apply {
                put("senderFingerprint", identity.fingerprint)
                put("senderPublicKey", identity.publicKey)
                put("senderAlias", identity.alias)
                put("senderPort", identity.serverPort)
                put("encryptedPayload", encryptedPkg.encryptedPayload)
                put("encryptedAesKey", encryptedPkg.encryptedAesKey)
                put("iv", encryptedPkg.iv)
                put("signature", signature)
            }.toString()

            // 4. Record to local isolated storage (outgoing history)
            val msgRecord = Message(
                peerFingerprint = peer.fingerprint,
                isIncoming = false,
                encryptedPayload = encryptedPkg.encryptedPayload,
                encryptedAesKey = encryptedPkg.encryptedAesKey,
                iv = encryptedPkg.iv,
                senderFingerprint = identity.fingerprint,
                signature = signature,
                isVerified = true
            )
            messageDao.insertMessage(msgRecord)

            // 5. Connect and stream directly over TLS/TCP socket to recipient
            log("Streaming E2EE frame strictly peer-to-peer to ${peer.host}:${peer.port}...", "NETWORK")
            val transportSuccess = P2PClient.sendMessage(peer.host, peer.port, jsonPayload)

            if (transportSuccess) {
                log("Delivery Confirmed to peer node '${peer.alias}'!", "SUCCESS")
                peerDao.insertOrUpdatePeer(peer.copy(lastActive = System.currentTimeMillis()))
                return true
            } else {
                log("Direct transport down. Message cached in secure database, will deliver when peer host is online.", "ERROR")
                return false
            }
        } catch (e: Exception) {
            log("E2EE pipeline failure: ${e.localizedMessage}", "ERROR")
            return false
        }
    }

    /**
     * E2EE Inbox Parsing & Authentication Pipeline
     */
    suspend fun handleIncomingPayload(jsonString: String, clientIp: String) {
        val identity = identityDao.getIdentityOnce() ?: return
        try {
            val json = JSONObject(jsonString)
            val senderFingerprint = json.getString("senderFingerprint")
            val senderPublicKey = json.getString("senderPublicKey")
            val senderAlias = json.getString("senderAlias")
            val senderPort = json.getInt("senderPort")
            val encryptedPayload = json.getString("encryptedPayload")
            val encryptedAesKey = json.getString("encryptedAesKey")
            val iv = json.getString("iv")
            val signature = json.getString("signature")

            log("Server received direct payload from $clientIp:$senderPort. Fingerprint: $senderFingerprint", "NETWORK")

            // 1. Authenticate packet integrity
            val signatureVerified = SecurityHelper.verifySignature(
                payload = encryptedPayload,
                signatureString = signature,
                publicKeyString = senderPublicKey
            )

            if (!signatureVerified) {
                log("SECURITY ALERT: Cryptographic signature mismatch. Threat vector blocked! Dropping compromised in-transit packet.", "ERROR")
                return
            }

            log("Verification Success! Packet signature authenticated. Registering decentral storage block...", "SUCCESS")

            // 2. Auto-Discovery (Register or update sender's node details)
            val uniqueAlias = resolveUniqueAlias(senderAlias, senderFingerprint)
            if (uniqueAlias != senderAlias) {
                log("Discovered peer name collision ('$senderAlias'). Assigning unique alias: '$uniqueAlias'", "WARNING")
            }
            val autoDiscoveredPeer = Peer(
                fingerprint = senderFingerprint,
                alias = uniqueAlias,
                host = clientIp,
                port = senderPort,
                publicKey = senderPublicKey,
                lastActive = System.currentTimeMillis()
            )
            peerDao.insertOrUpdatePeer(autoDiscoveredPeer)

            // 3. Save received E2EE frame on disk
            val msgRecord = Message(
                peerFingerprint = senderFingerprint,
                isIncoming = true,
                encryptedPayload = encryptedPayload,
                encryptedAesKey = encryptedAesKey,
                iv = iv,
                senderFingerprint = senderFingerprint,
                signature = signature,
                isVerified = true
            )
            messageDao.insertMessage(msgRecord)
            log("E2EE payload sealed into independent decentralized storage history.", "SUCCESS")
        } catch (e: Exception) {
            log("De-serialization failure: corrupt incoming TCP socket stream. Core aborted. Error: ${e.localizedMessage}", "ERROR")
        }
    }
}
