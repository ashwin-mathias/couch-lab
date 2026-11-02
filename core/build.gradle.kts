// Shared code for every Sparky app (3.0): models, Xtream, parsers, saved library, favorites,
// backup, settings, log, update check. Plain Kotlin/JVM, so nothing Android-only can creep in.
// Android TV and the desktop apps use it as is; iPhone/iPad (3.2) will turn it into a
// Kotlin Multiplatform module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // Streaming JSON for the big provider lists (the same reader Android's JsonReader came from).
    implementation("com.google.code.gson:gson:2.11.0")
    // org.json is built into Android; desktop apps add it themselves.
    compileOnly("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303")
}

tasks.test {
    useJUnitPlatform()
}
