plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bibliotube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bibliotube.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://buccrasmylvkzymbhfwx.supabase.co\"")
        // Publishable key: publica por diseno (RLS la protege). Nunca la service_role aqui.
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_By0vWY199stSDffMAtfvRw_Bb4QxrNs\"")
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.biometric)
    implementation(libs.zxing.core)
    implementation(project(":bt-downloader"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.workmanager)
    ksp(libs.room.compiler)
}
