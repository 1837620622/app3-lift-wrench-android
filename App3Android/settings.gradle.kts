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

val app3AndroidBuildRoot = providers.gradleProperty("app3.android.buildRoot")
    .orElse(providers.environmentVariable("APP3_ANDROID_BUILD_ROOT"))
    .orNull
if (!app3AndroidBuildRoot.isNullOrBlank()) {
    gradle.startParameter.projectCacheDir = file("$app3AndroidBuildRoot/project-cache")
}

rootProject.name = "WrenchLiftAndroid"
include(":app")
