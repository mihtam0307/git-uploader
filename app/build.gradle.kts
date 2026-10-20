plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vc = (findProperty("vcode") as String?)?.toInt() ?: 1

android {
    namespace = "com.mihtam.uploader"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mihtam.uploader"
        minSdk = 24
        targetSdk = 34
        versionCode = vc
        versionName = "1.0.$vc"
    }
    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "uploader123"
            keyAlias = "uploader"
            keyPassword = "uploader123"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
