import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val secretsFile = rootProject.file("local.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val yandexMapsApiKey: String =
    secrets.getProperty("YANDEX_MAPS_API_KEY")
        ?: System.getenv("YANDEX_MAPS_API_KEY")
        ?: ""

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.screenmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared"))
        }
        androidMain.dependencies {
            implementation(libs.mapkit.android)
            implementation("androidx.activity:activity-compose:1.10.1")
        }
    }
}

android {
    namespace = "com.example.cleancity"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.cleancity"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "YANDEX_MAPS_API_KEY", "\"$yandexMapsApiKey\"")
        manifestPlaceholders["YANDEX_MAPS_API_KEY"] = yandexMapsApiKey
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
