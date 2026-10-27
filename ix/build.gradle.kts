import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Version comes from the root buildscript classpath (the same Kotlin the app uses); declaring a
    // version here would conflict with it.
    id("org.jetbrains.kotlin.jvm")
}

/**
 * Ix is deliberately Android-free: no Context, no Uri, no Compose. It uses the same JDK 17 toolchain
 * as the app and produces JVM 17 bytecode, so the engine's tests run without the Android SDK.
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        explicitApi()
    }
}

dependencies {
    testImplementation(libs.junit)
}
