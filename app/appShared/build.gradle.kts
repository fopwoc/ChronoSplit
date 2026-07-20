plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    android {
        namespace = "dev.fopwoc.chronosplit.app.shared"
        compileSdk = libs.versions.androidSdk.get().toInt()
        minSdk = 26
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.models)
            api(projects.shared.compose)
            api(libs.androidxLifecycleViewModel)
            implementation(libs.composeRuntime)
            implementation(libs.composeUi)
            implementation(libs.room3Runtime)
            implementation(libs.sqliteBundled)
            implementation(libs.coroutines)
            implementation(libs.ktorClientCore)
            implementation(libs.ktorClientContentNegotiation)
            implementation(libs.ktorClientWebsockets)
            implementation(libs.ktorSerializationJson)
        }
        commonTest.dependencies {
            implementation(libs.test)
            implementation(libs.coroutines.test)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.room3Compiler)
    add("kspAndroid", libs.room3Compiler)
    add("kspIosArm64", libs.room3Compiler)
    add("kspIosSimulatorArm64", libs.room3Compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
