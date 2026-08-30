import dev.fopwoc.chronosplit.buildlogic.BuildIdentityResolver

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.serialization)
    alias(libs.plugins.composeCompiler)
}

val buildIdentity = BuildIdentityResolver.resolve(rootProject)

android {
    namespace = "dev.fopwoc.chronosplit.android"
    compileSdk = libs.versions.androidSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.fopwoc.chronosplit.android"
        minSdk = 35
        targetSdk = libs.versions.androidSdk.get().toInt()
        versionCode = buildIdentity.buildNumber
        versionName = buildIdentity.version
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.app.appShared)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxMaterial3)
    implementation(libs.androidxNavigation3Runtime)
    implementation(libs.androidxNavigation3Ui)
    implementation(libs.androidxLifecycleViewModelCompose)
    implementation(libs.ktorClientCio)
}
