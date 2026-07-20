plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.shared.compose)
            implementation(projects.shared.server)
            implementation(compose.desktop.currentOs)
            implementation(libs.ktorServerNetty)
            implementation(libs.coroutines)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.fopwoc.chronosplit.jvmapp.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "ChronoSplit"
            packageVersion = "1.0.0"
            macOS { bundleID = "dev.fopwoc.chronosplit.jvmapp" }
        }
    }
}
