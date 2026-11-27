plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.a6rs.monitor"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.a6rs.monitor"
        minSdk = 23
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }
}
