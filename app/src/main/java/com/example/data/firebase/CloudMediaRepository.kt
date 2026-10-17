package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.MediaItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CloudMediaRepository(
    private val firestore: FirebaseFirestore
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val usersCollection = firestore.collection("users")

    /**
     * Updates or creates the user's profile metadata in Cloud Firestore.
     */
    suspend fun saveUserProfile(
        userId: String,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ) {
        val data = hashMapOf(
            "uid" to userId,
            "email" to (email ?: ""),
            "displayName" to (displayName ?: ""),
            "photoUrl" to (photoUrl ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            usersCollection.document(userId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "User profile synced to Firestore: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan user profile: ${e.message}", e)
            throw e
        }
    }

    /**
     * Saves or updates a media item into user's private cloud collection.
     */
    suspend fun saveMediaItem(userId: String, item: MediaItem) {
        val docRef = usersCollection.document(userId).collection("media").document(item.id)
        val payload = hashMapOf(
            "id" to item.id,
            "profileId" to item.profileId,
            "title" to item.title,
            "url" to item.url,
            "thumbnailUrl" to item.thumbnailUrl,
            "type" to item.type,
            "sourceType" to item.sourceType,
            "category" to item.category,
            "playlistId" to item.playlistId,
            "sourceUrl" to item.sourceUrl,
            "durationMs" to item.durationMs,
            "progressMs" to item.progressMs,
            "isFavorite" to item.isFavorite,
            "createdAt" to item.createdAt,
            "extraInfo" to item.extraInfo,
            "syncedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Media item synced: ${item.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sync media item: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a media item from user's cloud collection.
     */
    suspend fun deleteMediaItem(userId: String, mediaId: String) {
        try {
            usersCollection.document(userId).collection("media").document(mediaId).delete().await()
            Log.d(TAG, "Media item deleted from Firestore: $mediaId")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal hapus media item dari Firestore: ${e.message}", e)
            throw e
        }
    }

    /**
     * Fetches all media items for the user from Firestore.
     */
    suspend fun fetchMediaItems(userId: String): List<MediaItem> {
        return try {
            val snapshot = usersCollection.document(userId).collection("media").get().await()
            snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                val url = doc.getString("url") ?: return@mapNotNull null
                MediaItem(
                    id = id,
                    profileId = doc.getString("profileId") ?: "profile_utama",
                    title = title,
                    url = url,
                    thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                    type = doc.getString("type") ?: "VIDEO",
                    sourceType = doc.getString("sourceType") ?: "LINK",
                    category = doc.getString("category") ?: "Umum",
                    playlistId = doc.getString("playlistId") ?: "",
                    sourceUrl = doc.getString("sourceUrl") ?: "",
                    durationMs = doc.getLong("durationMs") ?: 0L,
                    progressMs = doc.getLong("progressMs") ?: 0L,
                    isFavorite = doc.getBoolean("isFavorite") ?: false,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    extraInfo = doc.getString("extraInfo") ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil media items dari Firestore: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Observes user's media items in real time via snapshot listener.
     */
    fun observeMediaItems(userId: String): Flow<List<MediaItem>> = callbackFlow {
        val listener = usersCollection.document(userId).collection("media")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Snapshot error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val url = doc.getString("url") ?: return@mapNotNull null
                        MediaItem(
                            id = id,
                            profileId = doc.getString("profileId") ?: "profile_utama",
                            title = title,
                            url = url,
                            thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                            type = doc.getString("type") ?: "VIDEO",
                            sourceType = doc.getString("sourceType") ?: "LINK",
                            category = doc.getString("category") ?: "Umum",
                            playlistId = doc.getString("playlistId") ?: "",
                            sourceUrl = doc.getString("sourceUrl") ?: "",
                            durationMs = doc.getLong("durationMs") ?: 0L,
                            progressMs = doc.getLong("progressMs") ?: 0L,
                            isFavorite = doc.getBoolean("isFavorite") ?: false,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            extraInfo = doc.getString("extraInfo") ?: ""
                        )
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Uploads all local items to Cloud Firestore in bulk.
     */
    suspend fun syncLocalListToCloud(userId: String, items: List<MediaItem>): Int {
        var count = 0
        for (item in items) {
            try {
                saveMediaItem(userId, item)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Skipping item sync failure: ${item.title}")
            }
        }
        return count
    }

    companion object {
        private const val TAG = "CloudMediaRepository"
    }
}
