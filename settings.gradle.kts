pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (Bangla OCR) is published on JitPack only.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Nirvor"
include(":app")
