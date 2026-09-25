plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.coffeejournal.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.coffeejournal.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Robolectric fetches android-all jars at runtime; use the Google mirror of Maven Central.
                test.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                test.systemProperty("robolectric.dependency.repo.id", "mavenCentralMirror")
                test.maxHeapSize = "2g"
            }
        }
    }
}

roborazzi {
    outputDir.set(file("screenshots"))
}

// The production database driver (BundledSQLiteDriver) loads a native SQLite. Its Android build cannot run on the
// test JVM, so the host build's library is unpacked here and handed to the driver through its documented system
// properties; tests that use the real platform module then run the same driver as the app.
val sqliteHostNatives: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}
val hostSqlite: Pair<String, String> = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm64" in it }) "arm64" else "x64"
    if ("mac" in os) "osx_$arch" to "libsqliteJni.dylib" else "linux_$arch" to "libsqliteJni.so"
}
val unpackSqliteHostNatives by tasks.registering(Sync::class) {
    from({ sqliteHostNatives.map { zipTree(it) } }) { include("natives/${hostSqlite.first}/**") }
    into(layout.buildDirectory.dir("sqlite-host"))
}
val sqliteHostDir = layout.buildDirectory.dir("sqlite-host/natives/${hostSqlite.first}")
tasks.withType<Test>().configureEach {
    dependsOn(unpackSqliteHostNatives)
    systemProperty("androidx.sqlite.driver.bundled.path", sqliteHostDir.get().asFile.absolutePath)
    systemProperty("androidx.sqlite.driver.bundled.name", hostSqlite.second)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)

    // JVM screenshot tests (Robolectric renders the real Compose screens without an emulator)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.material3)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.sqlite.framework)
    testImplementation(libs.room.runtime)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    sqliteHostNatives(libs.sqlite.bundled.jvm)
}
