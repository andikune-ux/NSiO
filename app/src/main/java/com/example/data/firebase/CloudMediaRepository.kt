package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist
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
     * Syncs portable profile metadata and non-sensitive preferences.
     * Profile PINs and server PINs are deliberately never uploaded.
     */
    suspend fun syncProfileMetadata(
        userId: String,
        profiles: List<Profile>,
        settings: List<ProfileSettings>
    ) {
        val userDocument = usersCollection.document(userId)
        profiles.distinctBy { it.id }.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { profile ->
                val payload = hashMapOf<String, Any>(
                    "id" to profile.id,
                    "name" to profile.name,
                    "avatarEmoji" to profile.avatarEmoji,
                    "isDefault" to profile.isDefault,
                    "createdAt" to profile.createdAt,
                    "isPinEnabled" to false
                )
                batch.set(userDocument.collection("profiles").document(profile.id), payload, SetOptions.merge())
            }
            batch.commit().await()
        }
        settings.distinctBy { it.profileId }.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { setting ->
                val payload = hashMapOf<String, Any>(
                    "profileId" to setting.profileId,
                    "language" to setting.language,
                    "themeMode" to setting.themeMode,
                    "displayMode" to setting.displayMode,
                    "gridColumns" to setting.gridColumns,
                    "gridColumnsPhone" to setting.gridColumnsPhone,
                    "gridColumnsTv" to setting.gridColumnsTv
                )
                batch.set(userDocument.collection("profile_settings").document(setting.profileId), payload, SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    /**
     * Returns portable profiles only. PINs are always disabled on a new device.
     */
    suspend fun fetchProfileMetadata(userId: String): Pair<List<Profile>, List<ProfileSettings>> {
        val userDocument = usersCollection.document(userId)
        val profileSnapshot = userDocument.collection("profiles").get().await()
        val profiles = profileSnapshot.documents.mapNotNull { document ->
            val id = document.getString("id") ?: document.id
            val name = document.getString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Profile(
                id = id,
                name = name,
                avatarEmoji = document.getString("avatarEmoji") ?: "🎬",
                pin = "",
                isPinEnabled = false,
                isDefault = document.getBoolean("isDefault") ?: false,
                createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
            )
        }
        val settingsSnapshot = userDocument.collection("profile_settings").get().await()
        val settings = settingsSnapshot.documents.mapNotNull { document ->
            val profileId = document.getString("profileId") ?: document.id
            ProfileSettings(
                profileId = profileId,
                language = document.getString("language") ?: "ID",
                themeMode = document.getString("themeMode") ?: "DARK",
                displayMode = document.getString("displayMode") ?: "TV",
                gridColumns = (document.getLong("gridColumns") ?: 4L).toInt().coerceIn(1, 8),
                gridColumnsPhone = (document.getLong("gridColumnsPhone") ?: 2L).toInt().coerceIn(1, 8),
                gridColumnsTv = (document.getLong("gridColumnsTv") ?: 4L).toInt().coerceIn(1, 8)
            )
        }
        return profiles to settings
    }

    /**
     * Syncs named source playlists as portable metadata. Playlist names are
     * user-editable, so they must not be reconstructed from source URLs.
     */
    suspend fun syncSourcePlaylists(userId: String, profileId: String, playlists: List<SourcePlaylist>) {
        require(playlists.all { it.profileId == profileId }) {
            "Playlist sinkronisasi harus berasal dari profil yang sama."
        }
        if (playlists.isEmpty()) return
        val collection = usersCollection.document(userId).collection("source_playlists")
        playlists.distinctBy { it.id }.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { playlist ->
                val payload = hashMapOf<String, Any>(
                    "id" to playlist.id,
                    "profileId" to playlist.profileId,
                    "name" to playlist.name,
                    "url" to playlist.url,
                    "sourceType" to playlist.sourceType,
                    "itemCount" to playlist.itemCount,
                    "createdAt" to playlist.createdAt,
                    "updatedAt" to playlist.updatedAt
                )
                batch.set(collection.document(playlist.id), payload, SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    suspend fun fetchSourcePlaylists(userId: String): List<SourcePlaylist> {
        val snapshot = usersCollection.document(userId).collection("source_playlists").get().await()
        return snapshot.documents.mapNotNull { document ->
            val id = document.getString("id") ?: document.id
            val profileId = document.getString("profileId") ?: return@mapNotNull null
            val url = document.getString("url") ?: return@mapNotNull null
            SourcePlaylist(
                id = id,
                profileId = profileId,
                name = document.getString("name")?.takeIf { it.isNotBlank() }
                    ?: url.substringAfter("//").substringBefore("/"),
                url = url,
                sourceType = document.getString("sourceType") ?: "LINK",
                itemCount = (document.getLong("itemCount") ?: 0L).toInt(),
                createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = document.getLong("updatedAt") ?: System.currentTimeMillis()
            )
        }
    }

    /**
     * Saves or updates a media item into user's private cloud collection.
     */
    suspend fun saveMediaItem(userId: String, item: MediaItem) {
        val docRef = usersCollection.document(userId).collection("media").document(item.id)
        val payload = mediaPayload(item)
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
     * Removes a playlist metadata document after its media references have
     * been handled. The document ID is globally unique in the local schema.
     */
    suspend fun deleteSourcePlaylist(userId: String, playlistId: String) {
        usersCollection.document(userId).collection("source_playlists")
            .document(playlistId).delete().await()
    }

    /**
     * Deletes cloud metadata for one playlist only. Legacy documents without
     * profileId are considered part of the default profile.
     */
    suspend fun deletePlaylistItems(userId: String, profileId: String, playlistId: String): Int {
        val mediaCollection = usersCollection.document(userId).collection("media")
        val snapshot = mediaCollection.whereEqualTo("playlistId", playlistId).get().await()
        val ownedDocuments = snapshot.documents.filter { document ->
            (document.getString("profileId") ?: DEFAULT_PROFILE_ID) == profileId
        }
        ownedDocuments.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        return ownedDocuments.size
    }

    /**
     * Fetches all media items for the user from Firestore.
     */
    suspend fun fetchMediaItems(userId: String): List<MediaItem> =
        fetchMediaItems(userId, null)

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
     * Synchronizes one local profile to Firestore.
     *
     * Existing documents belonging to other profiles are preserved. Stale
     * documents for this profile are deleted only after every current item
     * has been written successfully, preventing an interrupted upload from
     * erasing the last complete cloud copy.
     */
    suspend fun syncLocalListToCloud(
        userId: String,
        profileId: String,
        items: List<MediaItem>
    ): Int {
        require(items.all { it.profileId == profileId }) {
            "Semua item sinkronisasi harus berasal dari profil yang sama."
        }

        val mediaCollection = usersCollection.document(userId).collection("media")
        val uniqueItems = items.distinctBy { it.id }

        // An empty local profile may be a fresh install or an incomplete restore.
        // Never interpret it as an instruction to erase the cloud backup.
        if (uniqueItems.isEmpty()) return 0

        // Firestore batches allow at most 500 operations; keep a safety margin.
        uniqueItems.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { item ->
                val payload = mediaPayload(item)
                batch.set(mediaCollection.document(item.id), payload, SetOptions.merge())
            }
            batch.commit().await()
        }

        val keepIds = uniqueItems.mapTo(mutableSetOf()) { it.id }
        // Include legacy documents without profileId: older app versions wrote
        // them before profile scoping existed, and they belong to the default profile.
        val existingSnapshot = mediaCollection.get().await()
        val staleDocuments = existingSnapshot.documents.filter { document ->
            val documentProfileId = document.getString("profileId") ?: DEFAULT_PROFILE_ID
            documentProfileId == profileId && document.id !in keepIds
        }

        staleDocuments.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { document -> batch.delete(document.reference) }
            batch.commit().await()
        }

        return uniqueItems.size
    }

    /**
     * Reconciles one refreshed source playlist without touching other
     * playlists or manually added media in the same profile.
     */
    suspend fun syncPlaylistToCloud(
        userId: String,
        profileId: String,
        playlistId: String,
        items: List<MediaItem>
    ): Int {
        require(items.all { it.profileId == profileId && it.playlistId == playlistId }) {
            "Item sinkronisasi harus berasal dari playlist dan profil yang sama."
        }
        // An empty parse result must never be interpreted as an instruction to
        // delete every cloud item; explicit playlist deletion uses deletePlaylistItems().
        if (items.isEmpty()) return 0

        val mediaCollection = usersCollection.document(userId).collection("media")
        val uniqueItems = items.distinctBy { it.id }
        uniqueItems.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { item ->
                batch.set(mediaCollection.document(item.id), mediaPayload(item), SetOptions.merge())
            }
            batch.commit().await()
        }

        val keepIds = uniqueItems.mapTo(mutableSetOf()) { it.id }
        // Read legacy rows too; absent profileId means the default profile.
        // Filter in memory so another profile's media is never deleted.
        val existingSnapshot = mediaCollection.get().await()
        val staleDocuments = existingSnapshot.documents.filter { document ->
            val documentProfileId = document.getString("profileId") ?: DEFAULT_PROFILE_ID
            documentProfileId == profileId &&
                document.getString("playlistId") == playlistId &&
                document.id !in keepIds
        }
        staleDocuments.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        return uniqueItems.size
    }

    /**
     * Returns only the requested profile's media. Documents created by older
     * app versions without profileId are treated as belonging to the default
     * profile for backwards compatibility.
     */
    suspend fun fetchMediaItems(userId: String, profileId: String? = null): List<MediaItem> {
        return try {
            val snapshot = usersCollection.document(userId).collection("media").get().await()
            snapshot.documents.mapNotNull { doc ->
                val documentProfileId = doc.getString("profileId") ?: DEFAULT_PROFILE_ID
                if (profileId != null && documentProfileId != profileId) return@mapNotNull null
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                val url = doc.getString("url") ?: return@mapNotNull null
                MediaItem(
                    id = id,
                    profileId = documentProfileId,
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
            throw e
        }
    }

    private fun mediaPayload(item: MediaItem): HashMap<String, Any> = hashMapOf(
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

    companion object {
        private const val TAG = "CloudMediaRepository"
        private const val MAX_BATCH_OPERATIONS = 450
        private const val DEFAULT_PROFILE_ID = "profile_utama"
    }
}
