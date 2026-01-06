import org.gradle.api.tasks.Copy
import java.io.File
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // THE FIX IS HERE: Add the required Compose Compiler plugin
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dueckis.kawaiiraweditor"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.dueckis.kawaiiraweditor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.5"

        buildConfigField("String", "GITHUB_OWNER", "\"Duecki1\"")
        buildConfigField("String", "GITHUB_REPO", "\"IRIDIS\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    // XML ConstraintLayout used by widgets/layouts
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha11")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.code.gson:gson:2.10.1")
    // Newer versions include 16 KB page-size compatibility fixes for native libs on supported devices.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    // Override Compose transitive to ensure 16 KB-aligned native library packaging.
    implementation("androidx.graphics:graphics-path:1.1.0-beta01")
    implementation(libs.androidx.compose.runtime)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val rustAbiTargets = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
    "x86" to "i686-linux-android",
    "x86_64" to "x86_64-linux-android"
)

val androidApiLevel = 26
val rustProjectDir = rootProject.file("rust")
val ndkRoot = File(android.sdkDirectory, "ndk/${android.ndkVersion}")

val buildRust = tasks.register("buildRust") {
    group = "build"
    description = "Compiles the Rust native library for each Android ABI"
    doLast {
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val isWindows = osName.contains("windows")
        val hostTag = when {
            osName.contains("windows") -> "windows-x86_64"
            osName.contains("mac") -> "darwin-x86_64"
            else -> "linux-x86_64"
        }

        fun resolveTool(toolchainBin: File, baseName: String, vararg extensions: String): File {
            val candidates = buildList {
                extensions.forEach { ext -> add(File(toolchainBin, "$baseName$ext")) }
                add(File(toolchainBin, baseName))
            }
            return candidates.firstOrNull { it.exists() }
                ?: error("NDK tool not found: tried ${candidates.joinToString { it.absolutePath }}")
        }

        rustAbiTargets.forEach { (_, target) ->
            val normalizedTarget = target.uppercase(Locale.ROOT).replace('-', '_')
            val toolchainBin = File(ndkRoot, "toolchains/llvm/prebuilt/$hostTag/bin")
            val linkerTarget = if (target == "armv7-linux-androideabi") "armv7a-linux-androideabi" else target
            val clangBase = "${linkerTarget}${androidApiLevel}-clang"
            val clang = if (isWindows) resolveTool(toolchainBin, clangBase, ".cmd", ".exe") else resolveTool(toolchainBin, clangBase)
            val ar = if (isWindows) resolveTool(toolchainBin, "llvm-ar", ".exe") else resolveTool(toolchainBin, "llvm-ar")
            val ranlib = if (isWindows) resolveTool(toolchainBin, "llvm-ranlib", ".exe") else resolveTool(toolchainBin, "llvm-ranlib")

            exec {
                workingDir = rustProjectDir
                // Ensure ELF segments are aligned for 16 KB page-size devices.
                environment(
                    "RUSTFLAGS",
                    "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
                )
                environment("CARGO_TARGET_${normalizedTarget}_LINKER", clang.absolutePath)
                environment("CARGO_TARGET_${normalizedTarget}_AR", ar.absolutePath)
                environment("CARGO_TARGET_${normalizedTarget}_RANLIB", ranlib.absolutePath)
                commandLine("cargo", "build", "--release", "--target", target)
            }
        }
    }
}

val copyRustLibs = tasks.register<Copy>("copyRustLibs") {
    dependsOn(buildRust)
    into(File(projectDir, "src/main/jniLibs"))

    rustAbiTargets.forEach { (abi, target) ->
        from(File(rustProjectDir, "target/$target/release/libkawaiiraweditor.so")) {
            into(abi)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(copyRustLibs)
}
