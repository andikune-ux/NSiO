import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// The root build script calculates the version once for both local builds and CI.
@Suppress("UNCHECKED_CAST")
val sharedNsioVersion = rootProject.extra["nsioVersionValues"] as Map<String, String>

val nsioVersionName = providers.gradleProperty("nsioVersionName")
  .orElse(providers.environmentVariable("NSIO_VERSION_NAME"))
  .orElse(sharedNsioVersion.getValue("NSIO_VERSION_NAME"))
val nsioVersionCode = providers.gradleProperty("nsioVersionCode")
  .orElse(providers.environmentVariable("NSIO_VERSION_CODE"))
  .map { it.toInt() }
  .orElse(sharedNsioVersion.getValue("NSIO_VERSION_CODE").toInt())

android {
  namespace = "com.example"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "dev.andikune.NSiO"
    minSdk = 24
    targetSdk = 36
    // Ship only Android device ABIs. Keeping both ARM generations preserves
    // API 24-era 32-bit devices and modern 64-bit phones/Android TV.
    // x86/x86_64 emulator libraries are intentionally excluded from the APK.
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
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

  // Exclude emulator-only prebuilt TDLib JNI libraries from the distributed APK.
  packaging {
    jniLibs {
      excludes += setOf("**/x86/**", "**/x86_64/**")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Keep the same permanent signing identity as Debug and APKs distributed
      // by the in-app updater. AppUpdateManager rejects updates signed by another key.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
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
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
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
  implementation("androidx.media3:media3-datasource-okhttp:${libs.versions.media3.get()}")
  implementation("androidx.media3:media3-database:${libs.versions.media3.get()}")
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.session)
  implementation(libs.play.services.cast.framework)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)

  // CloudStream SDK compatibility runtime (GPL-3.0; see LICENSES/CLOUDSTREAM3.md).
  // NiceHttp is vendored in app/src/main/java/com/lagradost/nicehttp and uses this OkHttp client.
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
  implementation("org.jsoup:jsoup:1.17.2")
  implementation("me.xdrop:fuzzywuzzy:1.4.0")
  implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.10.0")
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
}

 
// Generate launcher density assets with a typed, configuration-cache-safe Kotlin task.
val nsioLauncherResDir = layout.buildDirectory.dir("generated/res/nsioLauncher")
val generateNsioLauncherIcons = tasks.register<GenerateNsioLauncherIconsTask>("generateNsioLauncherIcons") {
  sourceImage.set(layout.projectDirectory.file("src/main/icon-source/nsio-launcher.png"))
  outputDirectory.set(nsioLauncherResDir)
}

// Register generated resources through the Android Components Variant API.
// AGP now owns the producer/consumer relationship, so resource, manifest/deep-link,
// and packaging tasks all receive the correct implicit dependency.
androidComponents {
  onVariants(selector().all()) { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(generateNsioLauncherIcons) { task ->
      task.outputDirectory
    }
  }
}

// Robolectric downloads its Android SDK runtime at test execution time.
// Use Google's Maven Central mirror so CI is not blocked by Maven Central HTTP 429
// rate limits when multiple Robolectric tests request the same Android runtime.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
}
