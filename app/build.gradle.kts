import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val targetAbi = providers.gradleProperty("targetAbi").orElse("arm64-v8a")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.qujindai.locowiki.flashrecall.v2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qujindai.locowiki.flashrecall"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += targetAbi.get() }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "VOICEPRINT_QA_PCM_ALLOWED", "true")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("boolean", "VOICEPRINT_QA_PCM_ALLOWED", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    androidResources {
        noCompress += listOf("onnx", "txt")
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.2.aar"))

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.room3:room3-runtime:3.0.0")
    implementation("androidx.sqlite:sqlite-bundled:2.5.0")
    ksp("androidx.room3:room3-compiler:3.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
