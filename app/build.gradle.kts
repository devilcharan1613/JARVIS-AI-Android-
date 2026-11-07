plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "ai.jarvis.offline"
 compileSdk = 35
 defaultConfig {
  applicationId = "ai.jarvis.offline"
  minSdk = 26
  targetSdk = 35
  versionCode = 5
  versionName = "0.5.0"
  ndk { abiFilters += listOf("arm64-v8a") }
  externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O3") } }
 }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.security:security-crypto:1.1.0-alpha06")
 implementation("com.alphacephei:vosk-android:0.3.75")
 testImplementation("junit:junit:4.13.2")
}
