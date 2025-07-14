plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.musicvisualizer.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.musicvisualizer.android"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
} 