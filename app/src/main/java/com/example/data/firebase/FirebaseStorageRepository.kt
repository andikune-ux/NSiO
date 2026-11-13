package com.example.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseStorageRepository {
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadMedia(userId: String, localUri: Uri, displayName: String): Uri {
        require(userId.isNotBlank()) { "Akun penyimpanan tidak valid" }
        val safeName = displayName.trim().ifBlank { "media" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
        val extension = localUri.lastPathSegment?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?.let { ".$it" } ?: ""
        val ref = storage.reference.child("users/$userId/media/${UUID.randomUUID()}_$safeName$extension")
        return try {
            ref.putFile(localUri).await()
            ref.downloadUrl.await()
        } catch (e: Exception) {
            // If upload succeeded but URL retrieval failed, remove the object
            // so an unreferenced file is not left in the user's bucket.
            runCatching { ref.delete().await() }
            throw e
        }
    }

    fun isFirebaseStorageUrl(downloadUrl: String): Boolean {
        val uri = Uri.parse(downloadUrl)
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host in setOf("firebasestorage.googleapis.com", "storage.googleapis.com")
    }

    /**
     * Deletes only files inside this Firebase project's users/{userId}/media/ folder.
     * External URLs and files belonging to another account are deliberately rejected.
     */
    suspend fun deleteOwnedMedia(userId: String, downloadUrl: String) {
        require(userId.isNotBlank()) { "Akun penyimpanan tidak valid" }
        val uri = Uri.parse(downloadUrl)
        require(uri.scheme.equals("https", ignoreCase = true) &&
            uri.host in setOf("firebasestorage.googleapis.com", "storage.googleapis.com")
        ) { "URL bukan file Firebase Storage yang dikenali" }

        val ref = storage.getReferenceFromUrl(downloadUrl)
        val expectedPrefix = "users/$userId/media/"
        require(ref.bucket == storage.reference.bucket &&
            ref.path.startsWith(expectedPrefix) &&
            ref.path.length > expectedPrefix.length
        ) { "File bukan milik akun penyimpanan aktif" }

        ref.delete().await()
    }
}
