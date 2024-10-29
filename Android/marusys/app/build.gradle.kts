plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.marusys.hesap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.marusys.hesap"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
//        kotlinCompilerExtensionVersion '1.3.2'
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.runtime)
    implementation(libs.core.ktx)
    implementation(libs.material3)
    implementation(libs.accompanist.themeadapter.material3)
    implementation(libs.ui.android)
    implementation(libs.lifecycle.service)
    implementation(libs.ui.tooling.preview.android)

    debugImplementation("androidx.compose.ui:ui-tooling")
    // 텐서플로 의존성
    implementation("org.tensorflow:tensorflow-lite:2.10.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.10.0")

    // 스펙토그램 변환을 위한 의존성
    implementation("com.github.wendykierp:JTransforms:3.1")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}