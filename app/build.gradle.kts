import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.starrootisle.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.starrootisle.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Emulator → host; override in local.properties: ONLINE_URL=ws://LAN_IP:8790
        val localProps = Properties()
        val lp = rootProject.file("local.properties")
        if (lp.exists()) lp.inputStream().use { localProps.load(it) }
        val onlineUrl = (localProps.getProperty("ONLINE_URL")
            ?: System.getenv("ONLINE_URL")
            ?: "ws://10.0.2.2:8790").replace("\"", "")
        buildConfigField("String", "ONLINE_URL", "\"$onlineUrl\"")
    }

    // Release keystore (generated locally; see keystore/ if missing)
    val releaseStore = rootProject.file("keystore/starroot-release.jks")
    val debugStore = file(System.getProperty("user.home") + "/.android/debug.keystore")
    signingConfigs {
        create("release") {
            if (releaseStore.exists()) {
                storeFile = releaseStore
                storePassword = "starroot"
                keyAlias = "starroot"
                keyPassword = "starroot"
            } else {
                // Fallback so assembleRelease always produces an installable APK
                storeFile = debugStore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
}
