plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

// Robolectric's Android runtime jars are downloaded by Gradle (which retries) and then used offline.
// One configuration per jar: both are versions of the same artifact, and a single configuration
// would let Gradle's conflict resolution keep only the newest one.
val robolectricRuntime34: Configuration by configurations.creating
val robolectricRuntime35: Configuration by configurations.creating
val robolectricDir = layout.buildDirectory.dir("robolectric-runtime")
val copyRobolectricRuntime = tasks.register<Copy>("copyRobolectricRuntime") {
    from(robolectricRuntime34, robolectricRuntime35)
    into(robolectricDir)
    // ~300 MB: only download when the jars aren't there yet.
    onlyIf { (robolectricDir.get().asFile.listFiles { f -> f.name.endsWith(".jar") }?.size ?: 0) < 2 }
}

android {
    namespace = "io.github.mustypit.rifttracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.mustypit.rifttracker"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // Updates only install over the old app when every APK has the same signing certificate.
    // GitHub runners start clean, so the key is passed to Gradle explicitly: CI decodes the
    // SIGNING_KEYSTORE secret to a file and sets SIGNING_KEYSTORE_PATH. (Putting a key in
    // ~/.android/debug.keystore was ignored on the runners.) Never change this key, the
    // applicationId or this setup; CI checks the APK's certificate fingerprint after building.
    signingConfigs {
        System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
            create("fixed") {
                storeFile = file(path)
                // The keystore's own password; the secret is the keystore file itself.
                storePassword = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
                keyPassword = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
            }
        }
    }
    buildTypes {
        getByName("debug") { signingConfigs.findByName("fixed")?.let { signingConfig = it } }
        getByName("release") { signingConfigs.findByName("fixed")?.let { signingConfig = it } }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        // Lets Robolectric load the app's real resources, theme and manifest in JVM tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Full error messages in the build log, so CI failures can be diagnosed from the log.
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.dependsOn(copyRobolectricRuntime)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
        }
    }
}

roborazzi {
    // Screenshots from `./gradlew recordRoborazziDebug` land here.
    outputDir.set(file("screenshots"))
}

ksp {
    // Keeps a record of every database version so schema changes can migrate instead of wiping match history.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    // JVM-side Android: run real screens/database without a device, and render screenshots.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.40.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Android 14 runtime for Robolectric (matches robolectric 4.14.1 and @Config(sdk = [34])).
    robolectricRuntime34("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7") { isTransitive = false }
    // Android 15 runtime: Robolectric reads the app's resources with the target SDK's runtime.
    robolectricRuntime35("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7") { isTransitive = false }
}
