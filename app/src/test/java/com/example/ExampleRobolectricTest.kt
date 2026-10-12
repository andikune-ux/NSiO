package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.extractor.LinkExtractor
import com.example.data.extractor.ParseResult
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
}
