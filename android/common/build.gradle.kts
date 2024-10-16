import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.marusys.common"
    compileSdk = 34

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
}

dependencies {
    // AndroidX Core
    api(libs.androidx.core.ktx)
    // App compat and UI
    api(libs.androidx.appcompat)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.constraintlayout)
    api(libs.material)

    // Compose UI
    api(libs.androidx.ui)
    api(libs.androidx.material3)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.ui.graphics)
    // Compose Runtime
    api(libs.androidx.runtime)
    api(libs.androidx.runtime.android)

    // Navigation
    api(libs.androidx.navigation.common.ktx)
    api(libs.androidx.navigation.compose)

    // Lifecycle
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}