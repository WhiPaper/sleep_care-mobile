plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Wear OS 워치 앱 모듈입니다.
// 휴대폰 명령을 받아 전면 서비스로 심박 추적을 수행하고 Data Layer로 샘플을 보냅니다.
android {
    namespace = "com.sleepcare.watch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sleepcare.watch"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    // 모바일 앱과 같은 워치 메시지 경로/모델/코덱을 사용합니다.
    implementation(project(":watch-contracts"))
    // 실제 센서 SDK AAR을 libs 폴더에 넣으면 이 모듈에서 함께 패키징됩니다.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
