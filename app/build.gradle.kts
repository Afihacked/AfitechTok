plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    applicationVariants.configureEach {
        val appName = "Tik Downloader No Watermark"
        val versionName = "1.3-beta"
        val versionCode = 4

        outputs.configureEach {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "${appName}_v${versionName}_(${versionCode}).apk"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.properties["STORE_FILE"]?.toString() ?: "")
            storePassword = project.properties["qwerty"]?.toString() ?: ""
            keyAlias = project.properties["afitech"]?.toString() ?: ""
            keyPassword = project.properties["qwerty"]?.toString() ?: ""
        }
    }
    buildFeatures {
        viewBinding = true
    }
    namespace = "com.afitech.tikdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.afitech.tikdownloader"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            isShrinkResources = true
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.ads.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Untuk support coroutine
    kapt(libs.room.compiler) // Jika menggunakan Kotlin KAPT
    implementation(libs.glide)
    implementation(libs.okhttp)
}