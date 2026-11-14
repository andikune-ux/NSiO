import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
