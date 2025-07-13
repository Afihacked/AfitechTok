plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    applicationVariants.configureEach {
        val appName = "Sosmed Toolkit"
        val versionName = "3.1-beta"
        val versionCode = 10

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
    namespace = "com.afitech.sosmedtoolkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.afitech.sosmedtoolkit"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "3.1-beta"

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
    implementation (libs.lottie)
    implementation ("org.jsoup:jsoup:1.14.3")
    implementation(libs.fragmentKtx) // Menggunakan fragment-ktx versi 1.5.5
    implementation(libs.lifecycleViewModelKtx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.ads.api)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Untuk support coroutine
    kapt(libs.room.compiler) // Jika menggunakan Kotlin KAPT
    implementation(libs.glide)
    implementation(libs.okhttp)
    implementation(libs.lifecycle.viewmodel.ktx)
}