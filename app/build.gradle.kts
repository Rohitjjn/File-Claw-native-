import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Triggering fresh build packaging and clearing build state artifacts for successful installation
// Decode debug.keystore from base64 if it does not exist to ensure consistent signing across container instances
val keystoreFile = rootProject.file("debug.keystore")
val base64File = rootProject.file("debug.keystore.base64")
if (base64File.exists() && !keystoreFile.exists()) {
    try {
        val base64Content = base64File.readText().trim().replace("\\s".toRegex(), "")
        val decoded = Base64.getDecoder().decode(base64Content)
        keystoreFile.writeBytes(decoded)
        logger.quiet("Successfully decoded debug.keystore from debug.keystore.base64")
    } catch (e: Exception) {
        logger.error("Failed to decode debug.keystore: ${e.message}", e)
    }
}

android {
    namespace = "com.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aistudio.filesclaw.apxqzy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        register("debugSigning") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debugSigning")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debugSigning")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            pickFirsts.add("**/META-INF/DEPENDENCIES")
            pickFirsts.add("**/META-INF/LICENSE")
            pickFirsts.add("**/META-INF/LICENSE.txt")
            pickFirsts.add("**/META-INF/NOTICE")
            pickFirsts.add("**/META-INF/NOTICE.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Navigation and Serialization
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Image loading (Coil)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.coil)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
    implementation("org.jetbrains:markdown:0.7.3")

    // Archiving, Excel, CSV
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.junrar)
    implementation(libs.kotlin.csv)
    implementation(libs.fastexcel.reader)
    implementation(libs.fastexcel.writer)
    implementation(libs.zip4j)

    // Apache POI for DOCX manipulation is disabled for lightweight native alternative to speed up builds and avoid memory limit issues
    // implementation("org.apache.poi:poi-ooxml:5.2.5")
    // implementation("org.apache.poi:poi:5.2.5")
    // implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    // implementation("org.apache.commons:commons-compress:1.26.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
