plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 31
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

fun getGitCommitCount(): Int {
    return runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
        process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
    }.getOrElse { 0 }
}

fun getGitDescribe(): String {
    return runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--always"))
        process.inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrElse { "unknown" }
}

// The version can be pinned instead of derived from git:
//   ./gradlew assembleRelease -PKSU_VERSION_CODE=30008 -PKSU_VERSION_NAME=v3.4.1-xinglinghu
// CI clones this repo fresh, so its commit count differs from the one the LKM was
// built against and the manager would otherwise disagree with the kernel.
fun getVersionCode(): Int {
    val override = project.findProperty("KSU_VERSION_CODE")?.toString()
    if (!override.isNullOrBlank()) return override.toInt()
    return 30000 + getGitCommitCount()
}

fun getVersionName(): String {
    val override = project.findProperty("KSU_VERSION_NAME")?.toString()
    if (!override.isNullOrBlank()) return override
    return getGitDescribe()
}
