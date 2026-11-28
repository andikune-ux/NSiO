package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.backup.BackupUtils
import com.example.data.backup.ProfileBackupCrypto
import com.example.data.model.SourcePlaylist
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app_name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Net Stream iO", appName)
  }

  @Test
  fun `test link extractor with direct mp4 url`() = runBlocking {
    val result = LinkExtractor.parseUrl(
      "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
      "profile_test"
    )
    assertTrue(result is ParseResult.Single)
    val single = result as ParseResult.Single
    assertEquals("VIDEO", single.item.type)
  }



  @Test
  fun `profile backup crypto still opens legacy version one envelopes`() {
    val pin = "2468"
    val payload = """{"backupType":"PROFILE_BACKUP","version":4,"mediaItems":[]}"""
    val salt = ByteArray(16) { it.toByte() }
    val nonce = ByteArray(12) { (it + 1).toByte() }
    val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 600_000, 256)
    val keyBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(spec).encoded
    spec.clearPassword()
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
      javax.crypto.Cipher.ENCRYPT_MODE,
      javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
      javax.crypto.spec.GCMParameterSpec(128, nonce)
    )
    val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
    val encoder = java.util.Base64.getEncoder()
    val envelope = JSONObject()
      .put("backupType", "PROFILE_BACKUP_ENCRYPTED")
      .put("encryption", "PBKDF2-HMAC-SHA256+A256GCM")
      .put("formatVersion", 1)
      .put("iterations", 600_000)
      .put("salt", encoder.encodeToString(salt))
      .put("nonce", encoder.encodeToString(nonce))
      .put("ciphertext", encoder.encodeToString(ciphertext))

    assertEquals(payload, ProfileBackupCrypto.decrypt(envelope, pin))
  }

  @Test
  fun `profile backup encryption uses profile pin and rejects wrong pin`() {
    val payload = """{"backupType":"PROFILE_BACKUP","version":3,"sourceProfileName":"Balqyz","mediaItems":[]}"""
    val encrypted = ProfileBackupCrypto.encrypt(payload, "2468")
    val envelope = JSONObject(encrypted)
    assertEquals("PROFILE_BACKUP_ENCRYPTED", envelope.getString("backupType"))
    assertEquals(2, envelope.getInt("formatVersion"))
    assertEquals("GZIP", envelope.getString("compression"))
    assertEquals(payload, ProfileBackupCrypto.decrypt(envelope, "2468"))
    var rejected = false
    try {
      ProfileBackupCrypto.decrypt(envelope, "1357")
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertTrue(rejected)
  }




  @Test
  fun `profile backup omits settings belonging to another profile`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = File(context.cacheDir, "profile-backup-mismatched-settings").apply {
      deleteRecursively()
      mkdirs()
    }
    val result = BackupUtils.createProfileBackup(
      context = context,
      profile = Profile(id = "profile_settings_owner", name = "Owner"),
      settings = ProfileSettings(profileId = "different_profile", language = "EN"),
      mediaList = emptyList(),
      sourcePlaylists = emptyList(),
      profilePin = "",
      avatarAssignments = emptyMap(),
      storageRootOverride = root
    )
    assertTrue(result.message, result.isSuccess)
    assertTrue(!JSONObject(File(result.filePath).readText()).has("settings"))
  }

  @Test
  fun `Telegram integration cannot be claimed by a second profile`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("nsio_telegram_profile_binding", Context.MODE_PRIVATE)
      .edit().clear().commit()

    assertTrue(com.example.data.telegram.TelegramClientManager.claimProfile(context, "telegram-owner-a"))
    assertTrue(com.example.data.telegram.TelegramClientManager.isProfileOwner(context, "telegram-owner-a"))
    assertTrue(!com.example.data.telegram.TelegramClientManager.claimProfile(context, "telegram-owner-b"))
    assertTrue(!com.example.data.telegram.TelegramClientManager.isProfileOwner(context, "telegram-owner-b"))
    assertEquals("telegram-owner-a", com.example.data.telegram.TelegramClientManager.ownerProfileId(context))
  }

  @Test
  fun `TikTod profile preferences restore with remapped media IDs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.tiktok.TikTodLocalStore(context)
    val profileA = "backup_tiktod_source"
    val profileB = "backup_tiktod_target"
    store.setSelectedAvatar(profileA, "p2:custom-avatar")
    store.syncEligibleVideos(profileA, listOf("source-one", "source-two"))
    store.markVideoStarted(profileA, "source-one")

    val snapshot = store.exportProfilePreferences(profileA)
    store.importProfilePreferences(
      profileB,
      snapshot,
      mapOf("source-one" to "target-one", "source-two" to "target-two")
    )

    assertEquals("p2:custom-avatar", store.selectedAvatar(profileB))
    assertEquals("target-one", store.lastVideoId(profileB))
    val restored = store.exportProfilePreferences(profileB)
    val known = restored.getJSONArray("knownVideos")
    assertEquals(2, known.length())
    val pending = restored.getJSONArray("newVideos")
    assertEquals(1, pending.length())
    assertEquals("target-two", pending.getString(0))
  }

  @Test
  fun `profile backup exports only matching profile media and settings`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val profile = Profile(id = "profile_a", name = "Profile A")
    context.getSharedPreferences("nsio_catalog_preferences", Context.MODE_PRIVATE).edit()
      .putString("home_filter_profile_a", "CLOUDSTREAM_PLUGIN:provider-a")
      .putString("home_sort_profile_a", "TITLE").commit()
    context.getSharedPreferences("nsio_cloudstream_plugin_order", Context.MODE_PRIVATE).edit()
      .putString("plugin_order_profile_a", """["provider-a","provider-b"]""").commit()
    com.example.tiktok.TikTodLocalStore(context).setSelectedAvatar("profile_a", "p2:custom-avatar")
    val media = listOf(
      MediaItem(id = "a1", profileId = "profile_a", title = "Owned", url = "https://example.com/a.mp4", caption = "Caption milik profil A"),
      MediaItem(id = "b1", profileId = "profile_b", title = "Other", url = "https://example.com/b.mp4")
    )

    val result = BackupUtils.createProfileBackup(
      context = context,
      profile = profile,
      settings = ProfileSettings(profileId = "profile_a", language = "EN", themeMode = "LIGHT", displayMode = "HP", gridColumnsPhone = 3),
      mediaList = media,
      sourcePlaylists = listOf(SourcePlaylist(id = "playlist_a", profileId = "profile_a", name = "Favorit", url = "https://example.com/list")),
      profilePin = "",
      avatarAssignments = mapOf("a1" to "p1:balqyz"),
      storageRootOverride = File(context.cacheDir, "profile-backup-test-root")
    )

    assertTrue(result.message, result.isSuccess)
    val backupFile = File(result.filePath)
    assertTrue(backupFile.isFile)
    val json = JSONObject(backupFile.readText())
    val exportedMedia = json.getJSONArray("mediaItems")
    assertEquals(1, exportedMedia.length())
    assertEquals("a1", exportedMedia.getJSONObject(0).getString("id"))
    assertEquals("Caption milik profil A", exportedMedia.getJSONObject(0).getString("caption"))
    assertEquals("Profile A", json.getString("sourceProfileName"))
    val exportedSettings = json.getJSONObject("settings")
    assertEquals("EN", exportedSettings.getString("language"))
    assertEquals("LIGHT", exportedSettings.getString("themeMode"))
    assertEquals("HP", exportedSettings.getString("displayMode"))
    assertEquals(3, exportedSettings.getInt("gridColumnsPhone"))
    // Profile backups now carry identity metadata so the same profile can restore
    // its own name/avatar. Restore code must ignore this identity for a different ID.
    val exportedProfile = json.getJSONObject("profile")
    assertEquals("profile_a", exportedProfile.getString("id"))
    assertEquals("Profile A", exportedProfile.getString("name"))
    assertEquals("🎬", exportedProfile.getString("avatarEmoji"))
    assertEquals("p1:balqyz", json.getJSONObject("avatarAssignments").getString("a1"))
    assertEquals("p2:custom-avatar", json.getJSONObject("tikTodPreferences").getString("selectedAvatar"))
    val profilePreferences = json.getJSONObject("profilePreferences")
    assertEquals("CLOUDSTREAM_PLUGIN:provider-a", profilePreferences.getString("homeFilter"))
    assertEquals("TITLE", profilePreferences.getString("homeSort"))
    assertEquals("provider-b", profilePreferences.getJSONArray("cloudStreamPluginOrder").getString(1))
    val cloudStreamBackup = json.getJSONObject("cloudStreamBackup")
    assertTrue(cloudStreamBackup.has("repositories"))
    assertTrue(cloudStreamBackup.has("installedPlugins"))
    assertEquals("Favorit", json.getJSONArray("sourcePlaylists").getJSONObject(0).getString("name"))
    assertTrue(backupFile.name.contains("profile_a"))
  }

}
