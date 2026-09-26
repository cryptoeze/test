import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Play Store upload key: keystore.properties (local) or env vars (CI). Never commit it.
val uploadProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = uploadProps.getProperty(key) ?: System.getenv(key)
val hasUploadKey = signingValue("CRYPTOEZE_STORE_FILE")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.cryptoeze.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cryptoeze.app"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
    }

    signingConfigs {
        // Test key: only for APKs shared for testing, so each new build installs over the last.
        create("test") {
            storeFile = rootProject.file("signing/test.jks")
            storePassword = "cryptoeze-test"
            keyAlias = "cryptoeze-test"
            keyPassword = "cryptoeze-test"
        }
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(signingValue("CRYPTOEZE_STORE_FILE")!!)
                storePassword = signingValue("CRYPTOEZE_STORE_PASSWORD")
                keyAlias = signingValue("CRYPTOEZE_KEY_ALIAS")
                keyPassword = signingValue("CRYPTOEZE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "test")
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-messaging")
}
