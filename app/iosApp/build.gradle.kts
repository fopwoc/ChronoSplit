plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ChronoSplitIosApp"
            isStatic = true
            export(projects.app.appShared)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.app.appShared)
        }
        iosMain.dependencies {
            implementation(libs.ktorClientDarwin)
            implementation(libs.ktorClientWebsockets)
        }
    }
}
