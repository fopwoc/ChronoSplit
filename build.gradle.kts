import dev.fopwoc.chronosplit.buildlogic.BuildIdentityResolver
import dev.fopwoc.chronosplit.buildlogic.GenerateBuildIdentityTask
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}

val buildIdentity = BuildIdentityResolver.resolve(rootProject)

allprojects {
    group = "dev.fopwoc.chronosplit"
    version = buildIdentity.version
}

subprojects {
    tasks.withType<Jar>().configureEach {
        manifest.attributes(
            "Implementation-Version" to buildIdentity.version,
            "Build-Number" to buildIdentity.buildNumber,
            "Build-Commit" to buildIdentity.commitHash,
        )
    }
}

tasks.register<GenerateBuildIdentityTask>("generateBuildIdentity") {
    version.set(buildIdentity.version)
    numericVersion.set(buildIdentity.numericVersion)
    buildNumber.set(buildIdentity.buildNumber)
    commitHash.set(buildIdentity.commitHash)
    outputDirectory.set(layout.buildDirectory.dir("generated/build-identity"))
}
