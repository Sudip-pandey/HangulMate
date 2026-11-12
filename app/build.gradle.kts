plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hangulmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hangulmate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.1"

        // Size: ship ARM only. x86/x86_64 native libs (ML Kit OCR pipeline,
        // TFLite JNI) are emulator-only weight — every real phone is ARM,
        // and dropping them saves ~15 MB of the ~84 MB APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        // Size: keep only the app's own locales (en default + ne).
        resourceConfigurations += listOf("en", "ne")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Size: the CI debug APK is what Byte installs, so R8 runs here.
        // Minify + resource shrinking cut the unminified Compose/Room/Hilt
        // dex and unused resources. Libraries (Hilt, Room, Compose,
        // CameraX, ML Kit, TFLite) ship their own consumer ProGuard rules;
        // proguard-rules.pro adds keeps for the app's own data layer.
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            // Size: same R8 treatment as debug (was OFF before).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Test distribution: signed with the debug key so the release
            // APK installs and updates alongside the debug APK (same
            // signature, no install conflict for Byte's friend). This is
            // NOT a Play Store release key — publishing needs a real
            // release keystore + GitHub secrets, a separate step.
            signingConfig = signingConfigs.getByName("debug")
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

    // Robolectric needs the app's Android resources packaged for unit
    // tests (the test classes run the Room database against a real
    // Application context via ApplicationProvider).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Room schema export is enabled in HangulMateDatabase (exportSchema = true).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Room's KSP compiler generates HangulMateDatabase_Impl; without it
    // Room fails at runtime ("Cannot find implementation") even though
    // compilation succeeds.
    ksp(libs.androidx.room.compiler)

    // Room is declared here so the data layer can use it; entities/DAOs/database
    // and repository bindings are owned by the data-layer agent.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // CameraX — used by the camera feature agent.
    implementation(libs.androidx.camerax.core)
    implementation(libs.androidx.camerax.camera2)
    implementation(libs.androidx.camerax.lifecycle)
    implementation(libs.androidx.camerax.view)

    // ML Kit Korean text recognition (on-device, bundled model — no
    // download). TFLite model is bundled in assets/ml/.
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // The Room tests build a real in-memory database via
    // ApplicationProvider.getApplicationContext(), which only works under
    // Robolectric in a local JVM unit test — without it every DB test
    // fails with "No instrumentation registered!".
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
