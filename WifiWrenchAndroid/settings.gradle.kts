pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

val wifiWrenchAndroidBuildRoot = providers.gradleProperty("wifi.wrench.android.buildRoot")
    .orElse(providers.environmentVariable("WIFI_WRENCH_ANDROID_BUILD_ROOT"))
    .orNull
if (!wifiWrenchAndroidBuildRoot.isNullOrBlank()) {
    gradle.startParameter.projectCacheDir = file("$wifiWrenchAndroidBuildRoot/project-cache")
}

rootProject.name = "WrenchLiftAndroid"
include(":app")
