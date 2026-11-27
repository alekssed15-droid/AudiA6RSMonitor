plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.a6rs.monitor"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "ru.a6rs.monitor"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
        val mapkitKey = providers.gradleProperty("YANDEX_MAPKIT_API_KEY").orElse("").get()
        buildConfigField("String", "MAPKIT_API_KEY", "\"$mapkitKey\"")
    }
}

dependencies {
    implementation("com.yandex.android:maps.mobile:4.45.0-lite")
}
