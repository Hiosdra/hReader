import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("androidx.room")
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hiosdra.hreader"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
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
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.work:work-runtime-ktx:2.10.3")

    // Compose BOM and related libraries
    implementation(platform("androidx.compose:compose-bom:${rootProject.extra["composeBomVersion"]}"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Room Database
    implementation("androidx.room:room-ktx:${rootProject.extra["roomVersion"]}")
    implementation("androidx.room:room-runtime:${rootProject.extra["roomVersion"]}")
    ksp("androidx.room:room-compiler:${rootProject.extra["roomVersion"]}")

    // Networking
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")

    // JSON Processing
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // Dependency Injection (Koin)
    implementation(platform("io.insert-koin:koin-bom:4.1.0"))
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose-navigation")
    implementation("io.insert-koin:koin-androidx-workmanager")

    // Image Loading (Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.21.2")

    // Testing - JUnit
    testImplementation("junit:junit:4.13.2")

    // Testing - Android
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Testing - Compose
    androidTestImplementation(platform("androidx.compose:compose-bom:${rootProject.extra["composeBomVersion"]}"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Testing - Architecture
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.1")

    // MockK for mocking in unit tests
    testImplementation("io.mockk:mockk:1.14.5")

    // Debug Tools
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
