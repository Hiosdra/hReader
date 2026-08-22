import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.sentry.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.androidx.baselineprofile)
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

// Providers keep environment-backed values visible to Gradle's configuration cache. In
// particular, a cached debug configuration must not reuse an APK's old or empty DSN.
val sentryDsn = providers.environmentVariable("SENTRY_DSN")
    .orElse(providers.gradleProperty("sentryDsn"))
    .orElse("")
val sentryAuthToken = providers.environmentVariable("SENTRY_AUTH_TOKEN").orElse("")
val sentryOrg = providers.environmentVariable("SENTRY_ORG").orElse("")
val sentryProject = providers.environmentVariable("SENTRY_PROJECT").orElse("")
val sentryUploadEnabled = sentryAuthToken
    .zip(sentryOrg) { authToken, org ->
        authToken.isNotBlank() && org.isNotBlank()
    }
    .zip(sentryProject) { credentialsPresent, project ->
        credentialsPresent && project.isNotBlank()
    }

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
    // core-ktx 1.19 and the other AndroidX bumps require API 37.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.hiosdra.hreader"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
        resValue("string", "sentry_dsn", sentryDsn.get())

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
        resValues = true
    }
    packaging {
        jniLibs {
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libonnxruntime.so",
                "**/libsherpa-onnx-c-api.so",
                "**/libsherpa-onnx-cxx-api.so",
                "**/libsherpa-onnx-jni.so",
                "**/liblitertlm_jni.so"
            )
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
        generateKotlin = true
    }
}

// Configure KSP to export Room schemas
ksp {
    arg("room.incremental", "true")
}

sentry {
    debug.set(false)
    org.set(sentryOrg)
    projectName.set(sentryProject)
    authToken.set(sentryAuthToken)
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryUploadEnabled)
    uploadNativeSymbols.set(false)
    autoUploadNativeSymbols.set(false)
    includeNativeSources.set(false)
    includeSourceContext.set(false)
    tracingInstrumentation {
        enabled.set(false)
        logcat {
            enabled.set(false)
        }
    }
    autoInstallation {
        enabled.set(false)
    }
    includeDependenciesReport.set(false)
    telemetry.set(false)
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Paging: the article list is read a page at a time rather than as one list in memory.
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)

    // Installs the shipped ART profile on first run; without it the baseline profile is inert.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Compose BOM and related libraries
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Room Database
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit)

    // JSON Processing
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // Error reporting without the optional NDK and session-replay native modules.
    implementation(libs.sentry.android.core)

    // Dependency Injection (Koin)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose.navigation)
    implementation(libs.koin.androidx.workmanager)

    // Image Loading (Coil 3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)

    // Coroutines
    implementation(libs.coroutines.android)

    // HTML Parsing
    implementation(libs.jsoup)
    implementation(libs.commons.compress)

    // On-device speech synthesis
    implementation(files("libs/sherpa-onnx-1.13.4-arm64.aar"))

    // On-device Gemma inference
    implementation(libs.litert.lm.android)

    // Testing - JUnit
    testImplementation(libs.junit)

    // Testing - Android
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Testing - Compose
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Testing - Architecture
    testImplementation(libs.archunit)
    testImplementation(libs.archunit.junit4)

    // MockK for mocking in unit tests
    testImplementation(libs.mockk)

    // Debug Tools
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
