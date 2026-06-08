plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.7" apply false
}

val app3AndroidBuildRoot = providers.gradleProperty("app3.android.buildRoot")
    .orElse(providers.environmentVariable("APP3_ANDROID_BUILD_ROOT"))
    .orNull
if (!app3AndroidBuildRoot.isNullOrBlank()) {
    layout.buildDirectory.set(file("$app3AndroidBuildRoot/root"))
    subprojects {
        val projectName = path.removePrefix(":").replace(':', '_').ifBlank { "root" }
        layout.buildDirectory.set(file("$app3AndroidBuildRoot/$projectName"))
    }
}
