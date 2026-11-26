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
  fun `profile backup encryption uses profile pin and rejects wrong pin`() {
    val payload = """{"backupType":"PROFILE_BACKUP","version":3,"sourceProfileName":"Balqyz","mediaItems":[]}"""
    val encrypted = ProfileBackupCrypto.encrypt(payload, "2468")
    val envelope = JSONObject(encrypted)
    assertEquals("PROFILE_BACKUP_ENCRYPTED", envelope.getString("backupType"))
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
  fun `profile backup excludes other profiles media and settings`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val profile = Profile(id = "profile_a", name = "Profile A")
    val media = listOf(
      MediaItem(id = "a1", profileId = "profile_a", title = "Owned", url = "https://example.com/a.mp4"),
      MediaItem(id = "b1", profileId = "profile_b", title = "Other", url = "https://example.com/b.mp4")
    )

    val result = BackupUtils.createProfileBackup(
      context = context,
      profile = profile,
      settings = ProfileSettings(profileId = "profile_b", language = "EN"),
      mediaList = media,
      sourcePlaylists = listOf(SourcePlaylist(id = "playlist_a", profileId = "profile_a", name = "Favorit", url = "https://example.com/list")),
      profilePin = "",
      avatarAssignments = mapOf("a1" to "p1:balqyz")
    )

    println("PROFILE_BACKUP_TEST_RESULT: success=${result.isSuccess}, path=${result.filePath}, message=${result.message}")
    assertTrue(result.message, result.isSuccess)
    val backupFile = File(result.filePath)
    assertTrue(backupFile.isFile)
    val json = JSONObject(backupFile.readText())
    println("PROFILE_BACKUP_TEST_JSON: $json")
    val exportedMedia = json.getJSONArray("mediaItems")
    assertEquals(1, exportedMedia.length())
    assertEquals("a1", exportedMedia.getJSONObject(0).getString("id"))
    assertEquals("Profile A", json.getString("sourceProfileName"))
    assertTrue(!json.has("settings"))
    assertTrue(!json.has("profile"))
    assertEquals("p1:balqyz", json.getJSONObject("avatarAssignments").getString("a1"))
    assertEquals("Favorit", json.getJSONArray("sourcePlaylists").getJSONObject(0).getString("name"))
    assertTrue(backupFile.name.contains("profile_a"))
  }

}
