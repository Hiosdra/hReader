// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application").version("8.12.1") apply false
    id("org.jetbrains.kotlin.android").version("2.2.10") apply false
    id("org.jetbrains.kotlin.plugin.compose").version("2.2.10") apply false
    id("androidx.room") version "2.7.2" apply false
    id("com.github.ben-manes.versions") version "0.52.0" apply true
}

// Common versions used in multiple places
ext {
    set("roomVersion", "2.7.2")
    set("composeBomVersion", "2025.08.00")
}
