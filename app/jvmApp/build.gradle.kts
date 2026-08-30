import dev.fopwoc.chronosplit.buildlogic.BuildIdentityResolver

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.composeCompiler)
}

val buildIdentity = BuildIdentityResolver.resolve(rootProject)

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
            runtimeOnly(libs.logback)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.fopwoc.chronosplit.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "ChronoSplit"
            packageVersion = buildIdentity.numericVersion
            macOS { bundleID = "dev.fopwoc.chronosplit.jvmapp" }
        }
    }
}
