plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from environment variables set by CI (see .github/workflows/android.yml).
// Without them, builds are signed with the project's committed dev key (app/signing/vibepizzaiolo-dev.keystore),
// so every CI build has the same signature and installs over the previous one.
val releaseKeystore = System.getenv("VIBEPIZZAIOLO_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.vibepizzaiolo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vibepizzaiolo"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VIBEPIZZAIOLO_VERSION") ?: "0.1.0"
        // The community server, e.g. https://pizza.example.com. Empty: the community runs on the phone only.
        buildConfigField("String", "COMMUNITY_URL", "\"${System.getenv("VIBEPIZZAIOLO_COMMUNITY_URL") ?: ""}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = releaseKeystore
                storePassword = System.getenv("VIBEPIZZAIOLO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIBEPIZZAIOLO_KEY_ALIAS")
                keyPassword = System.getenv("VIBEPIZZAIOLO_KEY_PASSWORD")
            } else {
                storeFile = file("signing/vibepizzaiolo-dev.keystore")
                storePassword = "vibepizzaiolo"
                keyAlias = "vibepizzaiolo"
                keyPassword = "vibepizzaiolo"
            }
        }
        create("dev") {
            storeFile = file("signing/vibepizzaiolo-dev.keystore")
            storePassword = "vibepizzaiolo"
            keyAlias = "vibepizzaiolo"
            keyPassword = "vibepizzaiolo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("dev")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // the face models are memory-mapped straight from the APK
    androidResources { noCompress += "tflite" }
    // the community's data classes are shared with server/
    sourceSets["main"].kotlin.srcDir("../shared/src")
    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += listOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi", "NewerVersionAvailable")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // screenshots in the headless screen test are drawn the way a phone draws them
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // on-phone video editing for the pizza reel
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // MediaPipe's face detector and selfie segmenter, run with LiteRT, for your face on the pizzaiolo
    implementation("com.google.ai.edge.litert:litert:1.4.2")

    // Offline speech recognition
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // the community screens are driven end to end in a headless test
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
