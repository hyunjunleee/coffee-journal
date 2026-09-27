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
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.work.runtime)
            // the detail map's renderer (MapLibre Native); common code only sees DetailMapRenderer
            implementation(libs.maplibre.compose)
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
// klibs linked into the framework, plus what Kotlin/Native and skiko carry without a POM of their own.
if (enableIos) {
    extra["noticesTarget"] = file("src/iosMain/kotlin/com/coffeejournal/ui/about/PlatformLibraries.ios.kt")
    extra["noticesConfiguration"] = "iosArm64CompileKlibraries"
    extra["noticesApp"] = "iOS app"
    extra["noticesBundled"] = listOf(
        listOf("org.jetbrains.kotlin", "kotlin-native-runtime", libs.versions.kotlin.get(), "Kotlin/Native runtime and standard library", "https://kotlinlang.org/", "Apache-2.0"),
        listOf("org.jetbrains.skia", "skia", "bundled in skiko", "Skia", "https://skia.org/", "BSD-3-Clause"),
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
