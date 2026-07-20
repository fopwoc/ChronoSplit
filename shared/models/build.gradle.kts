plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvm()
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        commonMain.dependencies {
            api(libs.serialization)
            implementation(libs.datetime)
        }
        commonTest.dependencies {
            implementation(libs.test)
        }
    }
}
