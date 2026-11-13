package com.example

import org.junit.Assert.*
import com.example.data.model.MediaItem
import com.example.player.adjacentMedia
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun swipeNavigationIncludesPhotosAndStopsAtListEdges() {
    val items = listOf(
      MediaItem(id = "video-1", profileId = "p", title = "Video 1", url = "https://example.com/1.mp4"),
      MediaItem(id = "photo-1", profileId = "p", title = "Foto", url = "https://example.com/1.jpg", type = "PHOTO"),
      MediaItem(id = "video-2", profileId = "p", title = "Video 2", url = "https://example.com/2.mp4")
    )
    assertEquals("photo-1", adjacentMedia(items, "video-1", 1)?.id)
    assertEquals("video-2", adjacentMedia(items, "photo-1", 1)?.id)
    assertEquals("video-1", adjacentMedia(items, "photo-1", -1)?.id)
    assertNull(adjacentMedia(items, "video-1", -1))
    assertNull(adjacentMedia(items, "video-2", 1))
    assertNull(adjacentMedia(items, "missing", 1))
    assertNull(adjacentMedia(items, "photo-1", 0))
  }

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }
}
