plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version "2.1.20-2.0.0"
}

android {
    namespace = "com.hiosdra.hreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hiosdra.hreader"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MINIFLUX_BASE_URL", "\"https://miniflux.hiosdra.com\"")
        buildConfigField("String", "MINIFLUX_API_KEY", "\"pvHuBUHWfOhf-CQkdqAeETF9FORFPm3HskWIzR3fX4o=\"")
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.browser:browser:1.8.0")

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
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    annotationProcessor("androidx.room:room-compiler:2.7.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Koin
    implementation(platform("io.insert-koin:koin-bom:4.0.4"))
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose-navigation")
    implementation("io.insert-koin:koin-androidx-workmanager")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Accompanist
    implementation("com.google.accompanist:accompanist-pager:0.36.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
