plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    android {
        namespace = "dev.fopwoc.chronosplit.shared.compose"
        compileSdk = libs.versions.androidSdk.get().toInt()
        minSdk = 26
    }
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.models)
            implementation(libs.composeRuntime)
            implementation(libs.composeUi)
            implementation(libs.composeFoundation)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.test)
        }
    }
}
