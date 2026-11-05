plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.7" apply false
}

val wifiWrenchAndroidBuildRoot = providers.gradleProperty("wifi.wrench.android.buildRoot")
    .orElse(providers.environmentVariable("WIFI_WRENCH_ANDROID_BUILD_ROOT"))
    .orNull
if (!wifiWrenchAndroidBuildRoot.isNullOrBlank()) {
    layout.buildDirectory.set(file("$wifiWrenchAndroidBuildRoot/root"))
    subprojects {
        val projectName = path.removePrefix(":").replace(':', '_').ifBlank { "root" }
        layout.buildDirectory.set(file("$wifiWrenchAndroidBuildRoot/$projectName"))
    }
}
