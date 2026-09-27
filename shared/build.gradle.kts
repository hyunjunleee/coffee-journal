import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The iOS targets: on a Mac with coffeejournal.enableIos=true (the Xcode build, iosApp/project.yml); on any host with
// coffeejournal.iosKlibs=true, which compiles the iOS code to klibs only (Kotlin/Native cross-compilation, enabled in
// gradle.properties) and is how Linux CI checks it (.github/workflows/ios.yml).
val enableIos = (findProperty("coffeejournal.enableIos") as String?).toBoolean() &&
    org.gradle.internal.os.OperatingSystem.current().isMacOsX ||
    (findProperty("coffeejournal.iosKlibs") as String?).toBoolean()

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    if (enableIos) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
        // The simulator test executable is linked by Kotlin/Native itself, so it needs the MapLibre framework that the
        // app gets from Swift Package Manager: CI passes the folder holding the simulator's MapLibre.framework
        // (.github/workflows/ios.yml, the release the Swift package pins).
        val maplibreFrameworkDir = findProperty("coffeejournal.maplibreFrameworkDir") as String?
        // The test executable is a debug link, which keeps maplibre-compose's reference to MapLibre's unexported
        // MLNScaleBar class: it gets the stand-in class the Debug app compiles (iosApp/iosApp/MLNScaleBarStub.m says
        // why), compiled here on the Mac. The tests never open the map.
        val scaleBarStubSource = rootProject.file("iosApp/iosApp/MLNScaleBarStub.m")
        val scaleBarStub = layout.buildDirectory.file("ios-test-stub/MLNScaleBarStub.o").get().asFile
        val compileScaleBarStub = tasks.register<Exec>("compileIosTestScaleBarStub") {
            inputs.file(scaleBarStubSource)
            outputs.file(scaleBarStub)
            doFirst { scaleBarStub.parentFile.mkdirs() }
            commandLine(
                "xcrun", "--sdk", "iphonesimulator", "clang", "-target", "arm64-apple-ios14.0-simulator",
                "-c", scaleBarStubSource.path, "-o", scaleBarStub.path,
            )
        }
        iosSimulatorArm64().binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            if (maplibreFrameworkDir != null) linkerOpts("-F$maplibreFrameworkDir", "-rpath", maplibreFrameworkDir)
            linkerOpts(scaleBarStub.path)
            linkTaskProvider.configure {
                dependsOn(compileScaleBarStub)
                doFirst {
                    if (maplibreFrameworkDir == null) {
                        throw GradleException("The iOS tests link MapLibre: pass -Pcoffeejournal.maplibreFrameworkDir=<folder with MapLibre.framework> (see .github/workflows/ios.yml)")
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            // the detail map (MapLibre Native behind maplibre-compose) on both platforms; on iOS the MapLibre framework
            // itself is linked by the Xcode project from Swift Package Manager (iosApp/project.yml)
            implementation(libs.maplibre.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.work.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.coffeejournal.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The iOS app's "출처 · 라이선스" list, generated like the Android one (gradle/third-party-notices.gradle.kts) from the
// klibs linked into the framework, plus what the app carries without a POM of its own: the Kotlin/Native runtime, the
// Skia build inside skiko, and MapLibre Native iOS, which the Xcode project links from its Swift package.
if (enableIos) {
    val maplibreIos = libs.versions.maplibreIos.get()
    // the list names the MapLibre framework the app links, so the Xcode project must pin that same version
    check(Regex("exactVersion: ${Regex.escape(maplibreIos)}\\s").containsMatchIn(rootProject.file("iosApp/project.yml").readText())) {
        "iosApp/project.yml must pin MapLibre Native iOS $maplibreIos (gradle/libs.versions.toml maplibreIos)"
    }
    extra["noticesTarget"] = file("src/iosMain/kotlin/com/coffeejournal/ui/about/PlatformLibraries.ios.kt")
    extra["noticesConfiguration"] = "iosArm64CompileKlibraries"
    extra["noticesApp"] = "iOS app"
    extra["noticesBundled"] = listOf(
        listOf("org.jetbrains.kotlin", "kotlin-native-runtime", libs.versions.kotlin.get(), "Kotlin/Native runtime and standard library", "https://kotlinlang.org/", "Apache-2.0"),
        listOf("org.jetbrains.skia", "skia", "bundled in skiko", "Skia", "https://skia.org/", "BSD-3-Clause"),
        listOf("github.com/maplibre/maplibre-gl-native-distribution", "MapLibre", maplibreIos, "MapLibre Native iOS", "https://github.com/maplibre/maplibre-native", "BSD-2-Clause"),
    )
    apply(from = rootProject.file("gradle/third-party-notices.gradle.kts"))
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    if (enableIos) {
        add("kspIosArm64", libs.room.compiler)
        add("kspIosSimulatorArm64", libs.room.compiler)
    }
}
