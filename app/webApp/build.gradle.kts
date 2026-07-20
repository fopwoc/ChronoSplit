plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.shared.models)
            implementation(projects.shared.compose)
            implementation(libs.serialization)
            implementation(libs.composeRuntime)
            implementation(libs.composeUi)
            implementation(libs.composeFoundation)
        }
    }
}
