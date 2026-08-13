plugins {
    id("com.android.application")
}

android {
    namespace = "com.extremeos.vpnonlybrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.extremeos.vpnonlybrowser"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    // Resolves Mozilla's newest stable GeckoView. Pin the resolved version before production release.
    implementation("org.mozilla.geckoview:geckoview:latest.release")
}
