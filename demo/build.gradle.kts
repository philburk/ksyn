import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

repositories {
    mavenCentral()
    mavenLocal() // Add this
    google()
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm("desktop")

    // Disable js because kc-audio-bridge does not support it yet.
//    js {
//        browser()
//        binaries.executable()
//    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(project(":ksyn"))
            implementation(project(":ksyn-compose"))
            implementation("com.mobileer:audio-bridge:0.1.0")

            // 1. The Core (Required): Basic navigation (Stack, push/pop)
            implementation("cafe.adriel.voyager:voyager-navigator:1.1.0-beta03")
            // 2. ScreenModel (Recommended): The "ViewModel" for Voyager
            // This is essential if you want to keep state (variables) separate from UI
            implementation("cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta03")
            // 3. Transitions (Optional): Animations like "Slide" or "Fade"
            implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta03")
            // 4. Koin Integration (Optional): If you use Koin for Dependency Injection
            implementation("cafe.adriel.voyager:voyager-koin:1.1.0-beta03")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")
        }
    }
}

android {
    namespace = "com.softsynth.ksyn"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.softsynth.ksyn"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.softsynth.ksyn.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.softsynth.ksyn"
            packageVersion = "1.0.0"
        }
    }
}

// Define a task that copies the required JS files from the kc-audio-bridge JAR
// into the build directory so the development server can find them.
val copyAudioBridgeJsFiles = tasks.register<Copy>("copyAudioBridgeJsFiles") {
    // Lazily get the wasmJs runtime classpath configuration.
    // This avoids resolving it during the configuration phase.
    val wasmJsRuntime = configurations.named("wasmJsRuntimeClasspath")

    // The 'from' action will now execute later, during the execution phase.
    // At this point, all dependencies (JARs and projects) are properly resolved.
    from(wasmJsRuntime.map { configuration ->
        // We find the specific JAR we need from the resolved files.
        configuration.files.filter { it.isFile && it.name.startsWith("audio-bridge") }
            .map { zipTree(it) }
    }) {
        // Only include the JS files we absolutely need from the JAR.
        include("kcab-webaudio.js")
        include("kcab-output-stream.js")
    }

    // Set the destination directory for the copied files.
    into(layout.buildDirectory.dir("processedResources/wasmJs/main"))
}

// This dependency hook remains the same.
tasks.named("wasmJsProcessResources") {
    dependsOn(copyAudioBridgeJsFiles)
}
