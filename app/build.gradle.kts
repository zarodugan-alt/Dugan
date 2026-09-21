import java.util.Properties

// VoIP signalling is opt-in: it is the one part of Dugan that needs an external
// project (Firebase). Keeping it behind a flag + its own source set means
// `./gradlew assembleDebug` works out of the box with zero configuration.
//
//   ./gradlew assembleDebug -Pdugan.firebase=true
//
val enableFirebase: Boolean =
    (project.findProperty("dugan.firebase") as? String)?.toBoolean() ?: false

// Optional debug-only key seeding from a gitignored `keys.properties`.
val localKeys = Properties().apply {
    val f = rootProject.file("keys.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.dugan.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dugan.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Never bake secrets into the APK by default; these are empty unless
        // keys.properties exists locally.
        buildConfigField("String", "SEED_GROQ_KEY", "\"${localKeys.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "SEED_GEMINI_KEY", "\"${localKeys.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("boolean", "FIREBASE_SIGNALLING", "$enableFirebase")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal-use build: sign with the debug key so `assembleRelease` installs.
            // Replace with a real keystore before distributing.
            signingConfig = signingConfigs.getByName("debug")
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
        )
    }

    testOptions {
        // Lets unit tests touch android.util.Log and friends without Robolectric.
        unitTests.isReturnDefaultValues = true
    }
}

// Applied after the android block is configured so the source set exists.
if (enableFirebase) {
    apply(plugin = "com.google.gms.google-services")
    android.sourceSets.getByName("main").java.srcDir("src/firebase/java")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.accompanist.permissions)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    if (enableFirebase) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.database)
        implementation(libs.kotlinx.coroutines.play.services)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
