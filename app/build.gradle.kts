plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.afitech.afitechtok"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.afitech.afitechtok"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        val buildNumber = File(rootDir, "build_number.txt").let {
            if (!it.exists()) it.writeText("1")
            val num = it.readText().trim().toInt()
            it.writeText((num + 1).toString())
            num
        }

        versionName = "1.2.$buildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.properties["STORE_FILE"]?.toString() ?: "")
            storePassword = project.properties["STORE_PASSWORD"]?.toString() ?: ""
            keyAlias = project.properties["KEY_ALIAS"]?.toString() ?: ""
            keyPassword = project.properties["KEY_PASSWORD"]?.toString() ?: ""
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

    applicationVariants.all {

        val appName = "AfitechTok"
        val vName = versionName

        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "${appName}_v${vName}.apk"
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

    // UI & Animasi
    implementation(libs.lottie)
    implementation(libs.androidx.swiperefreshlayout)

    // Parsing HTML
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation (libs.shimmer)
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    kapt ("com.github.bumptech.glide:compiler:4.16.0")

    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")

//    implementation("com.google.android.gms:play-services-ads:25.0.0")
}
tasks.register("releaseFull") {

    dependsOn("assembleRelease")

    doLast {

        val versionName = android.defaultConfig.versionName
        val tag = "v$versionName"

        println("🚀 Releasing $tag")

        exec { commandLine("git", "add", ".") }

        exec {
            commandLine("git", "commit", "-m", "Release $tag")
            isIgnoreExitValue = true
        }

        exec { commandLine("git", "tag", tag) }
        exec { commandLine("git", "push") }
        exec { commandLine("git", "push", "origin", tag) }
    }
}