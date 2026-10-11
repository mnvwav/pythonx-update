plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mnvstudio.x11"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnvstudio.x11"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // The built-in Linux runs on these ABIs (proot builds below).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    packaging {
        jniLibs {
            // proot is executed from the native library directory, so it must be extracted at install time.
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":xserver"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
}

// proot (GPL-2.0, https://proot-me.github.io) runs the built-in Alpine Linux without root. Static Android builds come
// from green-green-avk/build-proot-android and are stored as jniLibs, the only place Android lets apps execute files.
// Override the source with -PprootBaseUrl=... if needed.
val prootAbis = mapOf("arm64-v8a" to "aarch64", "x86_64" to "x86_64")
val prootBaseUrl = providers.gradleProperty("prootBaseUrl")
    .orElse("https://raw.githubusercontent.com/green-green-avk/build-proot-android/master/packages")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

val fetchProot by tasks.registering {
    description = "Downloads proot for the built-in Linux into src/main/jniLibs"
    val base = prootBaseUrl
    val outDir = jniLibsDir.asFile
    outputs.dir(outDir)
    onlyIf { prootAbis.keys.any { !File(outDir, "$it/libproot.so").exists() } }
    doLast {
        for ((abi, arch) in prootAbis) {
            val dir = File(outDir, abi).apply { mkdirs() }
            if (File(dir, "libproot.so").exists()) continue
            val archive = File(temporaryDir, "proot-$arch.tar.gz")
            uri("${base.get()}/proot-android-$arch.tar.gz").toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
            val unpacked = File(temporaryDir, arch).apply { deleteRecursively() }
            project.copy { from(project.tarTree(project.resources.gzip(archive))); into(unpacked) }
            val files = unpacked.walkTopDown().filter { it.isFile }.toList()
            val proot = files.firstOrNull { it.name == "proot" } ?: error("proot not found in $archive")
            val loader = files.firstOrNull { it.name == "loader" } ?: error("proot loader not found in $archive")
            proot.copyTo(File(dir, "libproot.so"), true)
            loader.copyTo(File(dir, "libproot-loader.so"), true)
            files.firstOrNull { it.name == "loader32" }?.copyTo(File(dir, "libproot-loader32.so"), true)
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchProot) }
