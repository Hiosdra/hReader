import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.10"
    id("androidx.room")
    id("androidx.baselineprofile")
}


// Signing credentials come from keystore/keystore.properties locally and from
// environment variables on CI. Release builds stay unsigned when neither is
// present, so a plain checkout still builds.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String): String? =
    (System.getenv(key) ?: keystoreProperties.getProperty(key))?.takeIf { it.isNotBlank() }

val releaseStoreFile = rootProject.file(
    signingValue("RELEASE_KEYSTORE_PATH") ?: "keystore/release.jks"
)
val releaseStorePassword = signingValue("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile.exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

// The debug keystore is not a secret — Google fixes its password and alias — but
// it has to be the same everywhere, otherwise a CI build cannot update an app
// installed from a different build. CI decodes it from a secret; without the file
// AGP falls back to the local ~/.android/debug.keystore.
val debugStoreFile = rootProject.file(
    signingValue("DEBUG_KEYSTORE_PATH") ?: "keystore/debug.keystore"
)

android {
    namespace = "com.hiosdra.hreader"
    // core-ktx 1.19 and the other AndroidX bumps refuse to be consumed by an
    // app compiled against anything older than API 37. targetSdk stays at 36
    // until the Android 17 behaviour changes get a look on a real device.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.hiosdra.hreader"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        if (debugStoreFile.exists()) {
            getByName("debug") {
                storeFile = debugStoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 on: an unshrunk Compose build ships every unused library class and skips the
            // optimisation passes the runtime benefits most from.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
        generateKotlin = true
    }
}

// Configure KSP to export Room schemas
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

//noinspection UseTomlInstead
dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Paging: the article list is read a page at a time rather than as one list in memory.
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-paging:${rootProject.extra["roomVersion"]}")

    // Installs the shipped ART profile on first run; without it the baseline profile is inert.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    // Compose BOM and related libraries
    implementation(platform("androidx.compose:compose-bom:${rootProject.extra["composeBomVersion"]}"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // material3 no longer brings the icons along and the BOM stopped managing
    // them, so the version is pinned by hand. 1.7.8 is the last release there
    // will ever be; the way out is redrawing them as Material Symbols vectors.
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Room Database
    implementation("androidx.room:room-ktx:${rootProject.extra["roomVersion"]}")
    implementation("androidx.room:room-runtime:${rootProject.extra["roomVersion"]}")
    ksp("androidx.room:room-compiler:${rootProject.extra["roomVersion"]}")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")

    // JSON Processing
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // Dependency Injection (Koin)
    implementation(platform("io.insert-koin:koin-bom:4.2.2"))
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose-navigation")
    implementation("io.insert-koin:koin-androidx-workmanager")

    // Image Loading (Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.apache.commons:commons-compress:1.28.0")

    // On-device speech synthesis
    implementation(files("libs/sherpa-onnx-1.13.4-arm64.aar"))

    // Testing - JUnit
    testImplementation("junit:junit:4.13.2")

    // Testing - Android
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Testing - Compose
    androidTestImplementation(platform("androidx.compose:compose-bom:${rootProject.extra["composeBomVersion"]}"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Testing - Architecture
    testImplementation("com.tngtech.archunit:archunit:1.4.2")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.2")

    // MockK for mocking in unit tests
    testImplementation("io.mockk:mockk:1.14.11")

    // Debug Tools
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
