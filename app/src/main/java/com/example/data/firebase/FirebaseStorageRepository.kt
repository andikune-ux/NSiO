package com.example.data.firebase

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseStorageRepository {
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadMedia(userId: String, localUri: Uri, displayName: String): Uri {
        val safeName = displayName.trim().ifBlank { "media" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val extension = localUri.lastPathSegment?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..5 }
            ?.let { ".$it" } ?: ""
        val ref = storage.reference.child("users/$userId/media/${UUID.randomUUID()}_$safeName$extension")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await()
    }
}
