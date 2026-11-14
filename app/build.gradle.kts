import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import kotlin.math.abs
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Keep local builds aligned with the same Asia/Jakarta + mainline-commit
// version policy used by GitHub Actions. CI-provided values still take priority.
val computedNsioFallback: Pair<String, Int> = kotlin.run {
  val stateFile = rootProject.file(".versioning/nsio-version-state.json")
  if (!stateFile.isFile) {
    throw GradleException("Missing NSiO version state: ${stateFile.path}")
  }
  val state = stateFile.readText()
  fun stateInt(key: String): Int =
    Regex("\"$key\"\\s*:\\s*(\\d+)")
      .find(state)?.groupValues?.get(1)?.toIntOrNull()
      ?: throw GradleException("Invalid NSiO version state field: $key")

  val baselineText = Regex("\"baseline_month\"\\s*:\\s*\"(\\d{4}-\\d{2})\"")
    .find(state)?.groupValues?.get(1)
    ?: throw GradleException("Invalid NSiO baseline_month")
  val baseline = YearMonth.parse(baselineText)
  val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
  val monthDelta = (today.year - baseline.year) * 12 + today.monthValue - baseline.monthValue
  if (monthDelta < 0) throw GradleException("NSiO version baseline is later than the current Jakarta date")

  // Gradle's provider-based process API is compatible with configuration cache.
  val gitOutput = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-list", "--count", "HEAD")
  }.standardOutput.asText.get().trim()
  val commitOrdinal = gitOutput.toIntOrNull()
    ?: throw GradleException("Cannot calculate NSiO commit ordinal from git output: $gitOutput")
  if (commitOrdinal !in 1..99999) throw GradleException("NSiO commit ordinal outside supported range: $commitOrdinal")

  val major = stateInt("base_major") + monthDelta + stateInt("major_overhaul_count")
  val versionName = "$major.${today.dayOfMonth}.$commitOrdinal"
  val versionCodeLong = major.toLong() * 50_000_000L +
    today.dayOfYear.toLong() * 100_000L + commitOrdinal.toLong()
  if (versionCodeLong > 2_100_000_000L) {
    throw GradleException("NSiO versionCode exceeds Android's safe integer limit")
  }
  versionName to versionCodeLong.toInt()
}

val nsioVersionName = providers.gradleProperty("nsioVersionName")
  .orElse(providers.environmentVariable("NSIO_VERSION_NAME"))
  .orElse(computedNsioFallback.first)
val nsioVersionCode = providers.gradleProperty("nsioVersionCode")
  .orElse(providers.environmentVariable("NSIO_VERSION_CODE"))
  .map { it.toInt() }
  .orElse(computedNsioFallback.second)

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "dev.andikune.NSiO"
    minSdk = 24
    targetSdk = 36
    versionCode = nsioVersionCode.get()
    versionName = nsioVersionName.get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("org.json:json:20250517")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

 
// Generate launcher density assets from the approved artwork at build time.
val nsioLauncherResDir = layout.buildDirectory.dir("generated/res/nsioLauncher")
val generateNsioLauncherIcons = tasks.register("generateNsioLauncherIcons") {
  val source = layout.projectDirectory.file("src/main/icon-source/nsio-launcher.jpg")
  inputs.file(source)
  outputs.dir(nsioLauncherResDir)
  doLast {
    val original = ImageIO.read(source.asFile) ?: throw GradleException("Cannot read NSiO launcher source image")
    require(original.width == original.height) { "NSiO launcher source must be square" }
    val w = original.width
    val h = original.height
    val n = w * h
    val pixels = IntArray(n)
    original.getRGB(0, 0, w, h, pixels, 0, w)
    val transparent = BooleanArray(n)
    val queue = IntArray(n)
    var head = 0
    var tail = 0
    fun checker(rgb: Int): Boolean {
      val r = (rgb shr 16) and 255
      val g = (rgb shr 8) and 255
      val b = rgb and 255
      val neutral = abs(r - g) < 22 && abs(g - b) < 22
      return neutral && (r > 218 || (r in 165..225 && abs(r - 207) < 34))
    }
    fun enqueue(i: Int) {
      if (i in 0 until n && !transparent[i] && checker(pixels[i])) {
        transparent[i] = true
        queue[tail++] = i
      }
    }
    for (x in 0 until w) { enqueue(x); enqueue((h - 1) * w + x) }
    for (y in 0 until h) { enqueue(y * w); enqueue(y * w + w - 1) }
    while (head < tail) {
      val i = queue[head++]
      val x = i % w
      val y = i / w
      if (x > 0) enqueue(i - 1)
      if (x + 1 < w) enqueue(i + 1)
      if (y > 0) enqueue(i - w)
      if (y + 1 < h) enqueue(i + w)
    }
    val cleaned = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (i in 0 until n) cleaned.setRGB(i % w, i / w, if (transparent[i]) 0 else pixels[i] or (0xff shl 24))
    val root = nsioLauncherResDir.get().asFile
    root.deleteRecursively()
    fun write(image: BufferedImage, file: File) {
      file.parentFile.mkdirs()
      ImageIO.write(image, "png", file)
    }
    fun resize(image: BufferedImage, size: Int): BufferedImage {
      val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val graphics = out.createGraphics()
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.drawImage(image, 0, 0, size, size, null)
      graphics.dispose()
      return out
    }
    listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (density, size) ->
      val folder = File(root, "mipmap-$density")
      write(resize(cleaned, size), File(folder, "ic_launcher.png"))
      write(resize(cleaned, size), File(folder, "ic_launcher_round.png"))
    }
    val adaptive = BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB)
    val graphics = adaptive.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val safe = 292
    graphics.drawImage(cleaned, (432 - safe) / 2, (432 - safe) / 2, safe, safe, null)
    graphics.dispose()
    write(adaptive, File(root, "drawable-xxxhdpi/ic_launcher_foreground_art.png"))
    write(resize(cleaned, 1024), File(root, "drawable-nodpi/ic_nsio_logo.png"))
  }
}
android.sourceSets.getByName("main").res.srcDir(nsioLauncherResDir)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Resources") }
  .configureEach { dependsOn(generateNsioLauncherIcons) }
