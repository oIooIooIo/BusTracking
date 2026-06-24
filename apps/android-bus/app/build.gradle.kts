plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
    .getOrElse("http://10.0.2.2:8080/api/device/v1/")
val deviceApiKey = providers.gradleProperty("deviceApiKey")
    .getOrElse("demo-device-key")

android {
    namespace = "com.company.bustracking"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.company.bustracking"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0-demo"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "DEVICE_API_KEY", "\"$deviceApiKey\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
}
