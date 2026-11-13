package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
import com.example.data.backup.BackupUtils
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
      mediaList = media
    )

    assertTrue(result.isSuccess)
    val backupFile = File(result.filePath)
    assertTrue(backupFile.isFile)
    val json = JSONObject(backupFile.readText())
    val exportedMedia = json.getJSONArray("mediaItems")
    assertEquals(1, exportedMedia.length())
    assertEquals("profile_a", exportedMedia.getJSONObject(0).getString("profileId"))
    assertEquals("a1", exportedMedia.getJSONObject(0).getString("id"))
    assertTrue(!json.has("settings"))
    assertTrue(json.getJSONObject("profile").getBoolean("isPinEnabled").not())
  }

}
