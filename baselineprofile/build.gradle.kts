plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.hiosdra.hreader.baselineprofile"
    // Matches the app: the AndroidX releases it depends on refuse to be consumed by anything
    // compiled against an older API.
    compileSdk = 37
    compileSdkMinor = 1

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark drives a real build, so it needs a device or emulator running API 29+.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

// A profile is only meaningful when it is collected on a device that is neither throttled nor
// debuggable, which is what the plugin checks for before it records anything.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
