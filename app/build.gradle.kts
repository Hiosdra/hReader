plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
}

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

        buildConfigField("String", "MINIFLUX_BASE_URL", "\"https://miniflux.hiosdra.com\"")
        buildConfigField("String", "MINIFLUX_API_KEY", "\"2ddb13eaf3d0d19937d90d58d2864e76353e3dcab9d1755cb04dccabc39a63d0\"")
        buildConfigField("String", "CLOUDFLARE_CLIENT_ID", "\"7deac5772438964e5e8571c789fa32a7.access\"")
        buildConfigField("String", "CLOUDFLARE_CLIENT_SECRET", "\"cd2c46e1ad8514a31ac48ad25488adb66a2ca978d07436740b1a0559ac90df9b\"")
        buildConfigField("String", "OPENROUTER_KEY", "\"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\"")
    }

    buildTypes {
        release {
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
}

//noinspection UseTomlInstead
dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.browser:browser:1.9.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Moshi
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.3")

    // Koin
    implementation(platform("io.insert-koin:koin-bom:4.1.0"))
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose-navigation")
    implementation("io.insert-koin:koin-androidx-workmanager")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Accompanist
    implementation("com.google.accompanist:accompanist-pager:0.36.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ArchUnit
    testImplementation("com.tngtech.archunit:archunit:1.4.1")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.1")
}
