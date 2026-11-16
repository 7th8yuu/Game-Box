plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.gamebox.helper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gamebox.helper"
        // versionCode tăng theo số lần chạy CI để bản mới luôn cài đè được lên bản cũ
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        minSdk = 29; targetSdk = 33; versionCode = run; versionName = "1.0.$run"
    }
    // Khóa ký cố định: mọi bản build (debug lẫn release) cùng chữ ký nên cài đè không cần gỡ app cũ.
    // Đây là khóa dùng riêng cho app này, nằm trong repo công khai: ai có khóa đều ký được APK cài đè lên app.
    signingConfigs {
        create("gamebox") {
            storeFile = file("gamebox.keystore")
            storePassword = "fbe97d018bcca4808c283b99"
            keyAlias = "gamebox"
            keyPassword = "fbe97d018bcca4808c283b99"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("gamebox") }
        getByName("release") { signingConfig = signingConfigs.getByName("gamebox") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("dev.chrisbanes.haze:haze:1.2.0")
}
