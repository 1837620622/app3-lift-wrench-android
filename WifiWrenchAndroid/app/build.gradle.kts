plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val wifiWrenchVersionCode = providers.gradleProperty("wifi.wrench.versionCode")
    .orElse(providers.gradleProperty("app3.versionCode")) // 兼容旧 CI 参数名
    .orElse("2")
    .get()
    .toInt()
require(wifiWrenchVersionCode in 2..2_100_000_000) { "wifi.wrench.versionCode 超出合法范围" }

android {
    namespace = "com.chuankangkk.wrenchlift"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.chuankangkk.wrenchlift"
        minSdk = 26
        targetSdk = 35
        versionCode = wifiWrenchVersionCode
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug.$wifiWrenchVersionCode"
            // Secrets / keyAlias 仍用历史 APP3_* / app3-debug（绑定现有 keystore）；也接受 WIFI_WRENCH_DEBUG_*。
            val keyPath = providers.environmentVariable("WIFI_WRENCH_DEBUG_KEYSTORE")
                .orElse(providers.environmentVariable("APP3_DEBUG_KEYSTORE"))
                .orNull
            val keyPasswordValue = providers.environmentVariable("WIFI_WRENCH_DEBUG_STORE_PASSWORD")
                .orElse(providers.environmentVariable("APP3_DEBUG_STORE_PASSWORD"))
                .orNull
            if (!keyPath.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("debug").apply {
                    storeFile = file(keyPath)
                    storeType = "PKCS12"
                    storePassword = keyPasswordValue
                    keyAlias = "app3-debug" // keystore 内别名，改名需重签
                    keyPassword = keyPasswordValue
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    // Robolectric 原生图形下跑 Compose：工人页交互断言 + 截图证据（不需要模拟器）。
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // 仅测试：用 POI 读回导出的 xlsx，核对全量行数与原生图表引用（App 运行时不依赖 POI）。
    testImplementation("org.apache.poi:poi-ooxml:5.4.1")
}
