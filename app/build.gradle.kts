plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.douyin.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.douyin.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        versionName = "1.0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Haze blur for TopAppBar / BottomSheet / Player
    implementation("dev.chrisbanes.haze:haze:1.5.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")

    // ExoPlayer (Media3)
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")

    // FFmpegKit (community repackaged with native .so libraries, 16KB page-aligned)
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Core
    implementation("androidx.core:core-ktx:1.16.0")

    // DataStore（设置项持久化）
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Security (EncryptedSharedPreferences for token storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Douyin OpenSDK (OAuth 登录 + 开放平台 API)
    val openSdkVersion = "0.2.0.9"
    implementation("com.bytedance.ies.ugc.aweme:opensdk-china-external:$openSdkVersion")
    implementation("com.bytedance.ies.ugc.aweme:opensdk-common:$openSdkVersion")
}
