plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.softbuddy.pythonx"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.softbuddy.pythonx"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0"
        // The official CPython Android builds exist for these two ABIs only.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    packaging {
        jniLibs {
            // python3 and the runtime are executed from nativeLibraryDir, so they must be extracted at install.
            useLegacyPackaging = true
            // A zip archive, not an ELF library; stripping would fail.
            keepDebugSymbols += "**/libpystdlib.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.sora.editor)
    implementation(libs.sora.textmate)
    implementation(project(":terminal"))
}
