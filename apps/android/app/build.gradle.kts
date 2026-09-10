plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alfietv.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alfietv.player"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        getByName("debug")

        // Release/Play upload signing is injected by CI environment variables.
        // The keystore and passwords are never stored in the repository.
        val releaseStoreFile = System.getenv("ALFIE_RELEASE_STORE_FILE")
        val releaseStorePassword = System.getenv("ALFIE_RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = System.getenv("ALFIE_RELEASE_KEY_ALIAS")
        val releaseKeyPassword = System.getenv("ALFIE_RELEASE_KEY_PASSWORD")

        if (!releaseStoreFile.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()
        ) {
            create("playRelease") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val playSigning = signingConfigs.findByName("playRelease")
            signingConfig = playSigning ?: throw GradleException(
                "Release builds must use the stable Alfie TV release/upload key. " +
                    "Set ALFIE_RELEASE_STORE_FILE, ALFIE_RELEASE_STORE_PASSWORD, " +
                    "ALFIE_RELEASE_KEY_ALIAS and ALFIE_RELEASE_KEY_PASSWORD."
            )
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val media3 = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.activity:activity-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
}
