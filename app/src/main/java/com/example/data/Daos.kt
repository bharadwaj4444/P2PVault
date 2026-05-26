package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity_table LIMIT 1")
    fun getIdentity(): Flow<Identity?>

    @Query("SELECT * FROM identity_table LIMIT 1")
    suspend fun getIdentityOnce(): Identity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateIdentity(identity: Identity)
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers_table ORDER BY lastActive DESC")
    fun getAllPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM peers_table")
    suspend fun getAllPeersOnce(): List<Peer>

    @Query("SELECT * FROM peers_table WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getPeerByFingerprint(fingerprint: String): Peer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: Peer)

    @Delete
    suspend fun deletePeer(peer: Peer)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages_table WHERE peerFingerprint = :peerFingerprint ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerFingerprint: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages_table WHERE peerFingerprint = :peerFingerprint")
    suspend fun clearChatHistory(peerFingerprint: String)
}

@Dao
interface ServerLogDao {
    @Query("SELECT * FROM logs_table ORDER BY timestamp DESC LIMIT 200")
    fun getLogs(): Flow<List<ServerLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ServerLog)

    @Query("DELETE FROM logs_table")
    suspend fun clearLogs()
}
