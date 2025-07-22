plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.afitech.sosmedtoolkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.afitech.sosmedtoolkit"
        minSdk = 29
        targetSdk = 35
        versionCode = 13
        versionName = "4.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.properties["STORE_FILE"]?.toString() ?: "")
            storePassword = project.properties["qwerty"]?.toString() ?: ""
            keyAlias = project.properties["afitech"]?.toString() ?: ""
            keyPassword = project.properties["qwerty"]?.toString() ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            val appName = "Sosmed Toolkit"
            val versionName = "4.0-beta"
            val versionCode = 13
            val outputImpl = this as? com.android.build.gradle.api.ApkVariantOutput
            outputImpl?.outputFileName = "${appName}_v${versionName}_(${versionCode}).apk"
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
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.gridlayout)

    // ViewModel & Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.fragmentKtx)

    // AdMob
    implementation(libs.play.services.ads.api)

    // Media Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // UI & Animasi
    implementation(libs.lottie)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Parsing HTML
    implementation(libs.jsoup)

    // Glide & HTTP
    implementation(libs.glide)
    implementation(libs.okhttp)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("com.facebook.shimmer:shimmer:0.5.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    kapt ("com.github.bumptech.glide:compiler:4.16.0")

}
