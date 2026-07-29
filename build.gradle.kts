// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin itself and pins its own KGP; the classpath entry lifts
// that pin to the version the Compose compiler plugin below is built against.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application").version("9.3.1") apply false
    id("org.jetbrains.kotlin.plugin.compose").version("2.4.10") apply false
    id("androidx.room") version "2.8.4" apply false
}

// Common versions used in multiple places
ext {
    set("roomVersion", "2.8.4")
    set("composeBomVersion", "2026.06.01")
}
