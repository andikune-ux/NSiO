import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateNsioLauncherIconsTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceImage: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val original = ImageIO.read(sourceImage.get().asFile)
      ?: throw IllegalArgumentException("Cannot read NSiO launcher source image")
    require(original.width == original.height) { "NSiO launcher source must be square" }
    val width = original.width
    val height = original.height
    val count = width * height
    val pixels = IntArray(count)
    original.getRGB(0, 0, width, height, pixels, 0, width)
    val transparent = BooleanArray(count)
    val queue = IntArray(count)
    var head = 0
    var tail = 0

    fun isChecker(rgb: Int): Boolean {
      val red = (rgb shr 16) and 255
      val green = (rgb shr 8) and 255
      val blue = rgb and 255
      val neutral = abs(red - green) < 22 && abs(green - blue) < 22
      return neutral && (red > 218 || (red in 165..225 && abs(red - 207) < 34))
    }
    fun enqueue(index: Int) {
      if (index in 0 until count && !transparent[index] && isChecker(pixels[index])) {
        transparent[index] = true
        queue[tail++] = index
      }
    }
    for (x in 0 until width) {
      enqueue(x)
      enqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
      enqueue(y * width)
      enqueue(y * width + width - 1)
    }
    while (head < tail) {
      val index = queue[head++]
      val x = index % width
      val y = index / width
      if (x > 0) enqueue(index - 1)
      if (x + 1 < width) enqueue(index + 1)
      if (y > 0) enqueue(index - width)
      if (y + 1 < height) enqueue(index + width)
    }

    val cleaned = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (index in 0 until count) {
      cleaned.setRGB(index % width, index / width, if (transparent[index]) 0 else pixels[index] or (0xff shl 24))
    }
    val root = outputDirectory.get().asFile
    root.deleteRecursively()
    fun write(image: BufferedImage, file: File) {
      file.parentFile.mkdirs()
      check(ImageIO.write(image, "png", file)) { "Unable to write launcher PNG: ${file.path}" }
    }
    fun resize(image: BufferedImage, size: Int): BufferedImage {
      val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val graphics = result.createGraphics()
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.drawImage(image, 0, 0, size, size, null)
      graphics.dispose()
      return result
    }
    listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
      .forEach { (density, size) ->
        val folder = File(root, "mipmap-$density")
        write(resize(cleaned, size), File(folder, "ic_launcher.png"))
        write(resize(cleaned, size), File(folder, "ic_launcher_round.png"))
      }
    val adaptive = BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB)
    val graphics = adaptive.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val safeSize = 292
    graphics.drawImage(cleaned, (432 - safeSize) / 2, (432 - safeSize) / 2, safeSize, safeSize, null)
    graphics.dispose()
    write(adaptive, File(root, "drawable-xxxhdpi/ic_launcher_foreground_art.png"))
    write(resize(cleaned, 1024), File(root, "drawable-nodpi/ic_nsio_logo.png"))
  }
}
