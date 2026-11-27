import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

data class NsioVersionTuple(val major: Int, val day: Int, val ordinal: Int) : Comparable<NsioVersionTuple> {
  override fun compareTo(other: NsioVersionTuple): Int =
    compareValuesBy(this, other, NsioVersionTuple::major, NsioVersionTuple::day, NsioVersionTuple::ordinal)

  override fun toString(): String = "$major.$day.$ordinal"
}

fun gitOutput(vararg args: String): String =
  providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", *args)
  }.standardOutput.asText.get().trim()

fun parseNsioTag(raw: String): NsioVersionTuple? {
  val value = raw.trim().removePrefix("refs/tags/")
  val match = Regex("^[vV]\\.?([0-9]+)\\.([0-9]+)\\.([0-9]+)$").matchEntire(value) ?: return null
  return NsioVersionTuple(
    match.groupValues[1].toIntOrNull() ?: return null,
    match.groupValues[2].toIntOrNull() ?: return null,
    match.groupValues[3].toIntOrNull() ?: return null
  )
}

fun calculateNsioVersion(): Map<String, String> {
  val stateFile = rootProject.file(".versioning/nsio-version-state.json")
  if (!stateFile.isFile) throw GradleException("Missing NSiO version state: ${stateFile.path}")
  val state = stateFile.readText()
  fun stateInt(key: String): Int =
    Regex("\\\"$key\\\"\\\\s*:\\\\s*(\\\\d+)")
      .find(state)?.groupValues?.get(1)?.toIntOrNull()
      ?: throw GradleException("Invalid NSiO version state field: $key")

  val baselineText = Regex("\\\"baseline_month\\\"\\\\s*:\\\\s*\\\"(\\\\d{4}-\\\\d{2})\\\"")
    .find(state)?.groupValues?.get(1) ?: throw GradleException("Invalid NSiO baseline_month")
  val baseline = YearMonth.parse(baselineText)
  val overhaulCount = stateInt("major_overhaul_count")
  val baseMajor = stateInt("base_major")
  if (baseMajor < 1 || overhaulCount < 0) throw GradleException("Invalid NSiO MAJOR version state.")

  val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
  val monthDelta = (today.year - baseline.year) * 12 + today.monthValue - baseline.monthValue
  if (monthDelta < 0) throw GradleException("Current Jakarta month is earlier than the stored NSiO version baseline.")
  val calculatedMajor = baseMajor + monthDelta + overhaulCount
  val sourceSha = System.getenv("NSIO_SOURCE_SHA")
    ?: System.getenv("GITHUB_SHA")
    ?: gitOutput("rev-parse", "HEAD")
  if (gitOutput("cat-file", "-e", "$sourceSha^{commit}").isNotEmpty()) {
    throw GradleException("Cannot resolve NSiO source commit: $sourceSha")
  }

  val commitOverride = System.getenv("NSIO_BUILD_NUMBER")
  val commitOrdinal = (commitOverride?.toIntOrNull()
    ?: gitOutput("rev-list", "--count", sourceSha).toIntOrNull()
    ?: throw GradleException("Cannot calculate NSiO commit ordinal."))
  if (commitOrdinal !in 1..99999) throw GradleException("NSiO commit ordinal outside supported range: $commitOrdinal")

  val allTags = gitOutput("tag", "--list", "v*").lineSequence()
    .mapNotNull(::parseNsioTag).toList()
  val highestPublished = allTags.maxOrNull()
  val sourceTags = gitOutput("tag", "--points-at", sourceSha).lineSequence()
    .mapNotNull(::parseNsioTag)
  val existingSourceVersion = sourceTags.maxOrNull()

  val selected: NsioVersionTuple
  val selectedDate: LocalDate
  if (existingSourceVersion != null) {
    if (highestPublished != null && existingSourceVersion < highestPublished) {
      throw GradleException(
        "This source commit is already tagged $existingSourceVersion, below the newest published version $highestPublished. " +
          "Rebuild from a newer source commit to prevent an update downgrade."
      )
    }
    selected = existingSourceVersion
    val monthOffset = selected.major - baseMajor - overhaulCount
    val taggedMonth = baseline.plusMonths(monthOffset.toLong())
    selectedDate = try {
      taggedMonth.atDay(selected.day)
    } catch (error: Exception) {
      throw GradleException("Existing NSiO tag $selected has an invalid day for $taggedMonth.", error)
    }
  } else {
    var ordinal = commitOrdinal
    if (highestPublished != null) {
      val currentPrefix = calculatedMajor to today.dayOfMonth
      val latestPrefix = highestPublished.major to highestPublished.day
      when {
        currentPrefix < latestPrefix -> throw GradleException(
          "Calculated NSiO version $calculatedMajor.${today.dayOfMonth} is behind the newest published version $highestPublished. " +
            "Update the version baseline instead of publishing a lower version."
        )
        currentPrefix == latestPrefix -> ordinal = maxOf(ordinal, highestPublished.ordinal + 1)
      }
    }
    if (ordinal !in 1..99999) throw GradleException("NSiO version ordinal outside supported range: $ordinal")
    selected = NsioVersionTuple(calculatedMajor, today.dayOfMonth, ordinal)
    selectedDate = today
  }

  val versionCodeLong = selected.major.toLong() * 50_000_000L +
    selectedDate.dayOfYear.toLong() * 100_000L + selected.ordinal.toLong()
  if (versionCodeLong > 2_100_000_000L) throw GradleException("NSiO versionCode exceeds Android's safe integer limit.")

  return mapOf(
    "NSIO_VERSION_NAME" to selected.toString(),
    "NSIO_VERSION_TAG" to "v.$selected",
    "NSIO_VERSION_CODE" to versionCodeLong.toString(),
    "NSIO_MAJOR" to selected.major.toString(),
    "NSIO_DAY" to selected.day.toString(),
    "NSIO_COMMIT_NUMBER" to selected.ordinal.toString(),
    "NSIO_SOURCE_SHA" to sourceSha
  )
}

val nsioVersionValues = calculateNsioVersion()
rootProject.extra["nsioVersionValues"] = nsioVersionValues

tasks.register("printNsioVersion") {
  group = "versioning"
  description = "Print the shared NSiO version used by local and CI builds."
  doLast {
    nsioVersionValues.forEach { (key, value) -> println("$key=$value") }
  }
}
