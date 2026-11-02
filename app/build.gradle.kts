plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// A new ZIP unzipped on top of an older Nirvor folder leaves a second copy of the code in
// app/src/main/java (versions up to 1.0 kept the Kotlin code there). Two copies break the build,
// so stop early with a clear message instead of a confusing Room/KSP error.
if (file("src/main/java/com/nirvor/app").exists()) {
    throw GradleException(
        "\n\nপুরোনো ভার্সনের কোড পাওয়া গেছে: app/src/main/java ফোল্ডারটা মুছে দিন,\n" +
            "তারপর Build → Clean Project করে আবার Run করুন।\n" +
            "(Delete the old app/src/main/java folder, then Clean Project and build again.)\n"
    )
}

android {
    namespace = "com.nirvor.app"
    compileSdk = 35

    val appVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 13
    val appVersionName = (project.findProperty("versionName") as? String) ?: "2.1.1"

    defaultConfig {
        applicationId = "com.nirvor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // The download engine (yt-dlp + Python + FFmpeg) ships native code; real phones are ARM.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = listOf(
                file("release.jks"),
                file("../release.jks"),
                file("release.keystore"),
                file("../release.keystore"),
                System.getenv("KEYSTORE_FILE")?.let { file(it) }
            ).firstOrNull { it != null && it.exists() }

            if (keystoreFile != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "my-alias"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
            }
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

    buildFeatures {
        compose = true
    }

    // All app code is Kotlin and lives in src/main/kotlin.
    sourceSets["main"].java.srcDirs("src/main/kotlin")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
        // youtubedl-android runs Python/FFmpeg from the extracted native libraries.
        jniLibs {
            useLegacyPackaging = true
            // OCR (Tesseract) and the download engine may both ship the C++ runtime.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Download manager: yt-dlp engine (YouTube, Facebook, Instagram, TikTok, …) + FFmpeg for merging/MP3
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    // Thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Browser: incognito profiles / header control, pull-to-refresh, QR scanner
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Tool Box — camera tools (live preview, photo, frame analysis)
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Tool Box — AR measuring (optional: phones without ARCore still install and use the app)
    implementation("com.google.ar:core:1.47.0")
    // Tool Box — on-device recognition, models are inside the app (work offline)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3") // making QR codes
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // Tool Box — OCR for Bangla + English (language data is downloaded once, a few MB)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // Tool Box — Office: join / split PDFs and read the text of PDF pages (Word, PDF, Excel files are written by the app's own Kotlin code)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
