import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The MovieHub reference catalogue API (PRD §7).
 *
 * A standalone JVM service — deliberately NOT an Android module — that implements the exact
 * `CatalogApi` contract the app already knows how to call. Pointing
 * `BuildConfig.API_BASE_URL` at this server is all it takes for the app to fetch its
 * catalogue over the network instead of from the bundled asset.
 *
 * Runs on the same JDK/Gradle toolchain the app already uses: `./gradlew :server:run`.
 */
plugins {
    // No version: AGP 9 already puts the Kotlin Gradle plugin on the build classpath, so
    // this module inherits that same Kotlin rather than pinning a second, conflicting one.
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    // SLF4J backend so Ktor's startup/request logs render instead of a "no providers" warning.
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("com.example.streamingappzb.server.ApplicationKt")
}

kotlin {
    compilerOptions {
        // Target 17 to match the app; compiled by whatever JDK runs Gradle (the Studio JBR),
        // so no Java toolchain is provisioned/downloaded.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Keep the (source-less) Java compile at the same target as Kotlin, so Gradle's JVM-target
// consistency check passes without provisioning a Java toolchain.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The app's bundled catalogue is the single source of truth. Copy it into this module's
// resources at build time so the server and the app can never drift out of sync.
val syncCatalog by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/assets/catalog.json"))
    into(layout.buildDirectory.dir("generated/catalog"))
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/catalog"))
    }
}

tasks.named("processResources") {
    dependsOn(syncCatalog)
}
