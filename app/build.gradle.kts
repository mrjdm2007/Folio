import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing: create keystore.properties next to settings.gradle.kts (see README). Debug builds don't need it.
val ksFile = rootProject.file("keystore.properties")
val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }

android {
    namespace = "com.folio.app"
    compileSdk = 34
    defaultConfig { applicationId = "com.folio.app"; minSdk = 26; targetSdk = 34; versionCode = 5; versionName = "0.5" }
    signingConfigs {
        if (ksFile.exists()) create("release") {
            storeFile = file(ks.getProperty("storeFile"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false   // kept off so serialization/ML Kit can't break; turn on later with proper rules
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.mlkit:text-recognition:16.0.0")
}

// Downloads KaTeX into the app's assets at build time so equations render fully offline.
// If you are offline during the build it is skipped and the app falls back to the CDN at run time.
val katexDir = file("src/main/assets/katex")
tasks.register("downloadKatex") {
    doLast {
        val base = "https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/"
        val fonts = listOf("KaTeX_AMS-Regular", "KaTeX_Caligraphic-Bold", "KaTeX_Caligraphic-Regular", "KaTeX_Fraktur-Bold",
            "KaTeX_Fraktur-Regular", "KaTeX_Main-Bold", "KaTeX_Main-BoldItalic", "KaTeX_Main-Italic", "KaTeX_Main-Regular",
            "KaTeX_Math-BoldItalic", "KaTeX_Math-Italic", "KaTeX_SansSerif-Bold", "KaTeX_SansSerif-Italic", "KaTeX_SansSerif-Regular",
            "KaTeX_Script-Regular", "KaTeX_Size1-Regular", "KaTeX_Size2-Regular", "KaTeX_Size3-Regular", "KaTeX_Size4-Regular",
            "KaTeX_Typewriter-Regular").map { "fonts/$it.woff2" }
        (listOf("katex.min.js", "katex.min.css") + fonts).forEach { f ->
            val out = File(katexDir, f)
            if (!out.exists()) {
                out.parentFile.mkdirs()
                try { java.net.URL(base + f).openStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } } }
                catch (e: Exception) { println("KaTeX download skipped for $f: ${e.message}") }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("downloadKatex") }
