plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
    .orElse(providers.environmentVariable("MOBILE_API_BASE_URL"))
    .orNull ?: error("MOBILE_API_BASE_URL or -PapiBaseUrl must be set")
val deviceApiKey = providers.gradleProperty("deviceApiKey")
    .orElse(providers.environmentVariable("DEVICE_API_KEY"))
    .orNull ?: error("DEVICE_API_KEY or -PdeviceApiKey must be set")
val usesCleartextTraffic = providers.gradleProperty("usesCleartextTraffic")
    .orElse(providers.environmentVariable("MOBILE_USES_CLEARTEXT"))
    .getOrElse("false")

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
        manifestPlaceholders["usesCleartextTraffic"] = usesCleartextTraffic
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
