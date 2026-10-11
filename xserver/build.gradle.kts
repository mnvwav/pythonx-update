import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The X11 server itself: plain Kotlin with no Android dependencies, so it can be tested on a desktop JVM
// against real X clients (see HeadlessServer in the test sources).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

// Runs the server headless on DISPLAY=127.0.0.1:<display>, writing screenshots on request. Usage:
//   ./gradlew :xserver:headless --args="1 1280 800 /tmp/shots"
tasks.register<JavaExec>("headless") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.mnvstudio.x11.server.HeadlessServerKt")
}
